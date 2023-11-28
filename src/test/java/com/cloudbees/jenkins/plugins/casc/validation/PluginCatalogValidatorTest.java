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
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

public class PluginCatalogValidatorTest {

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
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @Test
    @WithEnvelope(OnePlugin.class)
    public void validTest() {
        // src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/validCatalog
        PlainBundle bundle = new PathPlainBundle(Paths.get((bundlesSrc.getRoot().getAbsolutePath() + "/validCatalog")));
        assertThat(bundle, Matchers.notNullValue());

        Collection<Validation> errors =  new PluginCatalogValidator().validate(bundle);

        assertThat(errors, hasSize(0));
    }

    @Test
    @WithEnvelope(OnePlugin.class)
    public void invalidTest() {
        // src/test/resources/com/cloudbees/jenkins/plugins/casc/validation/bundles/invalidCatalog
        PlainBundle bundle = new PathPlainBundle(Paths.get((bundlesSrc.getRoot().getAbsolutePath() + "/invalidCatalog")));
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
