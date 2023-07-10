package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import hudson.ExtensionList;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.impl.DefaultConfiguratorRegistry;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Source;
import io.jenkins.plugins.casc.yaml.YamlSource;
import io.jenkins.plugins.casc.yaml.YamlUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Execute the dryRun of jcasc configurator
 */
@OptionalExtension(requirePlugins = "configuration-as-code")
@SuppressRestrictedWarnings(value = {BundleLoader.class})
public class JCasCValidatorExtension extends AbstractValidator {

    private static final Logger LOGGER = Logger.getLogger(JCasCValidatorExtension.class.getName());

    @Override
    public ValidationCode getCode() {
        return ValidationCode.JCASC_CONFIGURATION;
    }

    @Override
    public List<Validation> validate(Path bundlePath) {

        if (bundlePath == null) {
            return Collections.emptyList();
        }

        BundleLoader.BundleDescriptor descriptor = null;
        try {
            descriptor = readDescriptor(bundlePath.resolve("bundle.yaml"));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error parsing the bundle descriptor", e);
            return Collections.singletonList(error("The bundle.yaml file cannot be parsed: " + e.getMessage()));
        }

        if (descriptor == null) {
            LOGGER.warning("Empty bundle descriptor");
            return Collections.singletonList(error("The bundle.yaml file seems to be empty. Validations cannot be performed."));
        }

        List<String> jcasc = descriptor.getJcasc();
        if (jcasc == null || jcasc.isEmpty()) {
            return Collections.emptyList();
        }

        List<Validation> errors = checkFiles(jcasc, bundlePath, "Jenkins Configuration as Code");

        if (!errors.isEmpty()) {
            return Collections.unmodifiableList(errors);
        }

        return validateJcasc(bundlePath, jcasc);
    }

    private List<Validation> validateJcasc(Path bundlePath, List<String> files) {
        // escalate to system, security checks must be performed before reaching to this point
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            List<InputStream> is = pathToInputStream(bundlePath, files);
            if (!is.isEmpty()) {
                try {
                    final DefaultConfiguratorRegistry registry = ExtensionList.lookupSingleton(DefaultConfiguratorRegistry.class);
                    final ConfigurationContext context = new ConfigurationContext(registry);
                    final List<YamlSource> sources = is.stream().map(inputStream -> YamlSource.of(inputStream)).collect(Collectors.toList());
                    final Mapping entries = YamlUtils.loadFrom(sources, context);
                    Map<Source, String> checks = ConfigurationAsCode.get().checkWith(entries, context);
                    if (checks.isEmpty()) {
                        return Collections.singletonList(Validation.info(ValidationCode.JCASC_CONFIGURATION, "[JCasCValidator] All configurations validated successfully."));
                    }

                    String validationMessage = checks.entrySet().stream().map(entry -> "\t- " + entry.getValue()).collect(Collectors.joining(System.lineSeparator()));
                    return Collections.singletonList(Validation.warning(ValidationCode.JCASC_CONFIGURATION, String.format("Invalid Jenkins configuration:%n%s", validationMessage)));
                } catch (Exception e) {
                    return Collections.singletonList(Validation.warning(ValidationCode.JCASC_CONFIGURATION, "It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: " + e.getMessage()));
                } finally {
                    close(is);
                }
            }

            return Collections.emptyList();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error validating Jcasc configuration", e);
            return Collections.singletonList(error("Unexpected error validating Jenkins configuration: " + e.getMessage()));
        }
    }

    private void close(List<InputStream> list) {
        if (list != null) {
            for (InputStream is : list) {
                try {
                    is.close();
                } catch (Exception e) {
                    // Ignore at this point.
                }
            }
        }
    }

    private List<InputStream> pathToInputStream(Path bundlePath, List<String> files) throws IOException {
        List<InputStream> is = new ArrayList<>();

        for (String file : files) {
            is.add(Files.newInputStream(bundlePath.resolve(file)));
        }

        return is;
    }
}
