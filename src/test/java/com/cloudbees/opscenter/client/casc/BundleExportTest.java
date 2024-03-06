package com.cloudbees.opscenter.client.casc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.IMRunner;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.YamlClientUtils;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import hudson.ExtensionList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class BundleExportTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(WireMockConfiguration
                                                                             .wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder configBundleSrc = new TemporaryFolder();

    @BeforeClass
    public static void configureBeekeeper() {
        System.setProperty("com.cloudbees.jenkins.plugins.assurance.StagingURLSource.CloudBees.url", wiremock.baseUrl());
        wiremock.stubFor(get(urlEqualTo("/beer/1.2/beer.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/icon-shim-1.0.1.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("icon-shim-1.0.1.hpi")));
        wiremock.stubFor(get(urlEqualTo("/caffeine-api/2.9.3-111.va_4034a_92d8b_c/caffeine-api.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("caffeine-api-2.9.3-111.va_4034a_92d8b_c.hpi")));
    }

    @Before
    public void setUp() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setProductId("core-cm");
    }

    @Test
    @Issue({"BEE-43472"})
    @SuppressWarnings("unchecked")
    @WithConfigBundleAndWiremock("apiVersion1")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginCatalogApiVersion1() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = Arrays
                .stream(pluginCatalogExporter.getExport().split("\n"))
                .filter(s -> !s.isBlank() && !s.startsWith("---"))
                .collect(Collectors.joining("\n"));

        assertThat(yaml, notNullValue());
        Map<String, Object> exportedYaml = toYaml(yaml);

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("name"), equalTo("casc-catalog"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("displayName"), equalTo("casc Catalog"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("Exported catalog should match plugin-catalog.yaml",
                   configuration.get("description"), equalTo("casc"));

        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   configuration.get("beekeeperExceptions"), nullValue());

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("Exported catalog should match plugin-catalog.yaml",
                   includedPlugins.get("beer").get("version"), equalTo("1.2"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   includedPlugins.get("icon-shim").get("url"), endsWith("icon-shim-1.0.1.hpi"));
    }

    @Test
    @Issue({"BEE-43472"})
    @SuppressWarnings("unchecked")
    @WithConfigBundleAndWiremock("apiVersion1NoCatalog")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginCatalogApiVersion1NoCatalog() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = Arrays
                .stream(pluginCatalogExporter.getExport().split("\n"))
                .filter(s -> !s.isBlank() && !s.startsWith("---"))
                .collect(Collectors.joining("\n"));

        assertThat(yaml, notNullValue());
        Map<String, Object> exportedYaml = toYaml(yaml);

        assertThat("Exported catalog should match the template",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("Exported catalog should match the template",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("Exported catalog should match the template",
                   exportedYaml.get("name"), equalTo("jenkins"));

        assertThat("Exported catalog should match the template",
                   exportedYaml.get("displayName"), equalTo("Autogenerated catalog from jenkins"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("Exported catalog should match the template",
                   configuration.get("description"), equalTo("Exported plugins"));

        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   configuration.get("beekeeperExceptions"), nullValue());

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("Exported catalog should match the template",
                   includedPlugins.get("beer"), nullValue());

        assertThat("Exported catalog should match the template",
                   includedPlugins.get("icon-shim"), nullValue());
    }


    @Test
    @Issue({"BEE-43472"})
    @SuppressWarnings("unchecked")
    @WithConfigBundleAndWiremock("apiVersion2")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginCatalogApiVersion2() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = Arrays
                .stream(pluginCatalogExporter.getExport().split("\n"))
                .filter(s -> !s.isBlank() && !s.startsWith("---"))
                .collect(Collectors.joining("\n"));

        assertThat(yaml, notNullValue());
        Map<String, Object> exportedYaml = toYaml(yaml);

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("name"), equalTo("casc-catalog"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   exportedYaml.get("displayName"), equalTo("casc Catalog"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("Exported catalog should match plugin-catalog.yaml",
                   configuration.get("description"), equalTo("casc"));

        Map<String, Object> beekeeperExceptions = (Map<String, Object>) configuration.get("beekeeperExceptions");
        Map<String, Object> caffeineException = (Map<String, Object>) beekeeperExceptions.get("caffeine-api");
        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   caffeineException.get("version"), equalTo("2.9.3-111.va_4034a_92d8b_c"));

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("Exported catalog should match plugin-catalog.yaml",
                   includedPlugins.get("beer").get("version"), equalTo("1.2"));

        assertThat("Exported catalog should match plugin-catalog.yaml",
                   includedPlugins.get("icon-shim").get("url"), endsWith("icon-shim-1.0.1.hpi"));
    }

    @Test
    @Issue({"BEE-43472"})
    @SuppressWarnings("unchecked")
    @WithConfigBundleAndWiremock("apiVersion2NoCatalog")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginCatalogApiVersion2NoCatalog() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = Arrays
                .stream(pluginCatalogExporter.getExport().split("\n"))
                .filter(s -> !s.isBlank() && !s.startsWith("---"))
                .collect(Collectors.joining("\n"));

        assertThat(yaml, notNullValue());

        assertThat("Exported catalog should be empty", yaml, equalTo(""));
    }


    private Map<String, Object> toYaml(String str) {
        Map<String, Object> yaml = YamlClientUtils.createDefault().load(str);
        return yaml;
    }

    @Target({ ElementType.METHOD})
    @JenkinsRecipe(WithConfigBundleAndWiremock.RuleRunnerImpl.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WithConfigBundleAndWiremock {

        String value();

        class RuleRunnerImpl extends IMRunner<WithConfigBundleAndWiremock> {
            @Override
            protected void decorateHome(CJPRule rule, File home) throws Exception {
                FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/BundleExportInControllerTest/" + recipe.value()).toFile(), configBundleSrc.getRoot());
                applyTemplate("plugins.yaml");
                applyTemplate("plugin-catalog.yaml");

                // We copy the bundled configuration to a temporal directory (bundledConfiguration) because we may modify
                // the files there. To allow other tests to reuse the original folder.
                FileUtils.copyDirectory(configBundleSrc.getRoot(), rule.getBundledConfiguration());

                // We now use as bundle the new folder
                System.setProperty(CJPRule.PROP_CASC_BUNDLE, rule.getBundledConfiguration().getAbsolutePath());
                ConfigurationBundleManager.reset();
            }

            private static void applyTemplate(String file) throws IOException {
                File template = configBundleSrc.getRoot().toPath().resolve(file).toFile();
                if (template.isFile()) {
                    String content;
                    try (InputStream in = FileUtils.openInputStream(template)) {
                        content = IOUtils.toString(in, StandardCharsets.UTF_8);
                    }
                    try (OutputStream out = FileUtils.openOutputStream(template, false)) {
                        IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
                    }
                }
            }

            @Override
            public void tearDown(JenkinsRule jenkinsRule, WithConfigBundleAndWiremock recipe) throws Exception {
                try {
                    super.tearDown(jenkinsRule, recipe);
                } finally {
                    System.clearProperty(CJPRule.PROP_CASC_BUNDLE);
                    ConfigurationBundleManager.reset();
                    System.clearProperty("casc.jenkins.config");
                    System.clearProperty("casc.merge.strategy");
                }
            }
        }
    }

}
