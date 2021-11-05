package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.ArrayUtils;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contributes CloudBees CasC related information to the support bundle.
 * It will include all the configuration as code files under the directory set in {@link ConfigurationBundleManager}.
 */
@Extension
public class CloudBeesCasCBundlesSupport extends Component {

    private static final Logger LOGGER = Logger.getLogger(CloudBeesCasCBundlesSupport.class.getName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "CloudBees Configuration as Code bundle";
    }

    @Override
    public void addContents(@NonNull Container container) {
        try {
            Jenkins jenkins = Jenkins.get();
            final File cascBundleDir = new File(jenkins.getRootDir(), ConfigurationBundleManager.CASC_BUNDLE_DIR);
            if (!cascBundleDir.exists()) {
                container.add(new NotAvailable());
            } else if (cascBundleDir.isDirectory()) {
                final File[] listFiles = cascBundleDir.listFiles();
                container.add(new BundleContent(listFiles));
                addEntries(cascBundleDir, cascBundleDir.getName(), container);
            } else {
                container.add(new NotADirectory());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to generate Configuration as Code bundles support content", e);
            container.add(new Error(e));
        }
    }

    private void addEntries(File directory, String currentPath, Container container) {
        String[] listFiles = directory.list();
        if (ArrayUtils.isNotEmpty(listFiles)) {
            for (String file : listFiles) {
                File current = new File(directory, file);
                String entryName = currentPath + "/" + file;
                if (current.isDirectory()) {
                    addEntries(current, entryName, container);
                } else {
                    container.add(new FileContent(entryName, current));
                }
            }
        }
    }

    /** Content listing the yaml files found in the bundle directory. */
    private abstract class Summary extends PrintedContent {
        Summary() {
            super(ConfigurationBundleManager.CASC_BUNDLE_DIR + "/summary.md");
        }

        void printTo(PrintWriter out, @NonNull String summary) {
            out.println("# CloudBees Configuration as Code bundle\n");
            out.println(summary);
        }
    }

    /** Content listing the yaml files found in the bundle directory. */
    private final class BundleContent extends Summary {
        private final File[] files;

        BundleContent(File[] files) {
            super();
            this.files = files;
        }

        @Override
        protected void printTo(PrintWriter out) {
            if (ArrayUtils.isNotEmpty(files)) {
                StringBuffer sb = new StringBuffer("");
                for (File file : files) {
                    if (file.isDirectory()) {
                        sb.append(appendDirectory(file));
                    } else {
                        sb.append("- ").append(file.getName()).append("\n");
                    }
                }
                printTo(out,"Files found in the bundle:\n" + sb.toString());
            } else {
                printTo(out, "Configuration as Code bundle is empty");
            }
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. directory.listFiles already checked")
        private String appendDirectory(File directory) {
            StringBuffer sb = new StringBuffer("");
            if (directory != null && directory.listFiles() != null) {
                for (File file : directory.listFiles()) {
                    if (file.isDirectory()) {
                        sb.append(appendDirectory(file));
                    } else {
                        sb.append("- ").append(directory.getName()).append("/").append(file.getName());
                    }
                }
            }
            return sb.toString();
        }
    }

    /** Content to show when the instance has not been configured using CasC. */
    private final class NotADirectory extends Summary {
        NotADirectory() {
            super();
        }

        @Override
        protected void printTo(PrintWriter out) {
            printTo(out, "Found a file, not a bundle directory");
        }
    }

    /** Content to show when the instance has not been configured using CasC. */
    private final class NotAvailable extends Summary {
        NotAvailable() {
            super();
        }

        @Override
        protected void printTo(PrintWriter out) {
            printTo(out, "Configuration as Code bundle not found");
        }
    }

    /** Content to show when there's an error preparing the content. */
    private final class Error extends Summary {
        private final Exception exception;

        Error(@NonNull Exception e) {
            super();
            this.exception = e;
        }

        @Override
        protected void printTo(PrintWriter out) {
            printTo(out, "Unable to generate content: " + exception.getMessage() + "\n");
            exception.printStackTrace(out);
        }
    }
}
