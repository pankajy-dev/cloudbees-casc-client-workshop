package com.cloudbees.jenkins.plugins.casc.validation;

import java.nio.file.Paths;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.ExtensionList;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class RbacValidatorExtensionTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void smokes() {
        RbacValidatorExtension validator = ExtensionList.lookupSingleton(RbacValidatorExtension.class);

//        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/missing-file"
//                                                          + "-bundle"));
//        Validation v = validations.get(0);
//        assertThat("missing-file-bundle: should be an error in JCASC", v.getLevel(), is(Validation.Level.ERROR));
//        assertThat("missing-file-bundle: should be an error in JCASC", v.getValidationCode(), is(ValidationCode.ITEMS_DEFINITION));
//        assertThat("missing-file-bundle: should be an error in JCASC", v.getMessage(), is("[ITEMS] - The bundle.yaml file references items.yaml in the Items section that "
//                                                                                          + "cannot be found. Impossible to validate items."));
//
//        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/unparsed-file-bundle"));
//        v = validations.get(0);
//        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getLevel(), is(Validation.Level.ERROR));
//        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getValidationCode(), is(ValidationCode.ITEMS_DEFINITION));
//        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getMessage(), is("[ITEMS] - The bundle.yaml file references items.yaml in the Items section that is "
//                                                                                           + "empty or has an invalid yaml format. Impossible to validate items."));

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/RbacValidatorExtensionTest/invalid-strategy/"));
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
        assertThat("We should get validation results containing an entry", validations, hasSize(0));
    }
}
