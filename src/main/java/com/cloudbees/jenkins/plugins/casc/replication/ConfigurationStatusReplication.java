package com.cloudbees.jenkins.plugins.casc.replication;

import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationStatusSingleton;
import hudson.Extension;

import java.lang.reflect.Proxy;

@Extension
public class ConfigurationStatusReplication extends ReplicationSetterProxy {
    private final ConfigurationStatus proxyInstance = (ConfigurationStatus) Proxy.newProxyInstance(
            ConfigurationStatusSingleton.class.getClassLoader(),
            new Class<?>[]{ConfigurationStatus.class},
            this
    );

    public ConfigurationStatusReplication() {
        super(ConfigurationStatusSingleton.INSTANCE, method -> method.getName().startsWith("set"));
    }

    public ConfigurationStatus getProxyInstance() {
        return proxyInstance;
    }

    @Override
    protected String getUUID() {
        return ConfigurationStatus.class.getName();
    }
}
