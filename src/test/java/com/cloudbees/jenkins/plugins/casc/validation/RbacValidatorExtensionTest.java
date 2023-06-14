package com.cloudbees.jenkins.plugins.casc.validation;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.ExtensionList;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class RbacValidatorExtensionTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void smokes() {
        RbacValidatorExtension validator = ExtensionList.lookupSingleton(RbacValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/missing-file-bundle"));
        Validation v = validations.get(0);
        assertThat("missing-file-bundle: should be an error in RBAC", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("missing-file-bundle: should be an error in RBAC", v.getValidationCode(), is(ValidationCode.RBAC_CONFIGURATION));
        assertThat("missing-file-bundle: should be an error in RBAC", v.getMessage(), is("[RBAC] - The bundle.yaml file references rbac.yaml in the RBAC section that "
                                                                                          + "cannot be found. Impossible to validate RBAC."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/unparsed-file-bundle"));
        v = validations.get(0);
        assertThat("unparsed-file-bundle: should be an error in RBAC", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("unparsed-file-bundle: should be an error in RBAC", v.getValidationCode(), is(ValidationCode.RBAC_CONFIGURATION));
        assertThat("unparsed-file-bundle: should be an error in RBAC", v.getMessage(), is("[RBAC] - The bundle.yaml file references rbac.yaml in the RBAC section that is "
                                                                                           + "empty or has an invalid yaml format. Impossible to validate RBAC."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/RbacValidatorExtensionTest/invalid-strategy/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be an error", validations.get(0).getLevel(), is(Validation.Level.ERROR));
        assertThat("It should be a RBAC error", validations.get(0).getValidationCode(), is(ValidationCode.RBAC_CONFIGURATION));
        assertThat("It should warn about strategy", validations.get(0).getMessage(), containsString("authorization strategy that will be configured is not RoleMatrixAuthorizationStrategy"));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/RbacValidatorExtensionTest/no-strategy/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be an error", validations.get(0).getLevel(), is(Validation.Level.ERROR));
        assertThat("It should be a RBAC error", validations.get(0).getValidationCode(), is(ValidationCode.RBAC_CONFIGURATION));
        assertThat("It should warn about strategy", validations.get(0).getMessage(), containsString("RoleMatrixAuthorizationStrategy is not the current authorization strategy"));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/RbacValidatorExtensionTest/invalid-permissions/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be an error", validations.get(0).getLevel(), is(Validation.Level.ERROR));
        assertThat("It should be a RBAC error", validations.get(0).getValidationCode(), is(ValidationCode.RBAC_CONFIGURATION));
        assertThat("It should sshow wrong permissions", validations.get(0).getMessage(),
                   allOf(containsString("non.existent.Permission"), containsString("another.non.existent.Permission")));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/RbacValidatorExtensionTest/valid/"));
        assertThat("We should get validation results containing an entry", validations.stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), hasSize(0));
    }
}
