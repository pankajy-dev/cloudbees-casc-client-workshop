package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.opscenter.client.casc.AbstractBundleVersionCheckerTest;
import hudson.cli.CLICommandInvoker;
import net.sf.json.JSONObject;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;

import java.nio.file.Paths;

import static com.cloudbees.opscenter.client.casc.CasCMatchers.hasInfoMessage;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

public class BundleVersionCheckerCommandQuietTest extends AbstractBundleVersionCheckerTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-5.zip")
    public void testQuietMode() {
        CLICommandInvoker.Result result;
        JSONObject jsonResult;

        // Updated to version 6 - Invalid
        String version6 = Paths
                .get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-6.zip")
                .toFile()
                .getAbsolutePath();
        System.setProperty("core.casc.config.bundle", version6);
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());

        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );
        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );

        // Updated to version 6 - Invalid - not quiet
        System.setProperty("core.casc.config.bundle", version6);
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).withArgs("--quiet", "false").invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());

        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );
        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );

        // Updated to version 6 - Invalid - quiet
        System.setProperty("core.casc.config.bundle", version6);
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).withArgs("--quiet", "true").invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());

        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   not(hasInfoMessage())
        );
        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   not(hasInfoMessage())
        );
    }
}