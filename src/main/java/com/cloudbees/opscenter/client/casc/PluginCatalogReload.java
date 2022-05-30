package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.EnvelopeExtension;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reload the plugin catalog
 */
@SuppressRestrictedWarnings({CloudBeesAssurance.class, Beekeeper.class})
@OptionalExtension(requirePlugins = {"cloudbees-assurance"}, ordinal = 5)
public final class PluginCatalogReload extends BundleReload {

    private static final Logger LOGGER = Logger.getLogger(PluginCatalogReload.class.getName());

    @Override
    public void doReload(ConfigurationBundle bundle) throws CasCException {
        EnvelopeExtension extension = bundle.getEnvelopeExtension();
        if (extension != null) {

            Envelope envelope = CloudBeesAssurance.get().getBeekeeper().getEnvelope();

            List<String> errors;

            try {
                errors = BeekeeperRemote.get().validateExtension(extension.getMetadata(), envelope.toJSON().toString());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "The Plugin Catalog cannot be validated because of " + e.getMessage());
                throw new CasCException("The Plugin Catalog cannot be validated", e);
            }

            if (!errors.isEmpty()) {
                LOGGER.log(Level.WARNING, "The Plugin Catalog cannot be validated because of " + errors);
                throw new CasCException("The Plugin Catalog cannot be validated because of " + errors);
            }
        }
        installCatalog(bundle.getEnvelopeExtension());
    }

    @Override
    public boolean isReloadable() {
        BundleComparator.Result comparisonResult = ConfigurationStatus.INSTANCE.getChangesInNewVersion();
        return comparisonResult != null && comparisonResult.getCatalog().withChanges();
    }

    /**
     * Installs a plugin catalog waiting until the update site has been refreshed
     * @param pluginCatalog catalog to be installed
     * @throws CasCException if the catalog is invalid
     * @return true if the catalog has been installed properly and the update site refreshed
     */
    private boolean installCatalog(EnvelopeExtension pluginCatalog) throws CasCException {
        if (pluginCatalog == null) {
            LOGGER.log(Level.INFO, "No catalog to be installed. Checking if removal is required.");

            String previous = BeekeeperRemote.get().removeExtension();
            if (previous != null) {
                LOGGER.log(Level.INFO, "Existing Plugin Catalog removed: " + previous);
            } else {
                LOGGER.log(Level.FINE, "No previous Plugin Catalog has to be removed.");
            }

        } else {
            List<String> installationErrors;

            try {
                installationErrors = CloudBeesAssurance.get().installExtension(pluginCatalog, true);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Plugin Catalog cannot be installed because of " + e.getMessage());
                throw new CasCException("Plugin Catalog cannot be installed. ", e);
            }

            if (!installationErrors.isEmpty()) {
                LOGGER.log(Level.WARNING, "Plugin Catalog can not be installed: {0}", installationErrors.toString());
                return false;
            }
        }
        return true;
    }
}
