package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Action to manage the Update log.
 *
 * If the update log is disabled, then it will return a 404 error.
 */
@Restricted(NoExternalUse.class)
@Extension
public class UpdateLogAction implements RootAction {

    private static final Logger LOG = Logger.getLogger(UpdateLogAction.class.getName());

    @Override
    public String getUrlName() {
        return "core-casc-update-log";
    }

    /**
     * Index redirects to Update log tab.
     * @return 200 and redirects to Update log tab.
     *         403 - Not authorized. Administer permission required.
     *         404 - Update log disabled
     */
    // stapler
    public HttpResponse doIndex() {
        checkPermissions();
        if (BundleUpdateLog.retentionPolicy() == 0) {
            return HttpResponses.notFound();
        }
        return BundleVisualizationLink.get().doUpdateLog();
    }

    // Visible for testing
    void checkPermissions() {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
    }

    /**
     * Content of the yaml file containing the validation.
     * @return 200 and the content of the yaml file.
     *         403 - Not authorized. Administer permission required or attempt to access a bundle outside the bundle directory.
     *         404 - Controller not configured with CasC or nonexistent file
     *         500 - Error processing the request
     */
    @WebMethod(name = "validation")
    public HttpResponse doValidation(StaplerRequest request) {
        checkPermissions();
        if (!ConfigurationBundleManager.isSet() || BundleUpdateLog.retentionPolicy() == 0) {
            return HttpResponses.notFound();
        }

        final String registry = StringUtils.isBlank(request.getRestOfPath()) ? null : request.getRestOfPath().substring(1);
        if (StringUtils.isBlank(registry)) {
            LOG.warning("'folder' parameter in request is null.");
            return HttpResponses.error(500, "Bundle version missing");
        }

        final Path updateLogFolder = getLogsFolder().normalize();
        if (!Files.exists(updateLogFolder)) {
            LOG.warning("Attempted to download a log entry, but the update log directory " + updateLogFolder + " cannot be found.");
            return HttpResponses.notFound();
        }

        final Path logPath = updateLogFolder.resolve(registry).normalize();
        if (!logPath.startsWith(updateLogFolder)) {
            LOG.warning("Attempted to access files outside the update log directory.");
            return HttpResponses.forbidden();
        }

        if (!logPath.toFile().exists()) {
            LOG.warning("Attempted to download a non-existent registry: " + logPath);
            return HttpResponses.notFound();
        }

        final Path filePath = logPath.resolve(BundleUpdateLog.VALIDATIONS_FILE);
        if (!filePath.toFile().exists()) {
            LOG.warning("Attempted to download a non-existent registry: " + filePath);
            return HttpResponses.notFound();
        }

        String content;
        try {
            content = FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error reading the registry " + registry, e);
            return HttpResponses.error(e);
        }

        LOG.fine("Downloading validation file from " + registry);
        return HttpResponses.text(content);
    }

    // Visible for testing
    Path getLogsFolder() {
        return BundleUpdateLog.getHistoricalRecordsFolder();
    }

    /**
     * Content of the registry in zip format
     * @return 200 and the bundle version in zip format.
     *         403 - Not authorized. Administer permission required or attempt to access a bundle outside the bundle directory.
     *         404 - Update log is disabled or the log does not exist
     *         500 - Error processing the request
     */
    @WebMethod(name = "download")
    public HttpResponse download(StaplerRequest request) {
        checkPermissions();
        if (!ConfigurationBundleManager.isSet() || BundleUpdateLog.retentionPolicy() == 0) {
            return HttpResponses.notFound();
        }

        final String registry = StringUtils.isBlank(request.getRestOfPath()) ? null : request.getRestOfPath().substring(1);
        if (StringUtils.isBlank(registry)) {
            LOG.warning("'folder' parameter in request is null.");
            return HttpResponses.error(500, "Bundle version missing");
        }

        final Path updateLogFolder = getLogsFolder().normalize();
        if (!Files.exists(updateLogFolder)) {
            LOG.warning("Attempted to download a log entry, but the update log directory " + updateLogFolder + " cannot be found.");
            return HttpResponses.notFound();
        }

        final Path logPath = updateLogFolder.resolve(registry).normalize();
        if (!logPath.startsWith(updateLogFolder)) {
            LOG.warning("Attempted to access files outside the update log directory.");
            return HttpResponses.forbidden();
        }

        if (!logPath.toFile().exists()) {
            LOG.warning("Attempted to download a non-existent registry.");
            return HttpResponses.notFound();
        }

        HttpResponse response = new ZipBundleResponse(updateLogFolder, registry);
        LOG.fine("Downloading registry: " + registry);
        return response;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    // Visible for testing
    static final class ZipBundleResponse implements HttpResponse {

        final private Path updateLogFolder;
        final private String registry;

        ZipBundleResponse(@NonNull Path updateLogFolder, @NonNull String registry) {
            if (!Files.exists(updateLogFolder.resolve(registry)) || !Files.isDirectory(updateLogFolder.resolve(registry))) {
                throw new IllegalArgumentException(updateLogFolder.resolve(registry) + " does not exist or is not a valid directory");
            }

            this.updateLogFolder = updateLogFolder;
            this.registry = registry;
        }

        @Override
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            rsp.setContentType("application/zip");
            rsp.addHeader("Content-Disposition", String.format("inline; filename=%s.zip;", this.registry));

            OutputStream out = rsp.getOutputStream();
            try (ZipOutputStream zipFile = new ZipOutputStream(out)) {
                addZipEntries(updateLogFolder.resolve(registry).toFile(), zipFile);
            }
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. bundleDirectory already checked, so neither bundleDirectory nor list cannot be null")
        private void addZipEntries(File originFolder, ZipOutputStream zipFile) throws IOException {
            if (originFolder.isDirectory() && originFolder.list() != null) {
                for (String file : originFolder.list()) {
                    File current = new File(originFolder, file);
                    String zipEntryName = updateLogFolder.resolve(registry).relativize(current.toPath()).toString();
                    if (current.isDirectory()) {
                        zipEntryName = zipEntryName + "/";
                    }
                    ZipEntry entry = new ZipEntry(zipEntryName);
                    zipFile.putNextEntry(entry);
                    if (current.isDirectory()) {
                        zipFile.closeEntry();
                        addZipEntries(current, zipFile);
                    } else {
                        zipFile.write(FileUtils.readFileToByteArray(current));
                    }
                }
            }
        }
    }
}
