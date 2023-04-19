package com.cloudbees.opscenter.client.casc;

import java.nio.file.Paths;
import java.time.Duration;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Issue("BEE-33295")
public class HotReloadAndRemoveStrategyFromDescriptorTest extends AbstractCJPTest {

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/version-1")
    public void testItemsRemoveStrategyHotReload() throws Exception {

        // Build the free style projects -> this will test if the items are removed
        FreeStyleProject fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        FreeStyleProject fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        FreeStyleProject fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNotNull("free-root must exist", fsp1);
        assertNotNull("free-in-folder must exist", fsp2);
        assertNotNull("free-in-folder-in-folder must exist", fsp3);

        rule.waitForCompletion(fsp1.scheduleBuild2(0).waitForStart());
        rule.waitForCompletion(fsp2.scheduleBuild2(0).waitForStart());
        rule.waitForCompletion(fsp3.scheduleBuild2(0).waitForStart());

        assertThat("free-root must have a build", fsp1.getBuilds(), not(empty()));
        assertThat("folder-root/free-in-folder must have a build", fsp2.getBuilds(), not(empty()));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build", fsp3.getBuilds(), not(empty()));

        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/version-2").toFile().getAbsolutePath());
        ExtensionList.lookupSingleton(BundleVisualizationLink.class).doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(BundleReloadAction.class).tryReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNotNull("free-root should have been re-created", fsp1);
        assertNotNull("free-in-folder should have been re-created", fsp2);
        assertNotNull("free-in-folder-in-folder  should have been re-created", fsp3);

        assertThat("free-root mustn't have a build as it has been recreated", fsp1.getBuilds().size(), is(0));
        assertThat("folder-root/free-in-folder mustn't have a build as it has been recreated", fsp2.getBuilds().size(), is(0));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder mustn't have a build as it has been recreated", fsp3.getBuilds().size(), is(0));
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.387.1-cb-5", 1, "", TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
