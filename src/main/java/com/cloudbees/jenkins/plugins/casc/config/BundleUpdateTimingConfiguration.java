package com.cloudbees.jenkins.plugins.casc.config;

import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;

/**
 * {@link GlobalConfiguration} to wrap the class {@link BundleUpdateTimingManager} from cloudbees-installation-manager
 */
@Extension
@Symbol("bundleUpdateTiming")
public class BundleUpdateTimingConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(BundleUpdateTimingConfiguration.class.getName());

    private transient boolean automaticRestart;
    private transient boolean automaticReload;
    private transient boolean skipNewVersions;
    private transient boolean reloadAlwaysOnRestart;
    private transient boolean rejectWarnings;
    private transient BundleUpdateTimingManager bundleUpdateTimingManager;

    public BundleUpdateTimingConfiguration() {
        load();
    }

    public static BundleUpdateTimingConfiguration get() {
        return ExtensionList.lookupSingleton(BundleUpdateTimingConfiguration.class);
    }

    @Override
    public synchronized void load() {
        bundleUpdateTimingManager = BundleUpdateTimingManager.get();
        automaticRestart = bundleUpdateTimingManager.isAutomaticRestart();
        automaticReload = bundleUpdateTimingManager.isAutomaticReload();
        skipNewVersions = bundleUpdateTimingManager.isSkipNewVersions();
        reloadAlwaysOnRestart = bundleUpdateTimingManager.isReloadAlwaysOnRestart();
        rejectWarnings = bundleUpdateTimingManager.isRejectWarnings();
    }

    /**
     * Method to check if the Bundle Update Timing is enabled. See {@link BundleUpdateTimingManager#isEnabled()} for more information about how to enable/disable
     * Bundle Update Timing.
     * If it's disabled, the Global Configuration will do nothing
     *
     * @return true if Bundle Update Timing is enabled. false otherwise
     */
    public boolean isEnabled() {
        return BundleUpdateTimingManager.isEnabled();
    }

    /**
     * <code>restart.automatic</code> value. If true, the instance performs a Safe restart when the new bundle version is detected.
     * This property does not apply when the instance is starting
     * @return true if exists and true, false otherwise
     */
    public boolean isAutomaticRestart() {
        return automaticRestart;
    }

    /**
     * Set the value to configure for <code>restart.automatic</code>.
     * @param automaticRestart boolean with the value
     */
    @DataBoundSetter
    public void setAutomaticRestart(boolean automaticRestart) {
        this.automaticRestart = automaticRestart;
    }

    /**
     * <code>reload.automatic</code> value. If true, the new bundle version is automatically applied if the hot-reload of the bundle can be performed.
     * If the hot reload is not possible, or this property is not set to true, the user will have to reload the bundle manually. This property
     * does not apply when the instance is starting
     * @return true if exists and true, false otherwise
     */
    public boolean isAutomaticReload() {
        return automaticReload;
    }

    /**
     * Set the value to configure for <code>reload.automatic</code>.
     * @param automaticReload boolean with the value
     */
    @DataBoundSetter
    public void setAutomaticReload(boolean automaticReload) {
        this.automaticReload = automaticReload;
    }

    /**
     * <code>reload.skip</code> value. If true then the new bundle versions are marked as skipped, and they are not applied when they are detected.
     * If <code>restart.automatic</code> and/or <code>reload.automatic</code> are configured, this property is ignored
     * @return true if exists and true, false otherwise
     */
    public boolean isSkipNewVersions() {
        return skipNewVersions;
    }

    /**
     * Set the value to configure for <code>reload.skip</code>.
     * @param skipNewVersions boolean with the value
     */
    @DataBoundSetter
    public void setSkipNewVersions(boolean skipNewVersions) {
        this.skipNewVersions = skipNewVersions;
    }

    /**
     * <code>reload.always.onrestart</code> value. If true, when the skip new version is configured, this property is ignored and
     * new version of the bundle is always applied on restart, so the skip only applies when the instance is up and running.
     * This property only applies when the instance is starting
     * @return true if exists and true, false otherwise
     */
    public boolean isReloadAlwaysOnRestart() {
        return reloadAlwaysOnRestart;
    }

    /**
     * Set the value to configure for <code>reload.always.onrestart</code>.
     * @param reloadAlwaysOnRestart boolean with the value
     */
    @DataBoundSetter
    public void setReloadAlwaysOnRestart(boolean reloadAlwaysOnRestart) {
        this.reloadAlwaysOnRestart = reloadAlwaysOnRestart;
    }

    /**
     * <code>reject.warnings</code> value. If true, a bundle with validation warnings is rejected and not applied.
     * @return true if exists and true, false otherwise
     */
    public boolean isRejectWarnings() {
        return rejectWarnings;
    }

    /**
     * Set the value to configure for <code>reject.warnings</code>.
     * @param rejectWarnings boolean with the value
     */
    @DataBoundSetter
    public void setRejectWarnings(boolean rejectWarnings) {
        this.rejectWarnings = rejectWarnings;
    }

    @Override
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "Claiming the access to setter method is not synchronized when save is synchronized. False positive")
    public synchronized void save() {
        if (isEnabled()) {
            bundleUpdateTimingManager.setAutomaticRestart(automaticRestart);
            bundleUpdateTimingManager.setAutomaticReload(automaticReload);
            bundleUpdateTimingManager.setRejectWarnings(rejectWarnings);
            bundleUpdateTimingManager.setSkipNewVersions(skipNewVersions);
            bundleUpdateTimingManager.setReloadAlwaysOnRestart(reloadAlwaysOnRestart);
            bundleUpdateTimingManager.save(); // If outdated, it won't save and will log
            LOGGER.fine("Bundle Update Timing enabled. Saving");
        } else {
            LOGGER.fine("Saving when Bundle Update Timing is disabled. Ignoring request");
        }
        load(); // Make sure we keep the latest stored values
    }
}
