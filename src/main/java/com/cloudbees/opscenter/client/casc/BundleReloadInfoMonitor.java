package com.cloudbees.opscenter.client.casc;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;

@Extension
@Symbol("bundleReloadInfoMonitor")
public class BundleReloadInfoMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return "Bundle hot reload confirmation monitor";
    }

    @Override
    public boolean isActivated() {
        return ConfigurationStatus.get().isShowSuccessfulInstallMonitor();
    }

    // Used by stapler
    @RequirePOST
    public void doAck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ConfigurationStatus.get().setShowSuccessfulInstallMonitor(false);
        rsp.forwardToPreviousPage(req);
    }
}
