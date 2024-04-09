package com.cloudbees.opscenter.client.casc.cli;

import org.junit.Test;
import net.sf.json.JSONObject;

import hudson.cli.CLICommandInvoker;

import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.opscenter.client.casc.AbstractBundleVersionCheckerTest;
import com.cloudbees.opscenter.client.casc.ConfigurationStatusSingleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BundleReloadInProgressCommandTest extends AbstractBundleVersionCheckerTest {

    @Test
    @WithEnvelope(TestEnvelope.class)
    public void update_bundles() throws Exception {
        // Simulate bundle reload
        ConfigurationStatusSingleton.INSTANCE.setCurrentlyReloading(true);
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleReloadInProgressCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("in-progress is true", JSONObject.fromObject(result.stdout()).getBoolean("reload-in-progress"), is(true));
        // Simulate reload completed
        ConfigurationStatusSingleton.INSTANCE.setCurrentlyReloading(false);
        result = new CLICommandInvoker(rule, BundleReloadInProgressCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
        assertThat("in-progress is true", JSONObject.fromObject(result.stdout()).getBoolean("reload-in-progress"), is(false));
    }



}
