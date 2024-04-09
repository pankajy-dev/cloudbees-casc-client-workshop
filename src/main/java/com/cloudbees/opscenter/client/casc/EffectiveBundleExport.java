package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

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
 * Exports the installed Core CasC bundle.
 *
 * If there is not any installed bundle, then it will return a 404 error.
 */
@Restricted(NoExternalUse.class)
@Extension
public class EffectiveBundleExport implements RootAction {

    private static final Logger LOG = Logger.getLogger(EffectiveBundleExport.class.getName());

    @Override
    public String getUrlName() {
        return "core-casc-effective-bundle-export";
    }

    /**
     * Export of whole current bundle as a zip file.
     * @return 200 and the whole bundle as zip file.
     *         403 - Not authorized. Administer permission required.
     *         404 - Controller not configured with CasC
     */
    // stapler
    public HttpResponse doIndex() {
        checkPermissions();

        if (!isCascConfigured()) {
            LOG.warning("Attempt to download the bundle when the controller is not using CasC Bundle yet");
            return HttpResponses.error(404, "This instance is not using a CasC bundle.");
        }

        try {
            HttpResponse response = getZipBundleResponse();
            LOG.fine("Downloading installed bundle in zip format.");
            return response;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error downloading the zipped bundle.", e);
            return HttpResponses.error(e);
        }
    }

    // Visible for testing
    boolean isCascConfigured() {
        return ConfigurationBundleManager.isSet();
    }

    // Visible for testing
    void checkPermissions() {
        Jenkins.get().checkPermission(CascPermission.CASC_ADMIN);
    }

    // Visible for testing
    Path getBundleFolder() {
        return ConfigurationBundleManager.getBundleFolder();
    }

    private HttpResponse getZipBundleResponse() {
        final Path bundleDirectory = getBundleFolder();
        return new ZipBundleResponse(bundleDirectory.toFile());
    }

    /**
     * Content of the yaml file
     * @return 200 and the content of the yaml file.
     *         403 - Not authorized. Administer permission required or attempt to access a bundle outside the bundle directory.
     *         404 - Controller not configured with CasC or nonexistent file
     *         500 - Bundle directory does not exist
     */

    @WebMethod(name = "downloadFile")
    public HttpResponse doDownloadFile(StaplerRequest request) {
        checkPermissions();
        final String file = StringUtils.isBlank(request.getRestOfPath()) ? null : request.getRestOfPath().substring(1);

        // Shouldn't be needed, but this check here does not hurt
        if (!isCascConfigured()) {
            LOG.warning("Attempted to download a bundle (" + file + ") when the controller is not using a CasC Bundle yet.");
            return HttpResponses.error(404, "This instance is not using a CasC bundle.");
        }
        final Path bundleFolder = getBundleFolder().normalize();
        if (!bundleFolder.toFile().exists()) {
            // Shouldn't happen, but the check does not hurt
            LOG.warning("Attempted to download the bundle, but the bundle directory " + bundleFolder + " cannot be found.");
            return HttpResponses.error(500, "Bundle directory does not exist.");
        }

        if (file == null) {
            LOG.warning("'file' parameter in request is null.");
            return HttpResponses.notFound();
        }

        final Path filePath = bundleFolder.resolve(file).normalize();
        if (!filePath.startsWith(bundleFolder)) {
            LOG.warning("Attempted to access files outside the bundle directory.");
            return HttpResponses.forbidden();
        }

        if (!filePath.toFile().exists()) {
            LOG.warning("Attempted to download a non-existent file.");
            return HttpResponses.notFound();
        }

        String content;
        try {
            content = readContentFile(filePath);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error reading file " + file, e);
            return HttpResponses.error(e);
        }

        LOG.fine("Downloading " + file);
        return HttpResponses.text(content);
    }

    // Visible for testing
    String readContentFile(Path filePath) throws IOException {
        return FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
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

        final private File bundleDirectory;

        ZipBundleResponse(@NonNull File bundleDirectory) {
            if (!bundleDirectory.exists() || !bundleDirectory.isDirectory()) {
                throw new IllegalArgumentException(bundleDirectory + " does not exist or is not a valid directory");
            }

            this.bundleDirectory = bundleDirectory;
        }

        @Override
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            final String exportFileName = String.format("bundle-%s.zip", getInstanceName());

            rsp.setContentType("application/zip");
            rsp.addHeader("Content-Disposition", String.format("inline; filename=%s;", exportFileName));

            OutputStream out = rsp.getOutputStream();
            try (ZipOutputStream zipFile = new ZipOutputStream(out)) {
                addZipEntries(zipFile);
            }
        }

        // Visible for testing
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. bundleDirectory already checked")
        void addZipEntries(@NonNull ZipOutputStream zipFile) throws IOException {
            try {
                if (bundleDirectory.isDirectory()) {
                    final Path bundlePath = bundleDirectory.toPath();
                    Files.walk(bundlePath).filter(Files::isRegularFile).forEach(path -> {
                        try {
                            final String zipEntryName = bundlePath.relativize(path).toFile().getPath();
                            final ZipEntry entry = new ZipEntry(zipEntryName);
                            zipFile.putNextEntry(entry);
                            zipFile.write(FileUtils.readFileToByteArray(path.toFile()));
                            zipFile.closeEntry();
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Error walking the tree directory", e);
                        }
                    });
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error walking the tree directory", e);
            }
        }
    }

    private static String getInstanceName() {
        if (StringUtils.isNotBlank(System.getProperty("MASTER_NAME"))) {
            return System.getProperty("MASTER_NAME").trim();
        } else if (StringUtils.isNotBlank(System.getProperty("HOSTNAME"))) {
            return System.getProperty("HOSTNAME").trim();
        } else {
            return "jenkins";
        }
    }
}
