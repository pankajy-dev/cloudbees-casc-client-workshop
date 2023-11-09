package com.cloudbees.opscenter.client.casc;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ValidationDetailsActionTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void doGetValidationsDetailsTest() throws IOException {
        JenkinsRule.WebClient wc = rule.createWebClient();
        JenkinsRule.JSONWebResponse response = wc.getJSON(rule.getURL() + "casc-validations-details/list");
        assertThat("We get an ok", response.getStatusCode(), is(HttpServletResponse.SC_OK));
        assertThat("Reponse contains validations details", response.getJSONObject().getJSONArray("validations").isEmpty(), is(false));
    }
}
