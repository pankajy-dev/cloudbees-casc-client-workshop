package com.cloudbees.opscenter.client.casc.cli;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import net.sf.json.JSONObject;

import hudson.cli.CLICommandInvoker;

import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.opscenter.client.casc.AbstractBundleVersionCheckerTest;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BundleReloadInProgressCommandTest extends AbstractBundleVersionCheckerTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Test
    @WithEnvelope(TestEnvelope.class)
    public void update_bundles() throws Exception {
        // Simulate bundle reload
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(true);
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleReloadInProgressCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("in-progress is true", JSONObject.fromObject(result.stdout()).getBoolean("reload-in-progress"), is(true));
        // Simulate reload completed
        ConfigurationStatus.INSTANCE.setCurrentlyReloading(false);
        result = new CLICommandInvoker(rule, BundleReloadInProgressCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("in-progress is true", JSONObject.fromObject(result.stdout()).getBoolean("reload-in-progress"), is(false));
    }



}
