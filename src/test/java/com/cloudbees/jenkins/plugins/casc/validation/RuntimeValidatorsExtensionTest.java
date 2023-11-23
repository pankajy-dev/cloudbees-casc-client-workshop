package com.cloudbees.jenkins.plugins.casc.validation;

import com.cloudbees.jenkins.cjp.installmanager.casc.InvalidBundleException;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.PlainBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RuntimeValidatorsExtensionTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PlainBundle<?> bundle;

    @Before
    public void setUp() throws IOException {
        bundle = mock(PlainBundle.class);
    }

    @Test
    public void testIOExceptionOnFolder() {
        try (MockedStatic<ConfigurationUpdaterHelper> configurationUpdaterHelperMockedStatic = Mockito.mockStatic(ConfigurationUpdaterHelper.class)) {
            // GIVEN a temporary folder creation that fails
            configurationUpdaterHelperMockedStatic
                    .when(ConfigurationUpdaterHelper::createTemporaryFolder)
                    .thenThrow(new IOException("Test exception"));

            // WHEN
            List<Validation> validations = new RuntimeValidatorsExtension().performRuntimeValidation(bundle);

            // THEN
            assertThat("The validations should be en empty list", validations, is(empty()));
        }
    }

    @Test
    public void testTemporaryFolderCreationAndDeletion() throws IOException {

        try (MockedStatic<ConfigurationUpdaterHelper> configurationUpdaterHelperMockedStatic = Mockito.mockStatic(ConfigurationUpdaterHelper.class);
             MockedStatic<AbstractValidator> abstractValidatorMockedStatic = Mockito.mockStatic(AbstractValidator.class)) {
            // Mock the temporary folder for later verification
            Path bundleFolder = temporaryFolder.newFolder().toPath();
            configurationUpdaterHelperMockedStatic.when(ConfigurationUpdaterHelper::createTemporaryFolder).thenReturn(bundleFolder);

            // Mock performValidation for later verification
            abstractValidatorMockedStatic.when(() -> AbstractValidator.performValidations(any())).thenAnswer((Answer<Void>) invocationOnMock -> {
                // WHEN the validation is performed

                // THEN the content of bundle.yaml should be the same as in the provided bundle
                Path bundleFile = bundleFolder.resolve("bundle.yaml");
                assertThat("Bundle file should exists", bundleFile.toFile().exists(), is(true));
                assertThat("Bundle file content is the descriptor", Files.readString(bundleFile), is("0123456789"));

                // THEN the content of 'a' file should be the same as in the provided bundle
                Path aPath = bundleFolder.resolve("a");
                assertThat("'a' file should exists", aPath.toFile().exists(), is(true));
                assertThat("Bundle file content is the descriptor", Files.readString(aPath), is("a content"));

                // THEN the content of 'b' file should be the same as in the provided bundle
                Path bPath = bundleFolder.resolve("b");
                assertThat("'b' file should exists", bPath.toFile().exists(), is(true));
                assertThat("Bundle file content is the descriptor", Files.readString(bPath), is("b content"));

                // THEN `null` content should be skipped
                Path nullPath = bundleFolder.resolve("null");
                assertThat("'null' file should not exists", nullPath.toFile().exists(), is(false));

                // THEN no other files should be present
                try (Stream<Path> list = Files.list(bundleFolder)) {
                    assertThat(
                            "There should be only 3 files in the folder: bundle.yaml, a and b",
                            list.map(Path::getFileName).map(Path::toString).collect(Collectors.toList()),
                            containsInAnyOrder("bundle.yaml", "a", "b")
                    );
                }
                return null;
            });

            // GIVEN a non-empty bundle
            // This does not need to be a valid bundle, no validation will be performed
            // Real validators are tested elsewhere
            when(bundle.getDescriptor()).thenReturn("0123456789");
            when(bundle.getFiles()).thenReturn(List.of("a", "b", "null"));
            when(bundle.getFile("a")).thenReturn("a content");
            when(bundle.getFile("b")).thenReturn("b content");
            when(bundle.getFile("null")).thenReturn(null);

            // WHEN
            new RuntimeValidatorsExtension().performRuntimeValidation(bundle);

            // THEN
            assertThat("The temporary folder should be deleted", bundleFolder.toFile().exists(), is(false));
        }
    }

    @Test
    public void testTemporaryFolderCreationAndDeletionWhenUnexpectedError() throws IOException {

        try (MockedStatic<ConfigurationUpdaterHelper> configurationUpdaterHelperMockedStatic = Mockito.mockStatic(ConfigurationUpdaterHelper.class);
             MockedStatic<AbstractValidator> abstractValidatorMockedStatic = Mockito.mockStatic(AbstractValidator.class)) {
            // Mock the temporary folder for later verification
            Path bundleFolder = temporaryFolder.newFolder().toPath();
            configurationUpdaterHelperMockedStatic.when(ConfigurationUpdaterHelper::createTemporaryFolder).thenReturn(bundleFolder);

            // Mock performValidation for later verification
            abstractValidatorMockedStatic
                    .when(() -> AbstractValidator.performValidations(any()))
                    .thenThrow(new NullPointerException("Test exception"));

            // GIVEN a non-empty bundle
            // The goal is to create at least two files and check that the folder is properly deleted when an exception occurs
            when(bundle.getDescriptor()).thenReturn("0123456789");
            when(bundle.getFiles()).thenReturn(List.of("a"));
            when(bundle.getFile("a")).thenReturn("a content");

            // WHEN
            assertThrows(NullPointerException.class, () -> new RuntimeValidatorsExtension().performRuntimeValidation(bundle));

            // THEN
            assertThat("The temporary folder should be deleted", bundleFolder.toFile().exists(), is(false));
        }
    }

    @Test
    public void testReturnEmptyListIfNoError() throws IOException {

        try (MockedStatic<ConfigurationUpdaterHelper> configurationUpdaterHelperMockedStatic = Mockito.mockStatic(ConfigurationUpdaterHelper.class);
             MockedStatic<AbstractValidator> abstractValidatorMockedStatic = Mockito.mockStatic(AbstractValidator.class)) {
            // Mock the temporary folder for later verification
            Path bundleFolder = temporaryFolder.newFolder().toPath();
            configurationUpdaterHelperMockedStatic.when(ConfigurationUpdaterHelper::createTemporaryFolder).thenReturn(bundleFolder);

            // GIVEN performValidations is OK
            abstractValidatorMockedStatic.when(() -> AbstractValidator.performValidations(any())).thenAnswer((Answer<Void>) invocationOnMock -> null);

            // GIVEN a non-empty bundle
            // This does not need to be a valid bundle, no validation will be performed
            // Real validators are tested elsewhere
            when(bundle.getDescriptor()).thenReturn("");

            // WHEN
            List<Validation> validations = new RuntimeValidatorsExtension().performRuntimeValidation(bundle);

            // THEN
            assertThat("The validation list should be empty", validations, is(empty()));

            // THEN
            assertThat("The temporary folder should be deleted", bundleFolder.toFile().exists(), is(false));
        }
    }

    @Test
    public void testReturnErrorsIfExceptionIsThrown() throws IOException {

        try (MockedStatic<ConfigurationUpdaterHelper> configurationUpdaterHelperMockedStatic = Mockito.mockStatic(ConfigurationUpdaterHelper.class);
             MockedStatic<AbstractValidator> abstractValidatorMockedStatic = Mockito.mockStatic(AbstractValidator.class)) {
            // Mock the temporary folder for later verification
            Path bundleFolder = temporaryFolder.newFolder().toPath();
            configurationUpdaterHelperMockedStatic.when(ConfigurationUpdaterHelper::createTemporaryFolder).thenReturn(bundleFolder);

            // GIVEN performValidations found errors
            Validation error1 = Validation.error(ValidationCode.JCASC_CONFIGURATION, "Test 1");
            Validation error2 = Validation.error(ValidationCode.RBAC_CONFIGURATION, "Test 2");
            InvalidBundleException validation = new InvalidBundleException(List.of(error1, error2));
            abstractValidatorMockedStatic.when(() -> AbstractValidator.performValidations(any())).thenThrow(validation);

            // GIVEN a non-empty bundle
            // This does not need to be a valid descriptor, no validation will be performed
            // Real validators are tested elsewhere
            when(bundle.getDescriptor()).thenReturn("");

            // WHEN
            List<Validation> validations = new RuntimeValidatorsExtension().performRuntimeValidation(bundle);

            // THEN
            assertThat("The validation list should contains the errors", validations, containsInAnyOrder(error1, error2));

            // THEN
            assertThat("The temporary folder should be deleted", bundleFolder.toFile().exists(), is(false));
        }
    }
}