package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.TextFile;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.Status;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class HotReloadTest extends AbstractIMTest {

    @Rule
    public final CJPRule rule;
    public HotReloadTest() {
        this.rule = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.rule;
    }

    @Ignore("This test is flaky when the update site data is requested.")
    @Issue("BEE-3618")
    @WithEnvelope(TwoPluginsV2dot289.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle")
    @Test
    public void reloadTest() throws IOException, CasCException {
        Assert.assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = update(ConfigurationBundleManager.get().getConfigurationBundle());
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("manager", false))) {
            Assert.assertFalse(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            Assert.assertTrue(Jenkins.get().hasPermission(Jenkins.MANAGE));

            service.reloadIfIsHotReloadable(bundle);

            await().atMost(Duration.ofSeconds(120)).until(() ->
                    Jenkins.get().getPlugin("beer"), notNullValue());

            Assert.assertTrue(Jenkins.get().getPlugin("beer").getWrapper().isActive());
        }
    }

    @Test
    @Issue("BEE-5221")
    @WithEnvelope(TwoPluginsV2dot289.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle-with-catalog")
    public void pluginCatalogRemovalTest() throws IOException, CasCException {
        Assert.assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = noPluginsUpdate(ConfigurationBundleManager.get().getConfigurationBundle());
        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);

        try (ACLContext a = ACL.as(User.getById("manager", false))) {
            Assert.assertFalse(Jenkins.get().hasPermission(Jenkins.ADMINISTER));
            Assert.assertTrue(Jenkins.get().hasPermission(Jenkins.MANAGE));

            Status status = BeekeeperRemote.get().getStatus();

            assertThat(status.getExtension(), notNullValue());

            service.reloadIfIsHotReloadable(bundle);

            await().atMost(Duration.ofSeconds(120)).until(() ->
                    BeekeeperRemote.get().getStatus().getExtension(), nullValue());

        }
    }

    @AfterClass
    public static void after() {
        System.clearProperty("casc.jenkins.config");
    }

    private ConfigurationBundle noPluginsUpdate(ConfigurationBundle bundle) {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        Set<String> plugins = new HashSet<>();

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setItems(listOf(folder, "items.yaml"))
                .setJcasc(listOf(folder, "jenkins.yaml"))
                .setRbac(listOf(folder, "rbac.yaml"))
                .setPlugins(plugins)
                .build();
    }

    private ConfigurationBundle update(ConfigurationBundle bundle) {
        Path folder = ConfigurationBundleManager.getBundleFolder();

        Set<String> plugins = new HashSet<>();
        plugins.add("beer");

        return ConfigurationBundle.builder()
                .setVersion("new")
                .setCatalog(null)
                .setItems(listOf(folder, "items.yaml"))
                .setJcasc(listOf(folder, "jenkins.yaml"))
                .setRbac(listOf(folder, "rbac.yaml"))
                .setPlugins(plugins)
                .build();
    }

    private List<TextFile> listOf(Path folder, String file) {
        List<TextFile> list = new ArrayList<>();
        list.add(TextFile.of(folder.resolve(file)));

        return list;
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1",1,"",TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
