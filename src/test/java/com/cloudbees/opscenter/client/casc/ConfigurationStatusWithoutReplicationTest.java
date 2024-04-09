package com.cloudbees.opscenter.client.casc;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigurationStatusWithoutReplicationTest {

    @Test
    public void testConfigurationStatusNotProxied() throws Throwable {
        ConfigurationStatus configurationStatus = ConfigurationStatus.get();
        assertThat("ConfigurationStatus should no be proxied", configurationStatus, sameInstance(ConfigurationStatusSingleton.INSTANCE));
    }
}