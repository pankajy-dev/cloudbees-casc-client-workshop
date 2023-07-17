package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Test;

import net.sf.json.JSONObject;

import hudson.model.FreeStyleProject;
import hudson.model.User;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class CheckBundleDeletionsHttpEndpointTest extends AbstractBundleVersionCheckerTest{

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/sync")
    public void doCheckReloadDeletionsTest() throws IOException {
        CJPRule.WebClient wc = rule.createWebClient();

        // No changes in the instance, we expect no deletions
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        JSONObject jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("deletions should be an empty list", jsonResult.getJSONObject("items").getJSONArray("deletions"), empty());

        // Creating 2 items, as strategy is SYNC they should be deleted when the bundle is applied
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted");
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted-too");
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("deletions should contain 1 element", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(2));
        assertThat("to-be-deleted should be in the response", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too"));
        assertThat("The instance still has 4 items", rule.jenkins.getAllItems(), hasSize(4));

        // Using an invalid remove strategy should return an error
        ConfigurationBundleManager.get().getConfigurationBundle().getItemRemoveStrategy().setItems("invalid");
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 500", resp.getStatusCode(), is(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        assertThat("Error is indicated", jsonResult.getString("error"), containsString("Unknown items removeStrategy"));
    }

    private WebResponse requestWithToken(HttpMethod method, URL fullURL, User asUser, CJPRule.WebClient wc) throws IOException {
        try {
            WebRequest getRequest = new WebRequest(fullURL, method);
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        }
        catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }
}
