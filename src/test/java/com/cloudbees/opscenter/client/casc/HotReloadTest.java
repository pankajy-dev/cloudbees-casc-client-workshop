package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.TextFile;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.Status;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.jvnet.hudson.test.LoggerRule.recorded;

public class HotReloadTest extends AbstractIMTest {
    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc").toFile(), bundlesSrc.getRoot());

        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }

    }

    @Rule
    public final CJPRule rule;
    @Rule
    public LoggerRule loggerRule;

    public HotReloadTest() {
        this.rule = new CJPRule(this.tmp);
        this.loggerRule = new LoggerRule().record(BundleReload.class, Level.FINE);
    }

    @Override
    protected CJPRule rule() {
        return this.rule;
    }

    @Ignore("This test is flaky when the update site data is requested.")
    @Issue("BEE-3618")
    @WithEnvelope(TwoPluginsV2dot289.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle")
    @Test
    public void reloadTest() throws IOException, CasCException {
        assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = update(ConfigurationBundleManager.get().getConfigurationBundle());
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("manager", false))) {
            assertFalse(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            assertTrue(Jenkins.get().hasPermission(Jenkins.MANAGE));

            service.reloadIfIsHotReloadable(bundle);

            await().atMost(Duration.ofSeconds(120)).until(() ->
                    Jenkins.get().getPlugin("beer"), notNullValue());

            assertTrue(Jenkins.get().getPlugin("beer").getWrapper().isActive());
            assertTrue(Jenkins.get().getNumExecutors() == 2);
            assertTrue(Objects.equals(Jenkins.get().getSystemMessage(), "From 01_jenkins.yaml"));
        }
    }

    @Test
    @Issue("BEE-5221")
    @WithEnvelope(TwoPluginsV2dot289.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/initial-bundle")
    public void pluginCatalogRemovalTest() throws Exception {
        // This is a dirty hack to be able to manipulate the plugin catalog as in the BeforeClass method:
        // - WithConfigBundle needs a static resource, so we cannot use the "hacked" bundle that is in the TemporaryFolder rule
        // - So we initialize the CJPRule using a dummy bundle
        // - First thing we do is to apply the real bundle we need for this test (We need plugin catalog to force the bundle as no hot-reloadable)
        //      - Change the System property to point to the real bundle
        //      - Force the check of the new version
        //      - Force the reload of the bundle
        //      - As the reload is done in a background thread, we wait until it's finished
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/bundle-with-catalog").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload();
        await("bundle is completely reloaded").atMost(3, TimeUnit.MINUTES).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = noPluginsUpdate(ConfigurationBundleManager.get().getConfigurationBundle());
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("admin", false))) {
            assertTrue(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            assertTrue(Jenkins.get().hasPermission(Jenkins.MANAGE));

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
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-1")
    public void partialAndFullReload() throws Exception {
        assertTrue(ConfigurationBundleManager.isSet());

        // Variables, so full reload
        loggerRule.capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-2").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName())));
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName())));
        assertThat("Variables has changed, thus full reload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName())));

        // Only jcasc, so partial reload
        loggerRule.capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-3").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Only JCasC has changed, thus partial reload. Expected JCasCReload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName())));
        assertThat("Only JCasC has changed, thus partial reload. Not expected ItemsReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName()))));
        assertThat("Only JCasC has changed, thus partial reload. Not expected RbacReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName()))));

        // Only items, so partial reload
        loggerRule.capture(10); // Initialize capture
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadTest/version-4").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        assertThat("Only items has changed, thus partial reload. Not expected JCasCReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + JCasCReload.class.getName()))));
        assertThat("Only items has changed, thus partial reload. Expected ItemsAndRbacReload", loggerRule, recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.ItemsReload.class.getName())));
        assertThat("Only items has changed, thus partial reload. Not expected RbacReload", loggerRule, not(recorded(Level.FINE, containsString("Reloading bundle section " + BundleReload.RbacReload.class.getName()))));
    }

    @AfterClass
    public static void after() {
        System.clearProperty("casc.jenkins.config");
    }

    private ConfigurationBundle noPluginsUpdate(ConfigurationBundle bundle) {
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

    private ConfigurationBundle update(ConfigurationBundle bundle) {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        Set<String> plugins = new HashSet<>();
        plugins.add("beer");

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setItems(listOf(folder, "items.yaml"))
                .setJcasc(listOf(folder, "jenkins.yaml"))
                .setRbac(listOf(folder, "rbac.yaml"))
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

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1",1,"",TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
