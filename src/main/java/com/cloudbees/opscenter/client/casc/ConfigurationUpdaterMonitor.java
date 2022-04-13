package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
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
        return isUpdateAvailable() || isCandidateAvailable();
    }

    public boolean isUpdateAvailable() {
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
    }

    public boolean isCandidateAvailable() {
        return ConfigurationStatus.INSTANCE.isCandidateAvailable();
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
