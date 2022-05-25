package com.cloudbees.jenkins.plugins.casc.comparator;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleComparatorTest {

    @Test
    public void test_compare() throws Exception {
        final Path base = Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/comparator/BundleComparatorTest");

        // Same bundle
        BundleComparator.Result result = BundleComparator.compare(base.resolve("original"), base.resolve("original"));
        assertTrue("Same bundle, so no changes", result.sameBundles());
        assertFalse("Same bundle, so no changes", result.getJcasc().withChanges());
        assertThat("Same bundle, so no changes", result.getJcasc().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getJcasc().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getJcasc().getUpdatedFiles(), empty());
        assertFalse("Same bundle, so no changes", result.getItems().withChanges());
        assertThat("Same bundle, so no changes", result.getItems().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getItems().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getItems().getUpdatedFiles(), empty());
        assertFalse("Same bundle, so no changes", result.getRbac().withChanges());
        assertThat("Same bundle, so no changes", result.getRbac().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getRbac().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getRbac().getUpdatedFiles(), empty());
        assertFalse("Same bundle, so no changes", result.getCatalog().withChanges());
        assertThat("Same bundle, so no changes", result.getCatalog().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getCatalog().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getCatalog().getUpdatedFiles(), empty());
        assertFalse("Same bundle, so no changes", result.getPlugins().withChanges());
        assertThat("Same bundle, so no changes", result.getPlugins().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getPlugins().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getPlugins().getUpdatedFiles(), empty());
        assertFalse("Same bundle, so no changes", result.getVariables().withChanges());
        assertThat("Same bundle, so no changes", result.getVariables().getNewFiles(), empty());
        assertThat("Same bundle, so no changes", result.getVariables().getDeletedFiles(), empty());
        assertThat("Same bundle, so no changes", result.getVariables().getUpdatedFiles(), empty());

        // Copied bundle
        result = BundleComparator.compare(base.resolve("original"), base.resolve("copy-of-original"));
        assertTrue("Copied bundle, so no changes", result.sameBundles());
        assertFalse("Copied bundle, so no changes", result.getJcasc().withChanges());
        assertThat("Copied bundle, so no changes", result.getJcasc().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getJcasc().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getJcasc().getUpdatedFiles(), empty());
        assertFalse("Copied bundle, so no changes", result.getItems().withChanges());
        assertThat("Copied bundle, so no changes", result.getItems().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getItems().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getItems().getUpdatedFiles(), empty());
        assertFalse("Copied bundle, so no changes", result.getRbac().withChanges());
        assertThat("Copied bundle, so no changes", result.getRbac().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getRbac().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getRbac().getUpdatedFiles(), empty());
        assertFalse("Copied bundle, so no changes", result.getCatalog().withChanges());
        assertThat("Copied bundle, so no changes", result.getCatalog().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getCatalog().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getCatalog().getUpdatedFiles(), empty());
        assertFalse("Copied bundle, so no changes", result.getPlugins().withChanges());
        assertThat("Copied bundle, so no changes", result.getPlugins().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getPlugins().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getPlugins().getUpdatedFiles(), empty());
        assertFalse("Copied bundle, so no changes", result.getVariables().withChanges());
        assertThat("Copied bundle, so no changes", result.getVariables().getNewFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getVariables().getDeletedFiles(), empty());
        assertThat("Copied bundle, so no changes", result.getVariables().getUpdatedFiles(), empty());

        // Same bundle but version 2
        result = BundleComparator.compare(base.resolve("original"), base.resolve("original-but-version2"));
        assertFalse("Same bundle, but changing to version 2", result.sameBundles());
        assertFalse("Same bundle, but changing to version 2", result.getJcasc().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getJcasc().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getJcasc().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getJcasc().getUpdatedFiles(), empty());
        assertFalse("Same bundle, but changing to version 2", result.getItems().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getItems().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getItems().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getItems().getUpdatedFiles(), empty());
        assertFalse("Same bundle, but changing to version 2", result.getRbac().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getRbac().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getRbac().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getRbac().getUpdatedFiles(), empty());
        assertFalse("Same bundle, but changing to version 2", result.getCatalog().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getCatalog().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getCatalog().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getCatalog().getUpdatedFiles(), empty());
        assertFalse("Same bundle, but changing to version 2", result.getPlugins().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getPlugins().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getPlugins().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getPlugins().getUpdatedFiles(), empty());
        assertFalse("Same bundle, but changing to version 2", result.getVariables().withChanges());
        assertThat("Same bundle, but changing to version 2", result.getVariables().getNewFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getVariables().getDeletedFiles(), empty());
        assertThat("Same bundle, but changing to version 2", result.getVariables().getUpdatedFiles(), empty());

        // Bundle with changes
        result = BundleComparator.compare(base.resolve("original"), base.resolve("changed"));
        assertFalse("Bundle has changed", result.sameBundles());
        assertTrue("Bundle has changed", result.getJcasc().withChanges());
        assertThat("Bundle has changed", result.getJcasc().getNewFiles(), contains("tool.yaml"));
        assertThat("Bundle has changed", result.getJcasc().getDeletedFiles(), contains("unclassified.yaml"));
        assertThat("Bundle has changed", result.getJcasc().getUpdatedFiles(), contains("jenkins.yaml"));
        assertTrue("Bundle has changed", result.getItems().withChanges());
        assertThat("Bundle has changed", result.getItems().getNewFiles(), contains("items3.yaml"));
        assertThat("Bundle has changed", result.getItems().getDeletedFiles(), contains("items1.yaml"));
        assertThat("Bundle has changed", result.getItems().getUpdatedFiles(), contains("items2.yaml"));
        assertTrue("Bundle has changed", result.getRbac().withChanges());
        assertThat("Bundle has changed", result.getRbac().getNewFiles(), contains("rbac3.yaml"));
        assertThat("Bundle has changed", result.getRbac().getDeletedFiles(), contains("rbac1.yaml"));
        assertThat("Bundle has changed", result.getRbac().getUpdatedFiles(), contains("rbac2.yaml"));
        assertTrue("Bundle has changed", result.getCatalog().withChanges());
        assertThat("Bundle has changed", result.getCatalog().getNewFiles(), empty());
        assertThat("Bundle has changed", result.getCatalog().getDeletedFiles(), empty());
        assertThat("Bundle has changed", result.getCatalog().getUpdatedFiles(), contains("plugin-catalog.yaml"));
        assertTrue("Bundle has changed", result.getPlugins().withChanges());
        assertThat("Bundle has changed", result.getPlugins().getNewFiles(), contains("plugins3.yaml"));
        assertThat("Bundle has changed", result.getPlugins().getDeletedFiles(), contains("plugins1.yaml"));
        assertThat("Bundle has changed", result.getPlugins().getUpdatedFiles(), contains("plugins2.yaml"));
        assertTrue("Bundle has changed", result.getVariables().withChanges());
        assertThat("Bundle has changed", result.getVariables().getNewFiles(), contains("variables3.yaml"));
        assertThat("Bundle has changed", result.getVariables().getDeletedFiles(), contains("variables1.yaml"));
        assertThat("Bundle has changed", result.getVariables().getUpdatedFiles(), contains("variables2.yaml"));
    }
}