package com.cloudbees.jenkins.plugins.casc.listener;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.events.CasCListener;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@SuppressWarnings("unused")
public class CasCListenerImpl implements CasCListener {
    private static final Logger LOGGER = Logger.getLogger(CasCListenerImpl.class.getName());

    @Override
    public void onCasCUpdate(
            boolean updateAvailable, boolean candidateAvailable, @NonNull Date lastCheckForUpdate, @CheckForNull String outdatedVersion,
            @CheckForNull String outdatedBundleInformation, boolean errorInNewVersion, String errorMessage, String compareOrigin, String compareOther,
            boolean currentlyReloading, boolean errorInReload, boolean showSuccessfulInstallMonitor, Boolean hotReloadable
    ) {
        // update ConfigurationStatus
        ConfigurationStatus.INSTANCE.setUpdateAvailable(updateAvailable);
        ConfigurationStatus.INSTANCE.setCandidateAvailable(candidateAvailable);
        ConfigurationStatus.INSTANCE.setLastCheckForUpdate(lastCheckForUpdate);
        ConfigurationStatus.INSTANCE.setOutdatedVersion(outdatedVersion);
        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(outdatedBundleInformation);
        ConfigurationStatus.INSTANCE.setErrorInNewVersion(errorInNewVersion);
        ConfigurationStatus.INSTANCE.setErrorMessage(errorMessage);

        if (compareOrigin != null && compareOther != null) {
            try {
                // Compute the diff between current and candidate bundle
                BundleComparator.Result result = BundleComparator.compare(Path.of(compareOrigin), Path.of(compareOther));
                ConfigurationStatus.INSTANCE.setChangesInNewVersion(result);
            } catch (IllegalArgumentException | IOException e) {
                ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                LOGGER.log(Level.WARNING, "Unexpected error comparing the candidate bundle and the current applied version", e);
            }
        }
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(currentlyReloading);
        ConfigurationStatus.INSTANCE.setErrorInReload(errorInReload);
        ConfigurationStatus.INSTANCE.setShowSuccessfulInstallMonitor(showSuccessfulInstallMonitor);

        // Update the bundle and the update log
        ConfigurationBundleManager.recreate();

        // Set the hot reloadable boolean
        if (hotReloadable != null) {
            ConfigurationBundle candidate = ConfigurationBundleManager.get().getCandidateAsConfigurationBundle();
            if (candidate != null) {
                // Check if the candidate is hot reloadable
                candidate.setHotReloadable(hotReloadable);
            }
        }
    }
}
