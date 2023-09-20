package com.cloudbees.opscenter.client.casc.cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.json.JsonHttpResponse;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.cli.CLICommand;

import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog.BundleUpdateLogAction;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.opscenter.client.casc.BundleReloadAction;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;

@Extension
public class BundleReloadCommand extends CLICommand {

    public final static String COMMAND_NAME = "casc-bundle-reload-bundle";

    @Option(name="-a", aliases = {"--asynchronous-run"}, usage="Executes reload asynchronously, returning immediately")
    private boolean async = false;

    @Override
    public String getShortDescription() { return "Reloads the CasC bundle.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Tries to reload the CasC bundle
     * User needs ADMINISTER role to run this
     * @return 0 Everything went ok, bundle is reloaded
     *         1 Bundle could not be reloaded (normally, because it's not hot reloadable)
     *         2 Some exception happened that didn't allow the bundle reload
     * @throws Exception IllegalStateException can be thrown if HotReloadAction is not found
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        if (configuration.isEnabled()) {
            if (configuration.isAutomaticReload()) {
                stderr.println("Automatic reload configured. It's not possible to manually reload the bundle. If there is any issue, proceed with a restart");
                return 0;
            } else {
                if (!ConfigurationUpdaterHelper.promoteCandidate()) {
                    stderr.println("Bundle could not be promoted. Proceed with a restart");
                    return 0;
                }
            }
        }
        BundleReloadAction action = ExtensionList.lookupSingleton(BundleReloadAction.class);
        BundleUpdateLog.BundleUpdateStatus.setCurrentAction(BundleUpdateLogAction.RELOAD,
                                                            BundleUpdateLog.BundleUpdateLogActionSource.CLI);
        JSONObject json = action.executeReload(async);

        int retValue = 0;
        if (json.opt("cause") == null){
            if (json.getBoolean("reloaded")){
                stdout.println("Bundle successfully reloaded.");
            } else {
                stdout.println(String.format("Bundle could not be reloaded: %s", json.getString("reason")));
                retValue = 0;
            }
        } else {
            stderr.println(json.getString("message"));
            retValue = 0;
        }
        return retValue;
    }
}
