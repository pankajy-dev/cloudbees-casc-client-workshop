package com.cloudbees.opscenter.client.casc.cli;

import java.util.Collections;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.cli.CLICommand;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.opscenter.client.casc.ConfigurationBundleService;

@Extension
public class CheckReloadDeletionsCommand extends CLICommand {
    public final static String COMMAND_NAME = "casc-bundle-check-reload-deletions";

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
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        try {
            ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
            JSONArray response = new JSONArray();
            response.addAll(bundle.getItems() == null ? Collections.EMPTY_LIST : service.getDeletionsOnReload(bundle)); // Not needed after cloudbees-casc-items-api:2.25
            stdout.println(new JSONObject().accumulate("deletions", response));
            return 0;
        } catch (CasCException ex){
            stderr.println("Error while checking deletions, invalid remove strategy provided");
            throw new IllegalArgumentException(ex);
        }
    }
}
