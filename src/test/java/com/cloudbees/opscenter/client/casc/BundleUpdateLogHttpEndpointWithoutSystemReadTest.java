package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import java.net.URL;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import net.sf.json.JSONObject;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Same as {@link BundleUpdateLogHttpEndpointTest} but without {@link Jenkins#SYSTEM_READ} enabled
 */
@Issue("BEE-49270")
public class BundleUpdateLogHttpEndpointWithoutSystemReadTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();
    private User admin;
    private User cascAdmin;
    private User user;
    private JenkinsRule.WebClient wc;


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

        cascAdmin = realm.createAccount("cascAdmin", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_ADMIN, cascAdmin.getId());
        authorizationStrategy.add(Jenkins.READ, cascAdmin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        cascAdmin.addProperty(new ApiTokenProperty());
        cascAdmin.getProperty(ApiTokenProperty.class).changeApiToken();

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_READ, user.getId());
        authorizationStrategy.add(Jenkins.READ, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
        user.addProperty(new ApiTokenProperty());
        user.getProperty(ApiTokenProperty.class).changeApiToken();

        wc = rule.createWebClient();
    }

    @Test
    public void check_permissions() throws Exception {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), user, wc);
        assertThat("User user does not have permissions", resp.getStatusCode(), is(403));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), cascAdmin, wc);
        assertThat("User cascAdmin does not have permissions", resp.getStatusCode(), is(403));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), admin, wc);
        assertThat("User admin has permissions", resp.getStatusCode(), is(200));
    }

    @Test
    public void check_no_casc() throws Exception {
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), cascAdmin, wc);
        assertThat("User cascAdmin does not have permissions", resp.getStatusCode(), is(403));
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-update-log"), admin, wc);
        assertThat("User admin has permissions", resp.getStatusCode(), is(200));
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("CasC disabled", response.getString("update-log-status"), is("CASC_DISABLED"));
    }

    private WebResponse requestWithToken(HttpMethod method, URL fullURL, User asUser, CJPRule.WebClient wc)
            throws IOException {

        try {
            WebRequest getRequest = new WebRequest(fullURL, method);
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        }
        catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }
}