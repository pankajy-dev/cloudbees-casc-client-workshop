package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;

import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.TextFile;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks permissions for {@link HotReloadTest} but without {@link Jenkins#SYSTEM_READ}
 * Just the permission check. Content in the previous test class
 */
@Issue("BEE-49270")
public class HotReloadWithoutSystemReadTest extends AbstractCJPTest {

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    private User admin;
    private User cascAdmin;
    private User user;

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

        cascAdmin = realm.createAccount("cascAdmin", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_ADMIN, cascAdmin.getId());
        authorizationStrategy.add(Jenkins.READ, cascAdmin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_READ, user.getId());
        authorizationStrategy.add(Jenkins.READ, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/bundles-no-SystemRead/simple-bundle")
    @Test
    public void permissionCheck() throws IOException, CasCException {
        assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = update();
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("user", false))) {
            assertThrows(AccessDeniedException3.class, () -> service.reloadIfIsHotReloadable(bundle));
        }
        try (ACLContext a = ACL.as(User.getById("cascAdmin", false))) {
            assertThrows(AccessDeniedException3.class, () -> service.reloadIfIsHotReloadable(bundle));
        }
        try (ACLContext a = ACL.as(User.getById("admin", false))) {
            service.reloadIfIsHotReloadable(bundle);
        }
    }

    private ConfigurationBundle update() {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setJcasc(listOf(folder, "jcasc/jenkins.yaml"))
                .setPlugins(null)
                .setItems(null)
                .build();
    }

    private List<TextFile> listOf(Path folder, String ... files) {
        List<TextFile> list = new ArrayList<>();
        for (String file : files) {
            list.add(TextFile.of(folder.resolve(file)));
        }
        return list;
    }

}
