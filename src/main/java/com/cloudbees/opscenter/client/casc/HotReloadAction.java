package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogActionSource;

import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.jenkins.plugins.casc.config.udpatetiming.PromotionErrorMonitor;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs the Configuration Bundle Hot Reload.
 */
@SuppressWarnings("unused")
@Extension
public class HotReloadAction implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(HotReloadAction.class.getName());

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "coreCasCHotReload";
    }

    public boolean isHotReloadable() {
        return BundleVisualizationLink.get().isHotReloadable();
    }

    // Used by jelly
    public boolean isReloadInProgress() {return ConfigurationStatus.INSTANCE.isCurrentlyReloading();}

    @RequirePOST
    public HttpResponse doReload() {
        Jenkins.get().checkPermission(getPermission());
        if (BundleUpdateTimingConfiguration.get().isEnabled()) {
            if (!ConfigurationUpdaterHelper.promoteCandidate()) {
                LOGGER.warning(() -> "Something failed promoting the new bundle version");
                PromotionErrorMonitor.get().show();
                // Redirecting to Manage as this way the administrative monitor shows up and the user is aware something went wrong
                return HttpResponses.redirectViaContextPath("/manage");
            }
        }

        BundleReloadAction realAction = ExtensionList.lookupSingleton(BundleReloadAction.class);
        BundleUpdateLog.BundleUpdateStatus.updateCurrent(bundleUpdateStatus -> {
            if (!bundleUpdateStatus.isOngoingAction()) {
                // if there is no ongoing action, then the API has been called directly
                bundleUpdateStatus.setAction(BundleUpdateLogAction.RELOAD, BundleUpdateLogActionSource.API);
            }
            // else it is called from BundleVisualizationLink or ConfigurationUpdaterMonitor
        });
        if (!realAction.tryReload(true)){
            LOGGER.log(Level.INFO, "Configuration Bundle hot reload has been requested but the current bundle can not be reloaded");
        }
        return HttpResponses.redirectViaContextPath("/manage");
    }

    /**
     *
     * @return allowed permission to view jelly page / fragment
     */
    // Used by jelly
    public Permission getPermission() {
        return CascPermission.CASC_ADMIN;
    }
}
