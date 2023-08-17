package com.cloudbees.opscenter.client.casc;

import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationUpdaterHelperTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog")
    @Test
    public void getUpdateCheckJsonResponseQuiet() {
        boolean hasInfo = ConfigurationUpdaterHelper.getUpdateCheckJsonResponse(false, false, false)
                                                    .getJSONObject("versions")
                                                    .getJSONObject("current-bundle")
                                                    .getJSONArray("validations")
                                                    .stream()
                                                    .anyMatch((message) -> message.toString().contains("INFO"));
        assertTrue("'quiet' is not set, the bundle should contains INFO messages", hasInfo);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog")
    @Test
    public void getUpdateCheckJsonResponse() {
        JSONObject updateCheckJsonResponse = ConfigurationUpdaterHelper.getUpdateCheckJsonResponse(false, false, true);
        boolean hasInfo = updateCheckJsonResponse.getJSONObject("versions")
                                               .getJSONObject("current-bundle")
                                               .getJSONArray("validations")
                                               .stream()
                                               .anyMatch((message) -> message.toString().contains("INFO"));
        assertFalse("'quiet' is set, the bundle should not contains INFO messages", hasInfo);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void fullValidation() {
        List<Validation> validations = ConfigurationUpdaterHelper.fullValidation(
                Path.of("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog"),
                false);
        boolean hasInfo = validations.stream()
                                     .anyMatch((message) -> message.toString().contains("INFO"));
        assertTrue("'quiet' is not set, the bundle should contains INFO messages", hasInfo);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void fullValidationQuiet() {
        List<Validation> validations = ConfigurationUpdaterHelper.fullValidation(
                Path.of("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog"),
                true);
        boolean hasInfo = validations.stream()
                                     .anyMatch((message) -> message.toString().contains("INFO"));
        assertFalse("'quiet' is set, the bundle should not contains INFO messages", hasInfo);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void shouldHotReloadWhenReloadAutomaticEnabled() {
        assertTrue(false);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void shouldAutoRestartWhenRestartAutomaticEnabled() {
        assertTrue(false);
    }

    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void shouldNotAutoUpdateWhenSkipVersionEnabled() {
        assertTrue(false);
    }

     // may need to be IT tests
    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void shoulDisplayAdminMonitorIfHotReloadNotPossible() {
        assertTrue(false);
    }

    // may need to be IT tests
    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @Test
    public void configScreenShouldShowSkipVersionButton() {
        assertTrue(false);
    }







    public static final class V2dot319 implements TestEnvelopeProvider {
        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.319.2", 1, "");
        }
    }

}