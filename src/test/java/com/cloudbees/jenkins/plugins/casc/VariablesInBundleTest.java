package com.cloudbees.jenkins.plugins.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class VariablesInBundleTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @Test
    @WithEnvelope(V2dot319.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/VariablesInBundleTest/bundle")
    public void simpleTest() {
        assertThat(Jenkins.get().getSystemMessage(), is("Jenkins configured using CasC"));
        Item item = Jenkins.get().getItemByFullName("test");
        assertThat(item, notNullValue());
        assertThat(item.getDisplayName(), is("job description"));
    }

    public static final class V2dot319 implements TestEnvelopeProvider {
        public V2dot319() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.319.2",1,"",
                    TestEnvelopes.beer12(),
                    TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
