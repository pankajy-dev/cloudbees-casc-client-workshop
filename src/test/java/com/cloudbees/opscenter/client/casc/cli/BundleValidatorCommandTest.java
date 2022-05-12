package com.cloudbees.opscenter.client.casc.cli;

import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleValidatorCommandTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private User admin;
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
        admin.addProperty(new ApiTokenProperty());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(Jenkins.READ, user.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        user.addProperty(new ApiTokenProperty());
        user.getProperty(ApiTokenProperty.class).changeApiToken();

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/cli/BundleValidatorCommandTest/").toFile(), tmp.getRoot());
    }

    @Test
    public void smokes() throws Exception {
        // Without permissions
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("valid-bundle.zip")))
                .asUser(user.getId()).invoke();
        assertThat("User user does not have permissions", result.returnCode(), is(6));
        assertThat("User user does not have permissions", result.stderr(), containsString("ERROR: user is missing the Overall/Administer permission"));

        // Valid without warnings
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("valid-bundle.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertTrue("valid-bundle.zip should be valid", response.getBoolean("valid"));
        assertFalse("valid-bundle.zip should not have validation messages", response.containsKey("validation-messages"));

        // Valid but with warnings
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("only-with-warnings.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages"), contains("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"));

        // No valid
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("invalid-bundle.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("User admin should have permissions", result.returnCode(), is(0));
        response = JSONObject.fromObject(result.stdout());
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("invalid-bundle.zip should have validation messages", response.getJSONArray("validation-messages"),
                containsInAnyOrder(
                        "ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.",
                        "WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"
                ));

        // Not a zip file
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("bundle.yaml")))
                .asUser(admin.getId()).invoke();
        assertThat("bundle.yaml is not a zip", result.returnCode(), is(3));
        assertThat("bundle.yaml is not a zip", result.stderr(), containsString("ERROR: Invalid zip file"));

        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("folder-bundle")))
                .asUser(admin.getId()).invoke();
        assertThat("bundle.yaml is not a zip", result.returnCode(), is(3));
        assertThat("bundle.yaml is not a zip", result.stderr(), containsString("ERROR: Invalid zip file"));

        // Without descriptor
        result = new CLICommandInvoker(rule, BundleValidatorCommand.COMMAND_NAME)
                .withStdin(Files.newInputStream(tmp.getRoot().toPath().resolve("without-descriptor.zip")))
                .asUser(admin.getId()).invoke();
        assertThat("bundle.yaml is not a zip", result.returnCode(), is(3));
        assertThat("bundle.yaml is not a zip", result.stderr(), containsString("ERROR: Invalid bundle - Missing descriptor"));
    }
}