package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
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
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.analytics.BundleValidationErrorGatherer;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.casc.validation.AbstractValidator;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
                String idBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getId();
                String checksumBeforeUpdate = ConfigurationBundleManager.get().getConfigurationBundle().getChecksum();
                if (ConfigurationBundleManager.get().downloadIfNewVersionIsAvailable()) {
                    ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
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
                        try {
                            Path candidatePath = BundleUpdateLog.getHistoricalRecordsFolder().resolve(newCandidate.getFolder());
                            BundleComparator.Result result = BundleComparator.compare(ConfigurationBundleManager.getBundleFolder(), candidatePath.resolve("bundle"));
                            ConfigurationStatus.INSTANCE.setChangesInNewVersion(result);
                        } catch (IllegalArgumentException | IOException e) {
                            ConfigurationStatus.INSTANCE.setChangesInNewVersion(null);
                            LOGGER.log(Level.WARNING, "Unexpected error comparing the candidate bundle and the current applied version", e);
                        }

                        ConfigurationBundleManager.promote();
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
                            newVersionIsValid ? ConfigurationBundleManager.get().getConfigurationBundle().getVersion() : newCandidate.getVersion()));
                    ConfigurationStatus.INSTANCE.setUpdateAvailable(newVersionIsValid);
                    ConfigurationStatus.INSTANCE.setCandidateAvailable(!newVersionIsValid);

                    if (ConfigurationStatus.INSTANCE.getOutdatedVersion() == null) {
                        // If there is no previous known version, store it
                        ConfigurationStatus.INSTANCE.setOutdatedVersion(versionBeforeUpdate);
                        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(idBeforeUpdate, versionBeforeUpdate, checksumBeforeUpdate);
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
            Throwable cause = e.getCause() != null ? e.getCause() : e; // If e is a NPE, then the cause is null
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
     * Note that the produced JSON will include all the validation messages.
     *
     * @param update true if there is a new version available suitable for installation.
     * @param isHotReload true if the new version can be applied with a Hot Reload
     * @return JSON response for CLI and HTTP Endpoint to check new versions
     * @see ConfigurationUpdaterHelper#getUpdateCheckJsonResponse(boolean, boolean, Boolean)
     */
    public static JSONObject getUpdateCheckJsonResponse(boolean update, boolean isHotReload) {
        // Dev memo: quiet mode is off by default, see BEE-35011
        return getUpdateCheckJsonResponse(update, isHotReload, false);
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
     * @param isHotReload true if the new version can be applied with a Hot Reload
     * @param quiet true to activate the quiet mode
     * @return JSON response for CLI and HTTP Endpoint to check new versions
     */
    public static JSONObject getUpdateCheckJsonResponse(boolean update, boolean isHotReload, Boolean quiet) {
        JSONObject json = new JSONObject();
        json.accumulate("update-available", update);

        // Using BundleVisualizationLink so information is the same as in UI
        BundleVisualizationLink bundleInfo = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
        JSONObject versionSummary = new JSONObject();

        JSONObject currentBundle = new JSONObject();
        currentBundle.accumulate("version", StringUtils.defaultString(bundleInfo.getBundleVersion(), "N/A"));
        JSONArray currentValidations = new JSONArray();
        if (Objects.equals(bundleInfo.getBundleVersion(), bundleInfo.getDownloadedBundleVersion())) {
            currentValidations.addAll(getValidations(bundleInfo.getBundleValidations(), quiet));
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
            newValidations.addAll(getValidations(validations, quiet));
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
        // Getting items that will be deleted on the update
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        try {
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            JSONArray deletions = new JSONArray();
            deletions.addAll(bundle.getItems() == null ? Collections.EMPTY_LIST : service.getDeletionsOnReload(bundle)); // Not needed after cloudbees-casc-items-api:2.25
            JSONObject responseContent = new JSONObject().accumulate("deletions", deletions);
            json.accumulate("items", responseContent);
        } catch (CasCException ex){
            LOGGER.log(Level.WARNING, "Error while checking deletions, invalid remove strategy provided");
        }

        return json;
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
        BundleVisualizationLink bundleInfo = ExtensionList.lookupSingleton(BundleVisualizationLink.class);

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
                row.accumulate("date", new SimpleDateFormat("dd MMMM yyyy").format(updateLogRow.getDate()));
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
     * @param quietParam if true, the quiet mode will be enabled
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
        return fullValidation(bundleDir, commit);
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
}
