package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

@Extension
public class BundleUpdateLogCommand extends CLICommand {
    public final static String COMMAND_NAME = "casc-bundle-update-log";

    @Override
    public String getShortDescription() { return "Gets the update log of the installed bundle.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Return information about the update log
     * @return 0 and a JSON object with the update log
     * @throws Exception As described in CLICommand
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(CascPermission.CASC_ADMIN); // Same as in UI
        try {
            stdout.println(ConfigurationUpdaterHelper.getUpdateLog());
        } catch (Exception e) {
            stderr.println("Error reading the Bundle update log: " + e.getMessage());
            return 1;
        }
        return 0;
    }

}
