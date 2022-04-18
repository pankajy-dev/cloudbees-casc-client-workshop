package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.beer12;
import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.e;
import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.empty;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class UpdateLogActionTest extends AbstractCJPTest {

    @Test
    @LocalData
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/core-casc-bundle")
    @WithEnvelope(TestEnvelope.class)
    public void smokes() throws Exception {
        try (JenkinsRule.WebClient wc = rule.createWebClient()) {
            HtmlPage bundleUpdateTab = wc.goTo("casc-bundle-export-ui/bundleUpdate");
            String content = bundleUpdateTab.getWebResponse().getContentAsString();
            assertThat("Expected bundle version is 4", content, containsString("You are using version 4"));
            assertThat("Current version with validations", content, containsString("Validation output for version 4"));
            assertThat("Expected version 5 was rejected", content, containsString("A new version of the Configuration Bundle (5) is available, but it cannot be applied because it has validation errors"));

            HtmlPage updateLogTab = wc.goTo("casc-bundle-export-ui/updateLog");
            HtmlAnchor link = (HtmlAnchor) updateLogTab.getElementById("view-20220418_00005");
            TextPage yamlPage = link.click();
            content = yamlPage.getWebResponse().getContentAsString();
            assertThat("Expected error in 20220418_00005", content, containsString("message: '''apiVersion'' property in the bundle.yaml file must be an integer.'"));
            link = (HtmlAnchor) updateLogTab.getElementById("download-20220418_00005");
            UnexpectedPage zipContent = link.click();
            String validationContent = null;
            String descriptorContent = null;
            boolean isCandidate = false;
            try (ZipInputStream zipFile = new ZipInputStream(zipContent.getWebResponse().getContentAsStream())) {
                ZipEntry entry;
                while ((entry = zipFile.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if ("validation.yaml".equals(entryName)) {
                        validationContent = IOUtils.toString(zipFile, StandardCharsets.UTF_8);
                    } else if ("bundle/bundle.yaml".equals(entryName)) {
                        descriptorContent = IOUtils.toString(zipFile, StandardCharsets.UTF_8);
                    } else if (".candidate".equals(entryName)) {
                        isCandidate = true;
                    }
                }
            }
            assertThat("Expected error in 20220418_00005", validationContent, containsString("message: '''apiVersion'' property in the bundle.yaml file must be an integer.'"));
            assertThat("Expected version 5 in 20220418_00005", descriptorContent, containsString("version: \"5\""));
            assertTrue("Version 5 should be marked as candidate in 20220418_00005", isCandidate);

            ConfigurationBundleManager cbm = ConfigurationBundleManager.get();
            BundleUpdateLog updateLog = cbm.getUpdateLog();
            assertThat("ConfigurationBundleManager has version 4 as current", cbm.getConfigurationBundle().getVersion(), is("4"));
            assertThat("ConfigurationBundleManager has version 4 as current validated", updateLog.getCurrentVersionValidations().getValidations(), not(empty()));
            assertThat("ConfigurationBundleManager has version 5 as rejected candidate", updateLog.getCandidateBundle().getVersion(), is("5"));
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