package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reload JCasC configuration.
 */
@OptionalExtension(requirePlugins = {"configuration-as-code"}, ordinal = 3)
public final class JCasCReload extends BundleReload {

    private static final Logger LOGGER = Logger.getLogger(JCasCReload.class.getName());

    @Override
    public void doReload(ConfigurationBundle bundle) throws CasCException {
        if (bundle.hasJCasCConfig()) {
            setCasCPath(bundle.getJCasCFilePath());
            try {
                ConfigurationAsCode.get().configure();
            } catch (ConfiguratorException e) {
                LOGGER.log(Level.WARNING, "Configuration as Code file cannot be applied: {0}", e.getMessage());
                LOGGER.log(Level.FINE, "Configuration as code file cannot be applied", e);
            }
        }

    }

    @Override
    public boolean isReloadable() {
        BundleComparator.Result comparisonResult = ConfigurationStatus.get().getChangesInNewVersion();
        return comparisonResult != null && comparisonResult.getJcasc().withChanges();
    }

    private void setCasCPath(Path jCasCFilePath) {
        if (jCasCFilePath != null) {
            LOGGER.log(Level.INFO, "Using JCasC config: " + jCasCFilePath);
            if (Files.isDirectory(jCasCFilePath)) {
                // CasC requires a path if the target is a folder
                System.setProperty("casc.jenkins.config", jCasCFilePath.toFile().getAbsolutePath());
            } else {
                // CasC requires a URI prior to configuration-as-code-1.20 and would fail on a path
                System.setProperty("casc.jenkins.config", jCasCFilePath.toUri().toString());
            }
        }
    }
}
