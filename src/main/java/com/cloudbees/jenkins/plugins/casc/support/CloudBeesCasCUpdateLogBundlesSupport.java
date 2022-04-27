package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.ArrayUtils;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contributes CloudBees CasC related information to the support bundle.
 * It will include the bundle log.
 */
@Extension
public class CloudBeesCasCUpdateLogBundlesSupport extends Component {

    private static final Logger LOGGER = Logger.getLogger(CloudBeesCasCUpdateLogBundlesSupport.class.getName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "CloudBees Configuration as Code bundle update log";
    }

    @Override
    public void addContents(@NonNull Container container) {
        try {
            if (BundleUpdateLog.retentionPolicy() != 0) {
                final Path cascUpdateLogDir = BundleUpdateLog.getHistoricalRecordsFolder();
                if (!Files.exists(cascUpdateLogDir)) {
                    container.add(new NotAvailable());
                } else {
                    final File[] listFiles = cascUpdateLogDir.toFile().listFiles((file, s) -> s.endsWith(BundleUpdateLog.BUNDLE_UPDATE_LOG_CSV));
                    if (listFiles.length > 0) {
                        container.add(new UpdateLogContent(listFiles));
                        for (File f : listFiles) {
                            container.add(new FileContent(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/" + f.getName(), cascUpdateLogDir.resolve(f.getName()).toFile()));
                        }
                    } else {
                        container.add(new NotAvailable());
                    }
                }
            } else {
                container.add(new NotAvailable());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to generate Configuration as Code bundles support content", e);
            container.add(new Error(e));
        }
    }

    /** Content listing the yaml files found in the bundle directory. */
    private abstract class Summary extends PrintedContent {
        Summary() {
            super(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/summary.md");
        }

        void printTo(PrintWriter out, @NonNull String summary) {
            out.println("# CloudBees Configuration as Code bundle update log\n");
            out.println(summary);
        }
    }

    /** Content listing the csv files found in the update log directory. */
    private final class UpdateLogContent extends Summary {
        private final File[] files;

        UpdateLogContent(File[] files) {
            super();
            this.files = files;
        }

        @Override
        protected void printTo(PrintWriter out) {
            if (ArrayUtils.isNotEmpty(files)) {
                StringBuffer sb = new StringBuffer("");
                for (File file : files) {
                    sb.append("- ").append(file.getName()).append("\n");
                }
                printTo(out,"Files found in the update log:\n" + sb.toString());
            } else {
                printTo(out, "Update log empty");
            }
        }
    }

    /** Update log is disabled. */
    private final class NotAvailable extends Summary {
        NotAvailable() {
            super();
        }

        @Override
        protected void printTo(PrintWriter out) {
            printTo(out, "Configuration as Code bundle update log disabled");
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
