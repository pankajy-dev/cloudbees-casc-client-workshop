package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.AbstractValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates a plugin catalog. Always returns warnings, never error.
 */
@SuppressRestrictedWarnings(value = {BundleLoader.class})
public class PluginCatalogValidator extends AbstractValidator {
    @Override
    public ValidationCode getCode() {
        return ValidationCode.PLUGIN_CATALOG;
    }

    @Override
    public Collection<Validation> validate(PlainBundle plainBundle) {
        BundleLoader.BundleDescriptor descriptor = plainBundle.getBundleDescriptor();
        if (descriptor == null) {
            return Collections.emptyList();
        }

        List<String> files = descriptor.getCatalog();
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        String path = files.get(0);

        String catalog = plainBundle.getFile(path);
        if (catalog == null) {
            return Collections.emptyList();
        }

        List<Validation> errors = validate(catalog).stream().map( msg -> warning(msg)).collect(Collectors.toList());

        return errors;
    }

    private List<String> validate(String catalog) {
        boolean oldAllow = BeekeeperRemote.get().isCapExceptionsAllowed();
        BeekeeperRemote.get().setCapExceptionsAllowed(true);

        List<String> validationErrors = BeekeeperRemote.get().validateExtension(catalog, null);

        BeekeeperRemote.get().setCapExceptionsAllowed(oldAllow);

        return validationErrors;
    }
}
