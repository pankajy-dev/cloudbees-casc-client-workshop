package com.cloudbees.opscenter.client.casc.cli;

import java.io.IOException;

import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

@Extension
public class BundleVersionSkipCommand extends CLICommand {
    public final static String COMMAND_NAME = "casc-bundle-skip";

    @Override
    public String getShortDescription() { return "Skip the current available new bundle version.";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Check the bundle version and returns whether there's a newer version or not
     * @return 0 and a text indicating there's a new version of the bundle*
     * @throws Exception As described in CLICommand
     */
    /**
     * Skip the candidate bundle if there's an available version.
     * Permission required: CASC_ADMIN
     * @return 0 and a text indicating the bundle is or not skipped
     *         1 if there's an error skipping the candidate
     */

    @Override
    protected int run() throws Exception {
        // Dev memo: please keep the business logic in this class in line with com.cloudbees.opscenter.client.casc.BundleReloadAction.doSkipBundle
        Jenkins.get().checkPermission(CascPermission.CASC_ADMIN);
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        if (!configuration.isEnabled()) {
            stderr.println("This instance does not allow to skip bundles. Please, enable Bundle Update Timing by setting the System property -Dcore.casc.bundle.update.timing.enabled=true");
            return 0;
        }

        BundleVisualizationLink updateTab = BundleVisualizationLink.get();
        if (!updateTab.isUpdateAvailable()) {
            stderr.println("Bundle version to skip not found.");
            return 0;
        }

        if (!updateTab.canManualSkip()) {
            // Just in case a Safe restart is scheduled and meanwhile the user try to skip it
            stderr.println("This instance does not allow to skip bundles. There is an automatic reload or restart that makes the skip operation ignored.");
            return 0;
        }

        try {
            if (ConfigurationUpdaterHelper.doSkipCandidate()) {
                stdout.println("Bundle version skipped.");
            } else {
                stderr.println("Bundle version couldn't be skipped. Please check logs in the instance.");
            }
            return 0;
        } catch (IOException e) {
            stderr.println("Error skipping the candidate bundle");
            e.printStackTrace(stderr);
            return 1;
        }
    }

}
