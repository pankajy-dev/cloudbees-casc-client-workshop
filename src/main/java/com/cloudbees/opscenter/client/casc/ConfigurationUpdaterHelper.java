package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.plugins.casc.analytics.BundleValidationErrorGatherer;
import com.cloudbees.jenkins.plugins.casc.validation.AbstractValidator;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import hudson.ExtensionList;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ConfigurationUpdaterHelper {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationUpdaterHelper.class.getName());

    /**
     * Check for new updates in configuration bundle are available.
     * @return True if new version is available
     * @throws CheckNewBundleVersionException if an error happens when the new version is checked or downloaded
     */
    public synchronized static boolean checkForUpdates() throws CheckNewBundleVersionException {
        boolean error = false;
        try {
            ConfigurationStatus.INSTANCE.setErrorMessage(null);
            if (ConfigurationBundleManager.isSet()) {
                ConfigurationStatus.INSTANCE.setLastCheckForUpdate(new Date());
                // If there is a new version, the new bundle instance will replace the current one
                // Keep the version of the current bundle to display it in the UI
                String versionBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
                if (ConfigurationBundleManager.get().downloadIfNewVersionIsAvailable()) {
                    BundleUpdateLog.CandidateBundle newCandidate = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
                    boolean newVersionIsValid = newCandidate != null && newCandidate.getValidations().getValidations().stream().noneMatch(v -> v.getLevel() == Validation.Level.ERROR);

                    if (newVersionIsValid) {
                        // Runtime validations
                        try {
                            AbstractValidator.validateCandidateBundle();
                        } catch (InvalidBundleException e) {
                            // With errors or warnings
                            List<Validation> validations = e.getValidationResult();
                            newCandidate.getValidations().addValidations(validations.stream().map(v -> v.serialize()).collect(Collectors.toList()));
                            Path candidatePath = BundleUpdateLog.getHistoricalRecordsFolder().resolve(newCandidate.getFolder());
                            newCandidate.getValidations().update(candidatePath.resolve(BundleUpdateLog.VALIDATIONS_FILE));
                            newVersionIsValid = newCandidate.getValidations().getValidations().stream().noneMatch(v -> v.getLevel() == Validation.Level.ERROR);
                        }
                    }

                    if (newVersionIsValid) {
                        ConfigurationBundleManager.promote();
                        // Send validation errors from promoted version
                        BundleUpdateLog.BundleValidationYaml vYaml = ConfigurationBundleManager.get().getUpdateLog().getCurrentVersionValidations();
                        if (vYaml != null) {
                            List<Validation> validations = vYaml.getValidations().stream().map(v -> Validation.deserialize(v)).collect(Collectors.toList());
                            new BundleValidationErrorGatherer(validations).send();
                        }
                    } else {
                        // Send validation errors from invalid candidate
                        List<Validation> validations = newCandidate.getValidations().getValidations().stream().map(v -> Validation.deserialize(v)).collect(Collectors.toList());
                        new BundleValidationErrorGatherer(validations).send();
                    }

                    LOGGER.log(Level.INFO, String.format("New Configuration Bundle available, version [%s]",
                            newVersionIsValid ? ConfigurationBundleManager.get().getConfigurationBundle().getVersion() : newCandidate.getVersion()));
                    ConfigurationStatus.INSTANCE.setUpdateAvailable(newVersionIsValid);
                    ConfigurationStatus.INSTANCE.setCandidateAvailable(!newVersionIsValid);

                    if (ConfigurationStatus.INSTANCE.getOutdatedVersion() == null) {
                        // If there is no previous known version, store it
                        ConfigurationStatus.INSTANCE.setOutdatedVersion(versionBeforeUpdate);
                    }
                    ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();

                    try {
                        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
                        boolean hotReloadable = service.isHotReloadable(bundle);
                        bundle.setHotReloadable(hotReloadable);
                    } catch (IllegalStateException e) {
                        LOGGER.log(Level.FINE, "Reload is disabled because ConfigurationBundleService is not loaded.");
                    }

                    return true;
                } else {
                    // When starting the instance, the bundle might be rejected, so there is a candidate that would not be shown when
                    // accessing the first time to Bundle update tab
                    BundleUpdateLog.CandidateBundle newCandidate = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
                    ConfigurationStatus.INSTANCE.setCandidateAvailable(newCandidate != null);
                }
            }

            return false;
        } catch (RuntimeException e) {
            // Thrown by com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager#failBundleLoading
            // Generally RuntimeException > InvalidBundleException > Real cause
            Throwable cause = e.getCause();
            if (cause instanceof InvalidBundleException) {
                cause = cause.getCause();
            }
            error = true;
            ConfigurationStatus.INSTANCE.setErrorMessage(cause.getMessage());
            throw new CheckNewBundleVersionException(cause.getMessage(), cause);
        } finally {
            ConfigurationStatus.INSTANCE.setErrorInNewVersion(error);
        }
    }

    /**
     * Filters the plugins set in another new set, removing plugins not in the envelope set.
     * @param plugins Plugins provided by the configuration bundle
     * @param envelope Plugins in the envelope and catalog
     * @return A subset of (plugins), removing plugins not in envelope
     */
    public static Set<String> getOnlyPluginsInEnvelope(Set<String> plugins, Set<String> envelope) {
        Set<String> filtered = new HashSet<>();

        for (String p : plugins) {
            if (envelope.contains(p)) {
                filtered.add(p);
            } else {
                LOGGER.log(Level.WARNING, "Skipping {0} plugin. Only plugins in the envelope can be installed.", p);
            }
        }

        return filtered;
    }

    /**
     * Build the JSON response for CLI and HTTP Endpoint to check new versions. Example of response
     * {
     *     "update-available": true,
     *     "versions": {
     *         "current-bundle": {
     *             "version": "2",
     *             "validations": []
     *         },
     *         "new-version": {
     *             "version": "5",
     *             "valid": true,
     *             "validations": [
     *                 "WARNING - [CATALOGVAL] - More than one plugin catalog file used in zip-core-casc-1652255664849. Using only first read file."
     *             ]
     *         }
     *     },
     *     "update-type": "RELOAD"
     * }
     * @param update true if there is a new version available suitable for installation.
     * @param isHotReload true if the new version can be applied with a Hot Reload
     * @return JSON response for CLI and HTTP Endpoint to check new versions
     */
    public static JSONObject getUpdateCheckJsonResponse(boolean update, boolean isHotReload) {
        JSONObject json = new JSONObject();
        json.accumulate("update-available", update);

        // Using BundleVisualizationLink so information is the same as in UI
        BundleVisualizationLink bundleInfo = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
        JSONObject versionSummary = new JSONObject();

        JSONObject currentBundle = new JSONObject();
        currentBundle.accumulate("version", StringUtils.defaultString(bundleInfo.getBundleVersion(), "N/A"));
        JSONArray currentValidations = new JSONArray();
        if (bundleInfo.getBundleVersion().equals(bundleInfo.getDownloadedBundleVersion())) {
            currentValidations.addAll(getValidations(bundleInfo.getBundleValidations()));
        }
        currentBundle.accumulate("validations", currentValidations);

        versionSummary.accumulate("current-bundle", currentBundle);
        if (update || bundleInfo.isCandidateAvailable()) {
            JSONObject newAvailable = new JSONObject();
            final boolean valid = !bundleInfo.isCandidateAvailable();
            newAvailable.accumulate("version", valid
                    ? StringUtils.defaultString(bundleInfo.getDownloadedBundleVersion(), "N/A")
                    : StringUtils.defaultString(bundleInfo.getCandidate().getVersion(), "N/A"));
            newAvailable.accumulate("valid", valid);
            BundleVisualizationLink.ValidationSection validations = valid ? bundleInfo.getBundleValidations() : bundleInfo.getCandidate().getValidations();
            JSONArray newValidations = new JSONArray();
            newValidations.addAll(getValidations(validations));
            newAvailable.accumulate("validations", newValidations);
            versionSummary.accumulate("new-version", newAvailable);
        }
        json.accumulate("versions", versionSummary);

        if (update && !bundleInfo.isCandidateAvailable()) {
            if (isHotReload) {
                json.accumulate("update-type", "RELOAD");
            } else {
                json.accumulate("update-type", "RESTART");
            }
        }

        return json;
    }

    private static List<String> getValidations(BundleVisualizationLink.ValidationSection vs) {
        List<String> list = new ArrayList<>();
        if (vs.hasErrors()) {
            list.addAll(vs.getErrors().stream().map(s -> Validation.Level.ERROR + " - " + s).collect(Collectors.toList()));
        }
        if (vs.hasWarnings()) {
            list.addAll(vs.getWarnings().stream().map(s -> Validation.Level.WARNING + " - " + s).collect(Collectors.toList()));
        }

        return list;
    }

}
