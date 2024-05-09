package com.cloudbees.opscenter.client.casc.cli;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import net.sf.json.JSONObject;

import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;

import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.opscenter.client.casc.AbstractBundleVersionCheckerTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class CheckReloadDeletionsItemsTest extends AbstractBundleVersionCheckerTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/sync")
    public void checkDeletionsTest() throws IOException {
        // No changes in the instance, we expect no deletions
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, CheckReloadItemsCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        JSONObject jsonResult = JSONObject.fromObject(result.stdout());
        assertThat("Command should end correctly", result.returnCode(), is(0));
        assertThat("We should get an empty list", jsonResult.getJSONObject("items").getJSONArray("deletions"), empty());

        // Creating 2 items, as strategy is SYNC they should be deleted when the bundle is applied
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted");
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted-too");
        result = new CLICommandInvoker(rule, CheckReloadItemsCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        jsonResult = JSONObject.fromObject(result.stdout());
        assertThat("Command should end correctly", result.returnCode(), is(0));
        assertThat("We should get a list with 2 items", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(2));
        assertThat("Created items should be in deletions list", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too"));
        assertThat("The instance still has 4 items", rule.jenkins.getAllItems(), hasSize(4));

        // Using an invalid remove strategy should return an error
        ConfigurationBundleManager.get().getConfigurationBundle().getItemRemoveStrategy().setItems("invalid");
        result = new CLICommandInvoker(rule, CheckReloadItemsCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("Command should end with IllegalArgumentException error code", result.returnCode(), is(3));
        assertThat("Error response should contain the reason", result.stderr(), containsString("Unknown items removeStrategy"));
    }

    @Test
    @Issue("BEE-44300")
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/none")
    public void doCheckReloadDeletionsShouldUseCandidateBundleTest() throws IOException {
        // No changes in the instance, we expect no deletions
        // Bundle "none" contains 3 folders and the sync strategy for the items is "none".
        assertThat("Bundle 'none' contains 3 folders", rule.jenkins.getAllItems(), hasSize(3));

        // Creating 2 items, as strategy is NONE they should be no deletions
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted");
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted-too");
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, CheckReloadItemsCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        JSONObject jsonResult = JSONObject.fromObject(result.stdout());
        assertThat("Response code should be a 200", result.returnCode(), is(0));
        assertThat("deletions should be an empty list", jsonResult.getJSONObject("items").getJSONArray("deletions"), empty());

        // Bundle "sync" contains only 2 folders, and the sync strategy for the items is "sync".
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/sync").toFile().getAbsolutePath());
        new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();

        result = new CLICommandInvoker(rule, CheckReloadItemsCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        jsonResult = JSONObject.fromObject(result.stdout());
        assertThat("Response code should be a 200", result.returnCode(), is(0));
        assertThat("deletions should contain 3 element", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(3));
        assertThat("to-be-deleted should be in the response", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too", "folder-to-be-deleted"));
        assertThat("The instance still has 5 items", rule.jenkins.getAllItems(), hasSize(5));
    }
}
