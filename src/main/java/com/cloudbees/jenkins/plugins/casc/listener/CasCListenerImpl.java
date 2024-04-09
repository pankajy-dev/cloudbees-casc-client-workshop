package com.cloudbees.jenkins.plugins.casc.listener;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.replication.CasCListener;
import com.cloudbees.opscenter.client.casc.CheckNewBundleVersionException;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.util.logging.Level;
import java.util.logging.Logger;

@OptionalExtension(requirePlugins = "cloudbees-replication")
@SuppressWarnings("unused")
public class CasCListenerImpl implements CasCListener {
    private static final Logger LOGGER = Logger.getLogger(CasCListenerImpl.class.getName());

    @Override
    public void onBundlePromote() {
        ConfigurationUpdaterHelper.promoteCandidate();
    }

    @Override
    public void onNewVersionAvailable() {
        ConfigurationBundleManager.refreshUpdateLog();
        try {
            ConfigurationUpdaterHelper.checkForUpdates(true);
        } catch (CheckNewBundleVersionException e) {
            LOGGER.log(Level.WARNING, "Error checking the new bundle version.", e);
        }
    }

    @Override
    public void onRefreshUpdateLog() {
        ConfigurationBundleManager.refreshUpdateLog();
    }
}
