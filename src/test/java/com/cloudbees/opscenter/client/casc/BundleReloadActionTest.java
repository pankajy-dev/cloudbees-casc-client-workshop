package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.report.InstalledPluginsReport;
import com.cloudbees.jenkins.cjp.installmanager.casc.plugin.management.report.RequestedPluginReportEntry;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Scope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.awaitility.Awaitility;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class BundleReloadActionTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();
    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    /**
     * Rule to restore system props after modifying them in a test:  bundle
     */
    @Rule
    public final FlagRule<String> bundleProp = FlagRule.systemProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/bundle-with-catalog").toFile().getAbsolutePath());
    @Rule
    public FlagRule<String> ucTestUrl = FlagRule.systemProperty("com.cloudbees.jenkins.plugins.assurance.StagingURLSource.CloudBees.url", wiremock.baseUrl());

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));
        wiremock.stubFor(get(urlEqualTo("/chucknorris.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("chucknorris.hpi")));
        wiremock.stubFor(get(urlPathEqualTo("/envelope-core-cm/update-center.json")).willReturn(aResponse().withStatus(200).withBodyFile("uc-core-cm-2.375.1.1.json")));
        wiremock.stubFor(get(urlEqualTo("/workflow-step-api/639.v6eca_cd8c04a_a_/workflow-step-api.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("workflow-step-api.hpi")));
        wiremock.stubFor(get(urlEqualTo("/cloudbees-casc-shared/1.0/cloudbees-casc-shared.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("cloudbees-casc-shared.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc").toFile(), bundlesSrc.getRoot());
        // Sanitise plugin-catalog.yaml
        replaceUrlPlaceholder(bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog").resolve("plugin-catalog.yaml"));
        replaceUrlPlaceholder(bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog-v2").resolve("plugin-catalog.yaml"));
        replaceUrlPlaceholder(bundlesSrc.getRoot().toPath().resolve("plugins-v2").resolve("plugins.yaml"));
    }

    private static void replaceUrlPlaceholder(Path file) throws IOException {
        String content;
        try (InputStream in = FileUtils.openInputStream(file.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(file.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/initial-bundle")
    public void checkHigherVersionAndHotReloadTest() throws Exception {
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
        await("Version 1 is completely reloaded").atMost(3, TimeUnit.MINUTES).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        initializeRealm(rule);
        // GIVEN The bundle is version 1 and there are 2 users: admin (with CASC_ADMIN role) and user (with CASC_READ role)
        // Jenkins.READ permission is needed to access any endpoint
        User admin = setSecurityRealmUser(rule, "admin", CascPermission.CASC_ADMIN, Jenkins.READ);
        User plainUser = setSecurityRealmUser(rule, "user", CascPermission.CASC_READ, Jenkins.READ);
        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        // WHEN Checking for a newer version if there's no new version
        // THEN Admin should get the result, no update available for the moment
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        assertThat("We should get a 200", resp.getStatusCode() , is(HttpServletResponse.SC_OK));
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("There's no new version available", !response.getBoolean("update-available"));

        // THEN User should also get a 403, no update available for the moment
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), plainUser, wc, false);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // WHEN Reloading if the bundle is not hot reloadable
        // THEN Admin should get the result, not possible to update
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, false);
        assertThat("We should get a 200", resp.getStatusCode() , is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("The bundle is not hot reloadable", !response.getBoolean("reloaded"));

        // THEN User should get a forbidden (403) error, as MANAGE permissions are required
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), plainUser, wc, false);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // WHEN there's a new version of the bundle
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/bundle-with-catalog-v2").toFile().getAbsolutePath());
        // THEN any user should get a 200 with update-available: true

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        assertThat("", response.get("update-type") != null && Objects.equals("RELOAD/RESTART/SKIP", response.get("update-type")));

        CLICommandInvoker.Result result =
                new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(),hasNoErrorOutput()));
        assertThat(result.stdout(), allOf(containsString("update-available"), containsString("true")));
        assertThat(result.stdout(), allOf(containsString("update-type"), containsString("RELOAD")));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        assertThat("", response.get("update-type") != null && Objects.equals("RELOAD/RESTART/SKIP", response.get("update-type")));

        // WHEN the bundle is hot reloadable
        // THEN Admin should get a 200 with reloaded: true
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("The bundle was reloaded", response.getBoolean("reloaded"));
        assertThat("Completed doesn't appear, as it's sync", response.getOrDefault("completed", null), nullValue());
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @Issue("BEE-22192")
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle")
    public void asynchronousReloadRaisesMonitorTest() throws Exception {
        initializeRealm(rule);
        User admin = setSecurityRealmUser(rule, "admin", CascPermission.CASC_ADMIN, Jenkins.READ);
        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        // Monitor should be disabled
        assertThat("Monitor is disabled", ExtensionList.lookupSingleton(BundleReloadErrorMonitor.class).isActivated(), is(false));

        // Setup a new failing version of the bundle
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle-invalid").toFile().getAbsolutePath());
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));

        // We should get a response indicating reload is requested
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, true);
        response = JSONObject.fromObject(resp.getContentAsString());
        // Wait for the bundle to reload and we should have a failure
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1)); // To avoid flooding with requests
        await().atMost(Duration.ofSeconds(30)).until(() -> reloadComplete(admin, wc));
        assertThat("Error monitor is activated", ExtensionList.lookupSingleton(BundleReloadErrorMonitor.class).isActivated(), is(true));
        assertThat("Info monitor is deactivated", ExtensionList.lookupSingleton(BundleReloadInfoMonitor.class).isActivated(), is(false));

        // Setup old working version of the bundle
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle").toFile().getAbsolutePath());

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, true);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("Update was applied", response.getBoolean("reloaded"));
        assertThat("Completed field is informed", response.get("completed"), notNullValue());
        // Wait for the bundle to reload and we should have removed the failure monitor
        await().atMost(Duration.ofSeconds(30)).until(() -> reloadComplete(admin, wc));
        assertThat("Error monitor is deactivated", ExtensionList.lookupSingleton(BundleReloadErrorMonitor.class).isActivated(), is(false));
        assertThat("Info monitor is activated", ExtensionList.lookupSingleton(BundleReloadInfoMonitor.class).isActivated(), is(true));

        // Doing 2 consecutive requests, 2nd one should answer "reloaded": false as 1st one is still running
        // Simulate a request is already running
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(true);
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, true);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("Update was not applied in 2nd request", response.getBoolean("reloaded"), is(false));
        assertThat("Update was not applied in 2nd request", response.getString("reason"), containsString("A reload is already in progress"));
    }

    @Test
    @WithEnvelope(ThreePluginsV2dot289.class)
    @Issue("BEE-22192")
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/plugins-v1")
    public void v2PluginsReloadTest() throws CheckNewBundleVersionException, CasCException {
        // Updating to another version using apiVersion 1
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/plugins-v1-2").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        BundleReload.PluginsReload reload = ExtensionList.lookupSingleton(BundleReload.PluginsReload.class);
        reload.doReload(ConfigurationBundleManager.get().getCandidateAsConfigurationBundle());
        await("Version 2 is completely reloaded").atMost(3, TimeUnit.MINUTES).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        InstalledPluginsReport report = ConfigurationBundleManager.get().getReport();
        assertThat("New plugin should appear as requested", report.getRequested().get("cloudbees-casc-shared"), notNullValue());
        assertThat("Bootstrap plugin should still appear as bootstrap", report.getRequested().get("icon-shim"), nullValue());

        // Updating to a apiVersion 2 bundle
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/plugins-v2").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        reload = ExtensionList.lookupSingleton(BundleReload.PluginsReload.class);
        reload.doReload(ConfigurationBundleManager.get().getCandidateAsConfigurationBundle());
        await("Version 3 is completely reloaded").atMost(3, TimeUnit.MINUTES).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("beer is installed", Jenkins.get().getPlugin("beer"), notNullValue());
        assertThat("chucknorris is installed", Jenkins.get().getPlugin("chucknorris"), notNullValue());
        assertThat("chucknorris dependency is installed", Jenkins.get().getPlugin("workflow-step-api"), notNullValue());
        assertThat("dependency not in CAP nor with URL is not installed", Jenkins.get().getPlugin("manage-permissions"), nullValue());
        // Report
        report = ConfigurationBundleManager.get().getReport();
        assertThat("icon-shim is still in bootstrap", report.getBootstrap().get("icon-shim"), notNullValue());
        Map<String, RequestedPluginReportEntry> plugins = report.getRequested();
        assertThat("beer is installed", plugins.get("beer"), notNullValue());
        assertThat("beer has no dependencies", plugins.get("beer").getDependencies().keySet(), empty());
        assertThat("chucknorris is installed", plugins.get("chucknorris"), notNullValue());
        assertThat("chucknorris has dependencies", plugins.get("chucknorris").getDependencies().containsKey("workflow-step-api"), is(true));
        assertThat("manage-permissions is not installed", plugins.get("manage-permissions"), nullValue());
    }

    private boolean reloadComplete(User user, CJPRule.WebClient wc) throws IOException {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-reload-running"), user, wc, false);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        return !response.getBoolean("reload-in-progress");
    }

    private static void initializeRealm(CJPRule j){
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
    }

    private static User setSecurityRealmUser(CJPRule j, String username, Permission... permissions) throws IOException {
        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) j.jenkins.getSecurityRealm();
        User user = realm.createAccount(username, "password");
        j.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
        for (Permission permission: permissions) {
            authorizationStrategy.add(permission, user.getId());
        }
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
        user.addProperty(new ApiTokenProperty());
        user.getProperty(ApiTokenProperty.class).changeApiToken();
        return user;
    }

    private WebResponse requestWithToken(HttpMethod method, URL fullURL, User asUser, CJPRule.WebClient wc, boolean async)
            throws IOException {
        try {
            WebRequest getRequest = new WebRequest(fullURL, method);
            if (async) {
                getRequest.setRequestParameters(Collections.singletonList(new NameValuePair("asynchronous", "true")));
            }
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        }
        catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }

    @AfterClass
    public static void after() {
        System.clearProperty("casc.jenkins.config");
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "", TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }

    public static final class ThreePluginsV2dot289 implements TestEnvelopeProvider {
        public ThreePluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "", TestEnvelopes.iconShim(),
                                   TestEnvelopes.p("cloudbees-casc-shared", "1.0", Scope.FAT, "6404ee70ffeca0b6e6b22af28bcb16821f85e216"),
                                   TestEnvelopes.p("workflow-step-api", "639.v6eca_cd8c04a_a_", Scope.FAT, "1ba5797d67fb17de1921570dc90c6deb7d20085c"));
        }
    }
}
