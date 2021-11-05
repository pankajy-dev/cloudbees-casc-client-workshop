package com.cloudbees.opscenter.client.casc;

import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports a Core CasC bundle.
 *
 * It will ignore if there is a bundle already installed, a generated bundle based on the current configuration is
 * always built and returned.
 */
@Restricted(NoExternalUse.class)
@Extension
public class BundleExport implements RootAction {

    private static final Logger LOG = Logger.getLogger(BundleExport.class.getName());

    @Override
    public String getUrlName() {
        return "core-casc-export";
    }

    public HttpResponse doDynamic(StaplerRequest request) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String yamlFile = request.getRestOfPath();
        if (yamlFile.startsWith("/")) {
            yamlFile = yamlFile.substring(1);
        }
        if (StringUtils.isBlank(yamlFile)) {
            return doIndex();
        } else if ("full-export".equals(yamlFile)) {
            return doZipExport();
        } else {
            String export = getExport(yamlFile);
            if (export != null) {
                return HttpResponses.text(export);
            }
        }

        return HttpResponses.notFound();
    }

    @CheckForNull
    private String getExport(String yamlFile) {
        BundleExporter exporter = BundleExporter.forYamlFile(yamlFile);
        if (exporter != null) {
            return exporter.getExport();
        }
        return null;
    }

    /**
     * @return the whole bundle as plain text (all yaml files concatenated).
     */
    private HttpResponse doIndex() {
        return getBundleResponse();
    }

    /**
     * Generate zip file containing all the exported yaml files
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. reader.getPluginCatalog(), reader.getGlobalRbac() and reader.getItems() already checked")
    private HttpResponse doZipExport() {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                final String exportFileName = String.format("core-casc-export-%s.zip", getInstanceName());

                rsp.setContentType("application/zip");
                rsp.addHeader("Content-Disposition", String.format("inline; filename=%s;", exportFileName));

                OutputStream out = rsp.getOutputStream();
                try (ZipOutputStream zipFile = new ZipOutputStream(out)) {
                    for (BundleExporter exporter : ExtensionList.lookup(BundleExporter.class)) {
                        String export = exporter.getExport();
                        if (export != null) {
                            zipFile.putNextEntry(new ZipEntry(exporter.getYamlFile()));
                            zipFile.write(export.getBytes(StandardCharsets.UTF_8));

                        }
                    }
                }
            }
        };
    }

    @VisibleForTesting
    public BundleResponse getBundleResponse() {
        return new BundleResponse(
                getExport("bundle.yaml"),
                getExport("jenkins.yaml"),
                getExport("plugins.yaml"),
                getExport("plugin-catalog.yaml"),
                getExport("items.yaml"),
                getExport("rbac.yaml"));
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
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
