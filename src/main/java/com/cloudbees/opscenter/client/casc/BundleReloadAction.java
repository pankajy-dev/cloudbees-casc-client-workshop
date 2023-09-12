package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.RootAction;
import hudson.triggers.SafeTimerTask;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;
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
     *              "completed": Optional, false if operation is happening asynchronously and hasn't completed
     *         403 - Not authorized. Administer permission required.
     *         500 - Server error while validating the catalog or trying to create the items
     */
    @POST
    @WebMethod(name = "reload-bundle")
    public HttpResponse doReloadBundle(@QueryParameter boolean asynchronous) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
            if (configuration.isEnabled()) {
                if (configuration.isAutomaticReload()) {
                    return new JsonHttpResponse(new JSONObject().accumulate("reloaded", false).accumulate("reason", "Automatic reload configured. It's not possible to manually reload the bundle. If there is any issue, proceed with a restart"));
                } else {
                    if (!ConfigurationUpdaterHelper.promoteCandidate()) {
                        return new JsonHttpResponse(new JSONObject().accumulate("reloaded", false).accumulate("reason", "Bundle could not be promoted. Proceed with a restart"));
                    }
                }
            }
            return new JsonHttpResponse(executeReload(asynchronous));
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
     *              "completed": Optional, false if operation is happening asynchronously and hasn't completed
     *         403 - Not authorized. Administer permission required.
     *         500 - Server error while validating the catalog or trying to create the items
     */
    @POST
    @WebMethod(name = "force-reload-bundle")
    public HttpResponse doForceReloadBundle(@QueryParameter boolean asynchronous) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            return new JsonHttpResponse(executeForceReload(asynchronous));
        } catch (CasCException | IOException ex) {
            LOGGER.log(Level.WARNING, "Error while reloading the bundle", ex);
            return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET request to check what items would be deleted if the bundle is applied
     * <p>
     *     {@code JENKINS_URL/casc-bundle-mgnt/check-reload-items}
     *     Permission required: MANAGE
     * </p>
     * @return  200 and a JSON object with the result:
     *              "deletions": ["item-1", "item-2", ...]
     *          403 - Not authorized. READ permission required.
     *          500 - Server error while checking items or bundle remove strategy
     */
    @GET
    @WebMethod(name = "check-reload-items")
    public HttpResponse doCheckReloadDeletions() {
        Jenkins.get().checkPermission(Jenkins.MANAGE); // Not performing any real deletion, so should be safe
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        try {
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            JSONArray deletions = new JSONArray();
            deletions.addAll(bundle.getItems() == null ? Collections.EMPTY_LIST : service.getDeletionsOnReload(bundle)); // Not needed after cloudbees-casc-items-api:2.25
            JSONObject responseContent = new JSONObject().accumulate("deletions", deletions);
            return new JsonHttpResponse(new JSONObject().accumulate("items", responseContent));
        } catch (CasCException ex) {
            LOGGER.log(Level.WARNING, "Could not process remoteStrategy for items (maybe invalid strategy?)", ex);
            return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public JSONObject executeReload(boolean async) throws CasCException, IOException {
        String username = Jenkins.getAuthentication2().getName();
        if (ConfigurationStatus.INSTANCE.isCurrentlyReloading()) {
            LOGGER.log(Level.INFO, "Reload bundle configuration requested by {0}.  Ignored as a reload is already in progress", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "A reload is already in progress, please wait for it to complete");
        }
        if (tryReload(async)) {
            JSONObject response = new JSONObject().accumulate("reloaded", true);
            if (async) {
                response.accumulate("completed", !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
            }
            return response;
        } else {
            LOGGER.log(Level.WARNING, "Reload request by {0} could not be completed. The updated configuration bundle cannot be hot reloaded.", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "Bundle is not hot reloadable");
        }
    }

    /**
     * @deprecated use {@link #executeReload(boolean)} instead
     * @return a json indicating the status of the reload action
     * @throws CasCException In case of some CasC specific failure
     * @throws IOException If there were RW operations exceptions
     */
    @Deprecated
    public JSONObject executeReload() throws CasCException, IOException {
        return executeReload(false);
    }

    public JSONObject executeForceReload(boolean async) throws CasCException, IOException {
        String username = Jenkins.getAuthentication2().getName();
        if (ConfigurationStatus.INSTANCE.isCurrentlyReloading()) {
            LOGGER.log(Level.INFO, "Reload bundle configuration requested by {0}.  Ignored as a reload is already in progress", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "A reload is already in progress, please wait for it to complete");
        }
        if (forceReload(async)) {
            JSONObject response = new JSONObject().accumulate("reloaded", true);
            if (async) {
                response.accumulate("completed", !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
            }
            return response;
        } else {
            LOGGER.log(Level.WARNING, "Reload request by {0} could not be completed. The updated configuration bundle cannot be hot reloaded.", username);
            return new JSONObject().accumulate("reloaded", false).accumulate("reason", "Bundle is not hot reloadable");
        }
    }

    /**
     * @deprecated use {@link #executeForceReload(boolean)} instead
     * @return a json indicating the status of the reload action
     * @throws CasCException In case of some CasC specific failure
     * @throws IOException If there were RW operations exceptions
     */
    @Deprecated
    public JSONObject executeForceReload() throws CasCException, IOException {
        return executeReload(false);
    }

    public boolean tryReload(boolean async) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        String username = Jenkins.getAuthentication2().getName();
        if (ConfigurationBundleManager.isSet() && isHotReloadable()) {
            LOGGER.log(Level.INFO, "Reloading bundle configuration, requested by {0}.", username);
            if (async){
                launchAsynchronousReload(false);
            } else {
                launchSynchronousReload(false);
            }
            LOGGER.log(Level.INFO, "Reloading bundle configuration requested by {0} completed", username);
            ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
            ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
            ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(null);
            return true;
        }
        return false;
    }

    /**
     * @deprecated use {@link #tryReload(boolean)} instead
     * @return a boolean indicating if reload could be performed
     */
    @Deprecated
    public boolean tryReload() throws IOException, CasCException{
        return tryReload(false);
    }

    public boolean forceReload(boolean async) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        if (ConfigurationBundleManager.isSet() && isHotReloadable()) {
            return tryReload(async);
        } else if (ConfigurationBundleManager.isSet() && !isHotReloadable()) {
            String username = Jenkins.getAuthentication2().getName();
            LOGGER.log(Level.INFO, "Reloading bundle configuration, requested by {0}.", username);
            if (async) {
                launchAsynchronousReload(true);
            } else {
                launchSynchronousReload(true);
            }
            LOGGER.log(Level.INFO, "Reloading bundle configuration requested by {0} completed", username);
        }
        return false;
    }

    @Deprecated
    public boolean forceReload() {
        return forceReload(false);
    }

    private void launchAsynchronousReload(boolean force) {
        Timer.get().submit(new TimerTask (){
            @Override
            public void run() {
                launchSynchronousReload(force);
            }
        });
    }

    private void launchSynchronousReload(boolean force) {
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(true);
        ConfigurationStatus.INSTANCE.setErrorInReload(false);
        ConfigurationStatus.INSTANCE.setShowSuccessfulInstallMonitor(false);
        try {
            if (force) {
                service.reload(bundle);
            } else {
                service.reloadIfIsHotReloadable(bundle);
            }
            ConfigurationStatus.INSTANCE.setShowSuccessfulInstallMonitor(true);
        } catch (IOException | CasCException ex) {
            LOGGER.log(Level.WARNING, String.format("Error while executing hot reload %s", ex.getMessage()), ex);
            ConfigurationStatus.INSTANCE.setErrorInReload(true);
        } finally {
            ConfigurationStatus.INSTANCE.setCurrentlyReloading(false);
        }
    }

    /**
     * Check if there's a new version of the bundle available
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/check-bundle-update }
     * Parameters: {@code quiet=[STRING] } optional parameter to indicate if the quiet mode should be enabled (true)
     *                                     or disabled (false). If not present, use the value from the config.
     * Permission required: READ
     * </p>
     * @return 200 and a boolean update-available field indicating if new version is available.
     */
    @GET
    @WebMethod(name = "check-bundle-update")
    public HttpResponse doGetBundleNewerVersion(@QueryParameter("quiet") String quietParam) {
        // Dev memo: please keep the business logic in this class in line with com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand.run
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        UpdateType reload = null;
        // First, check if an update is available
        // Dev memo: this must go first because it will update the version of the bundle if needed
        boolean update;
        try {
            update = ConfigurationUpdaterHelper.checkForUpdates();
        } catch (CheckNewBundleVersionException ex) {
            LOGGER.log(Level.WARNING, "Error while reloading the bundle", ex);
            return new JsonHttpResponse(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        if (!update) {
            // maybe the bundle is the same, but it is not yet applied, also check if an update is available (Only possible if Bundle Update Timing is disabled)
            update = ConfigurationStatus.INSTANCE.isUpdateAvailable();
        }

        if (update) {
            reload = ConfigurationUpdaterHelper.getUpdateTypeForCliAndEndpoint();
        }

        Boolean quiet = quietParam == null ? null : Boolean.valueOf(quietParam);
        return new JsonHttpResponse(ConfigurationUpdaterHelper.getUpdateCheckJsonResponse(update, reload, quiet));
    }

    /**
     * Check if there's a reload operation running
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/check-bundle-reload-running }
     * Permission required: READ
     * </p>
     * @return 200 and a boolean in-progress field indicating if a reload operation is running or not.
     */
    @GET
    @WebMethod(name = "check-bundle-reload-running")
    public HttpResponse doCheckReloadInProgress() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        return new JsonHttpResponse(new JSONObject().accumulate("reload-in-progress", ConfigurationStatus.INSTANCE.isCurrentlyReloading()));
    }

    /**
     * Return information about the update log
     * <p>
     * {@code JENKINS_URL/casc-bundle-mgnt/casc-bundle-update-log }
     * Permission required: MANAGE (as UI)
     * </p>
     * @return 200 and a JSON object with the update log
     */
    @GET
    @WebMethod(name = "casc-bundle-update-log")
    public HttpResponse doGetBundleUpdateLog() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            return new JsonHttpResponse(getBundleUpdateLog());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading the Bundle update log", e);
            return new JsonHttpResponse(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // Visible for testing
    JSONObject getBundleUpdateLog() {
        return ConfigurationUpdaterHelper.getUpdateLog();
    }

    /**
     * Validates a bundle. If validation goes as expected, the method will return a JSON output as following:
     * {
     *     "valid": false,
     *     "commit": 44e7cfa,
     *     "validation-messages": [
     *         "ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.",
     *         "WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"
     *     ]
     * }
     * Commit field will only appear if it was indicated in the request, this field is not supposed to be used manually.
     * Since the bundle has to be included in the call, this method can only be POST. The bundle must be included in zip file.
     * URL: {@code JENKINS_URL/casc-bundle-mgnt/casc-bundle-validate }
     * Parameters: {@code commit=[STRING] } optional parameter to indicate the commit hash associated with the bundles to validate
     * Parameters: {@code quiet=[STRING] } optional parameter to indicate if the quiet mode should be enabled (true)
     *                                     or disabled (false). If not present, use the value from the config.
     * Permission required: MANAGE
     * @return
     *      <table>
     *      <caption>Validation result</caption>
     *      <tr><th>Code</th><th>Output</th></tr>
     *      <tr><td>200</td><td>JSON output with the validation result</td></tr>
     *      <tr><td>500</td><td>The input is not a valid zip.</td></tr>
     *      <tr><td>403</td><td>User does not have {@link Jenkins#MANAGE} permission</td></tr>
     *      </table>
     */
    @POST
    @WebMethod(name = "casc-bundle-validate")
    public HttpResponse doBundleValidate(StaplerRequest req,
                                         @QueryParameter String commit,
                                         @QueryParameter("quiet") String quietParam) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        Path tempFolder = null;
        try {
            tempFolder = ConfigurationUpdaterHelper.createTemporaryFolder();
            Path bundleDir = null;
            try (BufferedInputStream in = new BufferedInputStream(req.getInputStream())) {
                // Copy zip from stdin
                Path zipSrc = tempFolder.resolve("bundle.zip");
                FileUtils.copyInputStreamToFile(in, zipSrc.toFile());

                if (!Files.exists(zipSrc)) {
                    LOGGER.log(Level.WARNING, "Invalid zip file: Zip file cannot be found in HTTP request");
                    JSONObject error = new JSONObject();
                    error.accumulate("error", "Invalid zip file: Zip file cannot be found in HTTP request");
                    return new JsonHttpResponse(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }

                bundleDir = tempFolder.resolve("bundle");
                FilePath zipFile = new FilePath(zipSrc.toFile());
                FilePath dst = new FilePath(bundleDir.toFile());
                zipFile.unzip(dst);
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Invalid zip file", e);
                JSONObject error = new JSONObject();
                error.accumulate("error", "Invalid zip file: Cannot be unzipped");
                error.accumulate("details", e.getMessage());
                return new JsonHttpResponse(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

            if (!Files.exists(bundleDir)) {
                LOGGER.log(Level.WARNING, "Invalid zip file. Bundle dir does not exist");
                JSONObject error = new JSONObject();
                error.accumulate("error", "Error unzipping the file");
                return new JsonHttpResponse(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

            if (!Files.exists(bundleDir.resolve("bundle.yaml"))) {
                LOGGER.log(Level.WARNING, "Invalid bundle - Missing descriptor");
                JSONObject error = new JSONObject();
                error.accumulate("error", "Invalid bundle - Missing descriptor");
                return new JsonHttpResponse(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

            Boolean quiet = quietParam == null ? null : Boolean.valueOf(quietParam);
            List<Validation> validations = ConfigurationUpdaterHelper.fullValidation(bundleDir, commit, quiet);
            return new JsonHttpResponse(ConfigurationUpdaterHelper.getValidationJSON(validations, commit));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading the zip file", e);
            return new JsonHttpResponse(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFolder != null && Files.exists(tempFolder)) {
                try {
                    FileUtils.deleteDirectory(tempFolder.toFile());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error deleting temporary files", e);
                    return new JsonHttpResponse(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    /**
     * Skip the candidate bundle if there's an available version.
     * URL: {@code JENKINS_URL/casc-bundle-mgnt/casc-bundle-skip }
     * Output:
     * {
     *     "skipped": true/false,
     *     "error": "Error promoting the candidate"
     * }
     * Permission required: MANAGE
     * @return
     *      <table>
     *      <caption>Validation result</caption>
     *      <tr><th>Code</th><th>Output</th></tr>
     *      <tr><td>200</td><td>JSON output with the validation result</td></tr>
     *      <tr><td>403</td><td>User does not have {@link Jenkins#MANAGE} permission</td></tr>
     *      <tr><td>404</td><td>Bundle to promote cannot be found. Potentially already skipped or promoted</td></tr>
     *      <tr><td>500</td><td>Wrong instance configuration that doesn't allow to skip</td></tr>
     *      </table>
     */
    @POST
    @WebMethod(name = "casc-bundle-skip")
    public HttpResponse doSkipBundle(StaplerRequest req) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        if (!configuration.isEnabled()) {
            return new JsonHttpResponse(new JSONObject().accumulate("error", "This instance does not allow to skip bundles. Please, enable Bundle Update Timing by setting the System property -Dcore.casc.bundle.update.timing.enabled=true"), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        BundleVisualizationLink updateTab = BundleVisualizationLink.get();
        if (!updateTab.isUpdateAvailable()) {
            return new JsonHttpResponse(new JSONObject().accumulate("error", "Bundle version to skip not found."), HttpServletResponse.SC_NOT_FOUND);
        }

        if (!updateTab.canManualSkip()) {
            // Just in case a Safe restart is scheduled and meanwhile the user try to skip it
            return new JsonHttpResponse(new JSONObject().accumulate("error", "This instance does not allow to skip bundles. There is an automatic reload or restart that makes the skip operation ignored."), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        boolean skipped;
        String error = null;
        try {
            skipped = ConfigurationUpdaterHelper.doSkipCandidate();
        } catch (IOException e) {
            skipped = false;
            error = e.getMessage();
            LOGGER.log(Level.WARNING, "Error skipping the candidate bundle", e);
        }

        JSONObject response = new JSONObject();
        response.accumulate("skipped", skipped);
        if (StringUtils.isNotBlank(error)) {
            response.accumulate("error", error);
        }

        return new JsonHttpResponse(response);
    }

}
