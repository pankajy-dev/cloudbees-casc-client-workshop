package com.cloudbees.jenkins.plugins.casc.validation;

import java.nio.file.Paths;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.ExtensionList;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class PluginCatalogValidatorExtensionTest extends AbstractIMTest {
    @Rule
    public CJPRule jenkins;

    public PluginCatalogValidatorExtensionTest () {
        this.jenkins = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.jenkins;
    }

    @Test
    @WithEnvelope(OnePluginV2dot289.class)
    public void smokes() {
        PluginCatalogValidatorExtension validator = ExtensionList.lookupSingleton(PluginCatalogValidatorExtension.class);

        List<Validation> validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/missing-file-bundle"));
        Validation v = validations.get(0);
        assertThat("missing-file-bundle: should be an error in plugin catalog", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("missing-file-bundle: should be an error in plugin catalog", v.getValidationCode(), is(ValidationCode.PLUGIN_CATALOG));
        assertThat("missing-file-bundle: should be an error in plugin catalog", v.getMessage(), is("[CATALOGVAL] - The bundle.yaml file references plugin-catalog.yaml in the plugin "
                                                                                          + "catalog section that cannot be found. Impossible to validate plugin catalog."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/bad-files/unparsed-file-bundle"));
        v = validations.get(0);
        assertThat("unparsed-file-bundle: should be an error in plugin catalog", v.getLevel(), is(Validation.Level.ERROR));
        assertThat("unparsed-file-bundle: should be an error in plugin catalog", v.getValidationCode(), is(ValidationCode.PLUGIN_CATALOG));
        assertThat("unparsed-file-bundle: should be an error in plugin catalog", v.getMessage(), is("[CATALOGVAL] - The bundle.yaml file references plugin-catalog.yaml in the "
                                                                                           + "plugin catalog section that is empty or has an invalid yaml format. Impossible to validate plugin catalog."));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/invalidCatalog/"));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be a warning", validations.get(0).getLevel(), is(Validation.Level.WARNING));
        assertThat("It should be a plugin catalog warning", validations.get(0).getValidationCode(), is(ValidationCode.PLUGIN_CATALOG));
        assertThat("It should contain not valid plugins", validations.get(0).getMessage(), containsString("icon-shim"));
        assertThat("It should not contain valid plugins", validations.get(0).getMessage(), not(containsString("beer")));

        validations = validator.validate(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog/"));
        assertThat("We should get empty validation results", validations, hasSize(0));
    }

    public static final class OnePluginV2dot289 implements TestEnvelopeProvider {
        public OnePluginV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1",1,"",TestEnvelopes.p("icon-shim"));
        }
    }
}
