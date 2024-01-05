package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

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
import org.jvnet.hudson.test.Issue;

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
        assertThat("deletions should contain 2 elements", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(2));
        assertThat("to-be-deleted should be in the response", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too"));
        assertThat("The instance still has 4 items", rule.jenkins.getAllItems(), hasSize(4));

        // Using an invalid remove strategy should return an error
        ConfigurationBundleManager.get().getConfigurationBundle().getItemRemoveStrategy().setItems("invalid");
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 500", resp.getStatusCode(), is(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        assertThat("Error is indicated", jsonResult.getString("error"), containsString("Unknown items removeStrategy"));
    }

    @Test
    @Issue("BEE-44300")
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/none")
    public void doCheckReloadDeletionsShouldUseCandidateBundleTest() throws IOException {
        CJPRule.WebClient wc = rule.createWebClient();

        WebResponse resp;
        JSONObject jsonResult;

        // No changes in the instance, we expect no deletions
        // Bundle "none" contains 3 folders and the sync strategy for the items is "none".
        assertThat("Bundle 'none' contains 3 folders", rule.jenkins.getAllItems(), hasSize(3));

        // Creating 2 items, as strategy is NONE they should be no deletions
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted");
        rule.jenkins.createProject(FreeStyleProject.class, "to-be-deleted-too");
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("deletions should be an empty list", jsonResult.getJSONObject("items").getJSONArray("deletions"), empty());

        // Bundle "sync" contains only 2 folders, and the sync strategy for the items is "sync".
        System.setProperty("core.casc.config.bundle",
                           Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/CheckBundleDeletionsHttpEndpointTest/sync").toFile().getAbsolutePath());
        requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);

        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-reload-items"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Response code should be a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("deletions should contain 3 element", jsonResult.getJSONObject("items").getJSONArray("deletions"), hasSize(3));
        assertThat("to-be-deleted should be in the response", jsonResult.getJSONObject("items").getJSONArray("deletions"), containsInAnyOrder("to-be-deleted", "to-be-deleted-too", "folder-to-be-deleted"));
        assertThat("The instance still has 5 items", rule.jenkins.getAllItems(), hasSize(5));
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
