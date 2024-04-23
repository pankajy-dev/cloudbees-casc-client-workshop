package com.cloudbees.opscenter.client.casc;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

@Extension
@Symbol("bundleReloadInfoMonitor")
public class BundleReloadInfoMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return "Bundle hot reload confirmation monitor";
    }

    @Override
    public boolean isActivated() {
        return ConfigurationStatus.INSTANCE.isShowSuccessfulInstallMonitor();
    }

    // Used by stapler
    @RequirePOST
    public void doAck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ConfigurationStatus.INSTANCE.setShowSuccessfulInstallMonitor(false);
        rsp.forwardToPreviousPage(req);
    }

    @Override
    public Permission getRequiredPermission() {
        return CascPermission.CASC_ADMIN;
    }
}
