package com.cloudbees.jenkins.plugins.casc.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PluginsToInstallValidator;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;

/**
 * Performs validations on plugins to install. This validation will generate only warnings, never errors
 */
@OptionalExtension(requirePlugins = "configuration-as-code")
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

        List<Validation> errors = new ArrayList<>();
        List<String> filesNotFound = new ArrayList<>();
        List<String> filesUnparseable = new ArrayList<>();
        for (String file : plugins) {
            Path path = bundlePath.resolve(file);
            if (!Files.exists(path)) {
                filesNotFound.add(file);
            } else {
                try {
                    Map<String, Object> parsed = parseYaml(path);
                    if (parsed == null || parsed.isEmpty()) {
                        filesUnparseable.add(file);
                    }
                } catch (IOException e) {
                    filesUnparseable.add(file);
                }
            }
        }
        if (!filesNotFound.isEmpty()) {
            String notFound = filesNotFound.stream().collect(Collectors.joining(", "));
            errors.add(warning(String.format("The bundle.yaml file references %s in the Plugins section that cannot be found. Impossible to validate plugins.", notFound)));
        }
        if (!filesUnparseable.isEmpty()) {
            String unparseable = filesUnparseable.stream().collect(Collectors.joining(", "));
            errors.add(warning(String.format("The bundle.yaml file references %s in the Plugins section that is empty or has an invalid yaml format. Impossible to validate plugins.",
                                           unparseable)));
        }

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
