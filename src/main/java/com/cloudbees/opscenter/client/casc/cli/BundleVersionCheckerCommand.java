package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.opscenter.client.casc.CheckNewBundleVersionException;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.cli.CLICommand;

import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.HotReloadAction;
import net.sf.json.JSONObject;

@Extension
public class BundleVersionCheckerCommand extends CLICommand {
    public final static String COMMAND_NAME = "casc-bundle-check-bundle-update";

    @Override
    public String getShortDescription() { return "Checks if a new version of the configuration bundle is available.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Check the bundle version and returns whether there's a newer version or not
     * @return 0 and a text indicating there's a new version of the bundle*
     * @throws Exception
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            boolean reload = false;
            boolean update;
            if (ConfigurationStatus.INSTANCE.isUpdateAvailable()) {
                update = true;
            } else {
                update = ConfigurationUpdaterHelper.checkForUpdates();
            }
            if (update) {
                reload = ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
            }

            stdout.println(createJson(update, reload));
        } catch (CheckNewBundleVersionException e) {
            stderr.println("Error checking the new bundle version: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private JSONObject createJson(boolean update, boolean reload) {
        JSONObject json = new JSONObject();
        json.accumulate("update-available", update);

        if (update) {
            if (reload) {
                json.accumulate("update-type", "RELOAD");
            } else {
                json.accumulate("update-type", "RESTART");
            }
        }

        return json;
    }

}
