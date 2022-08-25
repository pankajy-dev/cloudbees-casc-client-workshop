package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
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
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    public boolean isDisabled() {return ConfigurationStatus.INSTANCE.isCurrentlyReloading();}

    @RequirePOST
    public HttpResponse doReload() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        BundleReloadAction realAction = ExtensionList.lookupSingleton(BundleReloadAction.class);
        if (!realAction.tryReload(true)){
            LOGGER.log(Level.INFO, "Configuration Bundle hot reload has been requested but the current bundle can not be reloaded");
        }
        return HttpResponses.redirectViaContextPath("/manage");
    }
}
