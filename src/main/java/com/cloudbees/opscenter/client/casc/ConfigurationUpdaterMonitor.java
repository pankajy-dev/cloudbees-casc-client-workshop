package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
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
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
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

    @Override
    public String getDisplayName() {
        return "Configuration Bundle Update check";
    }

    public boolean isHotReloadable() {
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    /**
     * @return the version of the incoming bundle.
     */
    @CheckForNull
    public String getUpdateVersion(){
        if(isUpdateAvailable()) {
            return ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
        }
        return null;
    }

    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("restart")) {
            return HttpResponses.redirectViaContextPath("/safeRestart");
        } else if (req.hasParameter("reload")) {
            return HttpResponses.redirectViaContextPath("/coreCasCHotReload");
        } else if (req.hasParameter("dismiss")) {
            ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectViaContextPath("/manage");
        }
    }
}
