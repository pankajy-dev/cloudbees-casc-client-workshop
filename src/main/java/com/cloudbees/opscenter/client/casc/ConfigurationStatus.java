package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Date;

public interface ConfigurationStatus {
    static ConfigurationStatus get() {
        return ConfigurationStatusSingleton.INSTANCE;
    }

    boolean isUpdateAvailable();

    void setUpdateAvailable(boolean updateAvailable);

    boolean isCandidateAvailable();

    void setCandidateAvailable(boolean candidateAvailable);

    @NonNull
    Date getLastCheckForUpdate();

    void setLastCheckForUpdate(@NonNull Date lastCheckForUpdate);

    @CheckForNull
    String getOutdatedVersion();

    void setOutdatedVersion(@CheckForNull String outdatedVersion);

    @CheckForNull
    String getOutdatedBundleInformation();

    void setOutdatedBundleInformation(@CheckForNull String outdatedBundleInformation);

    void setOutdatedBundleInformation(String bundleId, String version, String checksum);

    @CheckForNull
    String bundleInfo(ConfigurationBundle bundle);

    @CheckForNull
    String bundleInfo(String id, String version, String checksum);

    boolean isErrorInNewVersion();

    void setErrorInNewVersion(boolean errorInNewVersion);

    String getErrorMessage();

    void setErrorMessage(String errorMessage);

    void setChangesInNewVersion(BundleComparator.Result changesInNewVersion);

    @CheckForNull
    BundleComparator.Result getChangesInNewVersion();

    boolean isCurrentlyReloading();

    void setCurrentlyReloading(boolean currentlyReloading);

    boolean isErrorInReload();

    void setErrorInReload(boolean errorInReload);

    boolean isShowSuccessfulInstallMonitor();

    void setShowSuccessfulInstallMonitor(boolean showSuccessfulInstallMonitor);
}
