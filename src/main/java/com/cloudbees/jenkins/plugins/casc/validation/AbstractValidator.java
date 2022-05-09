package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for those validator classes. The implementing classes extending this abstract class must be {@link hudson.Extension}
 */
public abstract class AbstractValidator implements ExtensionPoint {

    /**
     * Return the default {@link ValidationCode} for the validator.
     */
    public abstract ValidationCode getCode();

    /**
     * Perform the validation.
     * @param bundlePath Path to the bundle to validate.
     * @return List with the validation result. Empty list in case there are no errors or warnings.
     */
    public abstract List<Validation> validate(Path bundlePath);

    /**
     * Create a validation error with {@link Validation.Level} set to ERROR
     */
    protected final Validation error(String errorMsg) {
        return Validation.error(this.getCode(), errorMsg);
    }

    /**
     * Create a validation error with {@link Validation.Level} set to WARNING
     */
    protected final Validation warning(String warnMsg) {
        return Validation.warning(this.getCode(), warnMsg);
    }

    /**
     * Parse yaml file
     * @param file to be parsed
     * @return Map containing the parsed contend
     * @throws IOException if the content cannot be parsed
     */
    @CheckForNull
    Map<String, Object> parseYaml(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException(file + " does not exist");
        }
        return parseYaml(FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8));
    }

    /**
     * Parse yaml content
     * @param content to be parsed
     * @return Map containing the parsed contend
     * @throws IOException if the content cannot be parsed
     */
    @CheckForNull
    Map<String, Object> parseYaml(String content) throws IOException {
        try {
            Yaml yaml = new Yaml(new SafeConstructor());
            return yaml.load(content);
        } catch (Exception e) {
            throw new IOException("Error parsing yaml content", e);
        }
    }

    /**
     * Parse the bundle descriptor
     * @param file path to the bundle descriptor
     * @return {@link BundleLoader.BundleDescriptor} if the content can be parsed, null if the content is null
     * @throws IOException if the descriptor cannot be parsed
     */
    @CheckForNull
    protected BundleLoader.BundleDescriptor readDescriptor(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException(file + " does not exist");
        }

        return readDescriptor(FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8));
    }

    /**
     * Parse the bundle descriptor
     * @param content of the bundle descriptor
     * @return {@link BundleLoader.BundleDescriptor} if the content can be parsed, null if the content is null
     * @throws IOException if the descriptor cannot be parsed
     */
    @CheckForNull
    protected BundleLoader.BundleDescriptor readDescriptor(String content) throws IOException {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        Yaml yaml = new Yaml(new RootLimitedConstructor(BundleLoader.BundleDescriptor.class));
        try {
            return yaml.load(content);
        } catch (Exception e) {
            throw new IOException("Error parsing yaml content", e);
        }
    }

    /**
     * Perform the validation from all the validator extensions loaded in the instance on the candidate bundle.
     * @throws InvalidBundleException if any validator find a warning or error.
     */
    public static void validateCandidateBundle() throws InvalidBundleException {
        final BundleUpdateLog.CandidateBundle candidateBundle = ConfigurationBundleManager.get().getUpdateLog().getCandidateBundle();
        if (candidateBundle == null) {
            return;
        }

        final Path candidatePath = BundleUpdateLog.getHistoricalRecordsFolder().resolve(candidateBundle.getFolder()).resolve("bundle");
        if (!Files.exists(candidatePath)) {
            return;
        }

        performValidations(candidatePath);
    }

    /**
     * Perform the validation from all the validator extensions loaded in the instance on the current bundle.
     * @throws InvalidBundleException if any validator find a warning or error.
     */
    public static void validateCurrentBundle() throws InvalidBundleException {
        final ConfigurationBundle currentBundle = ConfigurationBundleManager.get().getConfigurationBundle();
        if (ConfigurationBundle.EMPTY_BUNDLE.equals(currentBundle)) {
            return;
        }

        performValidations(ConfigurationBundleManager.getBundleFolder());
    }

    private static void performValidations(Path path) throws InvalidBundleException {

        List<Validation> validations = new ArrayList<>();
        for(AbstractValidator validator : ExtensionList.lookup(AbstractValidator.class)) {
            validations.addAll(validator.validate(path));
        }
        if (!validations.isEmpty()) {
            // TODO send event to segment - Maybe when CheckForUpdate?
            throw new InvalidBundleException(validations);
        }
    }
}
