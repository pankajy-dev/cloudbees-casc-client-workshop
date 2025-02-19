package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogActionSource;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateStatus;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ContentBundleValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.DescriptorValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.FileSystemBundleValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.MultipleCatalogFilesValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PluginCatalogInOCValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PluginsToInstallValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.YamlSchemaValidator;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.analytics.BundleValidationErrorGatherer;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.jenkins.plugins.casc.config.udpatetiming.PromotionErrorMonitor;
import com.cloudbees.jenkins.plugins.casc.config.udpatetiming.SafeRestartMonitor;
import com.cloudbees.jenkins.plugins.casc.listener.CasCPublisherHelper;
import com.cloudbees.jenkins.plugins.casc.validation.AbstractValidator;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.lifecycle.RestartNotSupportedException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletException;

public final class ConfigurationUpdaterHelper {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationUpdaterHelper.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).localizedBy(Locale.ENGLISH);

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
                String idBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getId();
                String checksumBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getChecksum();
                if (ConfigurationBundleManager.get().downloadIfNewVersionIsAvailable()) {
                    PromotionErrorMonitor.get().hide();
                    ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                    BundleUpdateLog.CandidateBundle newCandidate = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
                    boolean newVersionIsValid = newCandidate != null && !BundleValidator.shouldBeRejected(newCandidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).collect(Collectors.toList()));

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
                            newVersionIsValid = !BundleValidator.shouldBeRejected(validations);
                            ConfigurationBundleManager.refreshUpdateLog();
                        }
                    }

                    boolean newVersionAvailable = false;
                    if (newVersionIsValid) {
                        try {
                            Path candidatePath = BundleUpdateLog.getHistoricalRecordsFolder().resolve(newCandidate.getFolder());
                            BundleComparator.Result result = BundleComparator.compare(ConfigurationBundleManager.getBundleFolder(), candidatePath.resolve("bundle"));
                            ConfigurationStatus.INSTANCE.setChangesInNewVersion(result);
                        } catch (IllegalArgumentException | IOException e) {
                            ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                            LOGGER.log(Level.WARNING, "Unexpected error comparing the candidate bundle and the current applied version", e);
                        }

                        // promote method already has the logic for promoting and skipping when it corresponds, so just a matter of performing the
                        // Hot Reload / Safe Restart
                        if (BundleUpdateTimingManager.isEnabled()) {
                            // Update Bundle Timing enabled, so we don't promote:
                            // 1. The bundle might be promoted by an automatic reload
                            // 2. The bundle might be promoted by an automatic restart
                            // 3. The bundle might be promoted by a manual interaction
                            boolean isInvalid = newCandidate.isInvalid();
                            boolean toSkip = BundleUpdateTimingConfiguration.get().canSkipNewVersions() && newCandidate.isSkipped();
                            newVersionAvailable = !toSkip && !isInvalid;
                        } else {
                            // If bundle update timing is disabled, then we have to promote
                            ConfigurationBundle promoted = ConfigurationBundleManager.promote(true); // Plugin is ready, so the instance is up and running
                            newVersionAvailable = !versionBeforeUpdate.equals(promoted.getVersion());
                        }
                        // Send validation errors from promoted version
                        BundleUpdateLog.BundleValidationYaml vYaml = ConfigurationBundleManager.get().getUpdateLog().getCurrentVersionValidations();
                        if (vYaml != null) {
                            List<Validation> validations = vYaml.getValidations().stream().map(v -> Validation.deserialize(v)).collect(Collectors.toList());
                            new BundleValidationErrorGatherer(validations).send();
                        }

                    } else {
                        // Send validation errors from invalid candidate
                        if (newCandidate != null) {
                            List<Validation> validations = newCandidate.getValidations().getValidations().stream().map(v -> Validation.deserialize(v)).collect(Collectors.toList());
                            new BundleValidationErrorGatherer(validations).send();
                        }
                    }

                    LOGGER.log(Level.INFO, String.format("New Configuration Bundle available, version [%s]",
                            newVersionAvailable ? ConfigurationBundleManager.get().getConfigurationBundle().getVersion() : newCandidate.getVersion()));
                    ConfigurationStatus.INSTANCE.setUpdateAvailable(newVersionAvailable);
                    ConfigurationStatus.INSTANCE.setCandidateAvailable(!newVersionAvailable);

                    if (ConfigurationStatus.INSTANCE.getOutdatedVersion() == null) {
                        // If there is no previous known version, store it
                        ConfigurationStatus.INSTANCE.setOutdatedVersion(versionBeforeUpdate);
                        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(idBeforeUpdate, versionBeforeUpdate, checksumBeforeUpdate);
                    }

                    /*
                     * If not feasible the automatic reload or not configured, then checks the automatic restart. If configured
                     * Display an administrative monitor → User must know a Safe restart will happen (Do not offer dismiss or ignore)
                     * ConfigurationBundleManager#promote
                     * Execute Jenkins.get().doSafeRestart();
                     * If not configured the safe restart, then
                     * Checks the Use case 4 and depending. If skipping, then do not execute the promote method and mark as skipped.
                     * If not skipping, then the UI offers the Safe Restart and Reload buttons together with the new button “Skip Version” (use case 3). Details below.
                     * Clicking on Reload Configuration or in Safe Restart will perform the promote action before reloading or before restarting
                     */
                    if (newVersionIsValid && BundleUpdateTimingManager.isEnabled()) {
                        BundleUpdateTimingManager bundleUpdateTimingManager = BundleUpdateTimingManager.get();
                        boolean automaticReload = bundleUpdateTimingManager.isAutomaticReload();
                        boolean automaticRestart = bundleUpdateTimingManager.isAutomaticRestart();
                        boolean hotReloadable = isHotReloadable(ConfigurationBundleManager.get().getCandidateAsConfigurationBundle());

                        if (automaticRestart || (automaticReload && hotReloadable)) {
                            promoteCandidate();
                        }
                        ConfigurationBundle candidate = ConfigurationBundleManager.get().getCandidateAsConfigurationBundle();
                        if (candidate != null) {
                            candidate.setHotReloadable(hotReloadable);
                        }

                        if (automaticReload && hotReloadable) {
                            BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RELOAD,
                                                                BundleUpdateLogActionSource.AUTOMATIC);
                            // try to apply the hot reload
                            BundleReloadAction bundleReloadAction = ExtensionList.lookupSingleton(BundleReloadAction.class);
                            if (bundleReloadAction.executeReload(true).getBoolean("reloaded")) {
                                LOGGER.log(Level.INFO, "New bundle version reloaded as for an automatic reload. Async reload in progress");
                            } else {
                                LOGGER.log(Level.WARNING, "Hot reloaded failed. If configured, an automatic safe restart will happen. Otherwise, the manual reload must be performed");
                                if (automaticRestart) {
                                    SafeRestartMonitor.get().show();
                                    try {
                                        Jenkins.get().doSafeRestart(null, "A new bundle version has been detected and as for the automatic restart configuration, a Safe Restart has been scheduled.");
                                        ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
                                    } catch (RestartNotSupportedException | IOException | ServletException e) {
                                        SafeRestartMonitor.get().hide();
                                        throw new CasCException("Safe restart cannot be performed", e);
                                    }
                                }
                            }
                        } else {
                            if (!hotReloadable) {
                                LOGGER.log(Level.INFO, "New bundle version cannot be hot reloaded. If configured, an automatic safe restart will happen. Otherwise, the manual reload must be performed");
                            }
                            if (automaticRestart) {
                                SafeRestartMonitor.get().show();
                                BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RESTART, BundleUpdateLogActionSource.AUTOMATIC, BundleUpdateLog.BundleUpdateStatus::success);
                                try {
                                    Jenkins.get().doSafeRestart(null, "A new bundle version has been detected and as for the automatic restart configuration, a Safe Restart has been scheduled.");
                                    ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
                                } catch (RestartNotSupportedException | IOException | ServletException e) {
                                    SafeRestartMonitor.get().hide();
                                    throw new CasCException("Safe restart cannot be performed", e);
                                }
                            }
                        }
                    } else {
                        ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
                        boolean hotReloadable = isHotReloadable(bundle);
                        bundle.setHotReloadable(hotReloadable);
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
        } catch (CasCException | IOException | RuntimeException e) {
            // Thrown by com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager#failBundleLoading
            // Generally RuntimeException > InvalidBundleException > Real cause
            Throwable cause = e.getCause() != null ? e.getCause() : e; // If e is a NPE, then the cause is null
            if (cause instanceof InvalidBundleException) {
                cause = cause.getCause();
            }
            error = true;
            ConfigurationStatus.INSTANCE.setErrorMessage(cause.getMessage());
            throw new CheckNewBundleVersionException(cause.getMessage(), cause);
        } finally {
            ConfigurationStatus.INSTANCE.setErrorInNewVersion(error);
            CasCPublisherHelper.publishCasCUpdate();
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
                LOGGER.log(Level.WARNING, "Skipping {0} plugin. Only plugins in the envelope can be installed via update center.", p);
            }
        }

        return filtered;
    }

    /**
     * Build the JSON response for CLI and HTTP Endpoint to check new versions. Example of response
     * <pre>
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
     * </pre>
     * If quiet mode is activated (true) then the validations will contain only WARNING and ERROR messages.
     *
     * @param update true if there is a new version available suitable for installation.
     * @param updateType available operation after the new version is detected
     * @param quiet true to activate the quiet mode, false to deactivate it, 'null' to use the value from ConfigurationBundleManager.
     * @return JSON response for CLI and HTTP Endpoint to check new versions
     */
    public static JSONObject getUpdateCheckJsonResponse(boolean update, @CheckForNull UpdateType updateType, Boolean quiet) {
        JSONObject json = new JSONObject();
        json.accumulate("update-available", update);

        // Using BundleVisualizationLink so information is the same as in UI
        BundleVisualizationLink bundleInfo = BundleVisualizationLink.get();
        JSONObject versionSummary = new JSONObject();

        JSONObject currentBundle = new JSONObject();
        currentBundle.accumulate("version", StringUtils.defaultString(bundleInfo.getBundleVersion(), "N/A"));
        JSONArray currentValidations = new JSONArray();
        if (Objects.equals(bundleInfo.getBundleVersion(), bundleInfo.getDownloadedBundleVersion())) {
            currentValidations.addAll(getValidations(bundleInfo.getBundleValidations(), quiet));
        }
        currentBundle.accumulate("validations", currentValidations);

        versionSummary.accumulate("current-bundle", currentBundle);
        boolean updateTimingEnabled = BundleUpdateTimingManager.isEnabled();
        if (update || bundleInfo.isCandidateAvailable()) {
            JSONObject newAvailable = new JSONObject();
            final boolean valid = bundleInfo.isUpdateAvailable();
            String newVersion = updateTimingEnabled ? bundleInfo.getUpdateVersion() : bundleInfo.getDownloadedBundleVersion();
            newAvailable.accumulate("version", valid ? StringUtils.defaultString(newVersion, "N/A") : StringUtils.defaultString(bundleInfo.getCandidate().getVersion(), "N/A"));
            newAvailable.accumulate("valid", valid);
            BundleVisualizationLink.ValidationSection validations = null;
            if (updateTimingEnabled) {
                validations = bundleInfo.getCandidate().getValidations();
            } else {
                validations = valid ? bundleInfo.getBundleValidations() : bundleInfo.getCandidate().getValidations();
            }
            JSONArray newValidations = new JSONArray();
            newValidations.addAll(getValidations(validations, quiet));
            newAvailable.accumulate("validations", newValidations);
            versionSummary.accumulate("new-version", newAvailable);
        }
        json.accumulate("versions", versionSummary);

        if (update) {
            if (updateTimingEnabled) {
                BundleUpdateLog.CandidateBundle candidateBundle = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
                if (candidateBundle != null && !candidateBundle.isInvalid()) {
                    json.accumulate("update-type", updateType != null ? updateType.label : UpdateType.UNKNOWN);
                } else if (candidateBundle == null && updateType != null) {
                    // Automatic reload in place
                    json.accumulate("update-type", updateType.label);
                }
            } else if (!bundleInfo.isCandidateAvailable()) {
                json.accumulate("update-type", updateType != null ? updateType.label : UpdateType.UNKNOWN);
            }
        }
        if (update || bundleInfo.isCandidateAvailable()) {
            // Getting items that will be deleted on the update
            try {
                json.accumulateAll(getUpdateCheckReloadItemsDeletionJsonResponse());
            } catch (CasCException ex) {
                json.accumulate("items", ex.getMessage());
                LOGGER.log(Level.WARNING, "Error while checking deletions", ex);
            }
        }

        return json;
    }

    /**
     * Build the JSON response for CLI and HTTP Endpoint to check what items would be deleted if the bundle is applied. Example of response
     * <pre>
     * {
     *   "items": {
     *     "deletions": [
     *       "X",
     *       "Y",
     *       "Z"
     *     ]
     *   }
     * }
     * </pre>
     */
    public static JSONObject getUpdateCheckReloadItemsDeletionJsonResponse() throws CasCException {
        ConfigurationBundleManager configurationBundleManager = ConfigurationBundleManager.get();
        ConfigurationBundle bundle = Objects.requireNonNullElse(
                configurationBundleManager.getCandidateAsConfigurationBundle(),
                configurationBundleManager.getConfigurationBundle()
        );
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        JSONArray deletions = new JSONArray();
        deletions.addAll(service.getDeletionsOnReload(bundle));
        JSONObject responseContent = new JSONObject().accumulate("deletions", deletions);
        return new JSONObject().accumulate("items", responseContent);
    }

    private static List<String> getValidations(BundleVisualizationLink.ValidationSection vs, Boolean quietParam) {
        List<String> list = new ArrayList<>();
        if (vs.hasErrors()) {
            list.addAll(vs.getErrors().stream().map(s -> Validation.Level.ERROR + " - " + s).collect(Collectors.toList()));
        }
        if (vs.hasWarnings()) {
            list.addAll(vs.getWarnings().stream().map(s -> Validation.Level.WARNING + " - " + s).collect(Collectors.toList()));
        }
        boolean quiet = quietParam != null ? quietParam : vs.isQuiet();
        if (!quiet && vs.hasInfoMessages()) {
            list.addAll(vs.getInfoMessages().stream().map(s -> Validation.Level.INFO + " - " + s).collect(Collectors.toList()));
        }

        return list;
    }

    /**
     * Build the JSON response for CLI and HTTP Endpoint with detailed information about the update log.
     * <strong>CasC disabled</strong>
     * {
     *     "update-log-status": "CASC_DISABLED"
     * }
     * <strong>Update log disabled</strong>
     * {
     *     "update-log-status": "DISABLED"
     * }
     * <strong>Update log enabled</strong>
     * {
     *     "update-log-status": "ENABLED",
     *     "retention-policy": 10,
     *     "versions": [
     *         {
     *             "version": "6",
     *             "date": "09 May 2022",
     *             "errors": 0,
     *             "warnings": 0,
     *             "folder": "20220509_00006"
     *         },
     *         {
     *             "version": "5",
     *             "date": "09 May 2022",
     *             "errors": 1,
     *             "warnings": 0,
     *             "folder": "20220509_00005"
     *         },
     *         {
     *             "version": "4",
     *             "date": "09 May 2022",
     *             "errors": 0,
     *             "warnings": 0,
     *             "folder": "20220509_00004"
     *         },
     *         {
     *             "version": "3",
     *             "date": "09 May 2022",
     *             "errors": 1,
     *             "warnings": 0,
     *             "folder": "20220509_00003"
     *         },
     *         {
     *             "version": "2",
     *             "date": "09 May 2022",
     *             "errors": 0,
     *             "warnings": 0,
     *             "folder": "20220509_00002"
     *         },
     *         {
     *             "version": "1",
     *             "date": "09 May 2022",
     *             "errors": 0,
     *             "warnings": 0,
     *             "folder": "20220509_00001"
     *         }
     *     ]
     * }
     * @return JSON response for CLI and HTTP Endpoint to check new versions
     */
    public static JSONObject getUpdateLog() {
        // Using BundleVisualizationLink so information is the same as in UI
        BundleVisualizationLink bundleInfo = BundleVisualizationLink.get();

        JSONObject json = new JSONObject();

        if (!bundleInfo.isBundleUsed()) {
            json.accumulate("update-log-status", "CASC_DISABLED");
        } else if (bundleInfo.withUpdateLog()) {
            json.accumulate("update-log-status", "ENABLED");
            json.accumulate("retention-policy", bundleInfo.getCurrentRetentionPolicy());
            JSONArray logs = new JSONArray();
            bundleInfo.getUpdateLog().forEach(updateLogRow -> {
                JSONObject row = new JSONObject();
                row.accumulate("version", updateLogRow.getVersion());
                row.accumulate("date", updateLogRow.getDate());
                row.accumulate("errors", updateLogRow.getErrors());
                row.accumulate("warnings", updateLogRow.getWarnings());
                row.accumulate("info-messages", updateLogRow.getInfoMessages());
                row.accumulate("folder", updateLogRow.getFolder());
                logs.add(row);
            });
            json.accumulate("versions", logs);
        } else {
            json.accumulate("update-log-status", "DISABLED");
        }

        return json;
    }

    // Bundle Validation CLI/Endpoint

    /**
     * Creates a temporary folder within JENKINS_HOME with permissions check (just rwx for the owner)
     * @return Path to the temporary folder
     * @throws IOException if the folder cannot be created
     */
    public static Path createTemporaryFolder() throws IOException {
        return createTemporaryFolder(null);
    }

    /**
     * Creates a temporary folder within JENKINS_HOME with permissions check (just rwx for the owner)
     * @param prefix to add to the temporary folder
     * @return Path to the temporary folder
     * @throws IOException if the folder cannot be created
     */
    public static Path createTemporaryFolder(String prefix) throws IOException {
        String folderName = StringUtils.isNotBlank(prefix) ? prefix + "-" + "cloudbees-casc-client-" : "cloudbees-casc-client-";
        Path tempFolder = Jenkins.get().getRootDir().toPath().resolve(folderName + System.currentTimeMillis());
        LOGGER.log(Level.FINER, "Creating temp folder at {0}", tempFolder);
        return createDirectories(tempFolder);
    }

    /**
     * Creates the folders until path with just rwx for the owner
     * @param path Path to be created
     * @return The path
     * @throws IOException If there are issues creating the folder or setting permissions
     */
    public static Path createDirectories(Path path) throws IOException {
        Set<String> supported = path.getFileSystem().supportedFileAttributeViews();

        if (supported.contains("posix")) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(perms);

            Files.createDirectories(path, attr);
        } else if (!supported.contains("posix") && supported.contains("acl")){
            Files.createDirectories(path);
            setACLOwnerOnlyPermissions(path);
        } else {
            Files.createDirectories(path);
            setBasicOwnerOnlyPermissions(path.toFile());
        }

        return path;
    }

    /**
     * Set owner only ACL permissions to an already created file/folder.
     * It doesn't check if ACL is supported or not.
     * @param path The path to the folder/file
     * @throws IOException if such an exception happens when getting the owner of the file.
     */
    private static void setACLOwnerOnlyPermissions(Path path) throws IOException {
        AclFileAttributeView aclFileView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        final UserPrincipal owner = aclFileView.getOwner();

        List<AclEntry> permissions = Lists.newArrayListWithCapacity(2);
        permissions.add(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.WRITE_OWNER,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.DELETE_CHILD,
                        AclEntryPermission.SYNCHRONIZE,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.ADD_FILE,
                        AclEntryPermission.ADD_SUBDIRECTORY,
                        AclEntryPermission.LIST_DIRECTORY,
                        AclEntryPermission.EXECUTE)
                .build()
        );

        try {
            UserPrincipal others = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName("EVERYONE@");

            permissions.add(AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(others)
                    .setPermissions(AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.DELETE_CHILD,
                            AclEntryPermission.SYNCHRONIZE)
                    .build()
            );
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "EVERYONE@ cannot be lookup for {0}", path);
        }

        aclFileView.setAcl(permissions);
    }

    /**
     * Set owner only permissions for the file
     * @param file The file
     */
    private static void setBasicOwnerOnlyPermissions(File file) {
        if (!file.setExecutable(false, false) || !file.setExecutable(true, true)) {
            // executable permission is not available in windows.
            LOGGER.log(Level.FINE, "Probably the OS doesn't support executable permission on {0}", file);
        }

        if (!file.setWritable(false, false) || !file.setWritable(true, true)) {
            LOGGER.log(Level.WARNING, "Writable permissions cannot be set on {0}", file);
        }

        if (!file.setReadable(false, false) || !file.setReadable(true, true)) {
            LOGGER.log(Level.WARNING, "Readable permissions cannot be set on {0}", file);
        }
    }

    /**
     * Make a full validation of the bundle: structural and runtime validations.
     * The returned list will contain all the validation messages.
     *
     * @param bundleDir Path to the bundle to validate
     * @return List of validation messages
     * @see ConfigurationUpdaterHelper#fullValidation(Path, String, Boolean)
     */
    @NonNull
    public static List<Validation> fullValidation(Path bundleDir) {
        // Dev memo: quiet mode is off by default, see BEE-35011
        return fullValidation(bundleDir, false);
    }

    /**
     * Make a full validation of the bundle: structural and runtime validations.
     * If quiet mode is activated (true), then the returned list will contain only WARNING and ERROR messages.
     *
     * @param bundleDir Path to the bundle to validate
     * @param quietParam true to activate the quiet mode, false to deactivate it, 'null' to use the value from ConfigurationBundleManager.
     * @return List of validation messages
     */
    @NonNull
    public static List<Validation> fullValidation(Path bundleDir, Boolean quietParam) {

        // Structural validations
        PlainBundle<Path> bundle = new PathPlainBundle(bundleDir);
        BundleValidator validator = new BundleValidator.Builder().withBundle(bundle)
                .addValidator(new FileSystemBundleValidator())
                .addValidator(new DescriptorValidator())
                .addValidator(new ContentBundleValidator())
                .addValidator(new YamlSchemaValidator())
                .addValidator(new PluginCatalogInOCValidator())
                .addValidator(new PluginsToInstallValidator())
                .addValidator(new MultipleCatalogFilesValidator())
                .build();
        ArrayList<Validation> validations = new ArrayList<>(validator.validate().getValidations());

        // Runtime validations
        try {
            AbstractValidator.performValidations(bundleDir);
        } catch (InvalidBundleException e) {
            validations.addAll(e.getValidationResult());
        }

        // Send event to Segment
        new BundleValidationErrorGatherer(validations).send();

        boolean quiet = quietParam != null ? quietParam : ConfigurationBundleManager.get().isQuiet();
        if (quiet) {
            return validations.stream()
                              .filter(validation -> validation.getLevel() == Validation.Level.ERROR
                                                    || validation.getLevel() == Validation.Level.WARNING)
                              .collect(Collectors.toList());
        } else {
            return validations;
        }
    }

    /**
     * Make a full validation of the bundle: structural and runtime validations.
     * The returned list will contain all the validation messages.
     * Also logs associated commit
     * @param bundleDir Path to the bundle to validate
     * @param commit The commit's hash for logging purposes
     * @return List of validation messages
     */
    @NonNull
    public static List<Validation> fullValidation(Path bundleDir, String commit) {
        // Dev memo: quiet mode is off by default, see BEE-35011
        return fullValidation(bundleDir, commit, false);
    }

    /**
     * Make a full validation of the bundle: structural and runtime validations.
     * Also logs associated commit
     * If quiet mode is activated (true), then the returned list will contain only WARNING and ERROR messages.
     *
     * @param bundleDir Path to the bundle to validate
     * @param commit The commit's hash for logging purposes
     * @param quietParam Define if the quiet mode will be used or not
     * @return List of validation messages
     */
    @NonNull
    public static List<Validation> fullValidation(Path bundleDir, String commit, Boolean quietParam) {
        if (StringUtils.isNotBlank(commit)) {
            LOGGER.log(Level.INFO, String.format("Validating bundles associated with commit %s", commit));
        }
        return fullValidation(bundleDir, quietParam);
    }

    public static JSONObject getValidationJSON(@NonNull List<Validation> validations) {
        return getValidationJSON(validations, null);
    }

    public static JSONObject getValidationJSON(@NonNull List<Validation> validations, String commit) {
        JSONObject json = new JSONObject();

        boolean valid = validations.stream().noneMatch(v -> v.getLevel() == Validation.Level.ERROR);
        json.accumulate("valid", valid);
        if (StringUtils.isNotBlank(commit)) {
            json.accumulate("commit", commit);
        }
        if (!validations.isEmpty()) {
            JSONArray array = new JSONArray();
            array.addAll(validations.stream().map(v -> v.toString()).collect(Collectors.toList()));
            json.accumulate("validation-messages", array);
        }

        return json;
    }

    /**
     * Perform the manual skip of candidate
     * @return true if it was possible to skip it. False otherwise
     */
    public synchronized static boolean skipCandidate() {
        try {
            return doSkipCandidate();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error skipping the candidate bundle", e);
            return false;
        }
    }

    /**
     * Internal method to perform the manual skip of candidate. To be used only in this plugin as it doesn't handle the exceptions
     * @return true if it was possible to skip it. False otherwise
     * @throws IOException if the candidate cannot be skipped
     */
    @Restricted(NoExternalUse.class)
    public synchronized static boolean doSkipCandidate() throws IOException {
        BundleUpdateLog updateLog = ConfigurationBundleManager.get().getUpdateLog();
        BundleUpdateLog.CandidateBundle fromUpdateLog = updateLog.getCandidateBundle();
        if (fromUpdateLog == null) {
            BundleUpdateStatus.failCurrentAction(BundleUpdateLogAction.SKIP, "Attempt to skip a candidate that doesn't exist. Ignoring");
            throw new IOException("Attempt to skip a candidate that doesn't exist. Ignoring");
        }
        BundleUpdateLog.CandidateBundle candidateBundle = updateLog.skipCandidate(fromUpdateLog);
        boolean skipped = candidateBundle.isSkipped();
        if (skipped) {
            BundleUpdateStatus.successCurrentAction(BundleUpdateLogAction.SKIP, bundleUpdateStatus -> bundleUpdateStatus.setSkipped(true));
        } else {
            BundleUpdateStatus.failCurrentAction(BundleUpdateLogAction.SKIP, "Fail to skip the candidate");
        }
        ConfigurationStatus.INSTANCE.setUpdateAvailable(!skipped);
        ConfigurationBundleManager.refreshUpdateLog();
        CasCPublisherHelper.publishCasCUpdate();
        return skipped;
    }

    /**
     * Promote the candidate so the instance can be reloaded or restarted
     * @return true if it was possible to promote it. False otherwise
     */
    public synchronized static boolean promoteCandidate() {
        final ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        final BundleUpdateLog.CandidateBundle candidateBundle = bundleManager.getUpdateLog().getCandidateBundle();
        final ConfigurationBundle currentBundle = bundleManager.getConfigurationBundle();

        if (candidateBundle == null) {
            LOGGER.log(Level.WARNING, "Attempt to promote a candidate that doesn't exist. Ignoring request");
            return false;
        }

        if (candidateBundle.isInvalid()) {
            LOGGER.log(Level.WARNING, "Attempt to promote a candidate with validations errors. Ignoring");
            return false;
        }

        if (BundleUpdateTimingConfiguration.get().canSkipNewVersions() && candidateBundle.isSkipped()) {
            LOGGER.log(Level.WARNING, "Attempt to promote a skipped candidate. Ignoring");
            return false;
        }

        ConfigurationBundle promoted = ConfigurationBundleManager.promote(true); // Plugin is active, so up and running
        boolean hotReloadable = isHotReloadable(promoted);
        ConfigurationBundleManager.get().getConfigurationBundle().setHotReloadable(hotReloadable);

        String currentVersion = StringUtils.defaultString(currentBundle.getBundleInfo());
        String promotedVersion = StringUtils.defaultString(promoted.getBundleInfo());
        boolean isPromoted = !promotedVersion.equals(currentVersion);
        if (isPromoted) {
            CasCPublisherHelper.publishCasCUpdate();
        }
        return isPromoted;
    }

    /**
     * Checks if the bundle is hot-reloadable
     * @param bundle to check. If null, then the method returns false
     * @return true if the bundle is hot-reloadable, false if it isn't.
     */
    public static boolean isHotReloadable(ConfigurationBundle bundle) {
        if (bundle != null) {
            try {
                ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
                return service.isHotReloadable(bundle);
            } catch (IllegalStateException e) {
                LOGGER.log(Level.WARNING, "Reload is disabled because either ConfigurationBundleService is not loaded or plugins could not be checked.", e);
            }
        }

        return false;
    }

    /**
     * When using the CLI or the endpoint, it returns the expected reload type
     * @return If bundle update timing is disable, RELOAD if the new version is hot-reloadable, otherwise RESTART
     *         If bundle update timing is enable:
     *              - If automatic reload and hot-reloadable: AUTOMATIC RELOAD
     *              - If automatic restart: AUTOMATIC RESTART
     *              - If skipped: SKIPPED
     *              - Otherwise: RELOAD/RESTART/SKIP or RESTART/SKIP depending on whether the bundle is hot reloadable
     */
    public static UpdateType getUpdateTypeForCliAndEndpoint() {
        if (BundleUpdateTimingManager.isEnabled()) {
            if (ConfigurationStatus.INSTANCE.isCurrentlyReloading()) {
                // Automatic reload in progress
                return UpdateType.AUTOMATIC_RELOAD;
            }
            if (!ConfigurationStatus.INSTANCE.isUpdateAvailable()) {
                // Skipped, automatic reload finished or automatic restart scheduled
                BundleUpdateLog.CandidateBundle candidateBundle = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
                if (candidateBundle != null && candidateBundle.isSkipped()) {
                    return UpdateType.SKIPPED;
                }
                return SafeRestartMonitor.get().isActivated() ? UpdateType.AUTOMATIC_RESTART : UpdateType.AUTOMATIC_RELOAD;
            }
            BundleUpdateLog.CandidateBundle candidateBundle = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
            ConfigurationBundle asConfigurationBundle = ConfigurationBundleManager.get().getCandidateAsConfigurationBundle();
            if (candidateBundle != null && asConfigurationBundle != null) {
                if (candidateBundle.isSkipped()) {
                    return UpdateType.SKIPPED;
                }

                return asConfigurationBundle.isHotReloadable() ? UpdateType.RELOAD_OR_SKIP : UpdateType.RESTART_OR_SKIP;
            }

            // Something went wrong and the promotion didn't happen, so let's force the restart. Should not happen, it's a safe restart
            return UpdateType.RESTART;
        }

        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable() ? UpdateType.RELOAD : UpdateType.RESTART;
    }

    /**
     * Parse a {@link LocalDateTime} object in UTC.
     * Output format: "{@link FormatStyle#LONG} {@link FormatStyle#SHORT} UTC" in {@link Locale#ENGLISH}. Ex, October 24, 2023, 11:50 AM UTC
     * @param ldt LocalDateTime to parse
     * @return String representing the LocalDateTime object
     * @throws DateTimeException if the date or time cannot be parsed
     */
    @CheckForNull
    public static String parse(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }

        return ldt.format(FORMATTER) + " UTC";
    }

}
