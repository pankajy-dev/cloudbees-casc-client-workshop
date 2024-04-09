package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

/**
 * Singleton for internal tracking of the status of a configuration.
 */
public enum ConfigurationStatusSingleton {
    INSTANCE;

    /**
     * True means there is a new version available.
     */
    private boolean updateAvailable = false;
    /**
     * True means there is a new version that was rejected.
     */
    private boolean candidateAvailable = false;
    /**
     *  The last time when the OC was checked for a newer version of the bundle.
     */
    @NonNull
    private Date lastCheckForUpdate = new Date();

    /**
     * When there is a new version available, keeps the version of current bundle until it is updated
     */
    @CheckForNull
    private String outdatedVersion;

    /**
     * When there is a new version available, keeps the bundle id, version and checksum of current bundle until it is updated
     */
    @CheckForNull
    private String outdatedBundleInformation;

    /**
     * True means an error happened when the new version of the bundle was checked or downloaded.
     */
    private boolean errorInNewVersion;

    /**
     * True indicates there was a problem during the bundle reload. Note this is the reload operation itself, not bundle downloading
     */
    private boolean errorInReload;

    /**
     * Message after an error happened when the new version of the bundle was checked or downloaded.
     */
    private String errorMessage;

    /**
     * Result of comparing the new version available and the current version. Set only if the version is valid
     */
    private BundleComparator.Result changesInNewVersion;

    /**
     * Flag that indicates if  there's a reload currently running
     */
    private boolean currentlyReloading;

    /**
     * Flag to indicate if the last build was successful, just used to show / hide info monitor
     */
    private boolean showSuccessfulInstallMonitor;

    /**
     * Returns true if there is a new version available.
     * @return True/False if there is/isn't a new version available.
     */
    public boolean isUpdateAvailable(){
        return updateAvailable;
    }

    /**
     * Set the availability of a new version of the configuration bundle.
     * @param updateAvailable boolean for setting the update availability.
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setUpdateAvailable(boolean updateAvailable) {
        this.updateAvailable = updateAvailable;
    }

    /**
     * Returns true if there is a new version that was rejected.
     * @return True/False if there is/isn't a new version that was rejected.
     */
    public boolean isCandidateAvailable(){
        return candidateAvailable;
    }

    /**
     * Set the existence of a new version that has been rejected.
     * @param candidateAvailable boolean for setting the candidate availability.
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setCandidateAvailable(boolean candidateAvailable) {
        this.candidateAvailable = candidateAvailable;
    }

    /**
     * @return the Date when the last check for update was done (by default it is the jenkins startup date).
     */
    @NonNull
    public Date getLastCheckForUpdate() {
        return new Date(lastCheckForUpdate.getTime());
    }

    /**
     * Set the timestamp of the last time OC was checked for a newer version of the bundle.
     * @param lastCheckForUpdate date, never null.
     */
    public void setLastCheckForUpdate(@NonNull Date lastCheckForUpdate) {
        this.lastCheckForUpdate = new Date(lastCheckForUpdate.getTime());
    }

    /**
     * @return the version of the current bundle when a new version is available. Null if there is no new version.
     */
    @CheckForNull
    public String getOutdatedVersion() {
        return outdatedVersion;
    }

    /**
     * Set the version of the current bundle before downloading a new version
     * @param outdatedVersion null if there is no new version
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setOutdatedVersion(@CheckForNull String outdatedVersion) {
        this.outdatedVersion = outdatedVersion;
    }

    /**
     * @return the bundle id, version and checksum of current bundle until it is updated
     */
    @CheckForNull
    public String getOutdatedBundleInformation() {
        return outdatedBundleInformation;
    }

