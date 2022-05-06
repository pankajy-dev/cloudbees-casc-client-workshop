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
import com.cloudbees.jenkins.plugins.casc.items.validation.ItemsValidator;
import com.cloudbees.jenkins.plugins.casc.rbac.validation.RbacValidator;

/**
 * Performs validations on RBAC in a running instance.
 * <ul>
 * <li>The authorization strategy must be {@link nectar.plugins.rbac.strategy.RoleMatrixAuthorizationStrategy}. This will generate an error since it is a requirement to configure RBAC</li>
 * <li>All permissions defined for a role must exist in the instance. This will generate an error since when the role is created we get an error if the permissions do not exist.</li>
 * </ul>
 *
 * The authorization strategy will be checked following this process:
 * <ol>
 * <li>The jcasc section is defined in bundle.yaml and it sets the authorization strategy.</li>
 * <li>The jcasc section is defined in bundle.yaml and it does not set the authorization strategy. Then the instance have the {@link nectar.plugins.rbac.strategy.RoleMatrixAuthorizationStrategy} configured.</li>
 * <li>The jcasc section is not defined in bundle.yaml but the instance have the {@link nectar.plugins.rbac.strategy.RoleMatrixAuthorizationStrategy} configured.</li>
 * </ol>
 */
@OptionalExtension(requirePlugins = "configuration-as-code")
@SuppressRestrictedWarnings(value = { BundleLoader.class})
public class RbacValidatorExtension extends AbstractValidator{

    private static final Logger LOGGER = Logger.getLogger(RbacValidatorExtension.class.getName());

    @Override
    public ValidationCode getCode() {
        return ValidationCode.RBAC_CONFIGURATION;
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

        List<String> rbac = descriptor.getRbac();
        if (rbac == null || rbac.isEmpty()) {
            return Collections.emptyList();
        }

        List<Validation> errors = new ArrayList<>();
        List<String> filesNotFound = new ArrayList<>();
        List<String> filesUnparseable = new ArrayList<>();
        for (String file : rbac) {
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
            errors.add(error(String.format("The bundle.yaml file references %s in the RBAC section that cannot be found. Impossible to validate RBAC.", notFound)));
        }
        if (!filesUnparseable.isEmpty()) {
            String unparseable = filesUnparseable.stream().collect(Collectors.joining(", "));
            errors.add(error(String.format("The bundle.yaml file references %s in the RBAC section that is empty or has an invalid yaml format. Impossible to validate RBAC.",
                                           unparseable)));
        }

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        RbacValidator validator = new RbacValidator();
        Collection<Validation> validations = validator.validate(new PathPlainBundle(bundlePath));
        if (!validations.isEmpty()) {
            LOGGER.log(Level.WARNING, String.format("Problems when processing RBAC detected: %s", validations.stream().map(x -> x.getMessage()).collect(Collectors.joining(","))));
        }
        return validations.stream().collect(Collectors.toList());
    }
}
