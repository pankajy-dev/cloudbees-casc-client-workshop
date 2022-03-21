package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Issue("BEE-15886")
public class BundleVisualizationLinkTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private User adminUser;
    private User readUser;

    @Before
    public void setUp() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);

        adminUser = realm.createAccount("alice", "alice");
        readUser = realm.createAccount("bob", "bob");

        ProjectMatrixAuthorizationStrategy authorizationStrategy = new ProjectMatrixAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.READ, readUser.getId());
        authorizationStrategy.add(Jenkins.ADMINISTER, adminUser.getId());
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void checkPermissions() throws Exception {
        try (ACLContext ctx = ACL.as(adminUser)) {
            ExtensionList.lookupSingleton(BundleVisualizationLink.class).doBundleUpdate();
        }

        try (ACLContext ctx = ACL.as(readUser)) {
            AccessDeniedException3 exception = assertThrows(AccessDeniedException3.class, () -> ExtensionList.lookupSingleton(BundleVisualizationLink.class).doBundleUpdate());
            assertThat(exception.getMessage(), containsString("bob is missing the Overall/Administer permission"));
        }
    }

    @Test
    public void checkUpdate() throws Exception {
        try (ACLContext ctx = ACL.as(adminUser); MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true);
            ConfigurationBundleManager mockedConfManager = mock(ConfigurationBundleManager.class);
            when(mockedConfManager.downloadIfNewVersionIsAvailable()).thenReturn(true);
            ConfigurationBundle mockedBundle = mock(ConfigurationBundle.class);
            when(mockedBundle.getVersion()).thenReturn("2");
            when(mockedBundle.getPlugins()).thenReturn(Collections.emptySet());
            when(mockedConfManager.getConfigurationBundle()).thenReturn(mockedBundle);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenReturn(mockedConfManager);

            ExtensionList.lookupSingleton(BundleVisualizationLink.class).doBundleUpdate();
            assertTrue(ConfigurationStatus.INSTANCE.isUpdateAvailable());
        }
    }

    @Test
    public void checkUpdate_downloadError() throws Exception {
        try (ACLContext ctx = ACL.as(adminUser); MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true);
            ConfigurationBundleManager mockedConfManager = mock(ConfigurationBundleManager.class);
            when(mockedConfManager.downloadIfNewVersionIsAvailable()).thenThrow(new RuntimeException(new IOException("Error response from bundle server: url=http://192.168.1.42:7080/zip-bundle/d2222ea38e7b9b9d509468eec1511b36/my-controller2, status=404")));
            ConfigurationBundle mockedBundle = mock(ConfigurationBundle.class);
            when(mockedBundle.getVersion()).thenReturn("1");
            when(mockedConfManager.getConfigurationBundle()).thenReturn(mockedBundle);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenReturn(mockedConfManager);

            ExtensionList.lookupSingleton(BundleVisualizationLink.class).doBundleUpdate();
            assertFalse(ConfigurationStatus.INSTANCE.isUpdateAvailable());
        }
    }
}