package com.cloudbees.jenkins.plugins.casc.analytics;

import com.cloudbees.analytics.gatherer.MockRecordingSenderAbstractTest;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.ValidationCode;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BundleValidationErrorGathererTest extends MockRecordingSenderAbstractTest {

    @Rule
    public final TemporaryFolder tmpFolder = new TemporaryFolder();

    private String initialValue;
    private File bundleSrc;

    @Before
    public void setUp() throws Exception {
        initialValue = System.getProperty("core.casc.config.bundle");

        Path initialVersion = ConfigurationBundleManager.getBundleFolder();
        bundleSrc = tmpFolder.newFolder("bundle");
        FileUtils.copyDirectory(initialVersion.toFile(), bundleSrc);

        System.setProperty("core.casc.config.bundle", bundleSrc.getAbsolutePath());
        ConfigurationBundleManager.reset();
        ConfigurationBundleManager.get(); // Promote first version
    }

    @After
    public void tearDown() {
        if (StringUtils.isBlank(initialValue)) {
            System.clearProperty("core.casc.config.bundle");
        } else {
            System.setProperty("core.casc.config.bundle", initialValue);
        }
    }

    @Test
    @LocalData
    public void smokes() throws Exception {
        FileUtils.deleteDirectory(bundleSrc);
        bundleSrc = tmpFolder.newFolder("bundle");
        Path newVersion = Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/analytics/BundleValidationErrorGathererTest/new-version");
        FileUtils.copyDirectory(newVersion.toFile(), bundleSrc);
        ConfigurationUpdaterHelper.checkForUpdates();
        Map<String, Object> expected = Stream.of(new String[][]{{BundleValidationErrorGatherer.Event.PROP_VALIDATION_CODE, ValidationCode.JCASC_CONFIGURATION.code()},
                        {BundleValidationErrorGatherer.Event.PROP_TOTAL_ERRORS, "1"},
                        {BundleValidationErrorGatherer.Event.PROP_TOTAL_WARNINGS, "0"}})
                .collect(Collectors.toMap(data -> data[0], data -> StringUtils.isNumeric(data[1]) ? Long.parseLong(data[1]) : data[1]));
        assertEventsSent(BundleValidationErrorGatherer.Event.EVENT, expected);
    }

}