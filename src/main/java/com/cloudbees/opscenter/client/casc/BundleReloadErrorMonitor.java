package com.cloudbees.opscenter.client.casc;

import org.jenkinsci.Symbol;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

@Extension
@Symbol("bundleReloadErrorMonitor")
public class BundleReloadErrorMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return "Bundle hot reload errors monitor";
    }

    @Override
    public boolean isActivated() {
        return ConfigurationStatus.INSTANCE.isErrorInReload();
    }

    @Override
    public Permission getRequiredPermission() {
        return CascPermission.CASC_ADMIN;
    }
}
