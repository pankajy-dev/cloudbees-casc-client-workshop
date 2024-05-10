package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.servlet.http.HttpServletResponse;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Same as {@link BundleReloadActionTest} but without {@link Jenkins#SYSTEM_READ} enabled
 * Just permission check
 */
@Issue("BEE-49270")
public class BundleReloadActionWithoutSystemReadTest extends AbstractCJPTest {

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


    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/bundles-no-SystemRead/initial-bundle")
    public void permissionCheck() throws Exception {
        final Path v2 = Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/bundles-no-SystemRead/bundle-v2");
        System.setProperty("core.casc.config.bundle", v2.toFile().getAbsolutePath());

        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), user, wc, false);
        assertThat("We should get a 403 for non Jenkins.ADMINISTER user", resp.getStatusCode() , is(HttpServletResponse.SC_FORBIDDEN));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), cascAdmin, wc, false);
        assertThat("We should get a 403 for non Jenkins.ADMINISTER user", resp.getStatusCode() , is(HttpServletResponse.SC_FORBIDDEN));

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc, false);
        assertThat("We should get a 200 for Jenkins.ADMINISTER", resp.getStatusCode() , is(HttpServletResponse.SC_OK));

        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), user, wc, false);
        assertThat("We should get a 403 for non Jenkins.ADMINISTER user", resp.getStatusCode() , is(HttpServletResponse.SC_FORBIDDEN));

        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), cascAdmin, wc, false);
        assertThat("We should get a 403 for non Jenkins.ADMINISTER user", resp.getStatusCode() , is(HttpServletResponse.SC_FORBIDDEN));

        resp = requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc, false);
        assertThat("We should get a 200 for Jenkins.ADMINISTER", resp.getStatusCode() , is(HttpServletResponse.SC_OK));
    }

    private WebResponse requestWithToken(HttpMethod method, URL fullURL, User asUser, CJPRule.WebClient wc, boolean async)
            throws IOException {
        try {
            WebRequest getRequest = new WebRequest(fullURL, method);
            if (async) {
                getRequest.setRequestParameters(Collections.singletonList(new NameValuePair("asynchronous", "true")));
            }
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        }
        catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "", TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
