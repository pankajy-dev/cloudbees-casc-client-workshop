package com.cloudbees.jenkins.plugins.casc.config;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateStatus;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.HotReloadAction;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import hudson.ExtensionList;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Ignore // see https://cloudbees.slack.com/archives/C0427CCHN5C/p1695145598395809
public class BundleUpdateStatusTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule
            wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));

    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    private static void verifyCurrentUpdateStatus(
            String message,
            BundleUpdateLog.BundleUpdateLogAction action,
            BundleUpdateLog.BundleUpdateLogActionSource source,
            String fromBundleVersion,
            String toBundleVersion,
            boolean skipped
    ) {
        BundleUpdateStatus current = BundleUpdateStatus.getCurrent();
        assertThat("BundleUpdateStatus should exists", current, notNullValue());
        assertThat(message, current.getAction(), is(action));
        assertThat(message, current.getSource(), is(source));
        assertThat("From bundle " + fromBundleVersion, current.getFromBundleVersion(), is(fromBundleVersion));
        assertThat("To bundle " + toBundleVersion, current.getToBundleVersion(), is(toBundleVersion));
        assertTrue("Action should be a success", current.isSuccess());
        assertNull("Action should be a success", current.getError());
        assertThat("Skipped should be " + skipped, current.isSkipped(), is(skipped));
        assertFalse("Action is finished", current.isOngoingAction());
    }

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/beer-1.3.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.3.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest").toFile(), bundlesSrc.getRoot());
        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("new-bundle-version-no-hot").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
        Path pcFile2 = bundlesSrc.getRoot().toPath().resolve("final-bundle-version-no-hot").resolve("plugin-catalog.yaml");
        try (InputStream in = FileUtils.openInputStream(pcFile2.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile2.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
    }

    @Before
    public void setUp() {
        // This is a dirty hack because the instance (it's an ENUM) is not reset between tests
        ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
        ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(null);
        ConfigurationBundleManager.reset();
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-1")
    public void skipMultipleVersions() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-2").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 2",
                BundleUpdateLog.BundleUpdateLogAction.SKIP,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "2",
                true
        );

        // Update version to 3
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-3").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 3",
                BundleUpdateLog.BundleUpdateLogAction.SKIP,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "3",
                true
        );

        configuration.setSkipNewVersions(false);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        // Update version to 4
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-4").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 4",
                BundleUpdateLog.BundleUpdateLogAction.CREATE,
                BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                "1",
                "4",
                false
        );

        configuration.setSkipNewVersions(false);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(false);
        configuration.save();

        // Update version to 5
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-5").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        verifyCurrentUpdateStatus(
                "Auto reload from 1 to 5",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "5",
                false
        );
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void testAPIReload() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2",
                                  false
        );

        // Let's reload as if the user had clicked the button
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // API because HotReloadAction was used directly
        verifyCurrentUpdateStatus("Reload using the Java API",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.API,
                                  "1",
                                  "2",
                                  false
        );
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void testManualReload() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2",
                                  false
        );

        // Simulate a manual action: BundleVisualizationLink or ConfigurationUpdaterMonitor
        BundleUpdateStatus.setCurrentAction(BundleUpdateLog.BundleUpdateLogAction.RELOAD, BundleUpdateLog.BundleUpdateLogActionSource.MANUAL);

        // Let's reload as if the user had clicked the button
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // MANUAL because there is an ongoing action
        verifyCurrentUpdateStatus("Reload using the Java API",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  "1",
                                  "2",
                                  false
        );
    }

}
