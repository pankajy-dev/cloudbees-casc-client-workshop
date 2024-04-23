package com.cloudbees.jenkins.plugins.casc.listener;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.events.CasCListener;
import com.cloudbees.jenkins.plugins.casc.events.CasCPublisher;
import com.cloudbees.jenkins.plugins.casc.events.CasCStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import hudson.Extension;

import java.io.IOException;
import java.nio.file.Path;
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
     * @param newStatus the new status
     */
    @Override
    public void onCasCUpdate(CasCStatus newStatus) {
        // update ConfigurationStatus
        ConfigurationStatus.INSTANCE.setUpdateAvailable(newStatus.isUpdateAvailable());
        ConfigurationStatus.INSTANCE.setCandidateAvailable(newStatus.isCandidateAvailable());
        ConfigurationStatus.INSTANCE.setLastCheckForUpdate(newStatus.getLastCheckForUpdate());
        ConfigurationStatus.INSTANCE.setOutdatedVersion(newStatus.getOutdatedVersion());
        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(newStatus.getOutdatedBundleInformation());
        ConfigurationStatus.INSTANCE.setErrorInNewVersion(newStatus.isErrorInNewVersion());
        ConfigurationStatus.INSTANCE.setErrorMessage(newStatus.getErrorMessage());

        if (newStatus.getCompareOrigin() != null && newStatus.getCompareOther() != null) {
            try {
                // Compute the diff between current and candidate bundle
                BundleComparator.Result result = BundleComparator.compare(Path.of(newStatus.getCompareOrigin()), Path.of(newStatus.getCompareOther()));
                ConfigurationStatus.INSTANCE.setChangesInNewVersion(result);
            } catch (IllegalArgumentException | IOException e) {
                ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                LOGGER.log(Level.WARNING, "Unexpected error comparing the candidate bundle and the current applied version", e);
            }
        }
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(newStatus.isCurrentlyReloading());
        ConfigurationStatus.INSTANCE.setErrorInReload(newStatus.isErrorInReload());
        ConfigurationStatus.INSTANCE.setShowSuccessfulInstallMonitor(newStatus.isShowSuccessfulInstallMonitor());

        // Update the bundle and the update log
        ConfigurationBundleManager.refreshUpdateLog();

        // Set the hot reloadable boolean
        if (newStatus.getHotReloadable() != null) {
            ConfigurationBundle candidate = ConfigurationBundleManager.get().getCandidateAsConfigurationBundle();
            if (candidate != null) {
                // Check if the candidate is hot reloadable
                candidate.setHotReloadable(newStatus.getHotReloadable());
            }
        }
    }
}
