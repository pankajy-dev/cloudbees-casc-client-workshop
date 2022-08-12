package com.cloudbees.opscenter.client.casc.visualization;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.casc.comparator.BundleComparator;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.HotReloadAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.beer12;
import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.e;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundleDiffActionTest extends AbstractCJPTest {

    protected User admin;

    @Before
    public void setUp() throws Exception {
        rule.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        rule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());

        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) rule.jenkins.getSecurityRealm();
        admin = realm.createAccount("admin", "password");
        rule.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) rule.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.ADMINISTER, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        admin.addProperty(new ApiTokenProperty());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();
    }


    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/visualization/BundleDiffActionTest/version-1")
    public void smokes() throws Exception {
        try (ACLContext a = ACL.as(User.getById("admin", false))) {
            final BundleVisualizationLink bundleUpdate = ExtensionList.lookupSingleton(BundleVisualizationLink.class);
            final BundleDiffAction diffAction = ExtensionList.lookupSingleton(BundleDiffAction.class);

            bundleUpdate.doBundleUpdate();
            assertFalse("No new version, so without diff object", bundleUpdate.withDiff());
            assertNull("No new version, so without diff object", ConfigurationStatus.INSTANCE.getChangesInNewVersion());
            assertNull("No new version, so without diff object", diffAction.getBundleDiff());

            // New version available
            System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/visualization/BundleDiffActionTest/version-2").toFile().getAbsolutePath());
            bundleUpdate.doBundleUpdate();
            assertTrue("New version, so with diff object", bundleUpdate.withDiff());
            assertNotNull("New version, so with diff object", ConfigurationStatus.INSTANCE.getChangesInNewVersion());
            BundleComparator.Result bundleDiff = diffAction.getBundleDiff();
            assertNotNull("New version, so with diff object", bundleDiff);
            assertThat("Expected current version 1", diffAction.getCurrentVersion(), is("1"));
            assertThat("Expected new version 2", diffAction.getNewVersion(), is("2"));
            assertTrue("Expected changes in jcasc", bundleDiff.getJcasc().withChanges());
            assertTrue("Expected changes in items", bundleDiff.getItems().withChanges());
            assertFalse("Not expected changes in rbac", bundleDiff.getRbac().withChanges());
            assertFalse("Not expected changes in plugins", bundleDiff.getPlugins().withChanges());
            assertFalse("Not expected changes in plugin catalog", bundleDiff.getCatalog().withChanges());
            assertFalse("Not expected changes in variables", bundleDiff.getVariables().withChanges());

            // Apply new version - Diff should be removed
            ExtensionList.lookupSingleton(HotReloadAction.class).doReload();
            // Wait for async reload to complete
            await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
            assertFalse("No new version, so without diff object", bundleUpdate.withDiff());
            assertNull("No new version, so without diff object", ConfigurationStatus.INSTANCE.getChangesInNewVersion());
            assertNull("No new version, so without diff object", diffAction.getBundleDiff());

            // Check again new version - No available
            bundleUpdate.doBundleUpdate();
            assertFalse("No new version, so without diff object", bundleUpdate.withDiff());
            assertNull("No new version, so without diff object", ConfigurationStatus.INSTANCE.getChangesInNewVersion());
            assertNull("No new version, so without diff object", diffAction.getBundleDiff());
        }
    }

    public static final class TestEnvelope implements TestEnvelopeProvider {
        @NonNull
        @Override
        public Envelope call() throws Exception {
            return e("2.332.2.6", 1, "2.332.2-cb-2", beer12());
        }
    }
}