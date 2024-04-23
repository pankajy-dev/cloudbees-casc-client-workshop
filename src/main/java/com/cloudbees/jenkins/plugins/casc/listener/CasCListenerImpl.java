package com.cloudbees.jenkins.plugins.casc.listener;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.events.CasCListener;
import com.cloudbees.jenkins.plugins.casc.events.CasCPublisher;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * (Copied from {@link CasCListener})
 * This interface is designed to be used in conjunction with {@link CasCPublisher}.
 *
 * The main use case is to send events between multiple replicas.
 *
 * The event propagation works as follows:
 * - a class in the CasC client plugin implements @{@link CasCListener}
 * - a class in the replication plugin implements @{@link CasCPublisher}
 *
 * When the CasC client plugin needs to send an event across all replicas,
 * it calls @{@link jenkins.util.Listeners#notify(Class, boolean, Consumer)} on @{@link CasCPublisher}.
 *
 * Then the replication plugin receives the notification and sends a message to each other replicas.
 *
 * In response to this message, the replication plugin calls @{@link jenkins.util.Listeners#notify(Class, boolean, Consumer)} on {@link CasCListener}
 *
 * Then the CasC client plugin receives the notification and can act accordingly.
 *
 *  @see CasCPublisher
 */
@Extension
@SuppressWarnings("unused")
public class CasCListenerImpl implements CasCListener {
    private static final Logger LOGGER = Logger.getLogger(CasCListenerImpl.class.getName());

    /**
     * (Copied from {@link CasCListener})
     * A change occurred on the bundle, the update log or any of the values in parameter.
     * @param updateAvailable whether a new version available.
     * @param candidateAvailable whether a new version was rejected.
     * @param lastCheckForUpdate the last time when a new version was checked.
     * @param outdatedVersion current bundle info (if a new version is available)
     * @param outdatedBundleInformation current bundle id, version and checksum (if a new version is available)
     * @param errorInNewVersion whether an error occurred when the new version of the bundle was checked.
     * @param errorMessage the error message or null.
     * @param compareOrigin used to compare the origin and the other bundle, the path to the origin file.
     * @param compareOther used to compare the origin and the other bundle, the path to the other file.
     * @param currentlyReloading whether a reload is currently in progress.
     * @param errorInReload whether a problem occurred during the reload.
     * @param showSuccessfulInstallMonitor whether the successful monitor should be displayed.
     * @param hotReloadable whether the candidate bundle is hot reloadable. Null if there is no candidate.
     */
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
        ConfigurationBundleManager.refreshUpdateLog();

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
