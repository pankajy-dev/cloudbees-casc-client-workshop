package com.cloudbees.opscenter.client.casc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exporter of the jenkins.yaml file as text.
 */
@OptionalExtension(requirePlugins = {"configuration-as-code"}, ordinal = 5)
public final class JCasCExporter extends BundleExporter {

    private static final Logger LOG = Logger.getLogger(BundleExporter.class.getName());

    @NonNull
    @Override
    public String getYamlFile() {
        return "jenkins.yaml";
    }

    /**
     * Calls {@link ConfigurationAsCode#export(OutputStream)} to generate the JCasC config file content.
     */
    @SuppressRestrictedWarnings(value = {ConfigurationAsCode.class})
    @Override
    @CheckForNull
    public String getExport() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ConfigurationAsCode.get().export(out);
            out.flush();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Cannot export JCasC file", e);
            return "Cannot export JCasC file. See Jenkins logs for more details.";
        }
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Jenkins configuration as defined by OSS CasC";
    }

    @Override
    public String getSection() {
        return "jcasc";
    }
}
