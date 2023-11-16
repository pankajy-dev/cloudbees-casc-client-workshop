package com.cloudbees.opscenter.client.casc.cli;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

import org.json.JSONObject;

import hudson.Extension;
import hudson.cli.CLICommand;

@Extension
public class AvailableValidationsCommand extends CLICommand {

    public final static String COMMAND_NAME = "casc-validations-details";

    @Override
    public String getShortDescription() {
        return "Returns a JSON list of all the available validations in the instance, including details for each validations";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    protected int run() throws Exception {
        // Notice this command doesn't do any explicit permission check, as it's returning a simple report of existing validations
        // so it gives no info about installed plugins or sensible information.
        String validations = "";
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("com/cloudbees/jenkins/plugins/casc/validation/validation-details.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.defaultCharset()));) {
            validations = reader.lines().collect(Collectors.joining("\n"));
        }
        JSONObject json = new JSONObject(validations);
        stdout.println(json.toString(2));
        return 0;
    }
}
