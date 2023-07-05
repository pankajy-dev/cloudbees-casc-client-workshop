package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.opscenter.client.casc.CheckNewBundleVersionException;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

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
     * @throws Exception As described in CLICommand
     */
    @Override
    protected int run() throws Exception {
        // Dev memo: please keep the business logic in this class in line with com.cloudbees.opscenter.client.casc.BundleReloadAction.doGetBundleNewerVersion
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            boolean reload = false;
            // First, check if an update is available
            // Dev memo: this must go first because it will update the version of the bundle if needed
            boolean update = ConfigurationUpdaterHelper.checkForUpdates();
            if (!update) {
                // maybe the bundle is the same, but it is not yet applied, also check if an update is available
                update = ConfigurationStatus.INSTANCE.isUpdateAvailable();
            }

            if (update) {
                reload = ConfigurationBundleManager.get().getConfigurationBundle().isHotReloadable();
            }

            stdout.println(ConfigurationUpdaterHelper.getUpdateCheckJsonResponse(update, reload));
        } catch (CheckNewBundleVersionException e) {
            stderr.println("Error checking the new bundle version: " + e.getMessage());
            return 1;
        }
        return 0;
    }

}
