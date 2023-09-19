package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogActionSource;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;

/**
 * Administrative monitor that warns the administrators when a new configuration bundle is available. It can be found at
 * Manage Jenkins page and allows you to move directly to the restart page.
 */
@Extension
public class ConfigurationUpdaterMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        return /* New version, which might or not be skipped */ isUpdateAvailable() || /* New invalid version */ isCandidateAvailable();
    }

    public boolean isUpdateAvailable() {
        return BundleVisualizationLink.get().isUpdateAvailable();
    }

    public boolean isCandidateAvailable() {
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
        return BundleVisualizationLink.get().isHotReloadable();
    }

    /**
     * @return the version of the incoming bundle.
     */
    @CheckForNull
    public String getUpdateVersion(){
        return BundleVisualizationLink.get().getUpdateVersion();
    }

    /**
     * @return true if the Skip button must appear
     */
    // used in jelly
    public boolean canManualSkip() {
        return BundleVisualizationLink.get().canManualSkip();
    }

    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("restart")) {
            BundleUpdateLog.BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RESTART, BundleUpdateLogActionSource.MANUAL,
                                                                bundleUpdateStatus -> bundleUpdateStatus.setSuccess(true));
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
