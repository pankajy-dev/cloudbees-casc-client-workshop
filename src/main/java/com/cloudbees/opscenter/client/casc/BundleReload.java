package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ItemRemoveStrategy;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.ParsedEnvelopeExtension;
import com.cloudbees.jenkins.plugins.casc.Bootstrap;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.items.ItemsProcessor;
import com.cloudbees.jenkins.plugins.casc.items.RemoveStrategyProcessor;

import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Marker interface for each CasC bundle section to be reloaded.
 */
@Restricted(NoExternalUse.class)
public abstract class BundleReload implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(BundleReload.class.getName());

    public static List<BundleReload> all() {
        return ExtensionList.lookup(BundleReload.class);
    }

    /**
     * Performs a full reload of the bundle ignoring if the bundle can be partially reloaded.
     * @param bundle to reload
     * @throws CasCException if an error happens when reloading a section
     */
    public static void fullReload(ConfigurationBundle bundle) throws CasCException {
        reload(bundle, true);
    }

    /**
     * Check if the bundle can be partially reloaded. If so, then only those sections with changes will be reloaded.
     * If the differences between the new version and the current installed version cannot be calculated ({@link ConfigurationStatus#getChangesInNewVersion()} returns null)
     * or if there are changes in the variables, then the partial reload is not allowed and a full reload is performed.
     * @param bundle to reload
     * @throws CasCException if an error happens when reloading a section
     */
    public static void reload(ConfigurationBundle bundle) throws CasCException {
        BundleComparator.Result comparisonResult = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
        boolean fullReload = comparisonResult == null || comparisonResult.getVariables().withChanges();
        reload(bundle, fullReload);
    }

    private static void reload(ConfigurationBundle bundle, boolean fullReload) throws CasCException {
        for (BundleReload bundleReload : BundleReload.all()) {
            if (fullReload || bundleReload.isReloadable()) {
                LOGGER.fine("Reloading bundle section " + bundleReload.getClass().getName());
                bundleReload.doReload(bundle);
            }
        }
    }

    /**
     * Reload the bundle section
     * @param bundle to reload
     */
    public abstract void doReload(ConfigurationBundle bundle) throws CasCException;

    /**
     * Method to check if the section has to be reloaded
     * Thought to be overridden, returns true by default
     * @return true if the section must be reloaded, false otherwise.
     */
    public boolean isReloadable() {
        return true;
    }

    /**
     * Reload / Install the plugins
     */
    @SuppressRestrictedWarnings({CloudBeesAssurance.class, Beekeeper.class})
    @Extension(ordinal = 4)
    public static final class PluginsReload extends BundleReload {

        private static final Logger LOGGER = Logger.getLogger(PluginsReload.class.getName());

        @Override
        public void doReload(ConfigurationBundle bundle) throws CasCException {
            Set<String> beekperPlugins = Sets.newHashSet(CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins().keySet());
            ParsedEnvelopeExtension.Expanded expanded =  CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
            if (expanded != null) {
                beekperPlugins.addAll(expanded.getConfiguration().getInclude().keySet());
            }
            Set<String> plugins = ConfigurationUpdaterHelper.getOnlyPluginsInEnvelope(bundle.getPlugins(), beekperPlugins);

            updateDirectlyUpdateSites();

            List<UpdateSite.Plugin> pluginsToInstall = Jenkins.get().getUpdateCenter().getAvailables().stream().filter(p -> plugins.contains(p.name)).collect(Collectors.toList());
            List<Future<UpdateCenter.UpdateCenterJob>> status = pluginsToInstall.stream().map(p -> p.deploy(true)).collect(Collectors.toList());

            for (Future<UpdateCenter.UpdateCenterJob> s : status) {
                try {
                    s.get(1, TimeUnit.MINUTES);
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Plugin installation failed {0}", e.getMessage());
                    LOGGER.log(Level.FINE, "Plugin installation failed", e);
                } catch (TimeoutException e) {
                    LOGGER.log(Level.WARNING, "Plugin installation timeout");
                    LOGGER.log(Level.FINE, "Plugin installation timeout", e);
                }
            }
        }

        @Override
        public boolean isReloadable() {
            BundleComparator.Result comparisonResult = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
            return comparisonResult != null && comparisonResult.getPlugins().withChanges();
        }

        private void updateDirectlyUpdateSites() {
            Jenkins.get().getUpdateCenter().getSites().stream().map(s -> s.updateDirectly()).collect(Collectors.toList()).stream().forEach(f -> {
                try {
                    if (f != null) {
                        f.get();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Fail to update update sites data because of " + e.getMessage());
                }
            });
        }

    }

    /**
     * Reload Items and RBAC.
     */
    @Extension(ordinal = 2)
    public static final class RbacReload extends BundleReload {

        private static final Logger LOGGER = Logger.getLogger(RbacReload.class.getName());

        @Override
        public void doReload(ConfigurationBundle bundle) throws CasCException {
            if (bundle.hasItems() || bundle.getRbac() != null) {
                try {
                    Bootstrap.initializeRbac();
                } catch (IOException | CasCException e) {
                    // TODO: let the exception to bubble up to fail fast (when we make the overall change about that)
                    LOGGER.log(Level.SEVERE, "Configuration as Code RBAC processing failed: {0}", e);
                    throw new CasCException("Configuration as Code RBAC processing failed", e);
                }
            }
        }

        @Override
        public boolean isReloadable() {
            BundleComparator.Result comparisonResult = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
            return comparisonResult != null && comparisonResult.getRbac().withChanges();
        }
    }

    /**
     * Reload Items and RBAC.
     */
    @Extension(ordinal = 1)
    public static final class ItemsReload extends BundleReload {

        private static final Logger LOGGER = Logger.getLogger(ItemsReload.class.getName());

        @Override
        public void doReload(ConfigurationBundle bundle) throws CasCException {
            if (bundle.hasItems() || bundle.getRbac() != null) {
                try {
                    Bootstrap.initializeItems();
                } catch (IOException | CasCException e) {
                    // TODO: let the exception to bubble up to fail fast (when we make the overall change about that)
                    LOGGER.log(Level.SEVERE, "Configuration as Code items processing failed: {0}", e);
                    throw new CasCException("Configuration as Code items processing failed", e);
                }
            }
        }

        @Override
        public boolean isReloadable() {
            try {
                ConfigurationBundle newBundleVersion = ConfigurationBundleManager.get().getConfigurationBundle();
                String removeStrategy;
                ItemRemoveStrategy fromDescriptor = newBundleVersion.getItemRemoveStrategy();
                if (fromDescriptor != null) {
                    removeStrategy = fromDescriptor.getItems();
                } else {
                    removeStrategy = !newBundleVersion.hasItems() || ItemsProcessor.from(newBundleVersion.getItems()).getRemoveStrategy() instanceof RemoveStrategyProcessor.None ?
                                     "none" : "sync"; // We don't care of the exact value. It's only to check if the remove strategy exists and implies a removal
                }
                boolean isRemoveStrategyWithRemoval = !"none".equalsIgnoreCase(removeStrategy);

                BundleComparator.Result comparisonResult = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
                boolean withChangesInItems = comparisonResult != null && comparisonResult.getItems().withChanges();

                return isRemoveStrategyWithRemoval || withChangesInItems;
            } catch (CasCException e) {
                LOGGER.log(Level.SEVERE, "Error checking if the items must be recreated. By default, bundle is applied again", e);
                return true;
            }
        }
    }

}
