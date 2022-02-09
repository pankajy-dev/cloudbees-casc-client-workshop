package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PathPlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopeProduct;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

public class PluginCatalogValidatorTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @Test
    @WithEnvelope(OnePlugin.class)
    public void validTest() {
        // src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog
        PlainBundle bundle = new PathPlainBundle(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog"));
        assertThat(bundle, Matchers.notNullValue());

        Collection<Validation> errors =  new PluginCatalogValidator().validate(bundle);

        assertThat(errors, hasSize(0));
    }

    @Test
    @WithEnvelope(OnePlugin.class)
    public void invalidTest() {
        // src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/invalidCatalog
        PlainBundle bundle = new PathPlainBundle(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/invalidCatalog"));
        assertThat(bundle, Matchers.notNullValue());

        Collection<Validation> errors =  new PluginCatalogValidator().validate(bundle);

        assertThat(errors, hasSize(1));

        assertThat(errors.stream().map(i -> i.getMessage()).collect(Collectors.toList()), hasItem("[CATALOGVAL] - 'icon-shim 1.0.1' cannot be installed because it is already part of CAP. "));
    }


    public static final class OnePlugin implements TestEnvelopeProvider {

        public OnePlugin() {
        }

        @NonNull
        public Envelope call() throws Exception {
            return TestEnvelopes.e(EnvelopeProduct.CORE_CM,
                    "2.276.1.1",
                    1,
                    "2.276",
                    TestEnvelopes.p("icon-shim", "2.0.3", "umtSUbNwIuqU6JGEQcBxqFt1X24="));
        }
    }
}
