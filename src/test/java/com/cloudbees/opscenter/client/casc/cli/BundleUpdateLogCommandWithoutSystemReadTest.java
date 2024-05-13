package com.cloudbees.opscenter.client.casc.cli;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
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

/**
 * Same as {@link BundleUpdateLogCommandTest} but without {@link Jenkins#SYSTEM_READ} enabled
 */
@Issue("BEE-49270")
public class BundleUpdateLogCommandWithoutSystemReadTest {

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
        assertThat("User user does not have permissions", result.stderr(), containsString("ERROR: user is missing the Overall/Administer permission"));
        assertThat("User user does not have permissions", result.returnCode(), is(6));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(cascAdmin.getId()).invoke();
        assertThat("User cascAdmin does not have permissions", result.returnCode(), is(6));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("User admin has permissions", result.returnCode(), is(0));
    }

    @Test
    public void check_no_casc() {
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(cascAdmin.getId()).invoke();
        assertThat("User cascAdmin does not have permissions", result.returnCode(), is(6));
        result = new CLICommandInvoker(rule, BundleUpdateLogCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("User admin has permissions", result.returnCode(), is(0));
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertThat("CasC disabled", response.getString("update-log-status"), is("CASC_DISABLED"));
    }
}