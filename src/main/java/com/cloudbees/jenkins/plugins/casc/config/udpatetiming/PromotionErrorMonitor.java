package com.cloudbees.jenkins.plugins.casc.config.udpatetiming;

import java.io.IOException;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;

/**
 * Administrative monitor that warns the administrators when a bundle cannot be promoted so the Reload or Safe restart operation is stopped.
 * It is intended that the monitor cannot be dismissed. If the user wants it to stop, that can be done through Jenkins &gt; Manage &gt; Configure
 */
@Extension
public class PromotionErrorMonitor extends AdministrativeMonitor {

    private boolean show = false;

    public PromotionErrorMonitor() {
        hide();
    }

    /**
     * Displays the administrative monitor
     */
    public void show() {
        show = true;
    }

    /**
     * Hides the administrative monitor. It's intended to be called only if the new bundle cannot be promoted.
     */
    public void hide() {
        show = false;
    }

    @Override
    public boolean isActivated() {
        return show;
    }

    @Override
    public String getDisplayName() {
        return "Safe Restart after a new bundle";
    }

    public static PromotionErrorMonitor get() {
        return ExtensionList.lookupSingleton(PromotionErrorMonitor.class);
    }

    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        hide();
        return HttpResponses.redirectViaContextPath("/manage");
    }
}
