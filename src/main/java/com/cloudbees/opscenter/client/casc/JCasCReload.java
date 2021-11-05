package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reload JCasC configuration.
 */
@OptionalExtension(requirePlugins = {"configuration-as-code"}, ordinal = 4)
public final class JCasCReload extends BundleReload {

    private static final Logger LOGGER = Logger.getLogger(JCasCReload.class.getName());

    @Override
    public void doReload(ConfigurationBundle bundle) throws CasCException {
        if (bundle.hasJCasCConfig()) {
            try {
                ConfigurationAsCode.get().configure();
            } catch (ConfiguratorException e) {
                LOGGER.log(Level.WARNING, "Configuration as Code file cannot be applied: {0}", e.getMessage());
                LOGGER.log(Level.FINE, "Configuration as code file cannot be applied", e);
            }
        }

    }
}
