package com.cloudbees.opscenter.client.casc.cli;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.cli.CLICommand;

import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.HotReloadAction;

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
        if (ConfigurationUpdaterHelper.checkForUpdates()){
            stdout.println("A new version of the configuration bundle is available.");
        } else {
            stdout.println("There are no configuration bundle updates available.");
        }
        return 0;
    }
}
