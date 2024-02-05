package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationContext;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class JCasCValidatorExtensionTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void smokes() {
        JCasCValidatorExtension validator = ExtensionList.lookupSingleton(JCasCValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/missing-file-bundle"));
        Validation v = validations.get(0);
        assertThat("missing-file-bundle: should be an error in JCASC", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("missing-file-bundle: should be an error in JCASC", v.getValidationCode(), is(ValidationCode.JCASC_CONFIGURATION));
        assertThat("missing-file-bundle: should be an error in JCASC", v.getMessage(), is("[JCASC] - The bundle.yaml file references jenkins.yaml in the Jenkins Configuration as Code section that cannot be found. Impossible to validate Jenkins Configuration as Code."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/unparsed-file-bundle"));
        v = validations.get(0);
        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getValidationCode(), is(ValidationCode.JCASC_CONFIGURATION));
        assertThat("unparsed-file-bundle: should be an error in JCASC", v.getMessage(), is("[JCASC] - The bundle.yaml file references jenkins.yaml in the Jenkins Configuration as Code section that is empty or has an invalid yaml format. Impossible to validate Jenkins Configuration as Code."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/with-valid-jcasc-bundle"));
        assertThat("with-valid-jcasc-bundle: should not have errors or warnings, just 1 info", validations.get(0).getMessage(), is("[JCASC] - [JCasCValidator] All configurations validated successfully."));
        assertThat("with-valid-jcasc-bundle: should not have errors or warnings, just 1 info", validations.get(0).getLevel(), is(Validation.Level.INFO));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/with-invalid-jcasc-bundle"));
        v = validations.get(0);
        assertThat("with-invalid-jcasc-bundle: should be a warning in JCASC", v.getLevel(), is(Validation.Level.WARNING));
        assertThat("with-invalid-jcasc-bundle: should be a warning in JCASC", v.getValidationCode(), is(ValidationCode.JCASC_CONFIGURATION));
        assertThat("with-invalid-jcasc-bundle: should be a warning in JCASC", v.getMessage(), containsString("[JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"));

    }

    @Issue("BEE-29721")
    @Test
    public void invalidAnchors(){
        JCasCValidatorExtension validator = ExtensionList.lookupSingleton(JCasCValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/with-invalid-jcasc-too-many-anchors"));
        Validation v = validations.get(0);
        String error = "[JCASC] - The bundle.yaml file references jenkins.yaml in the Jenkins Configuration as Code section that is empty or has an invalid yaml format. Impossible to validate Jenkins Configuration as Code.";
        assertThat("with-invalid-jcasc-too-many-anchors-bundle: should be a error in JCASC", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("with-invalid-jcasc-too-many-anchors-bundle: should be a error in JCASC", v.getValidationCode(), is(ValidationCode.JCASC_CONFIGURATION));
        assertThat("with-invalid-jcasc-too-many-anchors-bundle: should be a error in JCASC", v.getMessage(), is(error));
    }

    @Test
    public void invalidAnchorsFixed(){
        System.setProperty(ConfigurationContext.CASC_YAML_MAX_ALIASES_PROPERTY, "51");

        JCasCValidatorExtension validator = ExtensionList.lookupSingleton(JCasCValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/JCasCValidatorExtensionTest/with-invalid-jcasc-too-many-anchors"));
        assertThat("there is no validation error/warning, just 1 info message", validations, IsCollectionWithSize.hasSize(1));
    }
}