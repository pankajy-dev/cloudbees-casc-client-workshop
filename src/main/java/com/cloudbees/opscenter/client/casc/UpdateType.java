package com.cloudbees.opscenter.client.casc;

/**
 * Update type after a new version is detected
 */
public enum UpdateType {

    /** Bundle Update Timing is disabled and the new version is not hot reloadable */
    RESTART("RESTART"),
    /** Bundle Update Timing is disabled and the new version is hot reloadable */
    RELOAD("RELOAD"),
    /** Bundle Update Timing is enabled and an automatic reload happened */
    AUTOMATIC_RELOAD("AUTOMATIC RELOAD"),
    /** Bundle Update Timing is enabled and an automatic restart is scheduled */
    AUTOMATIC_RESTART("AUTOMATIC RESTART"),
    /** Bundle Update Timing is enabled and the new version is skipped */
    SKIPPED("SKIPPED"),
    /** Bundle Update Timing is enabled and the user can reload, restart or skip */
    RELOAD_OR_SKIP("RELOAD/RESTART/SKIP"),
    /** Bundle Update Timing is enabled and the user cannot reload, just restart or skip */
    RESTART_OR_SKIP("RESTART/SKIP"),
    /** Impossible to determine */
    UNKNOWN("UNKNOWN");


    public final String label;

    private UpdateType(String label) {
        this.label = label;
    }
}