package com.cloudbees.opscenter.client.casc.cli;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.cli.CLICommandInvoker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class AvailableValidationsCommandTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void getValidationsDetails() {
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, AvailableValidationsCommand.COMMAND_NAME).invoke();
        assertThat("Call ends successfully", result.returnCode(), is(0));
        assertThat("Output shows validations", result.stdout(), allOf(containsString("validations"),
                                                                    containsString("RbacValidator"),
                                                                    containsString("ItemsValidator"),
                                                                    containsString("AvailabilityPatternValidator"),
                                                                    containsString("ParentValidator"),
                                                                    containsString("RbacSchemaValidator"),
                                                                    containsString("PluginsToInstallValidator"),
                                                                    containsString("PluginCatalogSchemaValidator"),
                                                                    containsString("VariablesSchemaValidator"),
                                                                    containsString("ItemsSchemaValidator"),
                                                                    containsString("PluginsSchemaValidator"),
                                                                    containsString("YamlSchemaValidator"),
                                                                    containsString("DescriptorValidator"),
                                                                    containsString("ApiValidator"),
                                                                    containsString("ContentBundleValidator"),
                                                                    containsString("ItemRemoveStrategyValidator"),
                                                                    containsString("VersionValidator"),
                                                                    containsString("FileSystemBundleValidator"),
                                                                    containsString("BundleSchemaValidator"),
                                                                    containsString("MultipleCatalogFilesValidator"),
                                                                    containsString("JcascMergeStrategyValidator"),
                                                                    containsString("PluginCatalogInOCValidator"),
                                                                    containsString("PluginCatalogValidator"),
                                                                    containsString("JCasCValidatorExtension")));
        JSONObject json = new JSONObject(result.stdout());
        assertThat("Output is a valid JSON", json.getJSONArray("validations").isEmpty(), is(false));
    }

}
