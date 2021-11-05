package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.CJPPluginManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.license.nectar.utils.ProductDescriptionUtils;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopeProduct;
import com.cloudbees.opscenter.client.casc.BundleExporter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Configuration as Code Bundle Visualization.
 */
@IgnoreJRERequirement // this is local to the controller, no need to get it signature checked.
@Restricted(NoExternalUse.class)
@Extension
public class BundleVisualizationLink extends ManagementLink {

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/cloudbees-casc-api/images/CasC-Export.svg";
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
        ConfigurationUpdaterHelper.checkForUpdates();
        return HttpResponses.forwardToView(this, "_bundleupdate.jelly");
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
    public boolean isUpdateAvailable(){
        return ConfigurationStatus.INSTANCE.isUpdateAvailable();
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
}
