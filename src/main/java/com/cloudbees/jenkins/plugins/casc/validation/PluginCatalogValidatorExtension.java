package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Performs validations on items to create / update in a running instance
 */
@OptionalExtension(requirePlugins = "cloudbees-assurance")
@SuppressRestrictedWarnings(value = { BundleLoader.class})
public class PluginCatalogValidatorExtension extends AbstractValidator{

    private static final Logger LOGGER = Logger.getLogger(PluginCatalogValidatorExtension.class.getName());

    @Override
    public ValidationCode getCode() {
        return ValidationCode.PLUGIN_CATALOG;
    }

    @Override
    public List<Validation> validate(Path bundlePath) {
        if (bundlePath == null) {
            return Collections.emptyList();
        }

        BundleLoader.BundleDescriptor descriptor = null;
        try {
            descriptor = readDescriptor(bundlePath.resolve("bundle.yaml"));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error parsing the bundle descriptor", e);
            return Collections.emptyList(); // No need to return errors,
        }

        if (descriptor == null) {
            LOGGER.warning("Empty bundle descriptor");
            return Collections.singletonList(error("The bundle.yaml file seems to be empty. Validations cannot be performed."));
        }

        List<String> catalog = descriptor.getCatalog();
        if (catalog == null || catalog.isEmpty()) {
            return Collections.emptyList();
        }

        List<Validation> errors = checkFiles(catalog, bundlePath, "plugin catalog");

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        PluginCatalogValidator validator = new PluginCatalogValidator();
        Collection<Validation> validations = validator.validate(new PathPlainBundle(bundlePath));
        if (validations.isEmpty()) {
            validations.add(Validation.info(ValidationCode.PLUGIN_CATALOG, "[PluginCatalogValidator] All plugins in catalog were added to the envelope"));
        }
        logValidation(LOGGER, "Some plugins in the catalog were not added to the envelope: %s", validations);
        return validations.stream().collect(Collectors.toList());
    }
}
