package com.cloudbees.opscenter.client.casc;

import org.jenkinsci.Symbol;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;

@Extension
@Symbol("bundleReloadMonitor")
public class BundleReloadMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return "Bundle hot reload errors monitor";
    }

    @Override
    public boolean isActivated() {
        return ConfigurationStatus.INSTANCE.isErrorInNewVersion();
    }
}
