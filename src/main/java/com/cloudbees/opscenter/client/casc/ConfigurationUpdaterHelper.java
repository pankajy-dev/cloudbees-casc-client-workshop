package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import hudson.ExtensionList;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigurationUpdaterHelper {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationUpdaterHelper.class.getName());

    /**
     * Check for new updates in configuration bundle are available.
     * @return True if new version is available
     * @throws CheckNewBundleVersionException if an error happens when the new version is checked or downloaded
     */
    public synchronized static boolean checkForUpdates() throws CheckNewBundleVersionException {
        boolean error = false;
        try {
            ConfigurationStatus.INSTANCE.setErrorMessage(null);
            if (ConfigurationBundleManager.isSet()) {
                ConfigurationStatus.INSTANCE.setLastCheckForUpdate(new Date());
                // If there is a new version, the new bundle instance will replace the current one
                // Keep the version of the current bundle to display it in the UI
                String versionBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
                if (ConfigurationBundleManager.get().downloadIfNewVersionIsAvailable()) {
                    LOGGER.log(Level.INFO, () -> String.format("New Configuration Bundle available, version [%s]",
                            ConfigurationBundleManager.get().getConfigurationBundle().getVersion()));
                    ConfigurationStatus.INSTANCE.setUpdateAvailable(true);

                    if (ConfigurationStatus.INSTANCE.getOutdatedVersion() == null) {
                        // If there is no previous known version, store it
                        ConfigurationStatus.INSTANCE.setOutdatedVersion(versionBeforeUpdate);
                    }
                    ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();

                    try {
                        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
                        boolean hotReloadable = service.isHotReloadable(bundle);
                        bundle.setHotReloadable(hotReloadable);
                    } catch (IllegalStateException e) {
                        LOGGER.log(Level.FINE, "Reload is disabled because ConfigurationBundleService is not loaded.");
                    }

                    return true;
                }
            }

            return false;
        } catch (RuntimeException e) {
            // Thrown by com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager#failBundleLoading
            // Generally RuntimeException > InvalidBundleException > Real cause
            Throwable cause = e.getCause();
            if (cause instanceof InvalidBundleException) {
                cause = cause.getCause();
            }
            error = true;
            ConfigurationStatus.INSTANCE.setErrorMessage(cause.getMessage());
            throw new CheckNewBundleVersionException(cause.getMessage(), cause);
        } finally {
            ConfigurationStatus.INSTANCE.setErrorInNewVersion(error);
        }
    }

    /**
     * Filters the plugins set in another new set, removing plugins not in the envelope set.
     * @param plugins Plugins provided by the configuration bundle
     * @param envelope Plugins in the envelope and catalog
     * @return A subset of (plugins), removing plugins not in envelope
     */
    public static Set<String> getOnlyPluginsInEnvelope(Set<String> plugins, Set<String> envelope) {
        Set<String> filtered = new HashSet<>();

        for (String p : plugins) {
            if (envelope.contains(p)) {
                filtered.add(p);
            } else {
                LOGGER.log(Level.WARNING, "Skipping {0} plugin. Only plugins in the envelope can be installed.", p);
            }
        }

        return filtered;
    }

}
