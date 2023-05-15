package com.cloudbees.opscenter.client.casc.cli;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

import com.cloudbees.opscenter.client.casc.BundleReloadAction;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;

@Extension
public class BundleReloadInProgressCommand extends CLICommand {

    public final static String COMMAND_NAME = "casc-bundle-reload-running";

    @Override
    public String getShortDescription() { return "Checks if the CasC bundle is currently reloading.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Checks if bundle reload is currently running
     * User needs READ role to run this
     * @return 0 and prints a json {"in-progress": true | false}
     * @throws Exception As described in CLICommand
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        stdout.println(new JSONObject().accumulate("reload-in-progress", ConfigurationStatus.INSTANCE.isCurrentlyReloading()));
        return 0;
    }
}
