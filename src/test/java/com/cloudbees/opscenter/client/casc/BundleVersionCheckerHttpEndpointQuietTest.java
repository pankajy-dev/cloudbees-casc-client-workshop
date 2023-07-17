package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebResponse;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

import static com.cloudbees.opscenter.client.casc.BundleVersionCheckerHttpEndpointTest.requestWithToken;
import static com.cloudbees.opscenter.client.casc.CasCMatchers.hasInfoMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

public class BundleVersionCheckerHttpEndpointQuietTest extends AbstractBundleVersionCheckerTest {
    @Test
    @WithEnvelope(AbstractBundleVersionCheckerTest.TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-2.zip")
    public void testQuietMode() throws IOException {
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp;
        JSONObject jsonResult;

        // Check version 4 - Invalid - no param
        String version4Path = Paths
                .get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-4.zip")
                .toFile().getAbsolutePath();
        System.setProperty("core.casc.config.bundle", version4Path);
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());

        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );
        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );

        // Check version 4 - Invalid - not quiet
        System.setProperty("core.casc.config.bundle", version4Path);
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update?quiet=false"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());

        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );
        assertThat("Current version should contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   hasInfoMessage()
        );

        // Check version 4 - Invalid - Quiet
        System.setProperty("core.casc.config.bundle", version4Path);
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update?quiet=true"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());

        assertThat("Current version should not contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("current-bundle")
                             .getJSONArray("validations"),
                   not(hasInfoMessage())
        );
        assertThat("New version should not contains INFO messages",
                   jsonResult.getJSONObject("versions")
                             .getJSONObject("new-version")
                             .getJSONArray("validations"),
                   not(hasInfoMessage())
        );
    }
}
