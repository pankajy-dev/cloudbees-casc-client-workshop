package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.casc.validation.AbstractValidator;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
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
        try (ACLContext ctx = ACL.as(adminUser); MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class);
             MockedStatic<AbstractValidator> abstractValidatorMockedStatic = mockStatic(AbstractValidator.class)) {
            abstractValidatorMockedStatic.when(AbstractValidator::validateCandidateBundle).thenAnswer(invocationOnMock -> null);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true);
            ConfigurationBundleManager mockedConfManager = mock(ConfigurationBundleManager.class);
            when(mockedConfManager.downloadIfNewVersionIsAvailable()).thenReturn(true);
            ConfigurationBundle mockedBundle = mock(ConfigurationBundle.class);
            when(mockedBundle.getVersion()).thenReturn("2");
            when(mockedBundle.getPlugins()).thenReturn(Collections.emptySet());
            when(mockedConfManager.getConfigurationBundle()).thenReturn(mockedBundle);
            BundleUpdateLog mockedUpdateLog = mock(BundleUpdateLog.class);
            BundleUpdateLog.CandidateBundle mockedCandidate = mock(BundleUpdateLog.CandidateBundle.class);
            when(mockedCandidate.getVersion()).thenReturn("3");
            BundleUpdateLog.BundleValidationYaml mockedValidations = mock(BundleUpdateLog.BundleValidationYaml.class);
            when(mockedValidations.getValidations()).thenReturn(Collections.emptyList());
            when(mockedCandidate.getValidations()).thenReturn(mockedValidations);
            when(mockedCandidate.getFolder()).thenReturn(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_00001");
            when(mockedUpdateLog.getCandidateBundle()).thenReturn(mockedCandidate);
            when(mockedConfManager.getUpdateLog()).thenReturn(mockedUpdateLog);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenReturn(mockedConfManager);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::getBundleFolder).thenCallRealMethod();
            ConfigurationBundle mockedPromoted = mock(ConfigurationBundle.class);
            when(mockedPromoted.getVersion()).thenReturn("3");
            configurationBundleManagerMockedStatic.when(() -> ConfigurationBundleManager.promote(true)).thenReturn(mockedPromoted);

            BundleVisualizationLink bundleVisualizationLink = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
            bundleVisualizationLink.doBundleUpdate();
            assertTrue(ConfigurationStatus.INSTANCE.isUpdateAvailable());
            assertTrue(bundleVisualizationLink.isUpdateAvailable());
            assertFalse(ConfigurationStatus.INSTANCE.isErrorInNewVersion());
            assertFalse(bundleVisualizationLink.isErrorInNewVersion());
            assertThat(bundleVisualizationLink.getErrorMessage(), is(""));
        }
    }

    @Test
    public void checkUpdate_downloadError() throws Exception {
        try (ACLContext ctx = ACL.as(adminUser); MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(ConfigurationBundleManager.class)) {
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true);
            ConfigurationBundleManager mockedConfManager = mock(ConfigurationBundleManager.class);
            when(mockedConfManager.downloadIfNewVersionIsAvailable()).thenThrow(new RuntimeException(new InvalidBundleException(ValidationCode.LOAD, "Error loading the CasC bundle.", new IOException("Error response from bundle server: url=http://192.168.1.42:7080/zip-bundle/d2222ea38e7b9b9d509468eec1511b36/my-controller2, status=404"))));
            ConfigurationBundle mockedBundle = mock(ConfigurationBundle.class);
            when(mockedBundle.getVersion()).thenReturn("1");
            when(mockedConfManager.getConfigurationBundle()).thenReturn(mockedBundle);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenReturn(mockedConfManager);

            BundleVisualizationLink bundleVisualizationLink = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
            bundleVisualizationLink.doBundleUpdate();
            assertFalse(ConfigurationStatus.INSTANCE.isUpdateAvailable());
            assertFalse(bundleVisualizationLink.isUpdateAvailable());
            assertTrue(ConfigurationStatus.INSTANCE.isErrorInNewVersion());
            assertTrue(bundleVisualizationLink.isErrorInNewVersion());
            assertThat(bundleVisualizationLink.getErrorMessage(), containsString("Error response from bundle server: url=http://192.168.1.42:7080/zip-bundle/d2222ea38e7b9b9d509468eec1511b36/my-controller2, status=404"));
        }
    }

    /**
     * BundleVisualizationLinkTest should create the ValiationSection using the boolean from ConfigurationBundleManager
     */
    @Test
    public void testValiationSectionQuietConfig() {
        doTestValiationSectionQuietConfig(false);
        doTestValiationSectionQuietConfig(true);
    }

    private void doTestValiationSectionQuietConfig(boolean quiet) {
        try (ACLContext ctx = ACL.as(adminUser);
                MockedStatic<ConfigurationBundleManager> configurationBundleManagerMockedStatic = mockStatic(
                        ConfigurationBundleManager.class)) {

            BundleUpdateLog.BundleValidationYaml mockedValidations = mock(BundleUpdateLog.BundleValidationYaml.class);
            List<Validation.Serialized> validations = new ArrayList<>();
            validations.add(new Validation.Serialized(Validation.Level.INFO, "Unit test", ValidationCode.UNDEFINED));
            when(mockedValidations.getValidations()).thenReturn(validations);

            BundleUpdateLog mockedUpdateLog = mock(BundleUpdateLog.class);
            when(mockedUpdateLog.getCurrentVersionValidations()).thenReturn(mockedValidations);

            ConfigurationBundleManager mockedConfManager = mock(ConfigurationBundleManager.class);
            when(mockedConfManager.getUpdateLog()).thenReturn(mockedUpdateLog);
            when(mockedConfManager.isQuiet()).thenReturn(quiet);

            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::isSet).thenReturn(true);
            configurationBundleManagerMockedStatic.when(ConfigurationBundleManager::get).thenReturn(mockedConfManager);

            BundleVisualizationLink visualizationLink = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
            assertEquals(quiet, visualizationLink.getBundleValidations().isQuiet());
        }
    }
}