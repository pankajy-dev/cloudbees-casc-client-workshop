package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.cli.BundleUpdateLogCommand;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import net.sf.json.test.JSONAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.HttpResponse;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.mockito.Mockito.mockStatic;

public class BundleUpdateLogHttpEndpointTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    private User admin;
    private User user;
    private JenkinsRule.WebClient wc;


    @Before
    public void setUp() throws Exception {
        rule.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        rule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());

        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) rule.jenkins.getSecurityRealm();
        admin = realm.createAccount("admin", "password");
        rule.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) rule.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(CascPermission.CASC_ADMIN, admin.getId());
        authorizationStrategy.add(Jenkins.READ, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        admin.addProperty(new ApiTokenProperty());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_READ, user.getId());
        authorizationStrategy.add(Jenkins.READ, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        user.addProperty(new ApiTokenProperty());
        user.getProperty(ApiTokenProperty.class).changeApiToken();

        wc = rule.createWebClient();
    }

    @Test
    public void check_permissions() throws Exception {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), user, wc);
        assertThat("User user does not have permissions", resp.getStatusCode(), is(403));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), admin, wc);
        assertThat("User admin has permissions", resp.getStatusCode(), is(200));
    }

    @Test
    public void check_no_casc() throws Exception {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), admin, wc);
        assertThat("User admin has permissions", resp.getStatusCode(), is(200));
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("CasC disabled", response.getString("update-log-status"), is("CASC_DISABLED"));
    }

    @Test
    public void check_no_update_log() throws Exception {
        try (MockedStatic<BundleUpdateLog> bundleUpdateLogMockedStatic = mockStatic(BundleUpdateLog.class);
             MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true); // CasC enabled
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::retentionPolicy).thenReturn(0L); // Disable UpdateLog

            BundleReloadAction action = ExtensionList.lookupSingleton(BundleReloadAction.class);
            JSONObject response = JSONObject.fromObject(action.getBundleUpdateLog());
            assertThat("Update log disabled", response.getString("update-log-status"), is("DISABLED"));
        }
    }

    @Test
    @LocalData
    public void check_update_log() {
        try (MockedStatic<BundleUpdateLog> bundleUpdateLogMockedStatic = mockStatic(BundleUpdateLog.class);
             MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenCallRealMethod();
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true); // CasC enabled
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::retentionPolicy).thenReturn(10L); // UpdateLog enabled to 10 registries
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::getHistoricalRecordsFolder).thenCallRealMethod();

            BundleReloadAction action = ExtensionList.lookupSingleton(BundleReloadAction.class);
            JSONObject response = JSONObject.fromObject(action.getBundleUpdateLog());
            assertThat("Update log enabled", response.getString("update-log-status"), is("ENABLED"));
            assertThat("Retention policy", response.getLong("retention-policy"), is(10L));
            assertThat("There are 6 registries in the update log", response.getJSONArray("versions"), hasSize(6));
            JSONAssert.assertEquals("There are 6 registries in the update log",
                    "[\n" +
                            "    {\n" +
                            "        \"version\": \"6\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 0,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00006\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "        \"version\": \"5\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 1,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00005\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "        \"version\": \"4\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 0,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00004\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "        \"version\": \"3\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 1,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00003\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "        \"version\": \"2\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 0,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00002\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "        \"version\": \"1\",\n" +
                            "        \"date\": \"May 9, 2022, 12:00 AM UTC\",\n" +
                            "        \"errors\": 0,\n" +
                            "        \"warnings\": 0,\n" +
                            "        \"info-messages\": 0,\n" +
                            "        \"folder\": \"20220509_00001\"\n" +
                            "    }\n" +
                            "]"
                    , response.getJSONArray("versions"));
        }
    }

    private WebResponse requestWithToken(HttpMethod method, URL fullURL, User asUser, CJPRule.WebClient wc)
            throws IOException {

        try {
            WebRequest getRequest = new WebRequest(fullURL, method);
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        }
        catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }
}