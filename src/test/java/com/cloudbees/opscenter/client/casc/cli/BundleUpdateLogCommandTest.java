package com.cloudbees.opscenter.client.casc.cli;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.mockito.MockedStatic;
import net.sf.json.JSONObject;
import net.sf.json.test.JSONAssert;

import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.Mockito.mockStatic;

public class BundleUpdateLogCommandTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    private User admin;
    private User cascAdmin;
    private User user;

    @Before
    public void setUp() throws Exception {
        rule.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        rule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());

        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) rule.jenkins.getSecurityRealm();
        admin = realm.createAccount("admin", "password");
        rule.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) rule.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.ADMINISTER, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        cascAdmin = realm.createAccount("cascAdmin", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_ADMIN, cascAdmin.getId());
        authorizationStrategy.add(Jenkins.READ, cascAdmin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_READ, user.getId());
        authorizationStrategy.add(Jenkins.READ, user.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void check_permissions() {
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(user.getId()).invoke();
        assertThat("User user does not have permissions", result.stderr(), containsString("ERROR: user is missing the CloudBees CasC Permissions/Administer permission"));
        assertThat("User user does not have permissions", result.returnCode(), is(6));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(cascAdmin.getId()).invoke();
        assertThat("User cascAdmin has permissions", result.returnCode(), is(0));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("User admin has permissions", result.returnCode(), is(0));
    }

    @Test
    public void check_no_casc() {
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(cascAdmin.getId()).invoke();
        assertThat("User cascAdmin has permissions", result.returnCode(), is(0));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("User admin has permissions", result.returnCode(), is(0));
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertThat("CasC disabled", response.getString("update-log-status"), is("CASC_DISABLED"));
    }

    @Test
    public void check_no_update_log() {
        try (MockedStatic<BundleUpdateLog> bundleUpdateLogMockedStatic = mockStatic(BundleUpdateLog.class);
             MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true); // CasC enabled
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::retentionPolicy).thenReturn(0L); // Disable UpdateLog

            CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            assertThat("User admin has permissions", result.returnCode(), is(0));
            JSONObject response = JSONObject.fromObject(result.stdout());
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

            CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            assertThat("User admin has permissions", result.returnCode(), is(0));
            JSONObject response = JSONObject.fromObject(result.stdout());
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

}