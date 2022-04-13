package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.opscenter.client.casc.BundleExporter;
import com.cloudbees.opscenter.client.casc.CheckNewBundleVersionException;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.PluginCatalogExporter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configuration as Code Bundle Visualization.
 */
@IgnoreJRERequirement // this is local to the controller, no need to get it signature checked.
@Restricted(NoExternalUse.class)
@Extension
public class BundleVisualizationLink extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(BundleVisualizationLink.class.getName());

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/cloudbees-casc-client/images/CasC-Export.svg";
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
        return BundleUpdateLog.retentionPolicy() != 0;
    }

    /**
     * @return the current retention policy
     */
    //used in jelly
    public long getCurrentRetentionPolicy() {
        return BundleUpdateLog.retentionPolicy();
    }

    /**
     * @return true if the current controller has been configured with a CasC bundle
     */
    //used in jelly
    public boolean isBundleUsed(){
        return ConfigurationBundleManager.isSet();
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
                currentVersionValidations.getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.ERROR).map(v -> v.getMessage()).collect(Collectors.toList()));
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
        // Shouldn't be needed, but this check here does not hurt
        if (!isBundleUsed()) {
            return Collections.emptyList();
        }

        List<CasCFile> files = new ArrayList<>();
        final Path bundleFolder = ConfigurationBundleManager.getBundleFolder();
        for (String section : bundleFolder.toFile().list()) {
            String description = "";
            if (section.startsWith("..")) {
                continue; // https://stackoverflow.com/questions/50685385/kubernetes-config-map-symlinks-data-is-there-a-way-to-avoid-them
            } else if (section.startsWith("bundle")) {
                description = "The bundle descriptor";
            } else if (section.startsWith("jenkins") || section.startsWith("jcasc")) {
                description = "Jenkins configuration as defined by OSS CasC";
            } if (section.startsWith("plugins")) {
                description = "Plugins to install in the controller";
            } else if (section.startsWith("catalog") || section.startsWith("plugin-catalog")) {
                description = "The plugin catalog to install in the controller";
            } else if (section.startsWith("items")) {
                description = "The items to create in the controller";
            } else if (section.startsWith("rbac")) {
                description = "The global groups and roles in the controller";
            }
            File current = bundleFolder.resolve(section).toFile();
            if (current.isDirectory()) {
                for (String file : current.list()) {
                    files.add(new CasCFile(section, file, description));
                }
            } else {
                files.add(new CasCFile(null, section, description));
            }
        }

        Collections.sort(files);
        return files;
    }

    /**
     * DTO class with the information to locate the file within the bundle
     *
     * Section: Section in the bundle (folder). Null for bundle descriptor
     * Filename: File name in disk
     */
    public static final class CasCFile implements Comparable<CasCFile> {
        private final String section;
        private final String filename;
        private final String description;

        private CasCFile(String section, @NonNull String filename, @NonNull String description) {
            this.section = section;
            this.filename = filename;
            this.description = description;
        }

        public String getFullname() {
            return section == null ? filename : section + "/" + filename;
        }

        public String getSection() {
            return section;
        }

        public String getFilename() {
            return filename;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public int compareTo(CasCFile casCFile) {
            if (this.section == null && casCFile.section != null) {
                return -1;
            }
            if (this.section != null && casCFile.section == null) {
                return 1;
            }
            if (this.section != null && !this.section.equals(casCFile.section)) {
                return this.section.compareTo(casCFile.section);
            }

            return this.filename.compareTo(casCFile.filename);
        }

        @Override
        public String toString() {
            return getFullname();
        }

        @Override
        public int hashCode() {
            return Objects.hash(section, filename);
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
            return Objects.equals(this.section, that.section) && Objects.equals(this.filename, that.filename);
        }
    }

    /**
     * DTO containing the information regarding validations for UI
     */
    public static class ValidationSection {

        private final List<String> warnings;
        private final List<String> errors;

        private ValidationSection() {
            this(null, null);
        }

        private ValidationSection(List<String> warnings, List<String> errors) {
            List<String> warnings_ = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
            Collections.sort(warnings_);
            List<String> errors_ = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            Collections.sort(errors_);

            this.warnings = Collections.unmodifiableList(warnings_);
            this.errors = Collections.unmodifiableList(errors_);
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

        @NonNull
        public List<String> getWarnings() {
            return warnings;
        }

        @NonNull
        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * DTO containing the information regarding validations for UI
     */
    public static class CandidateSection {

        private final ValidationSection validations;
        private final String version;

        private CandidateSection() {
            this(null);
        }

        private CandidateSection(BundleUpdateLog.CandidateBundle candidate) {
            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            String version = null;
            if (candidate != null) {
                warnings = candidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.WARNING).map(v -> v.getMessage()).collect(Collectors.toList());
                errors = candidate.getValidations().getValidations().stream().map(serialized -> Validation.deserialize(serialized)).filter(v -> v.getLevel() == Validation.Level.ERROR).map(v -> v.getMessage()).collect(Collectors.toList());
                version = candidate.getVersion();
            }
            this.validations = new ValidationSection(warnings, errors);
            this.version = version;
        }

        @NonNull
        public ValidationSection getValidations() {
            return validations;
        }

        @CheckForNull
        public String getVersion() {
            return version;
        }

        public boolean isCandidate() {
            return StringUtils.isNotBlank(version) || !validations.isEmpty();
        }
    }

    public static class UpdateLogRow {

        private final String version;
        private final Date date;
        private final long errors;
        private final long warnings;
        private final String folder;

        private UpdateLogRow(BundleUpdateLog.CandidateBundle candidate) {
            this.folder = candidate == null ? null : candidate.getFolder();
            this.version = candidate == null ? null : candidate.getVersion();
            this.errors = candidate == null ? 0L : candidate.getValidations().getValidations().stream().filter(s -> s.getLevel() == Validation.Level.ERROR).count();
            this.warnings = candidate == null ? 0L : candidate.getValidations().getValidations().stream().filter(s -> s.getLevel() == Validation.Level.WARNING).count();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
            Date d = null;
            if (candidate != null) {
                try {
                    d = formatter.parse(candidate.getFolder().substring(0, candidate.getFolder().indexOf("_")));
                } catch (ParseException e) {
                    d = null;
                }
            }
            this.date = d;
        }

        public boolean isEmpty() {
            return StringUtils.isBlank(this.folder);
        }

        public String getVersion() {
            return version;
        }

        public Date getDate() {
            return date;
        }

        public long getErrors() {
            return errors;
        }

        public long getWarnings() {
            return warnings;
        }

        public String getFolder() {
            return folder;
        }
    }
}
