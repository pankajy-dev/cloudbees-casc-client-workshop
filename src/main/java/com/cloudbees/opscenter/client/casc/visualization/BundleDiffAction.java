package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import java.util.Arrays;
import java.util.List;

/**
 * Action to display the differences between the current bundle and the new available version
 */
@Restricted(NoExternalUse.class)
@Extension
@SuppressRestrictedWarnings(value = { BundleLoader.class})
public class BundleDiffAction implements RootAction {

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "bundle-diff-visualization";
    }

    private void checkPermissions() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
    }

    /**
     * Visualize the differences in the bundle descriptor between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doIndex() {
        return doDescriptor();
    }

    /**
     * Visualize the differences in the bundle descriptor between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doDescriptor() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "descriptor.jelly");
    }

    /**
     * Visualize the differences in the jcasc section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doJcasc() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "jcasc.jelly");
    }

    /**
     * Visualize the differences in the RBAC section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doRbac() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "rbac.jelly");
    }

    /**
     * Visualize the differences in the items section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doItems() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "items.jelly");
    }

    /**
     * Visualize the differences in the plugin catalog section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doCatalog() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "catalog.jelly");
    }

    /**
     * Visualize the differences in the jcasc section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doPlugins() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "plugins.jelly");
    }

    /**
     * Visualize the differences in the jcasc section between current bundle and new version bundle available.
     * @return 200 - Show the differences between bundle versions.
     *         403 - Not authorized. Manage permission required.
     *         404 - No differences available
     */
    public HttpResponse doVariables() {
        checkPermissions();
        if (ConfigurationStatus.get().getChangesInNewVersion() == null) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "variables.jelly");
    }

    /**
     * Get the version of the current bundle
     * @return The version of the current bundle.
     */
    @CheckForNull
    public String getCurrentVersion() {
        BundleComparator.Result changes = ConfigurationStatus.get().getChangesInNewVersion();
        if (changes == null) {
            // Should not happen since the method doIndex should have checked
            return BundleVisualizationLink.get().getBundleVersion();
        }

        BundleLoader.BundleDescriptor bundleDescriptor = changes.getOrigin().getBundleDescriptor();
        if (bundleDescriptor == null) {
            return null;
        }
        return bundleDescriptor.getVersion();
    }

    /**
     * Get the version of the new available bundle.
     * @return The version of the new available bundle.
     */
    @CheckForNull
    public String getNewVersion() {
        BundleComparator.Result changes = ConfigurationStatus.get().getChangesInNewVersion();
        if (changes == null) {
            // Should not happen since the method doIndex should have checked
            return BundleVisualizationLink.get().getUpdateVersion();
        }

        BundleLoader.BundleDescriptor bundleDescriptor = changes.getOther().getBundleDescriptor();
        if (bundleDescriptor == null) {
            return null;
        }
        return bundleDescriptor.getVersion();
    }

    /**
     * Get the differences between the current and new versions of the bundle.
     *
     * @return The differences between the current and new versions of the bundle. See {@link BundleComparator.Result}.
     */
    @CheckForNull
    public BundleComparator.Result getBundleDiff() {
        return ConfigurationStatus.get().getChangesInNewVersion();
    }

    /**
     * Get the differences by line of the bundle descriptor between the two compared bundles.
     * @return List with the differences by line of the bundle descriptor between the two compared bundles.
     */
    @CheckForNull
    public List<DiffRow> getBundleDescriptorDifferences() {
        BundleComparator.Result changes = ConfigurationStatus.get().getChangesInNewVersion();
        if (changes == null) {
            // Should not happen since the method doIndex should have checked
            return null;
        }

        return getDifferences(changes.getOrigin().getDescriptor(), changes.getOther().getDescriptor());
    }

    /**
     * Get the differences by line of a file between the two compared bundles.
     * @param file to read the differences
     * @return List with the differences by line of a file between the two compared bundles.
     */
    @CheckForNull
    public List<DiffRow> getFileDifferences(String file) {
        BundleComparator.Result changes = ConfigurationStatus.get().getChangesInNewVersion();
        if (changes == null) {
            // Should not happen since the method doIndex should have checked
            return null;
        }

        return getDifferences(changes.getOrigin().getFile(file), changes.getOther().getFile(file));
    }

    private List<DiffRow> getDifferences(String file1, String file2) {
        List<String> currentVersion = Arrays.asList(StringUtils.defaultString(file1).split(System.lineSeparator()));
        List<String> newVersion = Arrays.asList(StringUtils.defaultString(file2).split(System.lineSeparator()));

        DiffRowGenerator generator = DiffRowGenerator.create().showInlineDiffs(true).inlineDiffByWord(true).oldTag(f -> "~~").newTag(f -> "@@").build();
        return generator.generateDiffRows(currentVersion, newVersion);
    }

    /**
     * Return if the line is mark as old version with changes
     * @param line to check
     * @return true if the line is mark as old version with changes, false otherwise
     */
    public boolean oldWithChanges(String line) {
        return StringUtils.defaultString(line).contains("~~");
    }

    /**
     * Return if the line is mark as new version with changes
     * @param line to check
     * @return true if the line is mark as new version with changes, false otherwise
     */
    public boolean newWithChanges(String line) {
        return StringUtils.defaultString(line).contains("@@");
    }

    /**
     * Remove the old and new changes tags from a line
     * @param line to escape
     * @return The line without the new and old tags. If the line is null, then returns null
     */
    @CheckForNull
    public String escapeLine(String line) {
        if (line == null) {
            return null;
        }

        return line.replaceAll("~~", "").replaceAll("@@", "");
    }
}
