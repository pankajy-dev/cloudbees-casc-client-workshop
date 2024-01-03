package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.casc.rbac.validation.RbacValidator;
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
 * Performs validations on RBAC in a running instance.
 * <ul>
 * <li>The authorization strategy must be {@link nectar.plugins.rbac.strategy.RoleMatrixAuthorizationStrategy}. This will generate an error since it is a requirement to configure RBAC</li>
 * <li>All permissions defined for a role must exist in the instance. This will generate an error since when the role is created we get an error if the permissions do not exist.</li>
 * </ul>
 */
@OptionalExtension(requirePlugins = "cloudbees-casc-items-api")
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

        List<Validation> errors = checkFiles(rbac, bundlePath, "RBAC");

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        RbacValidator validator = new RbacValidator();
        Collection<Validation> validations = validator.validate(new PathPlainBundle(bundlePath));
        logValidation(LOGGER, "Problems when processing RBAC detected: %s", validations);
        return validations.stream().collect(Collectors.toList());
    }
}
