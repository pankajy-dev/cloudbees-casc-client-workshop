package com.cloudbees.opscenter.client.casc.visualization;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.springframework.security.access.AccessDeniedException;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

/**
 * Same as {@link BundleVisualizationLinkTest} but without {@link Jenkins#SYSTEM_READ} enabled
 * Just the permissions check. Content is checked in the other test class
 */
@Issue("BEE-49270")
public class BundleVisualizationLinkWithoutSystemRestartTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private User adminUser;
    private User readUser;
    private User cascAdmin;
    private User cascUser;

    @Before
    public void setUp() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);

        adminUser = realm.createAccount("alice", "alice");
        readUser = realm.createAccount("bob", "bob");
        cascAdmin = realm.createAccount("carol", "carol");
        cascUser = realm.createAccount("dan", "dan");
        ProjectMatrixAuthorizationStrategy authorizationStrategy = new ProjectMatrixAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.READ, readUser.getId());
        authorizationStrategy.add(Jenkins.ADMINISTER, adminUser.getId());
        authorizationStrategy.add(CascPermission.CASC_ADMIN, cascAdmin.getId());
        authorizationStrategy.add(CascPermission.CASC_READ, cascUser.getId());
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void checkPermissions() throws Exception {
        try (ACLContext ctx = ACL.as(adminUser)) {
            BundleVisualizationLink.get().doBundleUpdate();
        }

        try (ACLContext ctx = ACL.as(cascAdmin)) {
            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> BundleVisualizationLink.get().doBundleUpdate());
            assertThat(exception.getMessage(), containsString("carol is missing the Overall/Administer permission"));
        }

        try (ACLContext ctx = ACL.as(readUser)) {
            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> BundleVisualizationLink.get().doBundleUpdate());
            assertThat(exception.getMessage(), containsString("bob is missing the Overall/Administer permission"));
        }

        try (ACLContext ctx = ACL.as(cascUser)) {
            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> BundleVisualizationLink.get().doBundleUpdate());
            assertThat(exception.getMessage(), containsString("dan is missing the Overall/Administer permission"));
        }
    }
}