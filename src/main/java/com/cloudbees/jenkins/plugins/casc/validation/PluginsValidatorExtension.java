package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PluginsToInstallValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import hudson.Extension;
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
 * Performs validations on plugins to install. This validation will generate only warnings, never errors
 */
@Extension
@SuppressRestrictedWarnings(value = { BundleLoader.class})
public class PluginsValidatorExtension extends AbstractValidator{

    private static final Logger LOGGER = Logger.getLogger(PluginsValidatorExtension.class.getName());

    @Override
    public ValidationCode getCode() {
        return ValidationCode.PLUGIN_AVAILABLE;
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

        List<String> plugins = descriptor.getPlugins();
        if (plugins == null || plugins.isEmpty()) {
            return Collections.emptyList();
        }

        List<Validation> errors = checkFiles(plugins, bundlePath, "plugins");

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        PluginsToInstallValidator validator = new PluginsToInstallValidator();
        Collection<Validation> validations = validator.validate(new PathPlainBundle(bundlePath));
        if (!validations.isEmpty()) {
            LOGGER.log(Level.WARNING, String.format("Some plugins can not be installed: %s", validations.stream().map(x -> x.getMessage()).collect(Collectors.joining(","))));
        }
        return validations.stream().collect(Collectors.toList());
    }


}
