package com.cloudbees.jenkins.plugins.casc.comparator;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class to compare two bundles
 */
// TODO See if it should go into another repository to be available in other plugins.
public class BundleComparator {

    /**
     * Compare two bundles
     * @param origin Path to the reference bundle
     * @param other Path to the bundle to compare to origin
     * @return {@link Result} object with the result of the comparison
     * @throws IOException if there is an error reading the bundles
     * @throws IllegalArgumentException if any of the paths does not exist
     */
    public static Result compare(@NonNull Path origin, @NonNull Path other) throws IOException, IllegalArgumentException {
        if (!Files.exists(origin)) {
            throw new IllegalArgumentException(origin + " does not exist");
        }
        if (!Files.exists(origin)) {
            throw new IllegalArgumentException(origin + " does not exist");
        }

        final PathPlainBundle originBundle = new PathPlainBundle(origin);
        final PathPlainBundle otherBundle = new PathPlainBundle(other);

        return new Result(originBundle, otherBundle);
    }

    /**
     * Class to store of the result of the comparison:
     * - Compared bundles
     * - boolean saying if the bundles are the same
     * - differences found in every section
     */
    public static class Result {

        private final PathPlainBundle origin;
        private final PathPlainBundle other;
        private final boolean sameBundles;
        private final SectionDiff jcasc;
        private final SectionDiff items;
        private final SectionDiff rbac;
        private final SectionDiff catalog;
        private final SectionDiff plugins;
        private final SectionDiff variables;

        private Result(@NonNull PathPlainBundle origin, @NonNull PathPlainBundle other) {
            this.origin = origin;
            this.other = other;
            this.jcasc = new SectionDiff("jcasc", origin, other);
            this.items = new SectionDiff("items", origin, other);
            this.rbac = new SectionDiff("rbac", origin, other);
            this.catalog = new SectionDiff("catalog", origin, other);
            this.plugins = new SectionDiff("plugins", origin, other);
            this.variables = new SectionDiff("variables", origin, other);
            this.sameBundles = checkSameBundles();
        }

        /**
         * Return the reference bundle
         */
        @NonNull
        public PathPlainBundle getOrigin() {
            return origin;
        }

        /**
         * Return the bundle compared to the reference
         */
        @NonNull
        public PathPlainBundle getOther() {
            return other;
        }

        /**
         * Flag to say if the bundles are the same
         * @return true if the bundles are the same, false if they are different
         */
        public boolean sameBundles() {
            return sameBundles;
        }

        /**
         * Return the differences in the JCasC section
         */
        @NonNull
        public SectionDiff getJcasc() {
            return jcasc;
        }

        /**
         * Return the differences in the items section
         */
        @NonNull
        public SectionDiff getItems() {
            return items;
        }

        /**
         * Return the differences in the RBAC section
         */
        @NonNull
        public SectionDiff getRbac() {
            return rbac;
        }

        /**
         * Return the differences in the plugin catalog section
         */
        @NonNull
        public SectionDiff getCatalog() {
            return catalog;
        }

        /**
         * Return the differences in the plugins section
         */
        @NonNull
        public SectionDiff getPlugins() {
            return plugins;
        }

        /**
         * Return the differences in the variables section
         */
        @NonNull
        public SectionDiff getVariables() {
            return variables;
        }

        private boolean checkSameBundles() {
            if (Objects.equals(origin.getBundlePath(), other.getBundlePath())) {
                return true;
            }

            if (!Objects.equals(origin.getDescriptor(), other.getDescriptor())) {
                return false;
            }

            if (this.jcasc.withChanges() || this.items.withChanges() || this.rbac.withChanges() || this.catalog.withChanges() || this.plugins.withChanges() || this.variables.withChanges()) {
                return false;
            }

            return true;
        }
    }

    /**
     * Class to store the differences in a section of the bundle:
     * - boolean saying if there are differences
     * - New files compared to reference bundle (Not found in origin)
     * - Deleted files compared to reference bundle (Not found in the other bundle)
     * - Changed files (files with the same name but different content)
     */
    @SuppressRestrictedWarnings(value = { BundleLoader.class})
    public static class SectionDiff {

        private final List<String> newFiles;
        private final List<String> deletedFiles;
        private final List<String> updatedFiles;

        private SectionDiff(@NonNull String section, @NonNull PathPlainBundle bundle1, @NonNull PathPlainBundle bundle2) {
            final List<String> files1 = readSection(section, bundle1.getBundleDescriptor());
            final List<String> files2 = readSection(section, bundle2.getBundleDescriptor());

            this.newFiles = new ArrayList<>(files2.stream().filter(s -> !files1.contains(s)).collect(Collectors.toList()));
            this.deletedFiles = new ArrayList<>(files1.stream().filter(s -> !files2.contains(s)).collect(Collectors.toList()));
            this.updatedFiles = new ArrayList<>();
            files1.stream().filter(s -> files2.contains(s)).forEach(s -> {
                if (!Objects.equals(bundle1.getFile(s), bundle2.getFile(s))) {
                    this.updatedFiles.add(s);
                }
            });
        }

        private List<String> readSection(String section, BundleLoader.BundleDescriptor bundle) {
            if (bundle == null) {
                return Collections.emptyList();
            } else if ("jcasc".equals(section)) {
                return bundle.getJcasc();
            } else if ("items".equals(section)) {
                return bundle.getItems();
            } else if ("rbac".equals(section)) {
                return bundle.getRbac();
            } else if ("catalog".equals(section)) {
                return bundle.getCatalog();
            } else if ("plugins".equals(section)) {
                return bundle.getPlugins();
            } else if ("variables".equals(section)) {
                return bundle.getVariables();
            } else {
                return Collections.emptyList();
            }
        }

        /**
         * Flag to say if there are differences in this bundle section
         */
        public boolean withChanges() {
            return !newFiles.isEmpty() || !deletedFiles.isEmpty() || !updatedFiles.isEmpty();
        }

        /**
         * Files found in the second bundle but not in the reference bundle
         */
        public List<String> getNewFiles() {
            return Collections.unmodifiableList(newFiles);
        }

        /**
         * Files found in the reference bundle but not in the other bundle
         */
        public List<String> getDeletedFiles() {
            return Collections.unmodifiableList(deletedFiles);
        }

        /**
         * Files with the same name but different content
         */
        public List<String> getUpdatedFiles() {
            return Collections.unmodifiableList(updatedFiles);
        }
    }
}
