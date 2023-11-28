package com.cloudbees.jenkins.plugins.casc.validation;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
import org.junit.rules.TemporaryFolder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class PluginCatalogValidatorExtensionTest extends AbstractIMTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));
        wiremock.stubFor(get(urlEqualTo("/icon-shim-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("icon-shim-1.0.1.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles").toFile(), bundlesSrc.getRoot());
        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("validCatalog").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
        Path pcFile2 = bundlesSrc.getRoot().toPath().resolve("invalidCatalog").resolve("plugin-catalog.yaml");
        try (InputStream in = FileUtils.openInputStream(pcFile2.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile2.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
    }

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

        validations = validator.validate(Paths.get((bundlesSrc.getRoot().getAbsolutePath() + "/invalidCatalog")));
        assertThat("We should get validation results containing an entry", validations, hasSize(1));
        assertThat("It should be an error", validations.get(0).getLevel(), is(Validation.Level.ERROR));
        assertThat("It should be a plugin catalog warning", validations.get(0).getValidationCode(), is(ValidationCode.PLUGIN_CATALOG));
        assertThat("It should contain not valid plugins", validations.get(0).getMessage(), containsString("icon-shim"));
        assertThat("It should not contain valid plugins", validations.get(0).getMessage(), not(containsString("beer")));

        validations = validator.validate(Paths.get((bundlesSrc.getRoot().getAbsolutePath() + "/validCatalog")));
        assertThat("We should get only info validation results", validations, hasSize(1));
        assertThat("We should get only info validation results", validations.get(0).getLevel(), is(Validation.Level.INFO));
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
