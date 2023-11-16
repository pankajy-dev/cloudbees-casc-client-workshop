package com.cloudbees.opscenter.client.casc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.model.RootAction;

@Extension
public class ValidationDetailsAction implements RootAction {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "casc-validations-details";
    }

    @GET
    @WebMethod(name = "list")
    public HttpResponse doGetValidationsDetails() throws URISyntaxException, IOException {
        // Notice this command doesn't do any explicit permission check others than jenkins security settings, as it's returning a simple report of existing validations
        // so it gives no info about installed plugins or sensible information.
        String validations = "";
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("com/cloudbees/jenkins/plugins/casc/validation/validation-details.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.defaultCharset()));) {
            validations = reader.lines().collect(Collectors.joining("\n"));
        }
        JSONObject json = JSONObject.fromObject(validations);
        return new JsonHttpResponse(json);
    }
}
