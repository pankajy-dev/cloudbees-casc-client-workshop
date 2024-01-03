package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.BundleLoader;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.jenkins.plugins.casc.YamlClientUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
            Yaml yaml = YamlClientUtils.createDefault();
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
        RootLimitedConstructor rootLimitedConstructor = new RootLimitedConstructor(BundleLoader.BundleDescriptor.class);

        Yaml yaml = YamlClientUtils.Builder.create().setBaseConstructor(rootLimitedConstructor).build();
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

    /**
     * Perform the validation from all the validator extensions loaded in the instance on the current located in a concrete path.
     * If the bundle does not exist, then the validations are not executed.
     * This method is thought to be used by the CLI and the HTTP Endpoint.
     * @param path to find the bundle to validate
     * @throws InvalidBundleException if any validator find a warning or error.
     */
    public static void performValidations(@NonNull Path path) throws InvalidBundleException {
        if (!Files.exists(path)) {
            return;
        }

        List<Validation> validations = new ArrayList<>();
        for(AbstractValidator validator : ExtensionList.lookup(AbstractValidator.class)) {
            validations.addAll(validator.validate(path));
        }
        if (!validations.isEmpty()) {
            throw new InvalidBundleException(validations);
        }
    }

    /**
     * Log the validation as follows:
     * <ul>
     *     <li>Validations with {@link com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation.Level#INFO} are logged individually</li>
     *     <li>All others validations are joined into a single {@link String} using {@link String#format(String, Object...)}</li>
     * </ul>
     * @param logger The logger to use
     * @param warningAndErrorMessage Log the warnings and errors using this message. It must include one "%s" which will be used to display joined messages.
     * @param validations The validations to display.
     */
    public static void logValidation(Logger logger, String warningAndErrorMessage, Collection<Validation> validations) {
        if (!validations.isEmpty()) {
            List<Validation> infos =
                    validations.stream().filter(validation -> validation.getLevel() == Validation.Level.INFO).collect(Collectors.toList());
            infos.forEach(validation -> logger.log(Level.INFO, validation.getMessage()));

            List<String> other = validations
                    .stream()
                    .filter(validation -> validation.getLevel() != Validation.Level.INFO)
                    .map(Validation::getMessage)
                    .collect(Collectors.toList());
            if (!other.isEmpty()) {
                logger.log(Level.WARNING, String.format(warningAndErrorMessage, String.join(",", other)));
            }
        }
    }

    protected List<Validation> checkFiles(List<String> files, Path bundlePath, String section) {
        List<Validation> errors = new ArrayList<>();
        List<String> filesNotFound = new ArrayList<>();
        List<String> filesUnparseable = new ArrayList<>();
        for (String file : files) {
            Path path = bundlePath.resolve(file);
            if (!Files.exists(path)) {
                filesNotFound.add(file);
            } else {
                try {
                    Map<String, Object> parsed = parseYaml(path);
                    if (parsed == null || parsed.isEmpty()) {
                        filesUnparseable.add(file);
                    }
                } catch (IOException e) {
                    filesUnparseable.add(file);
                }
            }
        }
        if (!filesNotFound.isEmpty()) {
            String notFound = filesNotFound.stream().collect(Collectors.joining(", "));
            errors.add(error(String.format("The bundle.yaml file references %s in the %s section that cannot be found. Impossible to validate %s.", notFound, section, section)));
        }
        if (!filesUnparseable.isEmpty()) {
            String unparseable = filesUnparseable.stream().collect(Collectors.joining(", "));
            errors.add(error(String.format("The bundle.yaml file references %s in the %s section that is empty or has an invalid yaml format. Impossible to validate %s.",
                                             unparseable, section, section)));
        }
        return errors;
    }
}
