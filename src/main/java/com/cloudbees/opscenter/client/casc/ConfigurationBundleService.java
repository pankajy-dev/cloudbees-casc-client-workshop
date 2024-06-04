package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.PluginListExpander;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.EnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.ParsedEnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.PluginConfiguration;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.items.ItemsProcessor;
import com.cloudbees.jenkins.plugins.casc.items.RemoveStrategyProcessor;
import com.cloudbees.jenkins.plugins.casc.listener.CasCPublisherHelper;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopePlugin;
import com.cloudbees.jenkins.plugins.updates.envelope.Validation;
import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.VersionNumber;
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
import java.util.TreeSet;
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
     *      For apiVersion 1 this means there is a change in the plugin catalog
     *      For apiVersion 2 this means either there is a change in the plugin catalog or there is a change in the plugin configuration in plugins.yaml
     *   The catalog cannot be installed
     *
     * @param bundle The candidate bundle
     * @return true if the hot reload can be done
     */
    public boolean isHotReloadable(ConfigurationBundle bundle) {
        // Not possible but
         if (bundle == null) {
            LOGGER.warning("Bundle null, the Hot-Reload cannot be checked");
            return false;
        }

        // If there is no plugin to install, then can be reloaded
        if (bundle.getPlugins().isEmpty()) {
            LOGGER.info("Bundle does not have plugins to install, so the hot reload is possible");
            return true;
        }

        boolean pluginCatalog = pluginCatalogIsHotReloadable(bundle);
        if (!pluginCatalog) {
            LOGGER.info("Plugin Catalog contains changes in the installed plugins so the bundle cannot be hot-reloaded");
        }
        boolean plugins = !"2".equals(bundle.getApiVersion()) /* for apiVersion 1 plugin configs are always reloadable */ || pluginsConfigAreHotReloadable(bundle);
        if (!plugins) {
            LOGGER.info("Plugin configurations contain changes in installed plugins so the bundle cannot be hot-reloaded");
        }

        return pluginCatalog && plugins;
    }

    private boolean pluginCatalogIsHotReloadable(ConfigurationBundle bundle) {
        // Try to load the plugins on the new catalog, if exists.
        EnvelopeExtension catalog = bundle.getEnvelopeExtension();
        if (catalog == null) {
            LOGGER.info("Bundle does not have plugin catalog, so it won't prevent the hot reload");
            return true;
        }

        // As check is done with CASC_ADMIN now instead of ADMINISTER we need to impersonate SYSTEM2
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)){
            List<String> errors = BeekeeperRemote.get().validateExtension(catalog.getMetadata(), null);
            if (!errors.isEmpty()) {
                LOGGER.log(Level.WARNING, "Bundle cannot be reloaded as the Plugin Catalog has validation errors and it cannot be installed:\n" + errors.stream().collect(Collectors.joining("\n")));
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Plugin Catalog cannot be validated because of {0}. Configuration Bundle cannot be reloaded.", e.getMessage());
            LOGGER.log(Level.FINE, "Plugin Catalog cannot be validated", e);
            return false;
        }

        Map<String, PluginConfiguration.Expanded> pluginsInCatalog;
        try {
            Validation<ParsedEnvelopeExtension.Provided> validation = ParsedEnvelopeExtension.loader().fromJSON(catalog.getMetadata());
            Envelope envelope = CloudBeesAssurance.get().getBeekeeper().getEnvelope();
            pluginsInCatalog = validation.get().expand(envelope).get().getConfiguration().getInclude();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Plugin Catalog cannot be loaded because of {0}. Configuration Bundle cannot be reloaded.", e.getMessage());
            LOGGER.log(Level.FINE, "Plugin Catalog cannot be loaded", e);
            return false;
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

        Set<String> catalogDiffs = new TreeSet<>();
        // No other plugin but plugins in the catalog can offer an update
        for (String plugin : alreadyInstalledPlugins) {
            if (pluginsInCatalog.containsKey(plugin)) {
                PluginConfiguration.Expanded pic = pluginsInCatalog.get(plugin);
                Plugin puc = Jenkins.get().getPlugin(plugin);
                if (puc != null) {
                    String picVersion = pic.getPluginEntry().getVersion();
                    String pucVersion = puc.getWrapper().getVersion();
                    if (!Objects.equals(picVersion, pucVersion)) {
                        catalogDiffs.add(String.format("{plugin: %s, catalog: %s, installed: %s}",
                                                       plugin, picVersion, pucVersion));
                    }
                }
            }
        }

        if(!catalogDiffs.isEmpty()) {
            LOGGER.info("Configuration Bundle cannot be reloaded because of Plugin Catalog versions update: [" + String.join(",", catalogDiffs) + "]");
            return false;
        }

        return true;
    }

    private boolean pluginsConfigAreHotReloadable(ConfigurationBundle bundle) {
        try {
            Envelope envelope = CloudBeesAssurance.get().getBeekeeper().getEnvelope();
            Map<String, VersionNumber> expandedDryRunMap = PluginListExpander.dryRun(bundle, envelope, bundle.getEnvelopeExtension());
            // We need expandedDryRun below to circumvent the 'effectively final'
            // requirement of the upcoming lambda
            final Map<String, VersionNumber> expandedDryRun = expandedDryRunMap != null
                                                            ? expandedDryRunMap
                                                            : Collections.emptyMap();
            Map<String, VersionNumber> alreadyInstalled =
                Jenkins.get().getPluginManager()
                             .getPlugins()
                             .stream()
                             .filter(installed -> expandedDryRun.get(installed.getShortName()) != null)
                             .collect(Collectors.toMap(PluginWrapper::getShortName, PluginWrapper::getVersionNumber));

            Set<String> pluginsDiffs = new TreeSet<>();
            alreadyInstalled.entrySet().forEach(entry -> {
                VersionNumber installed = entry.getValue();
                VersionNumber fromDryRun = expandedDryRun.get(entry.getKey());
                if (!Objects.equals(installed, fromDryRun)) {
                    pluginsDiffs.add(String.format("{plugin: %s, from bundle: %s, installed: %s}", entry.getKey(), fromDryRun, installed));
                }
            });

            if (!pluginsDiffs.isEmpty()) {
                LOGGER.info("Configuration Bundle cannot be reloaded because some plugins are to be updated: [" + String.join(",", pluginsDiffs) + "]");
                return false;
            }
        } catch (InvalidBundleException | IOException e) {
            LOGGER.log(Level.WARNING, "Plugins from the bundle cannot be read. The bundle cannot be hot reloaded", e);
            return false;
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
        Jenkins.get().checkPermission(CascPermission.CASC_ADMIN);
        if(isHotReloadable(bundle)) {
            reload(bundle, false);
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
        reload(bundle, true);
    }

    private void reload(ConfigurationBundle bundle, boolean forceFull) throws IOException, CasCException {
        Jenkins.get().checkPermission(CascPermission.CASC_ADMIN);
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            String jcascMergeStrategy = bundle.getJcascMergeStrategy();
            if (jcascMergeStrategy != null) {
                System.setProperty("casc.merge.strategy", jcascMergeStrategy);
            }
            try {
                if (forceFull) {
                    BundleReload.fullReload(bundle);
                } else {
                    BundleReload.reload(bundle);
                }
                BundleUpdateLog.BundleUpdateStatus.successCurrentAction(BundleUpdateLogAction.RELOAD);
            } finally {
                // Differences are not valid anymore if:
                //   1. bundle is reloaded
                //   2. an error happens during the reload process and it has to happen again, so let's force a full reload for security
                ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                CasCPublisherHelper.publishCasCUpdate();
            }
        }
    }

    /**
     * Obtains the list of items that would be removed on bundle application
     * @param bundle The bundle to apply
     * @return A list containing the full names of the items that would be deleted, might be empty
     * @throws CasCException If the remove strategy indicated is not supported
     */
    public List<String> getDeletionsOnReload(ConfigurationBundle bundle) throws CasCException {
        List<String> itemsYaml = bundle.getItems();
        ItemsProcessor itemsProcessor = ItemsProcessor.from(itemsYaml, bundle.getItemRemoveStrategy());
        RemoveStrategyProcessor removeStrategyProcessor = itemsProcessor.getRemoveStrategy();
        return removeStrategyProcessor.getItemsToRemove();
    }
}
