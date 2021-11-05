package com.cloudbees.opscenter.client.casc;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class BundleResponseTest {

    @Test
    @Issue("BEE-9123")
    public void currentConfigurationAlwaysExported() throws Exception {
        try(final StringOutputStream out = new StringOutputStream()) {
            StaplerResponse response = mock(StaplerResponse.class);
            doReturn(out).when(response).getOutputStream();

            BundleResponse bundleResponse = new BundleResponse("descriptor", null, null, null, null, null);
            bundleResponse.generateResponse(mock(StaplerRequest.class), response, mock(Object.class));
            String rawYaml = ((StringOutputStream) response.getOutputStream()).getString();
            assertThat("bundle descriptor should be there always", rawYaml, containsString("# bundle.yaml"));
            assertThat("jcasc is not expected when it is null", rawYaml, not(containsString("# jenkins.yaml")));
            assertThat("plugins is not expected when it is null", rawYaml, not(containsString("# plugins.yaml")));
            assertThat("plugin catalog is not expected when it is null", rawYaml, not(containsString("# plugin-catalog.yaml")));
            assertThat("items is not expected when it is null", rawYaml, not(containsString("# items.yaml")));
            assertThat("rbac is not expected when it is null", rawYaml, not(containsString("# rbac.yaml")));
            response.getOutputStream().flush();

            bundleResponse = new BundleResponse("descriptor", "jcasc-content", "plugin-content", "plugin-catalog-content", "items-content", "rbac-content");
            bundleResponse.generateResponse(mock(StaplerRequest.class), response, mock(Object.class));
            rawYaml = ((StringOutputStream) response.getOutputStream()).getString();
            assertThat("bundle descriptor should be there always", rawYaml, containsString("# bundle.yaml"));
            assertThat("jcasc is expected when it is not null", rawYaml, containsString("# jenkins.yaml"));
            assertThat("plugins is expected when it is not null", rawYaml, containsString("# plugins.yaml"));
            assertThat("plugin catalog is expected when it is not null", rawYaml, containsString("# plugin-catalog.yaml"));
            assertThat("items is expected when it is not null", rawYaml, containsString("# items.yaml"));
            assertThat("rbac is expected when it is not null", rawYaml, containsString("# rbac.yaml"));
            response.getOutputStream().flush();
        }
    }

    private class StringOutputStream extends ServletOutputStream {

        StringBuilder toString = new StringBuilder();

        @Override
        public void write(int i) throws IOException {
            toString.append((char) i);
        }

        @Override
        public void flush() throws IOException {
            toString = new StringBuilder();
        }

        public String getString() {
            return toString.toString();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // NOOP
        }
    }
}