    /**
     * Set the bundle id, version and checksum of current bundle until it is updated
     * @param outdatedBundleInformation null if there is no new version
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setOutdatedBundleInformation(@CheckForNull String outdatedBundleInformation) {
        this.outdatedBundleInformation = outdatedBundleInformation;
    }

    /**
     * Set the bundle id, version and checksum of current bundle until it is updated
     * @param bundleId bundle identification
     * @param version bundle version
     * @param checksum bundle checksum
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setOutdatedBundleInformation(String bundleId, String version, String checksum) {
        this.outdatedBundleInformation = bundleInfo(bundleId, version, checksum);
    }

    @CheckForNull
    public String bundleInfo(ConfigurationBundle bundle) {
        if (bundle == null) {
            return null;
        }

        return bundleInfo(bundle.getId(), bundle.getVersion(), bundle.getChecksum());
    }

    @CheckForNull
    public String bundleInfo(String id, String version, String checksum) {
        final String id_ = StringUtils.defaultString(id);
        final String version_ = StringUtils.defaultString(version);
        final String checksum_ = StringUtils.defaultString(checksum);

        if (StringUtils.isBlank(id_) && StringUtils.isBlank(version_)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(id_)) {
            sb.append(id_);
        }
        if (StringUtils.isNotBlank(version_)) {
            if (StringUtils.isNotBlank(id_)) {
                sb.append(":");
            }
            sb.append(version_);
        }
        if (StringUtils.isNotBlank(checksum_) && !version_.equals(checksum_)) {
            sb.append(" (checksum " + checksum_ + ")");
        }

        return sb.toString();

    }

    /**
     * @return a true if an error happened when the new version of the bundle was checked or downloaded. false otherwise.
     */
    public boolean isErrorInNewVersion() {
        return errorInNewVersion;
    }

    /**
     * Set if an error happened when the new version of the bundle was checked or downloaded.
     * @param errorInNewVersion true if an error happened when the new version of the bundle was checked or downloaded. false otherwise.
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setErrorInNewVersion(boolean errorInNewVersion) {
        this.errorInNewVersion = errorInNewVersion;
    }

    /**
     * Message after an error happened when the new version of the bundle was checked or downloaded. Empty string if there was no error.
     */
    public String getErrorMessage() {
        if (!this.errorInNewVersion) {
            return "";
        }
        return StringUtils.defaultIfBlank(errorMessage, "Please check the logs for further information.");
    }

    /**
     * Set the error message
     * @param errorMessage null if errorInNewVersion is false
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Set the result of comparing the new available version to the current version
     * @param changesInNewVersion The results to set.
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public void setChangesInNewVersion(BundleComparator.Result changesInNewVersion) {
        this.changesInNewVersion = changesInNewVersion;
    }

    /**
     * Get the result of comparing the new available version to the current version
     * @return the result of comparing the new available version to the current version. It might be null.
     */
    @CheckForNull
    public BundleComparator.Result getChangesInNewVersion() {
        return this.changesInNewVersion;
    }

    /**
     * Checks if a hot reload is currently running
     * @return true if reload is running, false otherwise
     */
    public boolean isCurrentlyReloading() {
        return currentlyReloading;
    }

    /**
     * Sets the currently running parameter for a hot reload
     * @param currentlyReloading Indicates if the system is currently reloading
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public synchronized void setCurrentlyReloading(boolean currentlyReloading) {
        this.currentlyReloading = currentlyReloading;
    }

    public boolean isErrorInReload() {
        return errorInReload;
    }

    /**
     * Sets the error during reload flag in case a hot reload fails
     * @param errorInReload Indicates if there was an error during reload
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public synchronized void setErrorInReload(boolean errorInReload) {
        this.errorInReload = errorInReload;
    }

    public boolean isShowSuccessfulInstallMonitor() {
        return showSuccessfulInstallMonitor;
    }

    /**
     * Sets the flag to show the successful reload monitor
     * @param showSuccessfulInstallMonitor indicates if the monitor should be showed or not
     */
    @SuppressFBWarnings("ME_ENUM_FIELD_SETTER")
    public synchronized void setShowSuccessfulInstallMonitor(boolean showSuccessfulInstallMonitor) {
        this.showSuccessfulInstallMonitor = showSuccessfulInstallMonitor;
    }
}
