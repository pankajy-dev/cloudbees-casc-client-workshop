package com.cloudbees.jenkins.plugins.casc.config.udpatetiming;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

/**
 * Administrative monitor that warns the administrators when a Safe restart has been scheduled because a new bundle version is going to be applied.
 * It is intended that the monitor cannot be dismissed. If the user wants it to stop, that can be done through Jenkins &gt; Manage &gt; Configure
 * It is intended that the monitor cannot be hidden until the instance does not restart, so the users are aware the instance will be restarted shortly.
 */
@Extension
public class SafeRestartMonitor extends AdministrativeMonitor {

    private boolean show = false;

    public SafeRestartMonitor() {
        hide();
    }

    /**
     * Displays the administrative monitor
     */
    public void show() {
        show = true;
    }

    /**
     * Hides the administrative monitor. It's intended to be called only if there's an error and the Safe Restart cannot happen
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

    public static SafeRestartMonitor get() {
        return ExtensionList.lookupSingleton(SafeRestartMonitor.class);
    }

    @Override
    public Permission getRequiredPermission() {
        return CascPermission.CASC_ADMIN;
    }
}
