package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.replication.ConfigurationStatusReplication;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;

import java.util.Date;

public interface ConfigurationStatus {
    static ConfigurationStatus get() {
        ExtensionList<ConfigurationStatusReplication> lookup = ExtensionList.lookup(ConfigurationStatusReplication.class);
        if (lookup.isEmpty()) {
            return ConfigurationStatusSingleton.INSTANCE;
        } else {
            return lookup.get(0).getProxyInstance();
        }
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
