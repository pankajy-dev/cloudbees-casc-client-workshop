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

public class ItemsValidatorExtensionTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void smokes() {
        ItemsValidatorExtension validator = ExtensionList.lookupSingleton(ItemsValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/missing-file-bundle"));
        Validation v = validations.get(0);
        assertThat("missing-file-bundle: should be an error in items", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("missing-file-bundle: should be an error in items", v.getValidationCode(), is(ValidationCode.ITEMS_DEFINITION));
        assertThat("missing-file-bundle: should be an error in items", v.getMessage(), is("[ITEMS] - The bundle.yaml file references items.yaml in the items section that "
                                                                                          + "cannot be found. Impossible to validate items."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/unparsed-file-bundle"));
        v = validations.get(0);
        assertThat("unparsed-file-bundle: should be an error in items", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("unparsed-file-bundle: should be an error in items", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("unparsed-file-bundle: should be an error in items", v.getValidationCode(), is(ValidationCode.ITEMS_DEFINITION));
        assertThat("unparsed-file-bundle: should be an error in items", v.getMessage(), is("[ITEMS] - The bundle.yaml file references items.yaml in the items section that is "
                                                                                           + "empty or has an invalid yaml format. Impossible to validate items."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/ItemsValidatorExtensionTest/with-items/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be a warning", validations.get(0).getLevel(), is(Validation.Level.WARNING));
        assertThat("It should be an item warning", validations.get(0).getValidationCode(), is(ValidationCode.ITEMS_DEFINITION));
        assertThat("It should contain all not valid items", validations.get(0).getMessage(), allOf(containsString("pipeline"), containsString("clientController"),
                                                                                              containsString("not-a-valid-type")));
        assertThat("It should not contain allowed items", validations.get(0).getMessage(), not(anyOf(containsString("freeStyle"), containsString("folder"))));
    }
}
