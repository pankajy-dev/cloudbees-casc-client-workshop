package com.cloudbees.jenkins.plugins.casc.validation;

import java.nio.file.Paths;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.ExtensionList;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopeProduct;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;

import static com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager.isEmpty;

public class PluginsValidatorExtensionTest extends AbstractIMTest {
    @Rule
    public CJPRule jenkins;

    public PluginsValidatorExtensionTest() {
        this.jenkins = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.jenkins;
    }

    @Test
    @WithEnvelope(ThreePluginsV2dot289.class)
    public void smokes() {
        PluginsValidatorExtension validator = ExtensionList.lookupSingleton(PluginsValidatorExtension.class);

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

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/PluginsValidatorExtensionTest/invalid/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be a warning", validations.get(0).getLevel(), is(Validation.Level.WARNING));
        assertThat("It should be a PLUGINS warning", validations.get(0).getValidationCode(), is(ValidationCode.PLUGIN_AVAILABLE));
        assertThat("It should contain not valid plugins", validations.get(0).getMessage(), containsString("beer"));
        assertThat("It should not contain valid plugins", validations.get(0).getMessage(), not(anyOf(
                containsString("cloudbees-casc-items-api"), containsString("credentials"), containsString("cloudbees-assurance"))));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/PluginsValidatorExtensionTest/valid/"));
        assertThat("We should get empty validation results", validations, hasSize(0));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/PluginsValidatorExtensionTest/with-catalog/"));
        assertThat("We should get empty validation results", validations, hasSize(0));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/PluginsValidatorExtensionTest/invalid-with-catalog/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be a warning", validations.get(0).getLevel(), is(Validation.Level.WARNING));
        assertThat("It should be a PLUGINS warning", validations.get(0).getValidationCode(), is(ValidationCode.PLUGIN_AVAILABLE));
        assertThat("It should contain not valid plugins", validations.get(0).getMessage(), containsString("chucknorris"));
        assertThat("It should not contain valid plugins", validations.get(0).getMessage(), not(anyOf(
                containsString("cloudbees-casc-items-api"), containsString("credentials"), containsString("cloudbees-assurance"))));
    }

    public static final class ThreePluginsV2dot289 implements TestEnvelopeProvider {
        public ThreePluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1",1,"",TestEnvelopes.p("cloudbees-casc-items-api"), TestEnvelopes.p("credentials"), TestEnvelopes.p("cloudbees-assurance"));
        }
    }
}
