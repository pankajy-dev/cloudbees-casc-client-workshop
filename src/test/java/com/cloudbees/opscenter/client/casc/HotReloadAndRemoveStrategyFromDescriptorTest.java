package com.cloudbees.opscenter.client.casc;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import edu.umd.cs.findbugs.annotations.NonNull;
import nectar.plugins.rbac.groups.Group;
import nectar.plugins.rbac.groups.GroupContainer;
import nectar.plugins.rbac.groups.GroupContainerLocator;

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
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Issue("BEE-33295")
public class HotReloadAndRemoveStrategyFromDescriptorTest extends AbstractCJPTest {

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-1")
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

        // Version 2 declares the remove-all in the bundle descriptor, so it must override the none strategy from the items.yaml
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-2").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
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

        // Creating builds to test the removals
        rule.waitForCompletion(fsp1.scheduleBuild2(0).waitForStart());
        rule.waitForCompletion(fsp2.scheduleBuild2(0).waitForStart());
        rule.waitForCompletion(fsp3.scheduleBuild2(0).waitForStart());
        assertThat("free-root must have a build", fsp1.getBuilds(), not(empty()));
        assertThat("folder-root/free-in-folder must have a build", fsp2.getBuilds(), not(empty()));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build", fsp3.getBuilds(), not(empty()));

        // Version 3 doesn't set the remove strategy but introduces a change in the items.yaml, so it must be reloaded
        // remove strategy is none, so the removed item will remain and another will have a change
        // none of them is removed, so the builds remain
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-3").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNotNull("free-root should have remained", fsp1);
        assertNotNull("free-in-folder should have remained and updated", fsp2);
        assertThat("free-in-folder should have remained and updated", fsp2.getDescription(), is("changed!"));
        assertNotNull("free-in-folder-in-folder should have remained", fsp3);

        assertThat("free-root must have a build as it hasn't been recreated", fsp1.getBuilds().size(), is(1));
        assertThat("folder-root/free-in-folder must have a build as it hasn't been recreated", fsp2.getBuilds().size(), is(1));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build as it hasn't been recreated", fsp3.getBuilds().size(), is(1));

        // Version 4 doesn't set the remove strategy but introduces a change in the items.yaml, so it must be reloaded
        // remove strategy is sync, so the removed item won't remain and another will have a change
        // Only one job is removed, so the builds remain
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-4").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNull("free-root should have been removed", fsp1);
        assertNotNull("free-in-folder should have remained and updated", fsp2);
        assertThat("free-in-folder should have remained and updated", fsp2.getDescription(), is("changed again!"));
        assertNotNull("free-in-folder-in-folder should have remained", fsp3);

        assertThat("folder-root/free-in-folder must have a build as it hasn't been recreated", fsp2.getBuilds().size(), is(1));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build as it hasn't been recreated", fsp3.getBuilds().size(), is(1));

        // Version 5 doesn't set the remove strategy and doesn't introduce a change in the items.yaml, but as the remove strategy is sync
        // it must be reloaded
        // remove strategy is sync, so the removed item won't remain and another will have a change (will create another for the test)
        // Only one job is removed, so the builds remain
        rule.createFreeStyleProject("free-root");
        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        assertNotNull("Just created", fsp1);

        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-5").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNull("free-root should have been removed", fsp1);
        assertNotNull("free-in-folder should have remained and updated", fsp2);
        assertThat("free-in-folder should have remained and updated", fsp2.getDescription(), is("changed again!"));
        assertNotNull("free-in-folder-in-folder should have remained", fsp3);

        assertThat("folder-root/free-in-folder must have a build as it hasn't been recreated", fsp2.getBuilds().size(), is(1));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build as it hasn't been recreated", fsp3.getBuilds().size(), is(1));

        // Version 6 sets the remove strategy and doesn't introduce a change in the items.yam
        // remove strategy from descriptor is none and it must prevail over sync (from items.yaml)
        // there's no change, so nothing is reloaded
        // remove strategy is none, so the removed item will remain (will create another for the test)
        // builds remain
        rule.createFreeStyleProject("free-root");
        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        assertNotNull("Just created", fsp1);

        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-6").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNotNull("free-root should have remained", fsp1);
        assertNotNull("free-in-folder should have remained", fsp2);
        assertNotNull("free-in-folder-in-folder should have remained", fsp3);

        assertThat("folder-root/free-in-folder must have a build as it hasn't been recreated", fsp2.getBuilds().size(), is(1));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build as it hasn't been recreated", fsp3.getBuilds().size(), is(1));

        // Version 7 sets the remove strategy and introduces a change in the items.yam
        // remove strategy from descriptor is none and it must prevail over sync (from items.yaml)
        // there's a change, so it must be reflected
        // remove strategy is none, so the removed item will remain (created for version 6)
        // builds remain
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/items/version-7").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        fsp1 = Jenkins.get().getItemByFullName("free-root", FreeStyleProject.class);
        fsp2 = Jenkins.get().getItemByFullName("folder-root/free-in-folder", FreeStyleProject.class);
        fsp3 = Jenkins.get().getItemByFullName("folder-root/folder-in-folder/free-in-folder-in-folder", FreeStyleProject.class);

