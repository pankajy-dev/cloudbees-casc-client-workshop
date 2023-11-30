package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.StringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Contributes CloudBees CasC SCM Retriever related information to the support bundle.
 * It will include the logs for both the init-container and the sidecar
 */

@OptionalExtension(requirePlugins = {"master-provisioning-kubernetes", "support-core"})
public class CascScmRetrieverSupport extends Component {

    private static final Logger LOGGER = Logger.getLogger(CascScmRetrieverSupport.class.getName());

    /**
     * defines the system property key that can be used to ovverride the location where
     * this support component looks for log files.  The log location of the Casc Retriever is not
     * currently user-modifiable so for now this is mainly useful for testing
     */
    public static final String CASC_RETRIEVER_LOG_DIR_SYSPROP_NAME = "CASC_RETRIEVER_LOG_DIRECTORY";
    public static final String CASC_RETRIEVER_NOT_DEPLOYED = "CloudBees CasC SCM Retriever does not appear to be deployed";
    public static final String CASC_RETRIEVER_MD_FILE = "CloudBees CasC SCM Retriever.md";

    public String getLogDirectory() {
        //NOTE: this default value needs to be in sync with what is in the helm charts,
        // currently it is not user-modifiable here or in the charts.
        return System.getProperty(CASC_RETRIEVER_LOG_DIR_SYSPROP_NAME, "/var/jenkins_config/casc-retriever");
    }
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "CloudBees CasC SCM Retriever logs";
    }

    @NonNull
    @Override
    public Component.ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }


    @Override
    public void addContents(@NonNull Container result) {
        if(Files.exists(Path.of(getLogDirectory()))) {
            this.getCascRetrieverLogs(result);
        } else {
            // log directory does not exist, most likely this means that the casc-retriever is not deployed
            result.add((new StringContent(CASC_RETRIEVER_MD_FILE, CASC_RETRIEVER_NOT_DEPLOYED)));
        }
    }

    /**
     * Gather the logs
     * Looks for all files matching *.log.*
     *
     * @param result  The container to add the log file to
     */
    @NonNull
    private void getCascRetrieverLogs(Container result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(getLogDirectory()),   "*.log*")) {
            for (Path entry: stream) {
                File file = entry.toFile();
                if (file != null) {
                    result.add(new FileContent(file.getName(), entry.toFile()));
                }
            }
        }
        catch (IOException e) {
            LOGGER.warning("Problem collecting casc-retriever logs: " + e.getMessage());
        }
    }
}
