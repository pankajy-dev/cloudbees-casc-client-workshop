package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPPluginManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.ItemRemoveStrategy;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.PluginExpansionURLFactory;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.PluginListExpander;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.report.InstalledPluginsReport;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.ParsedEnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.plugin.PluginEntry;
import com.cloudbees.jenkins.plugins.casc.Bootstrap;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.YamlClientUtils;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.items.ItemsProcessor;
import com.cloudbees.jenkins.plugins.casc.items.RemoveStrategyProcessor;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopePlugin;

import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Plugin;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.json.JSONObject;

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
        BundleComparator.Result comparisonResult = ConfigurationStatus.get().getChangesInNewVersion();
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
            doReloadFromCatalogAndExtension(bundle);
            if ("2".equals(bundle.getApiVersion())) {
                doReloadFromUrlAndMavenPlugins(bundle);
            }
        }

        private void doReloadFromCatalogAndExtension(ConfigurationBundle bundle) throws CasCException {
            Set<String> beekperPlugins = Sets.newHashSet(CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins().keySet());
            ParsedEnvelopeExtension.Expanded expanded =  CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
            Set<String> expandedPlugins = new HashSet<>();
            if (expanded != null) {
                expandedPlugins.addAll(expanded.getConfiguration().getInclude().keySet());
            }
            beekperPlugins.addAll(expandedPlugins);
            Set<String> plugins = ConfigurationUpdaterHelper.getOnlyPluginsInEnvelope(bundle.getPlugins(), beekperPlugins);

            updateDirectlyUpdateSites();
            downloadPluginsFromUC(plugins);
            updatePluginReportV1(plugins, expandedPlugins);
        }

        private void downloadPluginsFromUC(Set<String> plugins) {
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

        // Will deploy plugins that are indicated via url / coordinates and update the report
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Path is known to exist")
        private void doReloadFromUrlAndMavenPlugins(ConfigurationBundle bundle) throws CasCException {
            Set<String> capDependenciesToInstall = new HashSet<>();
            Map<String, Path> pluginsToinstall = new HashMap<>();
            try {
                Path newPluginsList = PluginListExpander.expand(bundle, CloudBeesAssurance.get().getBeekeeper().getEnvelope(), CloudBeesAssurance.get().getBeekeeper().getInstalledExtension());
                if (newPluginsList != null && newPluginsList.toFile().exists()) {
                    List<String> plugins = FileUtils.readLines(newPluginsList.toFile());
                    for (String plugin : plugins) {
                        Plugin install = Jenkins.get().getPlugin(plugin);
                        if (install == null) {
                            // For performance reasons, only install new plugins (Updates won't be allowed as they will need a restart)
                            File pluginFile = PluginListExpander.getExpandedFile(plugin).toFile();
                            if (pluginFile.exists()) {
                                pluginsToinstall.put(plugin, pluginFile.toPath());
                            } else {
                                capDependenciesToInstall.add(plugin);
                            }
                        }
                    }
                    downloadPluginsFromUC(capDependenciesToInstall);
                    pluginsToinstall.forEach(this::deployDownloadedPlugins);
                    updatePluginReportV2(newPluginsList);
                }
            } catch (InvalidBundleException e) {
                LOGGER.log(Level.WARNING, String.format("Invalid bundle, could not process plugins: %s", e.getMessage()));
                LOGGER.log(Level.FINE, "Invalid bundle, could not process plugins: %s", e);
                throw new CasCException(e.getMessage());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("Problem writing plugins to disk: %s", e.getMessage()));
                LOGGER.log(Level.FINE, "Problem reading / writing plugins to disk", e);
            }
        }

        private void deployDownloadedPlugins(String pluginName, Path pluginFile) {
            JSONObject cfg = new JSONObject()
                                     .element("name", pluginName)
                                     .element("version", "0") // mandatory but not used in this case
                                     .element("url", pluginFile.toUri().toString())
                                     .element("dependencies", Collections.emptyList()); // not needed, as we're also adding dependencies
            try {
                new UpdateSite(UpdateCenter.ID_UPLOAD, null).new Plugin(UpdateCenter.ID_UPLOAD, cfg).deploy(true).get(1, TimeUnit.MINUTES);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, String.format("Could not download %s for plugin %s: %s", pluginFile, pluginName, e.getMessage()));
                LOGGER.log(Level.FINE, String.format("Could not download %s for plugin %s", pluginFile, pluginName), e);
            }
        }

        private void updatePluginReportV1(Set<String> plugins, Set<String> expandedPlugins){
            // Plugins are already filtered and have been installed, only needed check is if they're in the catalog (non-CAP) or not (CAP)
            // On this point all plugins are requested, bootstrap plugins should already be installed before reaching reload
            InstalledPluginsReport report = ConfigurationBundleManager.get().getReport();
            Map<String, EnvelopePlugin> beekeeperPlugins = new HashMap<>(CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins());
            ParsedEnvelopeExtension.Expanded expanded =  CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
            if (expanded != null) {
                expanded.getConfiguration().getInclude().forEach((k, v) -> beekeeperPlugins.put(k, v.asEnvelopePlugin()));
            }
            for (String plugin : plugins) {
                if (!report.getBootstrap().containsKey(plugin)) {
                    boolean cap = !expandedPlugins.contains(plugin);
                    report.addRequestedPlugin(plugin, beekeeperPlugins.get(plugin).getVersionNumber(), cap, "requested", beekeeperPlugins.get(plugin).getDependencies());
                }
            }

        }

        private void updatePluginReportV2(Path pluginList){
            InstalledPluginsReport report = ConfigurationBundleManager.get().getReport();
            Map<String, EnvelopePlugin> beekeeperPlugins = new HashMap(CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins());
            ParsedEnvelopeExtension.Expanded expanded =  CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
            if (expanded != null) {
                expanded.getConfiguration().getInclude().forEach((k, v) -> beekeeperPlugins.put(k, v.asEnvelopePlugin()));
            }
            // On this point all plugins are requested, bootstrap plugins should already be installed before reaching reload
            try {
                for (String plugin : Files.readAllLines(pluginList).stream().filter(p -> !report.getBootstrap().containsKey(p)).collect(Collectors.toSet())) {
                    if (beekeeperPlugins.containsKey(plugin)) { // We can get the dependencies from the envelope
                        boolean cap = true;
                        if (expanded != null && expanded.getConfiguration().getInclude().containsKey(plugin)) {
                            cap = false; // If it's added by the catalog it's not in CAP
                        }
                        report.addRequestedPlugin(plugin, beekeeperPlugins.get(plugin).getVersionNumber(), cap, "requested", beekeeperPlugins.get(plugin).getDependencies());
                    } else {
                        // We need to go into the expanded plugin folder and check it's dependencies
                        Path expandedFile = PluginListExpander.getExpandedFile(plugin);
                        if (expandedFile == null || !expandedFile.toFile().exists()) {
                            continue;
                        }
                        PluginEntry pluginEntry = PluginEntry.fromJarFile(expandedFile.toFile(), new PluginExpansionURLFactory());
                        if (pluginEntry != null) {
                            Map<String, VersionNumber> pluginDependencyReports = pluginEntry.getDependencies()
                                                                                    .stream()
                                                                                    .collect(Collectors.toMap(
                                                                                              dependencyEntry -> dependencyEntry.getName(),
                                                                                              dependencyEntry -> new VersionNumber(dependencyEntry.getVersion())));
                            report.addRequestedPlugin(plugin, pluginEntry.getVersionNumber(), false, "requested", pluginDependencyReports);
                        }
                    }
                }
                // TODO: Change this into a static final in installation-manager so it can be reused
                report.writeFile(new File(Jenkins.get().getRootDir(), CJPPluginManager.PLUGIN_INSTALLATION_REPORT));
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, String.format("Plugin installation report can not be read: %s", ex.getMessage()));
                LOGGER.log(Level.FINE, "Plugin installation report can not be read", ex);
            }
        }

        @Override
        public boolean isReloadable() {
            BundleComparator.Result comparisonResult = ConfigurationStatus.get().getChangesInNewVersion();
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

        /**
         * Check if RBAC configuration should be reloaded
         * - If remove strategy is sync, then the groups and roles must be recreated, as if the bundle is applied in a restart
         *   During the restart, with that strategy the groups and roles will be synchronized, so here it's the same
         * - Remove strategy from bundle prevails over remove strategy from yaml files
         * @return true if RBAC configuration must be reloaded
         */
        @Override
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive in newBundleVersion.getRbac(). Already checked with hasRBAC()")
        public boolean isReloadable() {
            ConfigurationBundle newBundleVersion = ConfigurationBundleManager.get().getConfigurationBundle();
            String removeStrategy = newBundleVersion.getRbacRemoveStrategy();
            if (StringUtils.isEmpty(removeStrategy)) {
                if (newBundleVersion.hasRBAC()) {
                    Map<String, Object> parsed = YamlClientUtils.createDefault().load(newBundleVersion.getRbac().get(0));
                    if (parsed != null) {
                        Map<String, Object> fromFile = (Map<String, Object>) parsed.getOrDefault("removeStrategy", new HashMap<>());
                        removeStrategy = (String) fromFile.getOrDefault("rbac", "update"); // If no present, then let's consider update so it's not reloaded
                    }
                }
            }
            boolean isRemoveStrategyWithRemoval = "sync".equalsIgnoreCase(removeStrategy);

            BundleComparator.Result comparisonResult = ConfigurationStatus.get().getChangesInNewVersion();
            boolean withChangesInItems = comparisonResult != null && comparisonResult.getRbac().withChanges();

            return isRemoveStrategyWithRemoval || withChangesInItems;
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

        /**
         * Check if Items configuration should be reloaded
         * - If remove strategy is different to none, then the items must be recreated, as if the bundle is applied in a restart
         *   During the restart with that strategy some items will be removed, so now they must be removed
         * - Remove strategy from bundle descriptor prevails over remove strategy from yaml files
         * @return true if Items configuration must be reloaded
         */
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

                BundleComparator.Result comparisonResult = ConfigurationStatus.get().getChangesInNewVersion();
                boolean withChangesInItems = comparisonResult != null && comparisonResult.getItems().withChanges();

                return isRemoveStrategyWithRemoval || withChangesInItems;
            } catch (CasCException e) {
                LOGGER.log(Level.SEVERE, "Error checking if the items must be recreated. By default, bundle is applied again", e);
                return true;
            }
        }
    }

}
