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
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;

/**
 * Performs validations on items to create / update in a running instance
 */
@OptionalExtension(requirePlugins = "configuration-as-code")
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

        List<Validation> errors = new ArrayList<>();
        List<String> filesNotFound = new ArrayList<>();
        List<String> filesUnparseable = new ArrayList<>();
        for (String file : catalog) {
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
            errors.add(warning(String.format("The bundle.yaml file references %s in the Catalog section that cannot be found. Impossible to validate plugin catalog.", notFound)));
        }
        if (!filesUnparseable.isEmpty()) {
            String unparseable = filesUnparseable.stream().collect(Collectors.joining(", "));
            errors.add(warning(String.format("The bundle.yaml file references %s in the Catalog section that is empty or has an invalid yaml format. Impossible to validate plugin catalog.",
                                       unparseable)));
        }

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        PluginCatalogValidator validator = new PluginCatalogValidator();
        Collection<Validation> validations = validator.validate(new PathPlainBundle(bundlePath));
        if (!validations.isEmpty()) {
            LOGGER.log(Level.WARNING, String.format("Some plugins in the catalog were not added to the envelope: %s",
                                                    validations.stream().map(x -> x.getMessage()).collect(Collectors.joining(","))));
        }
        return validations.stream().collect(Collectors.toList());
    }
}
