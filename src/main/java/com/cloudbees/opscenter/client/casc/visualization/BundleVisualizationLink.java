package com.cloudbees.opscenter.client.casc.visualization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.opscenter.client.casc.BundleExporter;
import com.cloudbees.opscenter.client.casc.CheckNewBundleVersionException;
import com.cloudbees.opscenter.client.casc.ConfigurationBundleService;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.PluginCatalogExporter;

/**
 * Configuration as Code Bundle Visualization.
 */
@Restricted(NoExternalUse.class)
@Extension
public class BundleVisualizationLink extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(BundleVisualizationLink.class.getName());

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/cloudbees-casc-client/images/CB_CasC_UpDown.svg";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "CloudBees Configuration as Code export and update";
    }

    @Override
    public String getDescription() {
        return "Export and update Configuration as Code bundles";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "casc-bundle-export-ui";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.MANAGE;
    }

    // used in jelly
    public List<BundleExporter> getExporters() {
        return BundleExporter.all();
    }

    // stapler
    public HttpResponse doIndex() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            // Only Overall/Administer is allowed to see the list of files in the bundle and download them, as well as
            // any other tab in the UI.
            return HttpResponses.forwardToView(this, "index.jelly");
        } else {
            // Overall/Manage is not allowed to see the list of files or download them, so the default index is wrong in this case.
            // The selected tab for Overall/Manage is the "Bundle Update" one
            // This is a redirect because the URL must change to match the URL in the tab in UI, so it is displayed as selected
            return HttpResponses.redirectViaContextPath("casc-bundle-export-ui/bundleUpdate");
        }
    }

    /**
     * @return false if the product is OC and the System property is not set
     */
    public boolean isPluginCatalogExportable() {
        return ExtensionList.lookup(PluginCatalogExporter.class).stream().anyMatch(exporter -> exporter.enabled());
    }

    /**
     * Serves "casc-bundle-export-ui/bundleUpdate".
     *
     * Requires MANAGE permission.
     * Renders the tab "Bundle Update", but first checks if there is any bundle update.
     * @return forward to the view "_bundleupdate.view"
     */
    // stapler
    public HttpResponse doBundleUpdate() throws Exception {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            ConfigurationUpdaterHelper.checkForUpdates();
        } catch (CheckNewBundleVersionException e) {
            LOGGER.log(Level.WARNING, "Error checking the new bundle version.", e);
        }
        return HttpResponses.forwardToView(this, "_bundleupdate.jelly");
    }

    public HttpResponse doUpdateLog() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        return HttpResponses.forwardToView(this, "_updateLog.jelly");
    }

    /**
     * Used in jelly to display the Update log tab
     * @return true if the update log is enabled
     */
    //used in jelly
    public boolean withUpdateLog() {
        return isBundleUsed() && BundleUpdateLog.retentionPolicy() != 0;
    }

    /**
     * @return the current retention policy
     */
    //used in jelly
    public long getCurrentRetentionPolicy() {
        return BundleUpdateLog.retentionPolicy();
    }

    /**
     * @return true if the current instance has been configured with a CasC bundle
     */
    //used in jelly
    public boolean isBundleUsed(){
        return ConfigurationBundleManager.isSet();
    }

    /**
     * @return if the instance can promote new bundle versions
     */
    // used in jelly
    public boolean instanceWillSkip() {
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        return  configuration.canSkipNewVersions() && configuration.isSkipNewVersions();
    }

    /**
     * @return if the instance will apply new bundle versions on restart
     */
    // used by jelly
    public boolean isAlwaysOnRestart() {
        return BundleUpdateTimingConfiguration.get().isReloadAlwaysOnRestart();
    }

    /**
     * @return True/False if there is/isn't a new version fo the casc bundle available.
     */
    //used in jelly
    public boolean isUpdateAvailable() {
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
    }

    /**
     * @return True/False if there is/isn't a new version that was rejected.
     */
    //used in jelly
    public boolean isCandidateAvailable() {
        return ConfigurationStatus.INSTANCE.isCandidateAvailable();
    }

    /**
     * @return True/False if an error happened while checking/downloading the new bundle version.
     */
    //used in jelly
    public boolean isErrorInNewVersion(){
        return ConfigurationStatus.INSTANCE.isErrorInNewVersion();
    }

    /**
     * @return Message from the error when the new bundle version cannot be checked/downloaded
     */
    //used in jelly
    public String getErrorMessage(){
        return ConfigurationStatus.INSTANCE.getErrorMessage();
    }

    /**
     *
     * @return true if a bundle reload is runnign in the background, false otherwise
     */
    // Used by jelly
    public boolean isReloadInProgress() {
        return ConfigurationStatus.INSTANCE.isCurrentlyReloading();
    }

    /**
     * @return the version of the incoming bundle.
     */
    @CheckForNull
    public String getUpdateVersion(){
        if(isUpdateAvailable()) {
            return ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
        }
        return null;
    }

    /**
     * @return the info of the incoming bundle.
     */
    @CheckForNull
    public String getUpdateInfo(){
        if(isUpdateAvailable()) {
            return ConfigurationBundleManager.get().getConfigurationBundle().getBundleInfo();
        }
        return null;
    }

    /**
     * @return the version of the currently installed bundle, or null if there is no bundle
     */
    //used in jelly
    @CheckForNull
    public String getBundleVersion(){
        if(ConfigurationStatus.INSTANCE.getOutdatedVersion() != null) {
            return ConfigurationStatus.INSTANCE.getOutdatedVersion();
        }
        return ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
    }

    /**
     * @return the version of the currently installed bundle, or null if there is no bundle
     */
    //used in jelly
    @CheckForNull
    public String getBundleInformation(){
        if(ConfigurationStatus.INSTANCE.getOutdatedVersion() != null) {
            return ConfigurationStatus.INSTANCE.getOutdatedBundleInformation();
        }
        return ConfigurationBundleManager.get().getConfigurationBundle().getBundleInfo();
    }

    /**
     * While {@link BundleVisualizationLink#getBundleVersion()} returns the installed version, this method returns the downloaded
     * version, i.e., the installed version or the new downloaded version if the new version still has to be applied.
     * @return the version of the currently downloaded bundle, or null if there is no downloaded bundle
     */
    //used in jelly
    @CheckForNull
    public String getDownloadedBundleVersion(){
        return ConfigurationBundleManager.get().getConfigurationBundle().getVersion();
    }

    /**
     * While {@link BundleVisualizationLink#getBundleInformation()} ()} returns the installed bundle information, this method returns the downloaded
     * bundle information, i.e., the installed version or the new downloaded version if the new version still has to be applied.
     * @return the version of the currently downloaded bundle, or null if there is no downloaded bundle
     */
    //used in jelly
    @CheckForNull
    public String getDownloadedBundleInfo(){
        return ConfigurationBundleManager.get().getConfigurationBundle().getBundleInfo();
    }

    /**
     * @return The current bundle validation.
     */
    //used in jelly
    @NonNull
    public ValidationSection getBundleValidations() {
        if (!ConfigurationBundleManager.isSet()) {
            return new ValidationSection();
        }
        BundleUpdateLog.BundleValidationYaml currentVersionValidations = ConfigurationBundleManager.get().getUpdateLog().getCurrentVersionValidations();
        if (currentVersionValidations == null) {
            return new ValidationSection();
        }
        return new ValidationSection(
                currentVersionValidations.getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.WARNING).map(v -> v.getMessage()).collect(Collectors.toList()),
                currentVersionValidations.getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.ERROR).map(v -> v.getMessage()).collect(Collectors.toList()),
                currentVersionValidations.getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.INFO).map(v -> v.getMessage()).collect(Collectors.toList()),
                ConfigurationBundleManager.get().isQuiet());
    }

    /**
     * @return The current candidate information.
     */
    // used in jelly
    @NonNull
    public CandidateSection getCandidate() {
        if (!ConfigurationBundleManager.isSet()) {
            return new CandidateSection();
        }
        return new CandidateSection(ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle());
    }

    /**
     * @return The 10 first elements of the update log registry.
     */
    //used in jelly
    @NonNull
    public List<UpdateLogRow> getTruncatedUpdateLog() {
        return getUpdateLog().stream().limit(10).collect(Collectors.toList());
    }

    /**
     * @return The full update log registry.
     */
    //used in jelly
    @NonNull
    public List<UpdateLogRow> getUpdateLog() {
        if (getCurrentRetentionPolicy() == 0) {
            return Collections.emptyList();
        }
        return ConfigurationBundleManager.get().getUpdateLog().getHistoricalRecords().stream().map(path -> new UpdateLogRow(BundleUpdateLog.CandidateBundle.loadCandidate(path))).filter(u -> !u.isEmpty()).collect(Collectors.toList());
    }

    /**
     * @return the Date when the last check for update was done (by default it is the jenkins startup date)
     */
    //used in jelly
    public Date getLastCheckForUpdate(){
        return ConfigurationStatus.INSTANCE.getLastCheckForUpdate();
    }

    //used in jelly
    public boolean isHotReloadable() {
        return ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
    }

    // used in jelly
    public boolean withDiff() {
        return ConfigurationStatus.INSTANCE.getChangesInNewVersion() != null;
    }

    // used in jelly
    public List<String> getItemsToDelete() {
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        try {
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            if (bundle.getItems() == null) { // Not needed after cloudbees-casc-items-api:2.25
                return Collections.EMPTY_LIST;
            } else {
                return service.getDeletionsOnReload(bundle);
            }

        } catch (CasCException ex){
            LOGGER.log(Level.WARNING, String.format("Bundle has an invalid items removeStrategy, not calculating items to delete"));
        }
        return Collections.EMPTY_LIST;
    }

    @RequirePOST
    public HttpResponse doAct(StaplerRequest req) throws IOException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        if (req.hasParameter("restart")) {
            return HttpResponses.redirectViaContextPath("/safeRestart");
        } else if (req.hasParameter("reload")) {
            return HttpResponses.redirectViaContextPath("/coreCasCHotReload");
        } else if (req.hasParameter("force")) {
            return HttpResponses.redirectViaContextPath("/coreCasCForceReload");
        } else {
            return HttpResponses.redirectViaContextPath(this.getUrlName() + "/bundleUpdate");
        }
    }

    // used in jelly
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. bundleFolder and subdirectories are checked, so listFiles cannot be null")
    public List<CasCFile> getEffectiveBundle() {
        final List<CasCFile> files = new ArrayList<>();

        try {
            // Shouldn't be needed, but this check here does not hurt
            if (isBundleUsed()) {
                final Path bundleFolder = ConfigurationBundleManager.getBundleFolder();
                Files.walk(bundleFolder).filter(Files::isRegularFile).forEach(path -> {
                    final String firstElement = path.subpath(0, 1).toFile().getPath();
                    // https://stackoverflow.com/questions/50685385/kubernetes-config-map-symlinks-data-is-there-a-way-to-avoid-them
                    if (!firstElement.startsWith("..")) {
                        final String fileName = bundleFolder.relativize(path).toFile().getPath();
                        final String description = toDescription(fileName);
                        files.add(new CasCFile(fileName, description));
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error walking the tree directory", e);
        }

        Collections.sort(files);

        return files;
    }

    private String toDescription(final String section) {
        if (section.startsWith("bundle")) {
            return "The bundle descriptor";
        } else if (section.startsWith("jenkins") || section.startsWith("jcasc")) {
            return "Jenkins configuration as defined by OSS CasC";
        } else if (section.startsWith("plugins")) {
            return "Plugins to install in the instance";
        } else if (section.startsWith("catalog") || section.startsWith("plugin-catalog")) {
            return "The plugin catalog to install in the instance";
        } else if (section.startsWith("items")) {
            return "The items to create in the instance";
        } else if (section.startsWith("rbac")) {
            return "The global groups and roles in the instance";
        } else {
            return "";
        }
    }

    /**
     * DTO class with the information to locate the file within the bundle
     *
     * Section: Section in the bundle (folder). Null for bundle descriptor
     * Filename: File name in disk
     */
    public static final class CasCFile implements Comparable<CasCFile> {
        private final String filename;
        private final String description;

        private CasCFile(@NonNull String filename, @NonNull String description) {
            this.filename = filename;
            this.description = description;
        }

        public String getFullname() {
            return filename;
        }

        @Deprecated
        public String getSection() {
            return null;
        }

        public String getFilename() {
            return filename;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public int compareTo(CasCFile casCFile) {
            return this.filename.compareTo(casCFile.filename);
        }

        @Override
        public String toString() {
            return getFullname();
        }

        @Override
        public int hashCode() {
            return Objects.hash(filename);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || ! (obj instanceof CasCFile)) {
                return false;
            }

            CasCFile that = (CasCFile) obj;
            return Objects.equals(this.filename, that.filename);
        }
    }

    /**
     * DTO containing the information regarding validations for UI
     */
    public static class ValidationSection {

        private final List<String> warnings;
        private final List<String> errors;
        private final List<String> infoMessages;
        private final boolean quiet;

        private ValidationSection() {
            // Dev memo: quiet mode if "false" by default, see BEE-35011
            this(null, null, null, false);
        }

        private ValidationSection(List<String> warnings, List<String> errors, List<String> info, boolean quiet) {
            List<String> warnings_ = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
            Collections.sort(warnings_);
            List<String> errors_ = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            Collections.sort(errors_);
            List<String> info_ = info != null ? new ArrayList<>(info) : new ArrayList<>();
            Collections.sort(info_);

            this.warnings = Collections.unmodifiableList(warnings_);
            this.errors = Collections.unmodifiableList(errors_);
            this.infoMessages = Collections.unmodifiableList(info_);
            this.quiet = quiet;
        }

        public boolean isEmpty() {
            return warnings.isEmpty() && errors.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasInfoMessages() {
            return !infoMessages.isEmpty();
        }

        @NonNull
        public List<String> getWarnings() {
            return warnings;
        }

        @NonNull
        public List<String> getErrors() {
            return errors;
        }

        @NonNull
        public List<String> getInfoMessages() {return infoMessages; }

        public boolean isQuiet() {
            return quiet;
        }
    }

    /**
     * DTO containing the information regarding validations for UI
     */
    public static class CandidateSection {

        private final ValidationSection validations;
        private final String version;
        private final String info;
        private final boolean invalid;
        private final boolean skipped;

        private CandidateSection() {
            this(null);
        }

        private CandidateSection(BundleUpdateLog.CandidateBundle candidate) {
            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<String> infos = new ArrayList<>();
            String version = null;
            StringBuilder info = null;
            boolean skipped = false;
            boolean invalid = false;
            if (candidate != null) {
                warnings = candidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.WARNING).map(v -> v.getMessage()).collect(Collectors.toList());
                errors = candidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.ERROR).map(v -> v.getMessage()).collect(Collectors.toList());
                infos = candidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.INFO).map(v -> v.getMessage()).collect(Collectors.toList());
                version = candidate.getVersion();
                info = new StringBuilder();
                if (StringUtils.isNotBlank(candidate.getId())) {
                    info.append(candidate.getId());
                }
                if (StringUtils.isNotBlank(version)) {
                    if (StringUtils.isNotBlank(candidate.getId())) {
                        info.append(":");
                    }
                    info.append(version);
                }
                if (StringUtils.isNotBlank(candidate.getChecksum())) {
                    info.append(" (checksum " + candidate.getChecksum() + ")");
                }

                skipped = candidate.isSkipped();
                invalid = candidate.isInvalid();
            }
            boolean quiet = ConfigurationBundleManager.get().isQuiet();
            this.validations = new ValidationSection(warnings, errors, infos, quiet);
            this.version = version;
            this.info = info == null ? null : info.toString();
            this.skipped = skipped;
            this.invalid = invalid;
        }

        @NonNull
        public ValidationSection getValidations() {
            return validations;
        }

        @CheckForNull
        public String getVersion() {
            return version;
        }

        @CheckForNull
        public String getInfo() {
            return info;
        }

        public boolean isInvalid() {
            return invalid;
        }

        public boolean isSkipped() {
            return skipped;
        }
    }

    public static class UpdateLogRow {

        private final String id;
        private final String version;
        private final String checksum;
        private final String description;
        private final Date date;
        private final long errors;
        private final long warnings;
        private final long infoMessages;
        private final String folder;
        private final boolean skipped;
        private final boolean invalid;

        private UpdateLogRow(BundleUpdateLog.CandidateBundle candidate) {
            this.id =  candidate == null ? null : candidate.getId();
            this.folder = candidate == null ? null : candidate.getFolder();
            this.version = candidate == null ? null : candidate.getVersion();
            this.checksum = candidate == null ? null : candidate.getChecksum();
            this.description = candidate == null ? null : candidate.getDescription();
            this.errors = candidate == null ? 0L : candidate.getValidations().getValidations().stream().filter(s -> s.getLevel() == Validation.Level.ERROR).count();
            this.warnings = candidate == null ? 0L : candidate.getValidations().getValidations().stream().filter(s -> s.getLevel() == Validation.Level.WARNING).count();
            this.infoMessages = candidate == null ? 0L : candidate.getValidations().getValidations().stream().filter(s -> s.getLevel() == Validation.Level.INFO).count();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
            Date d = null;
            boolean skipped = false;
            boolean invalid = false;
            if (candidate != null) {
                try {
                    d = formatter.parse(candidate.getFolder().substring(0, candidate.getFolder().indexOf("_")));
                } catch (ParseException e) {
                    d = null;
                }
                skipped = candidate.isSkipped();
                invalid = candidate.isInvalid();
            }
            this.date = d;
            this.skipped = skipped;
            this.invalid = invalid;
        }

        public boolean isEmpty() {
            return StringUtils.isBlank(this.folder);
        }

        public String getVersion() {
            return version;
        }

        public String getFullVersion() {
            if (StringUtils.defaultString(version).equals(StringUtils.defaultString(checksum))) {
                return version;
            }
            return version + " (checksum " + checksum + ")";
        }

        public String getId() {
            return id;
        }

        public String getChecksum() {
            return checksum;
        }

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "False positive")
        public Date getDate() {
            return date;
        }

        public long getErrors() {
            return errors;
        }

        public long getWarnings() {
            return warnings;
        }

        public long getInfoMessages() { return infoMessages; }

        public String getFolder() {
            return folder;
        }

        public String getDescription() {
            return description;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public boolean isInvalid() {
            return invalid;
        }
    }
}
