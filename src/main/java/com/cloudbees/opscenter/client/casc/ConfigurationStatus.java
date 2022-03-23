package com.cloudbees.opscenter.client.casc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

/**
 * Singleton for internal tracking of the status of a configuration.
 */
public enum ConfigurationStatus {
    INSTANCE;

    /**
     * True means there is a new version available.
     */
    private boolean updateAvailable = false;
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
     * True means an error happened when the new version of the bundle was checked or downloaded.
     */
    private boolean errorInNewVersion;

    /**
     * Message after an error happened when the new version of the bundle was checked or downloaded.
     */
    private String errorMessage;

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
    @SuppressWarnings("ME_ENUM_FIELD_SETTER")
    public void setUpdateAvailable(boolean updateAvailable) {
        this.updateAvailable = updateAvailable;
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
    @SuppressWarnings("ME_ENUM_FIELD_SETTER")
    public void setOutdatedVersion(@CheckForNull String outdatedVersion) {
        this.outdatedVersion = outdatedVersion;
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
    @SuppressWarnings("ME_ENUM_FIELD_SETTER")
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
    @SuppressWarnings("ME_ENUM_FIELD_SETTER")
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
