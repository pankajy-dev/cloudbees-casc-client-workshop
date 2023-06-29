package com.cloudbees.opscenter.client.casc.config;

import java.io.IOException;
import java.util.logging.Level;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.GlobalConfiguration;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;

@Extension
@Symbol("bundleValVisualization")
public class BundleValidationVisualizationConfiguration extends GlobalConfiguration {

    /**
     * See BEE-35011 for details
     * <p>
     * Define "quiet mode".
     * <p>
     * When using it, the INFO validation messages are not <em>displayed</em> (validation and storage are performed
     * normally).
     * <p>
     * As specified in BEE-35011, the default mode is "verbose"
     */
    private boolean quiet = false;

    public ValidationConfiguration() {
        load();
    }

    public static ValidationConfiguration get() {
        return ExtensionList.lookupSingleton(ValidationConfiguration.class);
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED, before = InitMilestone.JOB_LOADED)
    public void init() throws IOException {
        updateConfigurationBundleManager();
    }

    private void updateConfigurationBundleManager() {
        ConfigurationBundleManager.get().setQuiet(this.quiet);
    }

    public boolean isQuiet() {
        return quiet;
    }

    @DataBoundSetter
    public void setQuiet(boolean newValue) {
        this.quiet = newValue;
        save();
        updateConfigurationBundleManager();
    }
}