        assertNotNull("free-root should have remained", fsp1);
        assertNotNull("free-in-folder should have remained and updated", fsp2);
        assertThat("free-in-folder should have remained and updated", fsp2.getDescription(), is("changed again and again!"));
        assertNotNull("free-in-folder-in-folder should have remained", fsp3);

        assertThat("folder-root/free-in-folder must have a build as it hasn't been recreated", fsp2.getBuilds().size(), is(1));
        assertThat("folder-root/folder-in-folder/free-in-folder-in-folder must have a build as it hasn't been recreated", fsp3.getBuilds().size(), is(1));

    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-1")
    public void testRbacRemoveStrategyHotReload() throws Exception {
        GroupContainer container = GroupContainerLocator.locate(rule.jenkins);

        List<Group> global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        Group administrators = getGroup("Administrators", global);
        Group developers = getGroup("Developers", global);
        Group readers = getGroup("Readers", global);

        assertNotNull("Group Administrator is created", administrators);
        assertNotNull("Group Developers is created", developers);
        assertNotNull("Group Readers is created", readers);

        // Version 2 declares the sync in the bundle descriptor, so it must override the none strategy from the items.yaml
        // Adding a user, so we can check the sync works
        assertThat("Developers should not have users yet", developers.getUsers(), hasSize(0));
        developers.doAddUser("bob");
        assertThat("Developers should have a user now", developers.getUsers(), hasSize(1));

        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-2").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        administrators = getGroup("Administrators", global);
        developers = getGroup("Developers", global);
        readers = getGroup("Readers", global);

        assertNotNull("Group Administrator is re-created", administrators);
        assertNotNull("Group Developers is re-created", developers);
        assertThat("Group Developers is re-created and should not have users", developers.getUsers(), hasSize(0));
        assertNotNull("Group Readers is re-created", readers);

        // Version 3 doesn't set the remove strategy but introduces a change in the rbac.yaml, so it must be reloaded
        // remove strategy is update, so the removed group will remain
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-3").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        administrators = getGroup("Administrators", global);
        developers = getGroup("Developers", global);
        readers = getGroup("Readers", global);

        assertNotNull("Group Administrator is updated", administrators);
        assertThat("Group Administrator is updated from yaml file (added user)", administrators.getUsers(), hasSize(2));
        assertNotNull("Group Developers is updated", developers);
        assertNotNull("Group Readers is not removed", readers);

        // Version 4 doesn't set the remove strategy but introduces a change in the rbac.yaml, so it must be reloaded
        // remove strategy is sync, so the removed group won't remain and another will have a change
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-4").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        global = container.getGroups();
        assertThat(global.size(), Matchers.is(2));

        administrators = getGroup("Administrators", global);
        developers = getGroup("Developers", global);
        readers = getGroup("Readers", global);

        assertNotNull("Group Administrator is updated", administrators);
        assertThat("Group Administrator is updated from yaml file (removed user)", administrators.getUsers(), hasSize(1));
        assertNotNull("Group Developers is updated", developers);
        assertNull("Group Readers is removed", readers);

        // Version 5 sets the remove strategy and doesn't introduce a change in the items.yam
        // remove strategy from descriptor is update and it must prevail over sync (from rbac.yaml)
        // A manual group must remain
        GroupContainer<?> gc = GroupContainerLocator.locate(rule.jenkins);
        Group g = new Group(gc, "ManualGroup");
        gc.addGroup(g);
        g.setUsers(Collections.singletonList("bob"));
        rule.jenkins.save();
        global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-5").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        administrators = getGroup("Administrators", global);
        developers = getGroup("Developers", global);
        Group manual = getGroup("ManualGroup", global);

        assertNotNull("Group Administrator is updated", administrators);
        assertNotNull("Group Developers is updated", developers);
        assertNotNull("Group ManualGroup remains", manual);

        // Version 6 sets the remove strategy and introduces a change in the rbac.yam
        // remove strategy from descriptor is update and it must prevail over sync (from rbac.yaml)
        // there's a change, so it must be reflected
        // remove strategy is update, so the manually added group remains
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/HotReloadAndRemoveStrategyFromDescriptorTest/rbac/version-6").toFile().getAbsolutePath());
        BundleVisualizationLink.get().doBundleUpdate(); // Force the bundle update
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload(); // Reload the bundle
        await().atMost(Duration.ofSeconds(60)).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        global = container.getGroups();
        assertThat(global.size(), Matchers.is(3));

        administrators = getGroup("Administrators", global);
        developers = getGroup("Developers", global);
        manual = getGroup("ManualGroup", global);

        assertNotNull("Group Administrator is updated", administrators);
        assertThat("Group Administrator is updated from yaml file (added user)", administrators.getUsers(), hasSize(2));
        assertNotNull("Group Developers is updated", developers);
        assertNotNull("Group ManualGroup remains", manual);
    }

    private Group getGroup(String name, List<Group> groups) {
        for (Group group : groups) {
            if (group.getName().equals(name)) {
                return group;
            }
        }

        return null;
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
