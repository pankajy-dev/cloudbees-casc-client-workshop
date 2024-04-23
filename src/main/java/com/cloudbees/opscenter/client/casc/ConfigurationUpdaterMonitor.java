package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogActionSource;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Administrative monitor that warns the administrators when a new configuration bundle is available. It can be found at
 * Manage Jenkins page and allows you to move directly to the restart page.
 */
@Extension
public class ConfigurationUpdaterMonitor extends AdministrativeMonitor {

    private static final Logger LOGGER = Logger.getLogger(ConfigurationUpdaterMonitor.class.getName());

    private boolean updateAvailable;
    private boolean candidateAvailable;
    private String updateVersion;
    private boolean hotReloadable;
    private boolean canManualSkip;
    private String candidateVersion = null;

    @Override
    public Permission getRequiredPermission() {
        return CascPermission.CASC_ADMIN;
    }

    @Override
    public boolean isActivated() {
        boolean isReloading = ConfigurationStatus.INSTANCE.isCurrentlyReloading();

        if (isReloading) {
            LOGGER.fine("Bundle reload in progress. Skipping");
            this.updateAvailable = false;
            this.updateVersion = null;
            this.hotReloadable = false;
            this.canManualSkip = false;
            this.candidateAvailable = false;
            this.candidateVersion = null;
            return false; // Reloading and we want to show the monitor only if there is a new version or candidate available
        }

        BundleVisualizationLink visualizationLink = BundleVisualizationLink.get();
        this.updateAvailable = checkUpdateAvailable();
        this.updateVersion = visualizationLink.getUpdateVersion();
        this.hotReloadable = visualizationLink.isHotReloadable();
        this.canManualSkip = visualizationLink.canManualSkip();
        this.candidateAvailable = checkCandidateAvailable();
        this.candidateVersion = candidateVersion();

        LOGGER.fine("Displaying monitor with values:\n" + print());
        return /* New version, which might or not be skipped */ updateAvailable || /* New invalid version */ candidateAvailable;
    }

    private String print() {
        return String.format(
                "{%n" +
                "    isUpdateAvailable: %b,%n" +
                "    updateVersion: %s,%n" +
                "    isHotReloadable: %b,%n" +
                "    canManualSkip: %b,%n" +
                "    isCandidateAvailable: %b,%n" +
                "    candidateVersion: %s,%n" +
                "    display: %b,%n" +
                "}", updateAvailable, updateVersion, hotReloadable, canManualSkip, candidateAvailable, candidateVersion, updateAvailable || candidateAvailable);
    }

    public boolean isUpdateAvailable() {
        return this.updateAvailable;
    }

    private boolean checkUpdateAvailable() {
        return BundleVisualizationLink.get().isUpdateAvailable();
    }

    public boolean isCandidateAvailable() {
        return candidateAvailable;
    }

    private boolean checkCandidateAvailable() {
        boolean isCandidateAvailable = ConfigurationStatus.INSTANCE.isCandidateAvailable();
        // The candidate might be:
        // * invalid (and rejected) bundle
        // * Skipped valid bundle
        // We only want the monitor saying the new bundle is invalid. If the instance is configured to skip the bundle,
        // we don't need to do anything. It's just noise
        if (isCandidateAvailable) {
            BundleUpdateLog.CandidateBundle candidate = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
            if (candidate != null) {
                // At this point of the code, candidate cannot be null, but spotbugs is complaining
                isCandidateAvailable = candidate.isInvalid();
            }
        }
        return isCandidateAvailable;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    private String candidateVersion() {
        if (!ConfigurationBundleManager.isSet()) {
            return null;
        }
        BundleUpdateLog.CandidateBundle candidateBundle = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
        if (candidateBundle == null) {
            return null;
        }
        return candidateBundle.getVersion();
    }

    /**
     * @return if the instance has Bundle Updte Timing enabled
     */
    // used by jelly
    public boolean isUpdateTimingEnabled() {
        return BundleUpdateTimingConfiguration.get().isEnabled();
    }

    @Override
    public String getDisplayName() {
        return "Configuration Bundle Update check";
    }

    public boolean isHotReloadable() {
        if (!ConfigurationStatus.INSTANCE.isUpdateAvailable()) {
            // Check safe. If the admin monitor will show up in the mid-time the new version is applied, then do not display the button
            return false;
        }
        return hotReloadable;
    }

    /**
     * @return the version of the incoming bundle.
     */
    @CheckForNull
    public String getUpdateVersion(){
        return updateVersion;
    }

    /**
     * @return true if the Skip button must appear
     */
    // used in jelly
    public boolean canManualSkip() {
        if (!ConfigurationStatus.INSTANCE.isUpdateAvailable()) {
            // Check safe. If the admin monitor will show up in the mid-time the new version is applied, then do not display the button
            return false;
        }
        return canManualSkip;
    }

    /**
     * @return true if the Restart button must appear
     */
    // used in jelly
    public boolean canRestart() {
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
    }

    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("restart")) {
            BundleUpdateLog.BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RESTART, BundleUpdateLogActionSource.MANUAL,
                                                                BundleUpdateLog.BundleUpdateStatus::success);
            return HttpResponses.redirectViaContextPath("/safeRestart");
        } else if (req.hasParameter("reload")) {
            BundleUpdateLog.BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RELOAD, BundleUpdateLogActionSource.MANUAL);
            return HttpResponses.redirectViaContextPath("/coreCasCHotReload");
        } else if (req.hasParameter("dismiss")) {
            ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
            return HttpResponses.redirectViaContextPath("/manage");
        } else if (req.hasParameter("skip")) {
            if (isUpdateTimingEnabled()) {
                BundleUpdateLog.BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.SKIP, BundleUpdateLogActionSource.MANUAL);
                ConfigurationUpdaterHelper.skipCandidate();
            }
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectViaContextPath("/manage");
        }
    }
}
