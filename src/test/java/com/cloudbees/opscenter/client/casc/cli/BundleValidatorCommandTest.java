package com.cloudbees.opscenter.client.casc.cli;

import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;

import static com.cloudbees.opscenter.client.casc.CasCMatchers.hasInfoMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleValidatorCommandTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

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
        assertThat("User user does not have permissions", result.stderr(), containsString("ERROR: user is missing the CloudBees CasC Permissions/Administer permission"));

        // With permissions
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/valid-bundle.zip")))
                .asUser(cascAdmin.getId()).invokeWithArgs("-c", "COMMIT_HASH");
        assertThat("User cascAdmin should have permissions", result.returnCode(), is(0));

        // Validating the content
        // Valid without warnings
        logger.record(ConfigurationUpdaterHelper.class, Level.INFO).capture(5);
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/valid-bundle.zip")))
                .asUser(admin.getId()).invokeWithArgs("-c", "COMMIT_HASH");
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertTrue("valid-bundle.zip should be valid", response.getBoolean("valid"));
        assertTrue("valid-bundle.zip should not have validation messages", response.getJSONArray("validation-messages").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()).isEmpty());
        assertThat("Logs should contain the commit", logger.getMessages().contains("Validating bundles associated with commit COMMIT_HASH"));
        assertTrue("Validation results include commit", response.containsKey("commit"));
        assertThat("Validation results include indicated commit", response.getString("commit"), is("COMMIT_HASH"));

        // Valid but with warnings
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/only-with-warnings.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()),
                   hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations.")));
        assertFalse("Validation results don't include commit", response.containsKey("commit"));

        // Valid but with warnings - not quiet mode
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/only-with-warnings.zip")))
                .asUser(admin.getId())
                .withArgs("--quiet", "false")
                .invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertThat("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages"), hasInfoMessage());

        // Valid but with warnings - quiet mode
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/only-with-warnings.zip")))
                .asUser(admin.getId())
                .withArgs("--quiet", "true")
                .invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertThat("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages"), not(hasInfoMessage()));

        // No valid
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/invalid-bundle.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("invalid-bundle.zip should have validation messages", response.getJSONArray("validation-messages").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()),
            allOf(
                hasItem(containsString("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.")),
                hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations."))
            ));

        // Not a zip file
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/bundle.yaml")))
                .asUser(admin.getId()).invoke();
        assertThat("bundle.yaml is not a zip", result.returnCode(), is(3));
        assertThat("bundle.yaml is not a zip", result.stderr(), containsString("ERROR: Invalid zip file"));

        // Without descriptor
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/without-descriptor.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("without-descriptor.zip should not have descriptor", result.returnCode(), is(3));
        assertThat("without-descriptor.zip should not have descriptor", result.stderr(), containsString("ERROR: Invalid bundle - Missing descriptor"));
    }
}