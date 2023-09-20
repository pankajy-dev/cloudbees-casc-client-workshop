package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.opscenter.client.casc.AbstractBundleVersionCheckerTest;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundleVersionCheckerCommandTest extends AbstractBundleVersionCheckerTest {
    private static void verifyCurrentUpdateStatus(
            String message,
            BundleUpdateLog.BundleUpdateLogAction action,
            BundleUpdateLog.BundleUpdateLogActionSource source,
            String fromBundle,
            String toBundle
    ) {
        BundleUpdateLog.BundleUpdateStatus current = BundleUpdateLog.BundleUpdateStatus.getCurrent();
        assertThat("BundleUpdateStatus should exists", current, notNullValue());
        assertThat(message, current.getAction(), is(action));
        assertThat(message, current.getSource(), is(source));
        assertThat("From bundle " + fromBundle, current.getFromBundleVersion(), is(fromBundle));
        assertThat("To bundle " + toBundle, current.getToBundleVersion(), is(toBundle));
        assertTrue("Action should be a success", current.isSuccess());
        assertNull("Action should be a success", current.getError());
        assertThat("Skipped should be false", current.isSkipped(), is(false));
        assertFalse("Action is finished", current.isOngoingAction());
    }

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-1.zip")
    public void update_bundles() throws Exception {
        // Instance started up with version 1 - Valid
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        JSONObject jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-1.zip", false);
        assertVersions(jsonResult, "version-1.zip", "1", empty(), null, null, true);
        assertUpdateType(jsonResult, "version-1.zip", null);

        // Updated to version 2 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-2.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-2.zip", true);
        assertVersions(jsonResult, "version-2.zip", "1", empty(), "2", empty(), true);
        assertUpdateType(jsonResult, "version-2.zip", "RELOAD/RESTART/SKIP");
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.CLI, "1", "2"
        );

        // Updated to version 3 - Without version - Ignored
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-3.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-3.zip", false);
        assertVersions(jsonResult, "version-3.zip", "2", empty(), null, null, true);
        assertUpdateType(jsonResult, "version-3.zip", null);

        // Updated to version 4 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-4.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-4.zip", true);
        assertVersions(jsonResult, "version-4.zip", "2", empty(), "4", contains("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer."), false);
        assertUpdateType(jsonResult, "version-4.zip", null);

        verifyCurrentUpdateStatus(
                "Not reloaded, invalid",
                BundleUpdateLog.BundleUpdateLogAction.CREATE,
                BundleUpdateLog.BundleUpdateLogActionSource.INIT, "2", "4"
        );

        // Updated to version 5 - Valid but with warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-5.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-5.zip", true);
        assertVersions(jsonResult, "version-5.zip", "2", empty(), "5", contains(containsString("[CATALOGVAL] - More than one plugin catalog file used")), true);
        assertUpdateType(jsonResult, "version-5.zip", "RELOAD/RESTART/SKIP");
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.CLI, "2", "5"
        );

        // Updated to version 6 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-6.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-6.zip", true);
        assertVersions(jsonResult, "version-6.zip", "5", contains(containsString("[CATALOGVAL] - More than one plugin catalog file used")), "6", contains("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer."), false);
        assertUpdateType(jsonResult, "version-6.zip", null);

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.CREATE,
                BundleUpdateLog.BundleUpdateLogActionSource.INIT, "5", "6"
        );

        // Updated to version 7 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-7.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-7.zip", true);
        assertVersions(jsonResult, "version-7.zip", "5", contains(containsString("[CATALOGVAL] - More than one plugin catalog file used")), "7", empty(), true);
        assertUpdateType(jsonResult, "version-7.zip", "RELOAD/RESTART/SKIP");
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.CLI, "5", "7"
        );

        // Updated to version 8 - Valid structure / Invalid jenkins.yaml only warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-8.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-8.zip", true);
        assertVersions(jsonResult, "version-8.zip", "7", empty(), "8", contains("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"), true);
        assertUpdateType(jsonResult, "version-8.zip", "RELOAD/RESTART/SKIP");
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.CLI, "7", "8"
        );

        // Updated to version 9 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-9.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-9.zip", true);
        assertVersions(jsonResult, "version-9.zip", "8", contains("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"), "9", empty(), true);
        assertUpdateType(jsonResult, "version-9.zip", "RELOAD/RESTART/SKIP");
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.CLI, "8", "9"
        );

        // Updated to version 10 - Valid structure / Invalid jenkins.yaml
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-10.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-10.zip", true);
        assertVersions(jsonResult, "version-10.zip", "9", empty(), "10", contains("ERROR - [JCASC] - The bundle.yaml file references jcasc/jenkins.yaml in the Jenkins Configuration as Code section that is empty or has an invalid yaml format. Impossible to validate Jenkins Configuration as Code."), false);
        assertUpdateType(jsonResult, "version-10.zip", null);

        verifyCurrentUpdateStatus(
                "Reloaded using client",
                BundleUpdateLog.BundleUpdateLogAction.CREATE,
                BundleUpdateLog.BundleUpdateLogActionSource.INIT, "9", "10"
        );

        // Updated to version 11 - Valid
        // Also, creating some items not present in v11 to check they are informed as to be deleted
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted");
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted-too");
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-11.zip").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
        jsonResult = JSONObject.fromObject(result.stdout());
        assertUpdateAvailable(jsonResult, "version-11.zip", true);
        assertVersions(jsonResult, "version-11.zip", "9", empty(), "11", empty(), true);
        assertUpdateType(jsonResult, "version-11.zip", "RELOAD/RESTART/SKIP");
        assertThat("We should get a list with 2 items", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(2));
        assertThat("Created items should be in deletions list", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too"));
    }

}