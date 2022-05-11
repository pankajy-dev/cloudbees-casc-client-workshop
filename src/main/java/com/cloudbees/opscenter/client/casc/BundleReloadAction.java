package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BundleReloadAction implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(BundleReloadAction.class.getName());

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
        return "casc-bundle-mgnt";
    }

    public boolean isHotReloadable() {
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    /**
     * POST request to reload the bundle
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/reload-bundle}
     * Permission required: MANAGE
     * </p>
     * @return 200 and a JSON object with the result:
     *              "reloaded": Boolean that indicates if bundle was reloaded
     *              "reason": Optional String, indicating the reason why bundle wasn't reloaded if reloaded == false
     *         403 - Not authorized. Administer permission required.
     *         500 - Server error while validating the catalog or trying to create the items
     */
    @POST
    @WebMethod(name = "reload-bundle")
    public HttpResponse doReloadBundle() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            return new JsonHttpResponse(executeReload());
        } catch (CasCException | IOException ex) {
            LOGGER.log(Level.WARNING, "Error while reloading the bundle", ex);
            return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST request to force reload the bundle
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/force-reload-bundle}
     * Permission required: MANAGE
     * </p>
     * @return 200 and a JSON object with the result:
     *              "reloaded": Boolean that indicates if bundle was reloaded
     *              "reason": Optional String, indicating the reason why bundle wasn't reloaded if reloaded == false
     *         403 - Not authorized. Administer permission required.
     *         500 - Server error while validating the catalog or trying to create the items
     */
    @POST
    @WebMethod(name = "force-reload-bundle")
    public HttpResponse doForceReloadBundle() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            return new JsonHttpResponse(executeForceReload());
        } catch (CasCException | IOException ex) {
            LOGGER.log(Level.WARNING, "Error while reloading the bundle", ex);
            return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public JSONObject executeReload() throws CasCException, IOException {
        String username = Jenkins.getAuthentication2().getName();
        if (tryReload()) {
            return new JSONObject().accumulate("reloaded", true);
        }else {
            LOGGER.log(Level.WARNING, "Reload request by {0} could not be completed. The updated configuration bundle cannot be hot reloaded.", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "Bundle is not hot reloadable");
        }
    }

    public JSONObject executeForceReload() throws CasCException, IOException {
        String username = Jenkins.getAuthentication2().getName();
        if (forceReload()) {
            return new JSONObject().accumulate("reloaded", true);
        } else {
            LOGGER.log(Level.WARNING, "Reload request by {0} could not be completed. The updated configuration bundle cannot be hot reloaded.", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "Bundle is not hot reloadable");
        }
    }

    public boolean tryReload() throws IOException, CasCException{
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        String username = Jenkins.getAuthentication2().getName();
        if (ConfigurationBundleManager.isSet() && isHotReloadable()) {
            ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
            LOGGER.log(Level.INFO, "Reloading bundle configuration, requested by {0}.", username);
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            service.reloadIfIsHotReloadable(bundle);
            ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
            ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
            return true;
        }
        return false;
    }

    public boolean forceReload() throws IOException, CasCException{
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        if (ConfigurationBundleManager.isSet() && isHotReloadable()) {
            return tryReload();
        } else if (ConfigurationBundleManager.isSet() && !isHotReloadable()) {
            String username = Jenkins.getAuthentication2().getName();
            ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
            LOGGER.log(Level.INFO, "Reloading bundle configuration, requested by {0}.", username);
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            service.reload(bundle);
        }
        return false;
    }

    /**
     * Check if there's a new version of the bundle available
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/check-bundle-update }
     * Permission required: READ
     * </p>
     * @return 200 and a boolean update-available field indicating if new version is available.
     */
    @GET
    @WebMethod(name = "check-bundle-update")
    public HttpResponse doGetBundleNewerVersion() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        boolean update = ConfigurationStatus.INSTANCE.isUpdateAvailable();
        boolean reload = false;
        if (!update) {
            try {
                update = ConfigurationUpdaterHelper.checkForUpdates();
            } catch (CheckNewBundleVersionException ex) {
                LOGGER.log(Level.WARNING, "Error while reloading the bundle", ex);
                return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        if (update) {
            reload = ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
        }

        return new JsonHttpResponse(ConfigurationUpdaterHelper.getUpdateCheckJsonResponse(update, reload));
    }
}
