package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
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
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
    }

    public boolean isHotReloadable() {
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    @RequirePOST
    public HttpResponse doForceReload() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            BundleReloadAction realAction = ExtensionList.lookupSingleton(BundleReloadAction.class);
            if (isHotReloadable()) {
                if (!realAction.tryReload(true)){
                    LOGGER.log(Level.INFO, "Configuration Bundle force reload has been requested but the current bundle can not be reloaded");
                }
            } else {
                realAction.forceReload(true);
            }

            return HttpResponses.redirectViaContextPath("/manage");
        } catch (Exception e) {
            LOGGER.warning(() -> "An error occurred while reloading the configuration:\n" + e);
            return new HttpResponse() {
                @Override
                public void generateResponse(StaplerRequest staplerRequest, StaplerResponse staplerResponse, Object o) throws IOException, ServletException {
                    staplerRequest.setAttribute("hot_reload_error", e.getMessage());
                    staplerRequest.getView(ExtensionList.lookupSingleton(ForceReloadAction.class), "index")
                            .forward(staplerRequest, staplerResponse);
                }
            };
        }
    }
}
