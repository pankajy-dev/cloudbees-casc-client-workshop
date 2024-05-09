package com.cloudbees.opscenter.client.casc.cli;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Same as {@link BundleValidatorCommandTest} but without {@link Jenkins#SYSTEM_READ} enabled
 * Just checking permissions. Content in the other test
 */
@Issue("BEE-49270")
public class BundleValidatorCommandWithoutSystemReadTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule();

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
    public void smokes() throws Exception {
        // Without permissions
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/valid-bundle.zip")))
                .asUser(user.getId()).invoke();
        assertThat("User user does not have permissions", result.returnCode(), is(6));
        assertThat("User user does not have permissions", result.stderr(), containsString("ERROR: user is missing the Overall/Administer permission"));

        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/valid-bundle.zip")))
                .asUser(cascAdmin.getId()).invokeWithArgs("-c", "COMMIT_HASH");
        assertThat("User cascAdmin does not have permissions", result.returnCode(), is(6));

        // With permissions
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/valid-bundle.zip")))
                .asUser(admin.getId()).invokeWithArgs("-c", "COMMIT_HASH");
        assertThat("User admin should have permissions", result.returnCode(), is(0));
    }
}