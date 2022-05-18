package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.EnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.ParsedEnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.PluginConfiguration;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopePlugin;
import com.cloudbees.jenkins.plugins.updates.envelope.Validation;
import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.Plugin;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Provides configuration bundle reload support.
 */
@SuppressRestrictedWarnings({CloudBeesAssurance.class, Beekeeper.class})
@Extension
@Restricted({NoExternalUse.class})
public class ConfigurationBundleService {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationBundleService.class.getName());

    // timeout for update site to be refresh after catalog installation
    static int TIMEOUT_CATALOG_SECONDS = 50;

    /**
     * Checks if the bundle can be applied without a restart.
     * A bundle cannot be considered as potentially applied without restart if:
     *
     *   The bundle is null
     *   There is at least one plugin requested to be installed which is already installed and an update is offered for it
     *   The catalog cannot be installed
     *
     * @param bundle The candidate bundle
     * @return true if the hot reload can be done
     */
    public boolean isHotReloadable(ConfigurationBundle bundle) {
        // Not possible but
        if (bundle == null) {
            return false;
        }

        // If there is no plugin to install, then can be reloaded
        if (bundle.getPlugins().isEmpty()) {
            return true;
        }

        // Try to load the plugins on the new catalog, if exists.
        Map<String, PluginConfiguration.Expanded> pluginsInCatalog;
        EnvelopeExtension catalog = bundle.getEnvelopeExtension();
        if (catalog != null) {
            List<String> errors;

            try {
                errors = BeekeeperRemote.get().validateExtension(catalog.getMetadata(), null);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Plugin Catalog cannot be validated because of {0}. Configuration Bundle cannot be reloaded.", e.getMessage());
                LOGGER.log(Level.FINE, "Plugin Catalog cannot be validated", e);
                return false;
            }

            if (!errors.isEmpty()) {
                LOGGER.log(Level.WARNING, "The Plugin Catalog cannot be validated because of " + errors);
                return false;
            }

            try {
                Validation<ParsedEnvelopeExtension.Provided> validation = ParsedEnvelopeExtension.loader().fromJSON(catalog.getMetadata());
                Envelope envelope = CloudBeesAssurance.get().getBeekeeper().getEnvelope();
                pluginsInCatalog = validation.get().expand(envelope).get().getConfiguration().getInclude();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Plugin Catalog cannot be loaded because of {0}. Configuration Bundle cannot be reloaded.", e.getMessage());
                LOGGER.log(Level.FINE, "Plugin Catalog cannot be loaded", e);
                return false;
            }
        } else {
            pluginsInCatalog = Collections.EMPTY_MAP;
        }

        // List of plugins in the envelope
        Map<String, EnvelopePlugin> pluginsInEnvelope = CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins();

        Set<String> envelopeAndCatalogPlugins = Sets.newHashSet(pluginsInCatalog.keySet());
        envelopeAndCatalogPlugins.addAll(pluginsInEnvelope.keySet());

        // Remove all plugins not included with the envelope or catalog (it is the same what we are doing during the startup
        Set<String> plugins = ConfigurationUpdaterHelper.getOnlyPluginsInEnvelope(bundle.getPlugins(), envelopeAndCatalogPlugins);

        Set<String> alreadyInstalledPlugins = Sets.newHashSet();

        // Split the list in installed/uninstalled plugins
        Set<String> installed = Jenkins.get().getPluginManager().getPlugins().stream().map(p -> p.getShortName()).collect(Collectors.toSet());
        for (String plugin : plugins) {
            if (installed.contains(plugin)) {
                alreadyInstalledPlugins.add(plugin);
            }
        }

        // No other plugin but plugins in the catalog can offer an update
        for (String plugin : alreadyInstalledPlugins) {
            if (pluginsInCatalog.containsKey(plugin)) {
                PluginConfiguration.Expanded pic = pluginsInCatalog.get(plugin);
                Plugin puc = Jenkins.get().getPlugin(plugin);
                if (puc != null) {
                    String picVersion = pic.getPluginEntry().getVersion();
                    String pucVersion = puc.getWrapper().getVersion();
                    if (!Objects.equals(picVersion, pucVersion)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Reload configuration bundle if the hotReloadable flag is enabled.
     * @param bundle The configuration bundle
     * @throws IOException if the issue is related to bundle transport
     * @throws CasCException if the issue is related to bundle itself
     */
    public void reloadIfIsHotReloadable(ConfigurationBundle bundle) throws IOException, CasCException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        if(isHotReloadable(bundle)) {
            reload(bundle);
        }
    }

    /**
     * Reload configuration bundle: set the plugin catalog, install new plugins and apply configuration
     * as code file.
     * @param bundle The configuration bundle.
     * @throws IOException if the issue is related to bundle transport
     * @throws CasCException if the issue is related to bundle itself
     */
    public void reload(ConfigurationBundle bundle) throws IOException, CasCException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            String jcascMergeStrategy = bundle.getJcascMergeStrategy();
            if (jcascMergeStrategy != null) {
                System.setProperty("casc.merge.strategy", jcascMergeStrategy);
            }
            for (BundleReload bundleReload : BundleReload.all()) {
                bundleReload.doReload(bundle);
            }
        }
    }
}
