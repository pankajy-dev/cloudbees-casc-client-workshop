package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;

/**
 * Administrative monitor that warns the administrators when there are issues downloading configuration
 * bundle from Operations Center Server.
 */
@Extension
public class ConfigurationIssueMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        return ConfigurationBundleManager.isErrorFlagEnabled();
    }

    @Override
    public String getDisplayName() {
        return "Configuration Bundle download status check";
    }

}