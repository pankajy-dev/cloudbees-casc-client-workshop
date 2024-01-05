package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;

import hudson.Extension;
import hudson.cli.CLICommand;

import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.CasCException;

@Extension
public class CheckReloadItemsCommand extends CLICommand {
    public final static String COMMAND_NAME = "casc-bundle-check-reload-items";

    @Override
    public String getShortDescription() { return "Checks what items would be deleted if reloading the current bundle.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Check what items would be deleted if the bundle is applied
     * @return 0 and the list of items that will be removed
     * @throws IllegalArgumentException In case of providing a bundle with an invalid remove strategy
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        try {
            stdout.println(ConfigurationUpdaterHelper.getUpdateCheckReloadItemsDeletionJsonResponse());
            return 0;
        } catch (CasCException ex){
            stderr.println("Error while checking deletions, invalid remove strategy provided");
            throw new IllegalArgumentException(ex);
        }
    }
}
