package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.IMRunner;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.TextFile;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.Status;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.jvnet.hudson.test.LoggerRule.recorded;

public class HotReloadTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder(); // As for bundles we need a static rule and this.tmp is not static
    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    @BeforeClass
    public static void processBundles() {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/beer-1.3.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.3.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));
    }

    @Issue("BEE-3618")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundleAndWiremock("bundle-for-manage")
    @Test
    public void reloadTest() throws IOException, CasCException {
        assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = update();
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("manager", false))) {
            assertFalse(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            assertFalse(Jenkins.get().hasPermission(CascPermission.CASC_ADMIN));
            assertTrue(Jenkins.get().hasPermission(Jenkins.MANAGE));
            assertTrue(Jenkins.get().hasPermission(CascPermission.CASC_READ)); // Implied by CASC_ADMIN

            assertThrows(AccessDeniedException3.class, () -> service.reloadIfIsHotReloadable(bundle));
        }
        try (ACLContext a = ACL.as(User.getById("superadmin", false))) {
            assertTrue(Jenkins.get().hasPermission(Jenkins.ADMINISTER));

            service.reloadIfIsHotReloadable(bundle);

            await().atMost(Duration.ofSeconds(120)).until(() ->
                    Jenkins.get().getPlugin("configuration-as-code"), notNullValue());

            assertTrue(Jenkins.get().getPlugin("configuration-as-code").getWrapper().isActive());
            assertThat(Jenkins.get().getNumExecutors(), is(1));
            assertThat(Jenkins.get().getSystemMessage(), is("From 01_jenkins.yaml"));
        }
        try (ACLContext a = ACL.as(User.getById("admin", false))) {
            assertFalse(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            assertTrue(Jenkins.get().hasPermission(CascPermission.CASC_ADMIN));
            assertFalse(Jenkins.get().hasPermission(Jenkins.MANAGE));
            assertTrue(Jenkins.get().hasPermission(CascPermission.CASC_READ)); // Implied by CASC_ADMIN

            service.reloadIfIsHotReloadable(bundle); // Just a permission check
        }
    }

    @Test
    @Issue("BEE-5221")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundleAndWiremock("bundle-with-catalog")
    public void pluginCatalogRemovalTest() throws Exception {

        ConfigurationBundle bundle = noPluginsUpdate();
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("admin", false))) {
            assertTrue(Jenkins.get().hasPermission(CascPermission.CASC_ADMIN));
            assertTrue(Jenkins.get().hasPermission(CascPermission.CASC_READ)); // Implied by CASC_ADMIN

            Status status = BeekeeperRemote.get().getStatus();

            assertThat(status.getExtension(), notNullValue());

            service.reloadIfIsHotReloadable(bundle);

            await().atMost(Duration.ofSeconds(120)).until(() ->
                    BeekeeperRemote.get().getStatus().getExtension(), nullValue());

            assertTrue(Jenkins.get().getNumExecutors() == 2);
            assertTrue(Objects.equals(Jenkins.get().getSystemMessage(), "From 01_jenkins.yaml"));
        }
    }

    @Test
    @Issue("BEE-15460")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundleAndWiremock("version-1")
    public void partialAndFullReload() throws Exception {
        assertTrue(ConfigurationBundleManager.isSet());

        // Variables, so full reload
        loggerRule.record(BundleReload.class, Level.FINE).capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-2").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName())));
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName())));
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName())));

        // Only jcasc, so partial reload
        loggerRule.record(BundleReload.class, Level.FINE).capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-3").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Only JCasC has changed, thus partial reload. Expected JCasCReload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName())));
        assertThat("Only JCasC has changed, thus partial reload. Not expected ItemsReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName()))));
        assertThat("Only JCasC has changed, thus partial reload. Not expected RbacReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName()))));

        // Only items, so partial reload
        loggerRule.record(BundleReload.class, Level.FINE).capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-4").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Only items has changed, thus partial reload. Not expected JCasCReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName()))));
        assertThat("Only items has changed, thus partial reload. Expected ItemsAndRbacReload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName())));
        assertThat("Only items has changed, thus partial reload. Not expected RbacReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName()))));
    }

    @Test
    @Issue("BEE-43470 - Plugin Management 2.0")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundleAndWiremock("bundle-with-catalog")
    public void smokesForApiVersion1() {
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        // If plugins are not installed then the bundle is always hot reloadable
        assertNotNull("We need beer and manage-permission so the hot reload condition applies", Jenkins.get().getPlugin("beer"));
        assertNotNull("We need beer and manage-permission so the hot reload condition applies", Jenkins.get().getPlugin("manage-permission"));

        // Without changes in plugin catalog or plugins
        ConfigurationBundle cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog"));
        assertTrue("With apiVersion 1, no changes in plugin catalog or plugins means the bundle is hot-reloadable", service.isHotReloadable(cb));

        // Plugin catalog changes but plugins don't
        cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog_changes-in-catalog"));
        assertFalse("With apiVersion 1, changes in plugin catalog means the bundle is not hot-reloadable", service.isHotReloadable(cb));

        // Plugin catalog does not change but plugins do
        cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog_changes-in-plugins"));
        assertTrue("With apiVersion 1, changes in plugins but not in plugin catalog means the bundle is hot-reloadable", service.isHotReloadable(cb));
    }


    @Test
    @Issue("BEE-43470 - Plugin Management 2.0")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundleAndWiremock("bundle-apiversion-2")
    public void smokesForApiVersion2() {
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        // If plugins are not installed then the bundle is always hot reloadable
        assertNotNull("We need beer and manage-permission so the hot reload condition applies", Jenkins.get().getPlugin("beer"));
        assertNotNull("We need beer and manage-permission so the hot reload condition applies", Jenkins.get().getPlugin("manage-permission"));

        // Without changes in plugin catalog or plugins
        loggerRule.record(ConfigurationBundleService.class, Level.INFO).capture(10);
        ConfigurationBundle cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-apiversion-2"));
        assertTrue("With apiVersion 2, no changes in plugin catalog or plugins means the bundle is hot-reloadable", service.isHotReloadable(cb));
        assertThat("No message about hot reload", loggerRule, not(recorded(Level.INFO, containsString("Configuration Bundle cannot be reloaded"))));

        // Plugin catalog changes but plugins don't
        loggerRule.record(ConfigurationBundleService.class, Level.INFO).capture(10);
        cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-apiversion-2_changes-in-catalog"));
        assertFalse("With apiVersion 2, changes in plugin catalog means the bundle is not hot-reloadable", service.isHotReloadable(cb));
        assertThat("Logged the plugins with changes", loggerRule, recorded(Level.INFO, containsString("Configuration Bundle cannot be reloaded because of Plugin Catalog versions update: [{plugin: beer, catalog: 1.3, installed: 1.2}]")));

        // Plugin catalog does not change but plugins do
        loggerRule.record(ConfigurationBundleService.class, Level.INFO).capture(10);
        cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-apiversion-2_changes-in-plugins"));
        assertFalse("With apiVersion 2, changes in plugin configurations but not in plugin catalog means the bundle is not hot-reloadable", service.isHotReloadable(cb));
        assertThat("Logged the plugins with changes", loggerRule, recorded(Level.INFO, containsString("Configuration Bundle cannot be reloaded because some plugins are to be updated: [{plugin: manage-permission, from bundle: 1.0.1, installed: 1.0}]")));

        // Plugin catalog and plugins change
        loggerRule.record(ConfigurationBundleService.class, Level.INFO).capture(10);
        cb = ConfigurationBundleManager.getConfigurationBundleFromPath(bundlesSrc.getRoot().toPath().resolve("bundle-apiversion-2_changes-in-pc-and-plugins"));
        assertFalse("With apiVersion 2, changes in plugin configurations and in plugin catalog means the bundle is not hot-reloadable", service.isHotReloadable(cb));
        assertThat("Logged the plugins with changes", loggerRule, recorded(Level.INFO, containsString("Configuration Bundle cannot be reloaded because of Plugin Catalog versions update: [{plugin: beer, catalog: 1.3, installed: 1.2}]")));
        assertThat("Logged the plugins with changes", loggerRule, recorded(Level.INFO, containsString("Configuration Bundle cannot be reloaded because some plugins are to be updated: [{plugin: manage-permission, from bundle: 1.0.1, installed: 1.0}]")));
    }

    private ConfigurationBundle noPluginsUpdate() {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        Set<String> plugins = new HashSet<>();

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setItems(listOf(folder, "items/items.yaml"))
                .setJcasc(listOf(folder, "jcasc/01_jenkins.yaml", "jcasc/02_jenkins.yaml"))
                .setRbac(listOf(folder, "rbac/rbac.yaml"))
                .setPlugins(plugins)
                .build();
    }

    private ConfigurationBundle update() {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        Set<String> plugins = new HashSet<>();
        plugins.add("configuration-as-code"); // MANAGE cannot apply the plugin catalog, so we need a plugin from the (test-)envelope

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setItems(listOf(folder, "items/items.yaml"))
                .setJcasc(listOf(folder, "jcasc/jenkins.yaml"))
                .setRbac(listOf(folder, "rbac/rbac.yaml"))
                .setPlugins(plugins)
                .build();
    }

    private List<TextFile> listOf(Path folder, String ... files) {
        List<TextFile> list = new ArrayList<>();
        for (String file : files) {
            list.add(TextFile.of(folder.resolve(file)));
        }
        return list;
    }

    /**
     * Simplify the {@link com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle} for the tests resources in this test class and replace the %%URL%% from the bundle
     */
    @Target({ ElementType.METHOD})
    @JenkinsRecipe(WithConfigBundleAndWiremock.RuleRunnerImpl.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WithConfigBundleAndWiremock {

        String value();

        class RuleRunnerImpl extends IMRunner<WithConfigBundleAndWiremock> {
            @Override
            protected void decorateHome(CJPRule rule, File home) throws Exception {
                Path allBundles = Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest");
                FileUtils.copyDirectory(allBundles.toFile(), bundlesSrc.getRoot());

                for (File bundle : bundlesSrc.getRoot().listFiles()) {
                    String content;

                    // Sanitise plugin-catalog.yaml
                    Path pcFile = bundle.toPath().resolve("plugin-catalog.yaml");
                    if (Files.exists(pcFile)) {
                        try (InputStream in = FileUtils.openInputStream(pcFile.toFile())) {
                            content = IOUtils.toString(in, StandardCharsets.UTF_8);
                        }
                        try (OutputStream out = FileUtils.openOutputStream(pcFile.toFile(), false)) {
                            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
                        }
                    }

                    // Sanitise plugins.yaml
                    Path pFile = bundle.toPath().resolve("plugins.yaml");
                    if (Files.exists(pFile)) {
                        try (InputStream in = FileUtils.openInputStream(pFile.toFile())) {
                            content = IOUtils.toString(in, StandardCharsets.UTF_8);
                        }
                        try (OutputStream out = FileUtils.openOutputStream(pFile.toFile(), false)) {
                            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
                        }
                    }

                }
                // We copy the bundled configuration to a temporal directory (bundledConfiguration) because we may modify
                // the files there. To allow other tests to reuse the original folder.
                FileUtils.copyDirectory(new File(bundlesSrc.getRoot(), recipe.value()), rule.getBundledConfiguration());

                // We now use as bundle the new folder
                System.setProperty(CJPRule.PROP_CASC_BUNDLE, rule.getBundledConfiguration().getAbsolutePath());
                ConfigurationBundleManager.reset();
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
