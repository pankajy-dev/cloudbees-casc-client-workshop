package com.cloudbees.opscenter.client.casc;

/**
 * Exception thrown when the a change in the default visibility implies that a bundle already assigned to at least one
 * controller might become unavailable.
 */
public class CheckNewBundleVersionException extends Exception {

    public CheckNewBundleVersionException() {
        super("Error checking if a new version of the bundle is available.");
    }

    public CheckNewBundleVersionException(String message) {
        super(message);
    }

    public CheckNewBundleVersionException(String message, Throwable t) {
        super(message, t);
    }
}
