package com.cloudbees.opscenter.client.casc;

import hudson.Extension;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic task to check if there is a new version of the configuration bundle available.
 *
 * It can be disabled setting the com.cloudbees.opscenter.client.casc.ConfigurationUpdaterTask.disable system property.
 * A restart on changes can be done automatically if the system property
 * com.cloudbees.opscenter.client.casc.ConfigurationUpdaterTask.autorestart is enabled.
 * The recurrence period (20 minutes by default) can be configured with the system property
 * com.cloudbees.opscenter.client.casc.ConfigurationUpdaterTask.recurrencePeriod in minutes,
 */
@Extension
public class ConfigurationUpdaterTask extends PeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(ConfigurationUpdaterTask.class.getName());

    private static String RECURRENCE_PERIOD = ConfigurationUpdaterTask.class.getName() + ".recurrencePeriod";
    private static long DEFAULT_RECURRENCE_PERIOD_VALUE = 20;

    private static String AUTORESTART = ConfigurationUpdaterTask.class.getName() + ".autorestart";
    private static String DISABLE = ConfigurationUpdaterTask.class.getName() + ".disable";


    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(Math.max(Long.getLong(RECURRENCE_PERIOD, DEFAULT_RECURRENCE_PERIOD_VALUE), 1L));
    }

    private boolean shouldRestart() {
        return Boolean.getBoolean(AUTORESTART);
    }

    private boolean isDisable() {
        return Boolean.getBoolean(DISABLE);
    }

    @Override
    protected void doRun() throws Exception {
        if (!isDisable()) {
            if (ConfigurationUpdaterHelper.checkForUpdates() && shouldRestart()) {
                LOGGER.log(Level.INFO, "Restarting the instance because of a new Configuration Bundle and the system propery {0}.", AUTORESTART);
                Jenkins.get().safeRestart();
            }
        } else {
            LOGGER.log(Level.FINEST, "The execution of the periodic task is already disabled because the system propery {0}.", DISABLE);
        }
    }
}
