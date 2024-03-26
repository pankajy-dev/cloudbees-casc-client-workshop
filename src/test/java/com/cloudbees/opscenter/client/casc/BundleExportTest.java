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
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopeProduct;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.NonNull;

import jenkins.model.Jenkins;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundleExportTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(WireMockConfiguration.wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder configBundleSrc = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();


    @BeforeClass
    public static void configureBeekeeper() {
        System.setProperty("com.cloudbees.jenkins.plugins.assurance.StagingURLSource.CloudBees.url", wiremock.baseUrl());
        wiremock.stubFor(get(urlEqualTo("/beer/1.2/beer.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/icon-shim-1.0.1.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("icon-shim-1.0.1.hpi")));
        wiremock.stubFor(get(urlEqualTo("/caffeine-api/2.9.3-111.va_4034a_92d8b_c/caffeine-api.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("caffeine-api-2.9.3-111.va_4034a_92d8b_c.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permissions/latest/manage-permissions.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("manage-permissions-1.0.1.hpi")));
        wiremock.stubFor(get(urlEqualTo("/scm-api/latest/scm-api.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("scm-api-2.2.6.hpi")));
        wiremock.stubFor(get(urlEqualTo("/structs/latest/structs.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("structs-1.9.hpi")));
        wiremock.stubFor(get(urlEqualTo("/chucknorris/latest/chucknorris.hpi"))
                                 .willReturn(aResponse().withStatus(200).withBodyFile("chucknorris-1.0.hpi")));
        wiremock.stubFor(get(urlEqualTo("/service/rest/v1/search/assets/download?maven.artifactId=icon-shim&maven.extension=hpi&maven.baseVersion=1.0.1&maven.groupId=org.jenkins-ci.plugins"))
                                 .withBasicAuth("user1", "passwd1").willReturn(aResponse().withStatus(200).withBodyFile("icon-shim-1.0.1.hpi")));
    }

    @Before
    public void setUp() {
        if (testName.getMethodName().endsWith("_oc")) {
            ExtensionList.lookupSingleton(PluginCatalogExporter.class).setProductId("core-oc-traditional");
        } else {
            ExtensionList.lookupSingleton(PluginCatalogExporter.class).setProductId("core-cm");
        }
    }

    @Test
    @Issue({"BEE-7093", "BEE-6931"})
    @WithEnvelope(WithIconShimBootstrap.class)
    public void exportBundleWithoutProperty_oc() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setIsPluginCatalogEnabledInOC(false);

        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        assertNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), not(containsString("plugin-catalog.yaml")));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }

    @Test
    @Issue({"BEE-7093", "BEE-6931"})
    @WithEnvelope(WithIconShimBootstrap.class)
    public void exportBundleWithProperty_oc() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setIsPluginCatalogEnabledInOC(true);

        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        assertNotNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugin-catalog.yaml"));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }

    @Test
    @WithEnvelope(WithIconShimBootstrap.class)
    @Issue({"BEE-7093", "BEE-6931"})
    public void exportBundle() {
        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        assertTrue("icon-shim is in the plugins list and not in the extension",
                   resp.getPlugins().contains("icon-shim") && !resp.getPluginCatalog().contains("icon-shim"));
        assertNotNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugin-catalog.yaml"));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }

    @Test
    @WithEnvelope(WithIconShimBootstrap.class)
    @Issue({"BEE-6171"})
    public void sortedPluginsExport() {
        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();

        Map<String, Object> catalog = toYaml(resp.getPluginCatalog());
        Map<String, Object> plugins = toYaml(resp.getPlugins());

        List<String> catalogPlugins = ((Map<String,Object>)((List<Map<String, Object>>)catalog.get("configurations")).get(0).get("includePlugins")).keySet().stream().collect(Collectors.toList());
        List<String> pluginsPlugins = ((List<Map<String,String>>)plugins.get("plugins")).stream().map(p -> p.values().stream().findFirst().get()).collect(Collectors.toList());

        List<String> sortedCatalog = new ArrayList<>(catalogPlugins);
        sortedCatalog.sort(String::compareTo);

        assertThat(catalogPlugins, contains(sortedCatalog.toArray()));

        List<String> sortedPlugins = new ArrayList<>(pluginsPlugins);
        sortedPlugins.sort(String::compareTo);

        assertThat(pluginsPlugins, contains(sortedPlugins.toArray()));
    }

    @Test
    @Issue({"BEE-9578"})
    @WithEnvelope(WithIconShimBootstrap.class)
    public void exportPlugins() {
        BundleExporter.PluginsExporter pluginsExporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        String yaml = pluginsExporter.getExport();

        assertThat(yaml, notNullValue());
        assertThat(yaml, not(containsString("{")));
        assertThat(yaml, not(containsString("}")));

        assertThat(yaml, notNullValue());
    }

    @Test
    @Issue({"BEE-9580"})
    @WithEnvelope(WithIconShimBootstrap.class)
    public void exportPluginCatalog() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = pluginCatalogExporter.getExport();

        assertThat(yaml, notNullValue());
        assertThat(yaml, not(containsString("{")));
        assertThat(yaml, not(containsString("}")));
        Map<String, Object> obj = toYaml(yaml);

        assertThat(obj, notNullValue());
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

        assertThat("The export type must be 'plugin-catalog'",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("The version is always 1",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("The name is the one defined in the plugin-catalog",
                   exportedYaml.get("name"), equalTo("casc-catalog"));

        assertThat("The display name is the one defined in the plugin-catalog",
                   exportedYaml.get("displayName"), equalTo("casc Catalog"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("The description is the one defined in the plugin-catalog",
                   configuration.get("description"), equalTo("casc"));

        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   configuration.get("beekeeperExceptions"), nullValue());

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("The version of the beer plugin is the one defined in the plugin-catalog",
                   includedPlugins.get("beer").get("version"), equalTo("1.2"));

        assertThat("The version of the icon-shim plugin is the one defined in the plugin-catalog",
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

        assertThat("The export type must be 'plugin-catalog'",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("The version is always 1",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("The name is the one from the template",
                   exportedYaml.get("name"), equalTo("jenkins"));

        assertThat("The display name is the one from the template",
                   exportedYaml.get("displayName"), equalTo("Autogenerated catalog from jenkins"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("The description is the one from the template",
                   configuration.get("description"), equalTo("Exported plugins"));

        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   configuration.get("beekeeperExceptions"), nullValue());

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("Exported catalog should not contains beer",
                   includedPlugins.get("beer"), nullValue());

        assertThat("Exported catalog should not contains icon-shim",
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

        assertThat("The export type must be 'plugin-catalog'",
                   exportedYaml.get("type"), equalTo("plugin-catalog"));

        assertThat("The version is always 1",
                   exportedYaml.get("version"), equalTo("1"));

        assertThat("The name is the one defined in the plugin-catalog",
                   exportedYaml.get("name"), equalTo("casc-catalog"));

        assertThat("The display name is the one defined in the plugin-catalog",
                   exportedYaml.get("displayName"), equalTo("casc Catalog"));

        List<Map<String, Object>> configurations = (List<Map<String, Object>>) exportedYaml.get("configurations");
        Map<String, Object> configuration = configurations.get(0);
        assertThat("The description is the one defined in the plugin-catalog",
                   configuration.get("description"), equalTo("casc"));

        Map<String, Object> beekeeperExceptions = (Map<String, Object>) configuration.get("beekeeperExceptions");
        Map<String, Object> caffeineException = (Map<String, Object>) beekeeperExceptions.get("caffeine-api");
        assertThat("Exported catalog should not contains the beekeeperExceptions",
                   caffeineException.get("version"), equalTo("2.9.3-111.va_4034a_92d8b_c"));

        Map<String, Map<String, String>> includedPlugins = (Map<String, Map<String, String>>) configuration.get("includePlugins");
        assertThat("The version of the beer plugin is the one defined in the plugin-catalog",
                   includedPlugins.get("beer").get("version"), equalTo("1.2"));

        assertThat("The version of the icon-shim plugin is the one defined in the plugin-catalog",
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

    @Test
    @Issue("BEE-43473 - Plugin Management 2.0")
    @WithConfigBundleAndWiremock("apiVersion2Export")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginsApiVersion2() {
        assertNotNull("configuration-as-code is installed as CAP plugin", Jenkins.get().getPlugin("configuration-as-code"));
        assertNotNull("caffeine-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("caffeine-api"));
        assertNotNull("commons-text-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("commons-text-api"));
        assertNotNull("snakeyaml-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("snakeyaml-api"));
        assertNotNull("commons-lang3-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("commons-lang3-api"));
        assertNotNull("Beer is installed from the plugin catalog", Jenkins.get().getPlugin("beer"));
        assertNotNull("chucknorris is expanded and installed from the plugins.yaml", Jenkins.get().getPlugin("chucknorris"));
        assertNotNull("icon-shim is expanded and installed from the plugins.yaml", Jenkins.get().getPlugin("icon-shim"));
        assertNotNull("scm-api is expanded and installed from the plugins.yaml", Jenkins.get().getPlugin("scm-api"));
        assertNotNull("structs is installed as a dependency of scm-api", Jenkins.get().getPlugin("structs"));
        assertNotNull("As example, cloudbees-casc-items-controller is a test dependency, so installed as if manually - it will appear in the export", Jenkins.get().getPlugin("cloudbees-casc-items-controller"));
        assertNotNull("As example, master-provisioning-kubernetes is a test dependency, so installed as if manually - it will appear in the export", Jenkins.get().getPlugin("master-provisioning-kubernetes"));

        BundleExporter.PluginsExporter exporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        // The test dependencies are installed as well, as if they were manually installed
        String export = exporter.getExport();
        assertThat("The export is not null", export, notNullValue());
        Map<String, Object> exportedYaml = toYaml(export);
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) exportedYaml.get("plugins");
        Map<String, Object> beer = plugins.stream().filter(m -> m.get("id").equals("beer")).findFirst().orElse(null);
        assertNotNull("beer from Plugin catalog", beer);
        assertNull("beer from Plugin catalog", beer.get("url"));
        assertNull("beer from Plugin catalog", beer.get("credentialsId"));
        assertNull("beer from Plugin catalog", beer.get("repository"));
        assertNull("beer from Plugin catalog", beer.get("groupId"));
        assertNull("beer from Plugin catalog", beer.get("version"));

        Map<String, Object> chucknorris = plugins.stream().filter(m -> m.get("id").equals("chucknorris")).findFirst().orElse(null);
        assertNotNull("chucknorris from UC", chucknorris);
        assertNull("chucknorris from UC", chucknorris.get("url"));
        assertNull("chucknorris from UC", chucknorris.get("credentialsId"));
        assertNull("chucknorris from UC", chucknorris.get("repository"));
        assertNull("chucknorris from UC", chucknorris.get("groupId"));
        assertNull("chucknorris from UC", chucknorris.get("version"));

        Map<String, Object> cloudbeesCascItemsController = plugins.stream().filter(m -> m.get("id").equals("cloudbees-casc-items-controller")).findFirst().orElse(null);
        assertNotNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController);
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("url"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("credentialsId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("repository"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("groupId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("version"));

        Map<String, Object> masterProvisioningKubernetes = plugins.stream().filter(m -> m.get("id").equals("master-provisioning-kubernetes")).findFirst().orElse(null);
        assertNotNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes);
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("url"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("credentialsId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("repository"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("groupId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("version"));

        Map<String, Object> configurationAsCode = plugins.stream().filter(m -> m.get("id").equals("configuration-as-code")).findFirst().orElse(null);
        assertNotNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode);
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("url"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("credentialsId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("repository"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("groupId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("version"));

        Map<String, Object> caffeineApi = plugins.stream().filter(m -> m.get("id").equals("caffeine-api")).findFirst().orElse(null);
        assertNull("caffeine-api is a dependency of configuration-as-code, so not in the export", caffeineApi);

        Map<String, Object> scmApi = plugins.stream().filter(m -> m.get("id").equals("scm-api")).findFirst().orElse(null);
        assertNotNull("scm-api is from URL", scmApi);
        assertThat("scm-api is from URL", scmApi.get("url"), is(wiremock.baseUrl() + "/scm-api/latest/scm-api.hpi"));
        assertThat("scm-api is from URL", scmApi.get("credentialsId"), is("cred2"));
        assertNull("scm-api is from URL", scmApi.get("repository"));
        assertNull("scm-api is from URL", scmApi.get("groupId"));
        assertNull("scm-api is from URL", scmApi.get("version"));

        Map<String, Object> structs = plugins.stream().filter(m -> m.get("id").equals("structs")).findFirst().orElse(null);
        assertNull("structs is a dependency of scm-api, so not in the export", structs);

        Map<String, Object> iconShim = plugins.stream().filter(m -> m.get("id").equals("icon-shim")).findFirst().orElse(null);
        assertNotNull("icon-shim is from maven", iconShim);
        assertNull("icon-shim is from maven", iconShim.get("url"));
        assertNull("icon-shim is from maven", iconShim.get("credentialsId"));
        assertThat("icon-shim is from maven", iconShim.get("repositoryId"), is("test-repo"));
        assertThat("icon-shim is from maven", iconShim.get("groupId"), is("org.jenkins-ci.plugins"));
        assertThat("icon-shim is from maven", iconShim.get("version"), is("1.0.1"));

        List<Map<String, Object>> repositories = (List<Map<String, Object>>) exportedYaml.get("repositories");
        Map<String, Object> testRepo = repositories.stream().filter(m -> m.get("id").equals("test-repo")).findFirst().orElse(null);
        assertNotNull("testRepo repository is exported", testRepo);
        assertThat("testRepo repository is exported", testRepo.get("url"), is(wiremock.baseUrl()));
        assertThat("testRepo repository is exported", testRepo.get("credentialsId"), is("cred1"));
        assertThat("testRepo repository is exported", testRepo.get("layout"), is("nexus3"));

        List<Map<String, Object>> credentials = (List<Map<String, Object>>) exportedYaml.get("credentials");
        Map<String, Object> cred1 = credentials.stream().filter(m -> m.get("id").equals("cred1")).findFirst().orElse(null);
        assertNotNull("cred1 credential is exported", cred1);
        assertThat("cred1 credential is exported", cred1.get("user"), is("user1"));
        assertNotNull("cred1 credential is exported", cred1.get("password"));
        assertNull("cred1 credential is exported", cred1.get("token"));

        Map<String, Object> cred2 = credentials.stream().filter(m -> m.get("id").equals("cred2")).findFirst().orElse(null);
        assertNotNull("cred2 credential is exported", cred2);
        assertThat("cred2 credential is exported", cred2.get("user"), is("user2"));
        assertNotNull("cred2 credential is exported", cred2.get("password"));
        assertNull("cred2 credential is exported", cred2.get("token"));
    }

    @Test
    @Issue("BEE-43473 - Plugin Management 2.0")
    @WithConfigBundleAndWiremock("apiVersion2ExportNoCredNoRepo")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginsApiVersion2NoCredNoRepo() {
        assertNotNull("configuration-as-code is installed as CAP plugin", Jenkins.get().getPlugin("configuration-as-code"));
        assertNotNull("caffeine-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("caffeine-api"));
        assertNotNull("commons-text-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("commons-text-api"));
        assertNotNull("snakeyaml-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("snakeyaml-api"));
        assertNotNull("commons-lang3-api is installed as dependency of configuration-as-code", Jenkins.get().getPlugin("commons-lang3-api"));
        assertNotNull("Beer is installed from the plugin catalog", Jenkins.get().getPlugin("beer"));
        assertNotNull("chucknorris is expanded and installed from the plugins.yaml", Jenkins.get().getPlugin("chucknorris"));
        assertNotNull("scm-api is expanded and installed from the plugins.yaml", Jenkins.get().getPlugin("scm-api"));
        assertNotNull("structs is installed as a dependency of scm-api", Jenkins.get().getPlugin("structs"));
        assertNotNull("As example, cloudbees-casc-items-controller is a test dependency, so installed as if manually - it will appear in the export", Jenkins.get().getPlugin("cloudbees-casc-items-controller"));
        assertNotNull("As example, master-provisioning-kubernetes is a test dependency, so installed as if manually - it will appear in the export", Jenkins.get().getPlugin("master-provisioning-kubernetes"));

        BundleExporter.PluginsExporter exporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        // The test dependencies are installed as well, as if they were manually installed
        String export = exporter.getExport();
        assertThat("The export is not null", export, notNullValue());
        Map<String, Object> exportedYaml = toYaml(export);
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) exportedYaml.get("plugins");
        Map<String, Object> beer = plugins.stream().filter(m -> m.get("id").equals("beer")).findFirst().orElse(null);
        assertNotNull("beer from Plugin catalog", beer);
        assertNull("beer from Plugin catalog", beer.get("url"));
        assertNull("beer from Plugin catalog", beer.get("credentialsId"));
        assertNull("beer from Plugin catalog", beer.get("repository"));
        assertNull("beer from Plugin catalog", beer.get("groupId"));
        assertNull("beer from Plugin catalog", beer.get("version"));

        Map<String, Object> chucknorris = plugins.stream().filter(m -> m.get("id").equals("chucknorris")).findFirst().orElse(null);
        assertNotNull("chucknorris from UC", chucknorris);
        assertNull("chucknorris from UC", chucknorris.get("url"));
        assertNull("chucknorris from UC", chucknorris.get("credentialsId"));
        assertNull("chucknorris from UC", chucknorris.get("repository"));
        assertNull("chucknorris from UC", chucknorris.get("groupId"));
        assertNull("chucknorris from UC", chucknorris.get("version"));

        Map<String, Object> cloudbeesCascItemsController = plugins.stream().filter(m -> m.get("id").equals("cloudbees-casc-items-controller")).findFirst().orElse(null);
        assertNotNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController);
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("url"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("credentialsId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("repository"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("groupId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed", cloudbeesCascItemsController.get("version"));

        Map<String, Object> masterProvisioningKubernetes = plugins.stream().filter(m -> m.get("id").equals("master-provisioning-kubernetes")).findFirst().orElse(null);
        assertNotNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes);
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("url"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("credentialsId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("repository"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("groupId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed", masterProvisioningKubernetes.get("version"));

        Map<String, Object> configurationAsCode = plugins.stream().filter(m -> m.get("id").equals("configuration-as-code")).findFirst().orElse(null);
        assertNotNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode);
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("url"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("credentialsId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("repository"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("groupId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed", configurationAsCode.get("version"));

        Map<String, Object> caffeineApi = plugins.stream().filter(m -> m.get("id").equals("caffeine-api")).findFirst().orElse(null);
        assertNull("caffeine-api is a dependency of configuration-as-code, so not in the export", caffeineApi);

        Map<String, Object> scmApi = plugins.stream().filter(m -> m.get("id").equals("scm-api")).findFirst().orElse(null);
        assertNotNull("scm-api is from UC", scmApi);
        assertNull("scm-api is from UC", scmApi.get("url"));
        assertNull("scm-api is from UC", scmApi.get("credentialsId"));
        assertNull("scm-api is from UC", scmApi.get("repository"));
        assertNull("scm-api is from UC", scmApi.get("groupId"));
        assertNull("scm-api is from UC", scmApi.get("version"));

        Map<String, Object> structs = plugins.stream().filter(m -> m.get("id").equals("structs")).findFirst().orElse(null);
        assertNull("structs is a dependency of scm-api, so not in the export", structs);

        List<Map<String, Object>> repositories = (List<Map<String, Object>>) exportedYaml.get("repositories");
        assertNull("repositories are not exported", repositories);

        List<Map<String, Object>> credentials = (List<Map<String, Object>>) exportedYaml.get("credentials");
        assertNull("credentials are not exported", credentials);
    }

    @Test
    @Issue("BEE-43473 - Plugin Management 2.0")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    public void exportPluginsApiVersion2WithoutBundle() throws Exception {
        assertNotNull("As example, cloudbees-casc-items-controller is a test dependency, so installed as if manually - it will appear in the export", Jenkins.get().getPlugin("configuration-as-code"));
        assertNotNull("caffeine-api is installed as dependency of configuration-as-code, so it won't appear in the export", Jenkins.get().getPlugin("caffeine-api"));
        assertNotNull("commons-text-api is installed as dependency of configuration-as-code, so it won't appear in the export", Jenkins.get().getPlugin("commons-text-api"));
        assertNotNull("snakeyaml-api is installed as dependency of configuration-as-code, so it won't appear in the export", Jenkins.get().getPlugin("snakeyaml-api"));
        assertNotNull("commons-lang3-api is installed as dependency of configuration-as-code, so it won't appear in the export", Jenkins.get().getPlugin("commons-lang3-api"));
        assertNull("Beer is not installed", Jenkins.get().getPlugin("chucknorris"));
        assertNull("chucknorris is not installed", Jenkins.get().getPlugin("chucknorris"));

        BundleExporter.PluginsExporter exporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        // The test dependencies are installed as well, as if they were manually installed
        String export = exporter.getExport();
        assertThat("The export is not null", export, notNullValue());
        Map<String, Object> exportedYaml = toYaml(export);
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) exportedYaml.get("plugins");
        Map<String, Object> beer = plugins.stream().filter(m -> m.get("id").equals("beer")).findFirst().orElse(null);
        assertNull("beer is not installed as we don't have CasC Bundle", beer);

        Map<String, Object> chucknorris = plugins.stream().filter(m -> m.get("id").equals("chucknorris")).findFirst().orElse(null);
        assertNull("chucknorris is not installed as we don't have CasC Bundle", chucknorris);

        Map<String, Object> cloudbeesCascItemsController = plugins.stream().filter(m -> m.get("id").equals("cloudbees-casc-items-controller")).findFirst().orElse(null);
        assertNotNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController);
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController.get("url"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController.get("credentialsId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController.get("repository"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController.get("groupId"));
        assertNull("cloudbees-casc-items-controller is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", cloudbeesCascItemsController.get("version"));

        Map<String, Object> masterProvisioningKubernetes = plugins.stream().filter(m -> m.get("id").equals("master-provisioning-kubernetes")).findFirst().orElse(null);
        assertNotNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes);
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes.get("url"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes.get("credentialsId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes.get("repository"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes.get("groupId"));
        assertNull("master-provisioning-kubernetes is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", masterProvisioningKubernetes.get("version"));

        Map<String, Object> configurationAsCode = plugins.stream().filter(m -> m.get("id").equals("configuration-as-code")).findFirst().orElse(null);
        assertNotNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode);
        assertNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode.get("url"));
        assertNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode.get("credentialsId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode.get("repository"));
        assertNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode.get("groupId"));
        assertNull("configuration-as-code is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", configurationAsCode.get("version"));

        Map<String, Object> caffeineApi = plugins.stream().filter(m -> m.get("id").equals("caffeine-api")).findFirst().orElse(null);
        assertNotNull("caffeine-api is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi);
        assertNull("caffeine-api  is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi.get("url"));
        assertNull("caffeine-api  is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi.get("credentialsId"));
        assertNull("caffeine-api  is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi.get("repository"));
        assertNull("caffeine-api  is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi.get("groupId"));
        assertNull("caffeine-api  is a dependency of configuration-as-code, as there is no bundle is like with apiVersion 1", caffeineApi.get("version"));

        Map<String, Object> scmApi = plugins.stream().filter(m -> m.get("id").equals("scm-api")).findFirst().orElse(null);
        assertNotNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi);
        assertNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi.get("url"));
        assertNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi.get("credentialsId"));
        assertNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi.get("repository"));
        assertNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi.get("groupId"));
        assertNull("scm-api is dependency, so as if manually deployed. As there is no bundle is like with apiVersion 1", scmApi.get("version"));

        Map<String, Object> structs = plugins.stream().filter(m -> m.get("id").equals("structs")).findFirst().orElse(null);
        assertNotNull("structs is a dependency of scm-api, so not in the export", structs);
        assertNull("structs  is a dependency of scm-api, as there is no bundle is like with apiVersion 1", structs.get("url"));
        assertNull("structs  is a dependency of scm-api, as there is no bundle is like with apiVersion 1", structs.get("credentialsId"));
        assertNull("structs  is a dependency of scm-api, as there is no bundle is like with apiVersion 1", structs.get("repository"));
        assertNull("structs  is a dependency of scm-api, as there is no bundle is like with apiVersion 1", structs.get("groupId"));
        assertNull("structs  is a dependency of scm-api, as there is no bundle is like with apiVersion 1", structs.get("version"));

        List<Map<String, Object>> repositories = (List<Map<String, Object>>) exportedYaml.get("repositories");
        assertNull("repositories are not exported", repositories);

        List<Map<String, Object>> credentials = (List<Map<String, Object>>) exportedYaml.get("credentials");
        assertNull("credentials are not exported", credentials);

    }

    @Test
    @Issue("BEE-43473 - Plugin Management 2.0")
    @WithEnvelope(WithIconShimBootstrap.class) // We want a bootstrap plugin
    @WithConfigBundleAndWiremock("apiVersion2ExportNoCredNoRepo") // With bundle so it can have apiVersion 2
    public void exportWithBootstrap() {
        assertNotNull("Beer is installed, so the CasC Bundle is applied", Jenkins.get().getPlugin("chucknorris"));
        assertNotNull("icon-shim is installed as it is bootstrap", Jenkins.get().getPlugin("icon-shim"));

        BundleExporter.PluginsExporter exporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        String export = exporter.getExport();
        assertThat("chucknorris plugin is exported", export, containsString("chucknorris"));
        assertThat("icon-shim is not exported as it is bootstrap", export, not(containsString("icon-shim")));
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
                FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/BundleExportTest/" + recipe.value()).toFile(), configBundleSrc.getRoot());
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

    public static final class WithIconShimBootstrap implements TestEnvelopeProvider {
        @NonNull
        @Override
        public Envelope call() throws Exception {
            return TestEnvelopes.e(EnvelopeProduct.CORE_CM, "2.401.1.1", 1, "2.401.1.1", TestEnvelopes.p("icon-shim", "2.0.3"));
        }
    }

}
