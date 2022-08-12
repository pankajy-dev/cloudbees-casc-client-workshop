package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import hudson.model.User;
import net.sf.json.JSONObject;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class BundleVersionCheckerHttpEndpointTest extends AbstractBundleVersionCheckerTest {

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-1.zip")
    public void update_bundles() throws Exception {
        CJPRule.WebClient wc = rule.createWebClient();

        // Instance started up with version 1 - Valid
        WebResponse resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        JSONObject jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-1.zip", false);
        assertVersions(jsonResult, "version-1.zip", "1", empty(), null, null, true);
        assertUpdateType(jsonResult, "version-1.zip", null);

        // Updated to version 2 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-2.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-2.zip", true);
        assertVersions(jsonResult, "version-2.zip", "1", empty(), "2", empty(), true);
        assertUpdateType(jsonResult, "version-2.zip", "RELOAD");
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc); // Apply new version
        // Wait for async reload to complete
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // Updated to version 3 - Without version - Ignored
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-3.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-3.zip", false);
        assertVersions(jsonResult, "version-3.zip", "2", empty(), null, null, true);
        assertUpdateType(jsonResult, "version-3.zip", null);

        // Updated to version 4 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-4.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-4.zip", true);
        assertVersions(jsonResult, "version-4.zip", "2", empty(), "4", contains("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer."), false);
        assertUpdateType(jsonResult, "version-4.zip", null);

        // Updated to version 5 - Valid but with warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-5.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-5.zip", true);
        assertVersions(jsonResult, "version-5.zip", "2", empty(), "5", contains(containsString("[CATALOGVAL] - More than one plugin catalog file used")), true);
        assertUpdateType(jsonResult, "version-5.zip", "RELOAD");
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc); // Apply new version
        // Wait for async reload to complete
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // Updated to version 6 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-6.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-6.zip", true);
        assertVersions(jsonResult, "version-6.zip", "5", contains(containsString("[CATALOGVAL] - More than one plugin catalog file used")), "6", contains("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer."), false);
        assertUpdateType(jsonResult, "version-6.zip", null);

        // Updated to version 7 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-7.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-7.zip", true);
        assertVersions(jsonResult, "version-7.zip", "5", empty(), "7", empty(), true);
        assertUpdateType(jsonResult, "version-7.zip", "RELOAD");
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc); // Apply new version
        // Wait for async reload to complete
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // Updated to version 8 - Valid structure / Invalid jenkins.yaml only warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-8.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-8.zip", true);
        assertVersions(jsonResult, "version-8.zip", "7", empty(), "8", contains("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"), true);
        assertUpdateType(jsonResult, "version-8.zip", "RELOAD");
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc); // Apply new version
        // Wait for async reload to complete
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // Updated to version 9 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-9.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-9.zip", true);
        assertVersions(jsonResult, "version-9.zip", "8", empty(), "9", empty(), true);
        assertUpdateType(jsonResult, "version-9.zip", "RELOAD");
        requestWithToken(HttpMethod.POST, new URL(rule.getURL(), "casc-bundle-mgnt/reload-bundle"), admin, wc); // Apply new version
        // Wait for async reload to complete
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        // Updated to version 10 - Valid structure / Invalid jenkins.yaml
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-10.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-10.zip", true);
        assertVersions(jsonResult, "version-10.zip", "9", empty(), "10", contains("ERROR - [JCASC] - The bundle.yaml file references jcasc/jenkins.yaml in the Jenkins Configuration as Code section that is empty or has an invalid yaml format. Impossible to validate Jenkins Configuration as Code."), false);
        assertUpdateType(jsonResult, "version-10.zip", null);

        // Updated to version 11 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-11.zip").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update"), admin, wc);
        jsonResult = JSONObject.fromObject(resp.getContentAsString());
        assertUpdateAvailable(jsonResult, "version-11.zip", true);
        assertVersions(jsonResult, "version-11.zip", "9", empty(), "11", empty(), true);
        assertUpdateType(jsonResult, "version-11.zip", "RELOAD");
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