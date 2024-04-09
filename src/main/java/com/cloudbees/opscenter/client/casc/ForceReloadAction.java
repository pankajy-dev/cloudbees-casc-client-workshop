package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogActionSource;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ForceReloadAction implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(ForceReloadAction.class.getName());

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
        return "coreCasCForceReload";
    }

    public boolean isUpdateAvailable(){
        return ConfigurationStatus.get().isUpdateAvailable();
    }

    public boolean isHotReloadable() {
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    // Used by jelly
    public boolean isReloadInProgress() {return ConfigurationStatus.get().isCurrentlyReloading();}

    @RequirePOST
    public HttpResponse doForceReload() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        BundleReloadAction realAction = ExtensionList.lookupSingleton(BundleReloadAction.class);
        BundleUpdateLog.BundleUpdateStatus.updateCurrent(bundleUpdateStatus -> {
            if (!bundleUpdateStatus.isOngoingAction()) {
                // if there is no ongoing action, then the API has been called directly
                bundleUpdateStatus.setAction(BundleUpdateLogAction.RELOAD, BundleUpdateLogActionSource.API);
            }
            // else it is called from BundleVisualizationLink or ConfigurationUpdaterMonitor
        });
        if (isHotReloadable()) {
            if (!realAction.tryReload(true)){
                LOGGER.log(Level.INFO, "Configuration Bundle force reload has been requested but the current bundle can not be reloaded");
            }
        } else {
            realAction.forceReload(true);
        }

        return HttpResponses.redirectViaContextPath("/manage");
    }
}
