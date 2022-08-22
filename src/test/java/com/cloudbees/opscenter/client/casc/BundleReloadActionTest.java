package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
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
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class BundleReloadActionTest extends AbstractIMTest {

    @Rule
    public final CJPRule rule;

    /**
     * Rule to restore system props after modifying them in a test
     */
    @Rule
    public final FlagRule<String> props = FlagRule.systemProperty("core.casc.config.bundle", "src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle"
                                                                                             + "-with-catalog");

    public BundleReloadActionTest() {
        this.rule = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.rule;
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle-with-catalog")
    public void checkHigherVersionAndHotReloadTest() throws Exception {
        initializeRealm(rule);
        // GIVEN The bundle is version 1 and there are 2 users: admin (with ADMINISTER role) and user (with READ role)
        User admin = setSecurityRealmUser(rule, "admin", Jenkins.ADMINISTER);
        User plainUser = setSecurityRealmUser(rule, "user", Jenkins.READ);
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
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle-with-catalog-v2").toFile().getAbsolutePath());
        // THEN any user should get a 200 with update-available: true

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        assertThat("", response.get("update-type") != null && Objects.equals("RELOAD", response.get("update-type")));

        CLICommandInvoker.Result result =
                new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(),hasNoErrorOutput()));
        assertThat(result.stdout(), allOf(containsString("update-available"), containsString("true")));
        assertThat(result.stdout(), allOf(containsString("update-type"), containsString("RELOAD")));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        assertThat("", response.get("update-type") != null && Objects.equals("RELOAD", response.get("update-type")));

        // WHEN the bundle is hot reloadable
        // THEN Admin should get a 200 with reloaded: true
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("The bundle was reloaded", response.getBoolean("reloaded"));
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @Issue("BEE-22192")
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle")
    public void asynchronousReloadRaisesMonitorTest() throws Exception {
        initializeRealm(rule);
        User admin = setSecurityRealmUser(rule, "admin", Jenkins.ADMINISTER);
        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        // Monitor should be disabled
        assertThat("Monitor is disabled", Jenkins.get().getAdministrativeMonitor("com.cloudbees.opscenter.client.casc.BundleReloadMonitor").isActivated(), is(false));

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
        assertThat("Monitor is activated", ExtensionList.lookupSingleton(BundleReloadMonitor.class).isActivated(), is(true));

        // Setup old working version of the bundle
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle").toFile().getAbsolutePath());

        // Wait for reload to complete
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("There's a new version available", response.getBoolean("update-available"));
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, true);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("Update was applied", response.getBoolean("reloaded"));
        // Wait for the bundle to reload and we should have removed the failure monitor
        await().atMost(Duration.ofSeconds(30)).until(() -> reloadComplete(admin, wc));
        assertThat("Monitor is deactivated", ExtensionList.lookupSingleton(BundleReloadMonitor.class).isActivated(), is(false));

        // Doing 2 consecutive requests, 2nd one should answer "reloaded": false as 1st one is still running
        // Setting failing bundle to make sure request takes some (small) time to complete
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/items-bundle-invalid").toFile().getAbsolutePath());
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, true);
        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, false);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("Update was not applied", !response.getBoolean("reloaded"));
        await().atMost(Duration.ofSeconds(30)).until(() -> reloadComplete(admin, wc));
    }

    private boolean reloadComplete(User user, CJPRule.WebClient wc) throws IOException {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-reload-running"), user, wc, false);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        return !response.getBoolean("in-progress");
    }

    private static void initializeRealm(CJPRule j){
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
    }

    private static User setSecurityRealmUser(CJPRule j, String username, Permission permission) throws IOException {
        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) j.jenkins.getSecurityRealm();
        User user = realm.createAccount(username, "password");
        j.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(permission, user.getId());
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
}
