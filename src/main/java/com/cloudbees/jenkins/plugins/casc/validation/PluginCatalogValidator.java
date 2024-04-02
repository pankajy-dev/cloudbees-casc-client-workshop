package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.AbstractValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hudson.security.ACL;
import hudson.security.ACLContext;

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

        List<Validation> errors = validate(catalog).stream().map( msg -> error(msg)).collect(Collectors.toList());

        return errors;
    }

    private List<String> validate(String catalog) {

        String json = yaml2json(catalog);
        if (json == null) {
            return Arrays.asList("The catalog file content is not valid");
        }

        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            boolean oldAllow = BeekeeperRemote.get().isCapExceptionsAllowed();
            BeekeeperRemote.get().setCapExceptionsAllowed(true);

            List<String> validationErrors = BeekeeperRemote.get().validateExtension(json, null);

            BeekeeperRemote.get().setCapExceptionsAllowed(oldAllow);

            return validationErrors;
        }
    }

    private String yaml2json(String yaml) {
        Map<String,Object> object = parseYaml(yaml);

        if (object == null) {
            return null;
        }

        JSONObject json = JSONObject.fromObject(object);

        return json != null ? json.toString() : null;
    }

}
