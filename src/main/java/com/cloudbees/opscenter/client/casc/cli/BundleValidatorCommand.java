package com.cloudbees.opscenter.client.casc.cli;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import hudson.Extension;
import hudson.FilePath;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Option;
import org.springframework.security.access.AccessDeniedException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Extension
public class BundleValidatorCommand extends CLICommand {

    public final static String COMMAND_NAME = "casc-bundle-validate";

    @Option(name="-c", aliases = { "--commit"}, usage="Logs the indicated commit in the instance logs for further tracking", required = false)
    private String commit = null;

    @Override
    public String getShortDescription() { return "Validates a bundle on this instance. The bundle must be a zip file containing the bundle structure. Example of use: java -jar jenkins-cli.jar " + COMMAND_NAME + " < /path/to/bundle.zip";}

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    /**
     * Validates a bundle. If validation goes as expected, {@link CLICommand#stdout} will display a JSON output as following:
     * {
     *     "valid": false,
     *     "validation-messages": [
     *         "ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.",
     *         "WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"
     *     ]
     * }
     * @return
     *      <table>
     *      <caption>Validation result</caption>
     *      <tr><th>Code</th><th>Output</th></tr>
     *      <tr><td>0</td><td>JSON output with the validation result</td></tr>
     *      <tr><td>3</td><td>The input is not a valid zip.</td></tr>
     *      <tr><td>6</td><td>User does not have {@link Jenkins#MANAGE} permission</td></tr>
     *      </table>
     * @throws Exception If an unknown and/or unexpected issue occurred
     * @throws IllegalArgumentException If the zip file does not have the correct format
     * @throws AccessDeniedException If the user does not have {@link Jenkins#MANAGE} permission
     */
    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        Path tempFolder = null;
        try {
            tempFolder = ConfigurationUpdaterHelper.createTemporaryFolder();
            Path bundleDir = null;
            try (BufferedInputStream in = new BufferedInputStream(stdin)) {
                // Copy zip from request
                Path zipSrc = tempFolder.resolve("bundle.zip");
                FileUtils.copyInputStreamToFile(in, zipSrc.toFile());

                if (!Files.exists(zipSrc)) {
                    throw new IllegalArgumentException("Invalid zip file");
                }

                bundleDir = tempFolder.resolve("bundle");
                FilePath zipFile = new FilePath(zipSrc.toFile());
                FilePath dst = new FilePath(bundleDir.toFile());
                zipFile.unzip(dst);
            } catch (IOException | InterruptedException e) {
                throw new IllegalArgumentException("Invalid zip file");
            }

            if (!Files.exists(bundleDir)) {
                throw new IOException("Error unzipping the bundle");
            }

            if (!Files.exists(bundleDir.resolve("bundle.yaml"))) {
                throw new IllegalArgumentException("Invalid bundle - Missing descriptor");
            }

            List<Validation> validations = ConfigurationUpdaterHelper.fullValidation(bundleDir, commit);
            stdout.println(ConfigurationUpdaterHelper.getValidationJSON(validations));
        } finally {
            if (tempFolder != null && Files.exists(tempFolder)) {
                FileUtils.deleteDirectory(tempFolder.toFile());
            }
        }

        return 0;
    }

}
