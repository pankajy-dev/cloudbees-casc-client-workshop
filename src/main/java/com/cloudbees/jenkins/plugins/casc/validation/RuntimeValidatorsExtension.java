package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.RuntimeValidators;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import hudson.Extension;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link RuntimeValidators}.
 */
@Extension
public class RuntimeValidatorsExtension implements RuntimeValidators {
    private static final Logger LOGGER = Logger.getLogger(RuntimeValidatorsExtension.class.getName());

    @Override
    public List<Validation> performRuntimeValidation(PlainBundle<?> plainBundle) {
        Path bundlePath = null;
        try {
            bundlePath = ConfigurationUpdaterHelper.createTemporaryFolder();
            FileUtils.writeStringToFile(new File(bundlePath.toFile(), "bundle.yaml"), plainBundle.getDescriptor(), StandardCharsets.UTF_8);
            for (String filename : plainBundle.getFiles()) {
                String content = plainBundle.getFile(filename);
                if (content == null) {
                    continue;
                }
                File out = new File(bundlePath.toFile(), filename);
                FileUtils.writeStringToFile(out, content, StandardCharsets.UTF_8);
            }

            try {
                AbstractValidator.performValidations(bundlePath);
                return Collections.emptyList();
            } catch (InvalidBundleException e) {
                return e.getValidationResult();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to validate the bundle", e);
            return Collections.emptyList();
        } finally {
            if (bundlePath != null) {
                try {
                    FileUtils.deleteDirectory(bundlePath.toFile());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, String.format("Unable to cleanup the temporary folder: %s", bundlePath), e);
                }
            }
        }
    }
}
