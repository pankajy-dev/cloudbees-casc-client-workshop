package com.cloudbees.jenkins.plugins.casc.listener;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.events.CasCPublisher;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import jenkins.util.Listeners;

import java.util.Date;

public interface CasCPublisherHelper {
    /**
     * Call {@link CasCPublisher#publishCasCUpdate(boolean, boolean, Date, String, String, boolean, String, String, String, boolean, boolean, boolean, Boolean)}
     * using the state from the {@link ConfigurationStatus#INSTANCE} and the candidate bundle from {@link ConfigurationBundleManager}
     */
    static void publishCasCUpdate() {
        // Bundle diff payload
        // Dev memo: as BundleComparator.Result is not Serializable, the payload only contains the path to
        // both "origin" and "other" and the diff will be calculated on other replicas.
        BundleComparator.Result changesInNewVersion = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
        final String compareOrigin;
        final String compareOther;
        if (changesInNewVersion != null) {
            compareOrigin = changesInNewVersion.getOrigin().getBundlePath().toString();
            compareOther = changesInNewVersion.getOther().getBundlePath().toString();
        } else {
            compareOrigin = null;
            compareOther = null;
        }

        // Is candidate hot reloadable payload
        ConfigurationBundle candidate = ConfigurationBundleManager.get().getCandidateAsConfigurationBundle();
        final Boolean candidateIsHotReloadable;
        if(candidate != null) {
            candidateIsHotReloadable = candidate.isHotReloadable();
        } else {
            candidateIsHotReloadable = null;
        }

        // Everything is prepared, notify CasCPublisher
        Listeners.notify(CasCPublisher.class, true, (publisher) -> {
            publisher.publishCasCUpdate(
                    ConfigurationStatus.INSTANCE.isUpdateAvailable(),
                    ConfigurationStatus.INSTANCE.isCandidateAvailable(),
                    ConfigurationStatus.INSTANCE.getLastCheckForUpdate(),
                    ConfigurationStatus.INSTANCE.getOutdatedVersion(),
                    ConfigurationStatus.INSTANCE.getOutdatedBundleInformation(),
                    ConfigurationStatus.INSTANCE.isErrorInNewVersion(),
                    ConfigurationStatus.INSTANCE.getErrorMessage(),
                    compareOrigin,
                    compareOther,
                    ConfigurationStatus.INSTANCE.isCurrentlyReloading(),
                    ConfigurationStatus.INSTANCE.isErrorInReload(),
                    ConfigurationStatus.INSTANCE.isShowSuccessfulInstallMonitor(),
                    candidateIsHotReloadable
            );
        });
    }
}
