// https://cloudbees.atlassian.net/browse/OSS-1842 workaround
echo "Checking Jenkinsfile existence"
// def jenkinsfile
// try {
//     jenkinsfile = readTrusted 'Jenkinsfile'
//     echo "Existing Jenkinsfile found, using it instead of the default one!"
// } catch (e) {
//     echo "No Jenkinsfile found, using default behaviour"
// }
// if (jenkinsfile != null) {
//     evaluate jenkinsfile
//     return
// }
// END OSS-1842
jdkConfig = readYaml text: readPipelineRelative('config/jdks.yaml')
defaultConfig = readYaml text: readPipelineRelative('config/defaults.yaml')

//////////////////////////////////////////////////////////////////
// Environment variables to be set at organization folder level
//////////////////////////////////////////////////////////////////
// Name to use on commits
GIT_USERNAME = env.GIT_USERNAME
// Email address to use on commits
NOTIFICATION_TARGET = env.NOTIFICATION_TARGET
// Slack channel to send notifications to.
SLACK_CHANNEL = env.SLACK_CHANNEL
// Slack channel to send release notifications to.  If not defined it defaults to the value of ${SLACK_CHANNEL}
RELEASE_SLACK_CHANNEL = env.RELEASE_SLACK_CHANNEL?:SLACK_CHANNEL
// JIRA project to filter issues from. Only JIRA references from this project will be used during releases.
JIRA_PROJECT = env.JIRA_PROJECT ?: defaultConfig.jiraProject
// URR branch to use when building URR for ATHs run. Can be set by environment variable or by marker file (for maintenance branches)
URR_BRANCH = env.URR_BRANCH ?: defaultConfig.urrBranch
// JDK to use by default, values can be jdk11 or jdk8
DEFAULT_JDK = env.DEFAULT_JDK?: defaultConfig.jdk
// Team responsible for the artifact being built (to assign URR PR and add labels).  If not defined it defaults to team-gaia
TEAM = env.TEAM?: defaultConfig.team

//////////////////////////////////////////////////////////////////
// Variables that can be set through the marker file
//////////////////////////////////////////////////////////////////

// Options applied to all maven commands
GLOBAL_MAVEN_OPTS = defaultConfig.mavenOptions
// additional options for all maven commands, override via markerfile (for ex, older mvn versions
// do not support "--no-transfer-progress")
MAVEN_EXTRA_OPTS = ''
// maven options specific to the linux stage
LINUX_MAVEN_EXTRA_OPTS = ''
// maven options specific to the windows stage
WINDOWS_MAVEN_EXTRA_OPTS = ''
// maven options specific to the coverage stage
COVERAGE_MAVEN_EXTRA_OPTS = ''
// Enable this flag to just run `mvn install` without any type of test.
// Most of the pipeline stages will be skipped.
POM_ONLY = 'false'
// Enable or disable checkstyle analysis.
// Default: false (disabled)
CHECKSTYLE = 'false'
CHECKSTYLE_ALLOWED_VIOLATIONS = '0'

// Enable or disable the Windows build.
// Default: true (enabled)
WINDOWS_BUILD = 'true'

// Display name to use in release notes
// Default: pom.name
DISPLAY_NAME = ''

// Release notes ID to identify the plugin in the release notes repo.
// Default: pom.artifactId
RELEASE_NOTES_ID = ''
// Artifact Ids from multi-module project separated by space. Currently specified in marker file.
RELEASE_ARTIFACT_IDS = ''

// CJOC/Master/License ATH tests to run.
// Only categories can be used. For example: "groups={com.cloudbees.opscenter.categories.OperationsCenterContextCategory}"
CJOC_ATH_TESTS=''
MASTER_ATH_TESTS=''
LICENSE_ATH_TESTS=''
// CJOC/Master products to use for ATH
CJOC_ATH_PRODUCT='core-oc-traditional'
MASTER_ATH_PRODUCT='core-cm'

// the version (sha or branchname) to use for the ATHs
OC_ATH_VERSION=''
JE_ATH_VERSION=''

// Sets the form-element-path plugin version for ATH runs
// Used when running ATHs against ancient versions of Jenkins
FORM_ELEMENT_PATH_VERSION = ''

// Integration tests
// Script to run integration tests. Should be located in this repository under the utils folder.
INTEGRATION_TESTS_SCRIPT = env.INTEGRATION_TESTS_SCRIPT ?: ''
// Arguments to provide to the integration tests script
INTEGRATION_TESTS_ARGUMENTS = env.INTEGRATION_TESTS_ARGUMENTS ?:''
// Whether integration tests make the build fail if they fail
INTEGRATION_TESTS_FATAL = env.INTEGRATION_TESTS_FATAL ?:'true'
// Skip integration tests on occasions (if INTEGRATION_TESTS_SCRIPT is set)
SKIP_INTEGRATION_TESTS = 'false'

// Images references under k8s-pod-definitions for all stages in this pipeline
K8S_MARKER_POD = 'linux'
K8S_POM_ONLY_POD = 'linux'
K8S_PLUGIN_BUILD_POD = 'linux-plugin-build'
K8S_WINDOWS_PLUGIN_BUILD_POD = 'windows-plugin-build'
K8S_COVERAGE_BUILD_POD = 'linux-plugin-build'
K8S_RELEASE_CHECK_POD = 'linux-plugin-build'
K8S_CHECKSTYLE_POD = 'linux-plugin-build' // TODO this pod template is too heavyweight
K8S_URR_POD = 'linux-plugin-build' // TODO this pod template is too heavyweight
K8S_OC_ATH_POD = 'oc-ath'
K8S_CONTROLLERS_ATH_POD = 'controllers-ath'
K8S_RELEASE_POD = 'linux-plugin-build'
K8S_INTEGRATION_TESTS_POD = 'linux-plugin-integration'
// Surefire settings for linux
SUREFIRE_FORK_COUNT = '4'
// Surefire settings for windows
SUREFIRE_FORK_COUNT_WINDOWS = '4'
// When set to true, the linux and coverage stages will run in a VM with a docker daemon
NEED_DOCKER_VM = 'false'
// When set to true, the integration stage will run in a VM with a docker daemon
NEED_DOCKER_VM_FOR_IT = 'false'
// do not skip URR stage by default
SKIP_URR_STAGE = 'false'
// Set to true to skip the PR creation to URR.
SKIP_URR_PR = 'false'
// Set to true to skip promotion to mct
SKIP_PROMOTE = 'false'
// Whether to run tests/coverage on Java 8
JAVA8 = env.JAVA8?:'true'
// Whether to run tests/coverage on Java 11
JAVA11 = env.JAVA11?:'true'

//////////////////////////////////////////////////////////////////
// Internal variables
//////////////////////////////////////////////////////////////////

// Internal attributes set by the build itself. Don't configure externally.
// Maven project version
VERSION = ''
// Name of the person who approved the release
APPROVER = ''
// Whether the current commit can be released
RELEASABLE = 'true' // Hack for JENKINS-27092
// Artifact ID of the maven project. If multi-module, it will be the artifact ID of the aggregator project.
RELEASE_ARTIFACTID = ''
// Version of the maven project to release
RELEASE_VERSION = ''
// Control flags for optional stages
RUN_JACOCO = 'true'
RUN_HA_TEST=''
RUN_CJOC_ATH = 'false'
RUN_MASTER_ATH = 'false'
RUN_LICENSE_ATH = 'false'

contributorsSlackID = []
contributorsGitHubHandles = []

//////////////////////////////////////////////////////////////////
// Methods to override variables defined above through marker file
// These workaround the "method too large" issue that we are facing when putting all of this in a single script{} block
//////////////////////////////////////////////////////////////////
def overrides(def PROPS) {
    // override default maven extra options?
    if (PROPS['MAVEN_EXTRA_OPTS'] != null)  {
        MAVEN_EXTRA_OPTS = PROPS['MAVEN_EXTRA_OPTS']
        echo "USING ADDITIONAL MAVEN OPTIONS ${MAVEN_EXTRA_OPTS}"
    }

    if (PROPS['LINUX_MAVEN_EXTRA_OPTS'] != null)  {
        LINUX_MAVEN_EXTRA_OPTS = PROPS['LINUX_MAVEN_EXTRA_OPTS']
        echo "USING ADDITIONAL LINUX MAVEN OPTIONS ${LINUX_MAVEN_EXTRA_OPTS}"
    }

    if (PROPS['WINDOWS_MAVEN_EXTRA_OPTS'] != null)  {
        WINDOWS_MAVEN_EXTRA_OPTS = PROPS['WINDOWS_MAVEN_EXTRA_OPTS']
        echo "USING ADDITIONAL WINDOWS MAVEN OPTIONS ${WINDOWS_MAVEN_EXTRA_OPTS}"
    }

    if (PROPS['COVERAGE_MAVEN_EXTRA_OPTS'] != null)  {
        COVERAGE_MAVEN_EXTRA_OPTS = PROPS['COVERAGE_MAVEN_EXTRA_OPTS']
        echo "USING ADDITIONAL COVERAGE MAVEN OPTIONS ${COVERAGE_MAVEN_EXTRA_OPTS}"
    }
    // skip the urr stage?
    if (PROPS['SKIP_URR_STAGE'] != null) {
        SKIP_URR_STAGE = PROPS['SKIP_URR_STAGE']
        echo "SKIP URR STAGE? ${SKIP_URR_STAGE}"
    }

    if (PROPS['SKIP_PROMOTE'] != null) {
        SKIP_PROMOTE = PROPS['SKIP_PROMOTE']
        echo "SKIP PROMOTE ? ${SKIP_PROMOTE}"
    }

    if (PROPS['POM_ONLY'] == "true") {
        echo "POM ONLY => DISABLING JUNIT"
        POM_ONLY = "true"
    } else {
        echo "NO POM ONLY SETTINGS => JUNIT ENABLED"
    }
    if (PROPS['RUN_JACOCO'] == "false") {
        echo "RUN_JACOCO set to false => JaCoCo stage will be skipped"
        RUN_JACOCO = "false"
    }
    if (PROPS['DEFAULT_JDK'] != null) {
        DEFAULT_JDK = PROPS['DEFAULT_JDK']
        echo 'DEFAULT_JDK = ' + DEFAULT_JDK
    }
    // build for default jdk should be enabled, unless overridden below
    if (DEFAULT_JDK == 'jdk11') {
        JAVA11 = 'true'
    }
    if (DEFAULT_JDK == 'jdk8') {
        JAVA8 = 'true'
    }
    // Markerfile overrides
    if (PROPS['JAVA8'] != null) {
        JAVA8 = PROPS['JAVA8']
        echo 'JAVA8 = ' + JAVA8
    }
    if (PROPS['JAVA11'] != null) {
        JAVA11 = PROPS['JAVA11']
        echo 'JAVA11 = ' + JAVA11
    }
    // Only run Checkstyle on an opt-in basis (for the moment)
    // TODO - change this to opt-out so that we do it on everything once the repos are 'clean'
    if (PROPS['CHECKSTYLE'] != null) {
        echo "enabling checkstyle"
        CHECKSTYLE = PROPS['CHECKSTYLE']
        if(PROPS['CHECKSTYLE_ALLOWED_VIOLATIONS'] != null) {
            CHECKSTYLE_ALLOWED_VIOLATIONS = PROPS['CHECKSTYLE_ALLOWED_VIOLATIONS']
        }
    }
    if (PROPS['WINDOWS'] != 'true') {
        WINDOWS_BUILD = "false"
    }
    echo 'Build on Windows: ' + WINDOWS_BUILD

    if (PROPS['SKIP_INTEGRATION_TESTS'] != null) {
        SKIP_INTEGRATION_TESTS = PROPS['SKIP_INTEGRATION_TESTS']
    }
}

def overridesMavenPom(def PROPS, def pom) {
    VERSION = pom.version
    RELEASE_VERSION = pom.version.replaceAll('-SNAPSHOT', '')
    RELEASE_ARTIFACTID = pom.artifactId
    RELEASE_ARTIFACT_IDS = PROPS['RELEASE_ARTIFACT_IDS'] ?: RELEASE_ARTIFACTID
    DISPLAY_NAME = PROPS['DISPLAY_NAME'] ?: pom.name
    RELEASE_NOTES_ID = PROPS['RELEASE_NOTES_ID'] ?: pom.artifactId
}
def overridesATH(def PROPS) {
    // Only run License ATH if marker file contains LICENSE_ATH_TESTS property
    if (PROPS['LICENSE_ATH_TESTS']) {
        echo "LICENSE_ATH_TESTS => Enabling License ATH execution"
        RUN_LICENSE_ATH = "true"
        LICENSE_ATH_TESTS = PROPS['LICENSE_ATH_TESTS']
    } else {
        echo "No LICENSE_ATH_TESTS => Disabling License ATH execution"
    }

    // Only run CJOC ATH if marker file contains CJOC_ATH_TESTS property
    if (PROPS['CJOC_ATH_TESTS']) {
        echo "CJOC_ATH_TESTS => Enabling CJOC ATH execution"
        RUN_CJOC_ATH = "true"
        CJOC_ATH_TESTS = PROPS['CJOC_ATH_TESTS']
    } else {
        echo "No CJOC_ATH_TESTS => Disabling CJOC ATH execution"
    }

    // Only run Master ATH if marker file contains MASTER_ATH_TESTS property
    if (PROPS['MASTER_ATH_TESTS']) {
        echo "MASTER_ATH_TESTS => Enabling Master ATH execution"
        RUN_MASTER_ATH = "true"
        MASTER_ATH_TESTS = PROPS['MASTER_ATH_TESTS']
    } else {
        echo "No MASTER_ATH_TESTS => Disabling Master ATH execution"
    }
    CJOC_ATH_PRODUCT = PROPS['CJOC_ATH_PRODUCT'] ?: CJOC_ATH_PRODUCT
    echo 'CJOC_ATH_PRODUCT=' + CJOC_ATH_PRODUCT
    MASTER_ATH_PRODUCT = PROPS['MASTER_ATH_PRODUCT'] ?: MASTER_ATH_PRODUCT
    echo 'MASTER_ATH_PRODUCT=' + MASTER_ATH_PRODUCT
    if (CJOC_ATH_PRODUCT == 'core-oc') {
        RUN_HA_TEST = 'false'
        echo 'Not running HA tests on product ' + CJOC_ATH_PRODUCT
    } else {
        RUN_HA_TEST = 'true'
        echo 'Enabling HA tests on product ' + CJOC_ATH_PRODUCT
    }

    FORM_ELEMENT_PATH_VERSION = PROPS['FORM_ELEMENT_PATH_VERSION'] ?: FORM_ELEMENT_PATH_VERSION

    OC_ATH_VERSION = PROPS['OC_ATH_VERSION'] ?: OC_ATH_VERSION
    JE_ATH_VERSION = PROPS['JE_ATH_VERSION'] ?: JE_ATH_VERSION
    if (OC_ATH_VERSION) {
        echo 'OC ATH version: ' + OC_ATH_VERSION
    }
    if (JE_ATH_VERSION) {
        echo 'JE ATH version: ' + JE_ATH_VERSION
    }
}
def overridesPods(def PROPS) {
    // Kubernetes pod overrides
    K8S_MARKER_POD = PROPS['K8S_MARKER_POD'] ?: K8S_MARKER_POD
    K8S_POM_ONLY_POD = PROPS['K8S_POM_ONLY_POD'] ?: K8S_POM_ONLY_POD
    K8S_PLUGIN_BUILD_POD = PROPS['K8S_PLUGIN_BUILD_POD'] ?: K8S_PLUGIN_BUILD_POD
    K8S_WINDOWS_PLUGIN_BUILD_POD = PROPS['K8S_WINDOWS_PLUGIN_BUILD_POD'] ?: K8S_WINDOWS_PLUGIN_BUILD_POD
    K8S_COVERAGE_BUILD_POD = PROPS['K8S_COVERAGE_BUILD_POD'] ?: K8S_COVERAGE_BUILD_POD
    K8S_RELEASE_CHECK_POD = PROPS['K8S_RELEASE_CHECK_POD'] ?: K8S_RELEASE_CHECK_POD
    K8S_CHECKSTYLE_POD = PROPS['K8S_CHECKSTYLE_POD'] ?: K8S_CHECKSTYLE_POD
    K8S_URR_POD = PROPS['K8S_URR_POD'] ?: K8S_URR_POD
    K8S_OC_ATH_POD = PROPS['K8S_OC_ATH_POD'] ?: K8S_OC_ATH_POD
    K8S_CONTROLLERS_ATH_POD = PROPS['K8S_CONTROLLERS_ATH_POD'] ?: K8S_CONTROLLERS_ATH_POD
    K8S_RELEASE_POD = PROPS['K8S_RELEASE_POD'] ?: K8S_RELEASE_POD
}

def overridesSurefire(def PROPS) {
    SUREFIRE_FORK_COUNT = PROPS['SUREFIRE_FORK_COUNT'] ?: SUREFIRE_FORK_COUNT
    SUREFIRE_FORK_COUNT_WINDOWS = PROPS['SUREFIRE_FORK_COUNT_WINDOWS'] ?: SUREFIRE_FORK_COUNT_WINDOWS
    NEED_DOCKER_VM = PROPS['NEED_DOCKER_VM'] ?: NEED_DOCKER_VM
}

def overridesURR(def PROPS) {
    SKIP_URR_PR = PROPS['SKIP_URR_PR'] ?: SKIP_URR_PR
    URR_BRANCH = PROPS['URR_BRANCH'] ?: URR_BRANCH
}

def overridesIT(def PROPS) {
    K8S_INTEGRATION_TESTS_POD = PROPS['K8S_INTEGRATION_TESTS_POD'] ?: K8S_INTEGRATION_TESTS_POD
    INTEGRATION_TESTS_SCRIPT = PROPS['INTEGRATION_TESTS_SCRIPT'] ?: INTEGRATION_TESTS_SCRIPT
    INTEGRATION_TESTS_ARGUMENTS = PROPS['INTEGRATION_TESTS_ARGUMENTS'] ?: INTEGRATION_TESTS_ARGUMENTS
    INTEGRATION_TESTS_FATAL = PROPS['INTEGRATION_TESTS_FATAL'] ?: INTEGRATION_TESTS_FATAL
    NEED_DOCKER_VM_FOR_IT = PROPS['NEED_DOCKER_VM_FOR_IT'] ?: NEED_DOCKER_VM_FOR_IT
}

/**
 *
 * @param jdk jdk version to use. Example: jdk8, jdk11
 * @param mainBuildStage There is only one main build stage per build, where artifacts and spotbugs results are collected.
 * @return
 */
def buildLinux(String jdk, boolean mainBuildStage) {
    def linuxNode = { body ->
        if (NEED_DOCKER_VM == 'true') {
            node(jdkConfig[jdk].dockerLabel) {
                body.call()
            }
        } else {
            podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/' + K8S_PLUGIN_BUILD_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[jdk].agentImage.linux)) {
                node(POD_LABEL) {
                    container('maven') {
                        body.call()
                    }
                }
            }
        }
    }
    linuxNode {
        deleteDir()
        checkout scm
        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3', options: [artifactsPublisher(disabled: !mainBuildStage), junitPublisher(disabled: true), findbugsPublisher(disabled: true)]) {
            withEnv([
                    'MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS + ' ' + LINUX_MAVEN_EXTRA_OPTS,
                    'SUREFIRE_FORK_COUNT='+SUREFIRE_FORK_COUNT
            ]) {
            //TODO: NEVER upload this
                sh 'mvn ${MAVEN_ARGS} -DforkCount=${SUREFIRE_FORK_COUNT} -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false -DskipTests install'
            }
        }
//        junit testResults: '**/*-reports/*.xml'

        //Quality gate: we want zero spotbugs issue, so unstable at the first one.
        if (mainBuildStage) {
            recordIssues(
                    tool: spotBugs(), qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]]
            )
        }
    }
}

def buildWindows(String jdk) {
    checkout scm
    withEnv(['MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS + ' ' + WINDOWS_MAVEN_EXTRA_OPTS,
             'SUREFIRE_FORK_COUNT=' + SUREFIRE_FORK_COUNT_WINDOWS]) {
        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3',
                options: [artifactsPublisher(disabled: true), junitPublisher(disabled: true), findbugsPublisher(disabled: true), openTasksPublisher(disabled: true)]) {
            bat 'mvn %MAVEN_ARGS% -DforkCount=%SUREFIRE_FORK_COUNT% -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false -DskipTests install'
        }
        junit testResults: '**/*-reports/*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]
    }
}

def coverage(String jdk) {
    def coverageNode = { body ->
        if (NEED_DOCKER_VM == 'true') {
            node(jdkConfig[jdk].dockerLabel) {
                body.call()
            }
        } else {
            podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/' + K8S_COVERAGE_BUILD_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[jdk].agentImage.linux)) {
                node(POD_LABEL) {
                    container('maven') {
                        body.call()
                    }
                }
            }
        }
    }
    coverageNode {
        deleteDir()
        checkout scm
        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3',
                publisherStrategy: 'EXPLICIT',
                options: [artifactsPublisher(disabled: true), junitPublisher(disabled: true), jacocoPublisher()]) {
            withEnv([
                    'MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS + ' ' + COVERAGE_MAVEN_EXTRA_OPTS,
                    'SUREFIRE_FORK_COUNT='+SUREFIRE_FORK_COUNT
            ]) {
                sh 'mvn ${MAVEN_ARGS} -DforkCount=${SUREFIRE_FORK_COUNT} -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false -Penable-jacoco install'
            }
        }
        junit testResults: '**/*-reports/*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]
    }
}

String buildDockerImage(String jdk) {
    podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/kaniko.yaml')) {
        node(POD_LABEL) {
            timeout(30) {
                ansiColor('xterm') {
                    dir('docker/images/') {
                        writeFile file: 'buildDockerImage', text: readPipelineRelative('docker/images/buildDockerImage')
                        dir('maven-open' + jdk) {
                            writeFile file: 'Dockerfile', text: readPipelineRelative('docker/images/maven-open' + jdk + '/Dockerfile')
                            def image = 'gcr.io/cloudbees-ops-gcr/gaia/maven-open' + jdk + ':' + sh(script: 'sha256sum Dockerfile | cut -c-7', returnStdout: true).trim()
                            withEnv(['IMAGE=' + image]) {
                                container('kaniko') {
                                    sh 'sh ../buildDockerImage $IMAGE'
                                }
                            }
                            return image
                        }
                    }
                }
            }
        }
    }
}

def runIntegration(String jdk) {
    def itNode = { body ->
        if (NEED_DOCKER_VM_FOR_IT == 'true') {
            node(jdkConfig[jdk].dockerLabel) {
                body.call()
            }
        } else {
            podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/' + K8S_INTEGRATION_TESTS_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[jdk].agentImage.linux)) {
                node(POD_LABEL) {
                    container('maven') {
                        body.call()
                    }
                }
            }
        }
    }
    itNode {
        checkout scm
        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3', publisherStrategy: 'EXPLICIT', traceability: false) {
            if (!fileExists(INTEGRATION_TESTS_SCRIPT)) {
                echo 'Did not find integration tests script: ' + INTEGRATION_TESTS_SCRIPT + ' in the current repository, checking out from the default pipeline repository'
                writeFile file: INTEGRATION_TESTS_SCRIPT, text: readPipelineRelative('utils/' + INTEGRATION_TESTS_SCRIPT)
                sh 'chmod u+x ' + INTEGRATION_TESTS_SCRIPT
            }
            // To run CasC IT we need both artifacts + setting up variables
            unstash 'je-war'
            unstash 'jenkins-oc-war'
//             def ccWar = pwd() + "/je.war"
//             def ocWar = pwd() + "/jenkins-oc.war"
//             INTEGRATION_TESTS_ARGUMENTS += " -DOPERATIONS_CENTER_IT_CC_LOCATION=${ccWar} -DOPERATIONS_CENTER_IT_OC_LOCATION=${ocWar}"
            // Grant ssh and https credentials for ease of use
            sshagent(['github-ssh']) { withCredentials([gitUsernamePassword(credentialsId: 'github-https')]) {
                    withEnv([
                            'INTEGRATION_TESTS_SCRIPT=' + INTEGRATION_TESTS_SCRIPT,
                            'INTEGRATION_TESTS_ARGUMENTS=' + INTEGRATION_TESTS_ARGUMENTS,
                            'MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS + ' ' + LINUX_MAVEN_EXTRA_OPTS
                    ]) {
//                         sh 'ls -la'
//                         sh 'pwd'
//                         sh 'echo $INTEGRATION_TESTS_ARGUMENTS'
                        sh '''#!/bin/bash -ex
                        set -euo pipefail

                        args="${INTEGRATION_TESTS_ARGUMENTS:-}"

                        clone_oc_it() {
                          if [ -n "$CHANGE_ID" ]; then
                            if ! git clone -b ${CHANGE_BRANCH} https://github.com/${CHANGE_FORK}/operations-center-it.git operations-center-it; then
                              echo "https://github.com/${CHANGE_FORK}/operations-center-it.git does not exist. Cloning from cloudbees organization"
                              git clone https://github.com/cloudbees/operations-center-it.git operations-center-it
                            fi
                          else
                            git clone https://github.com/cloudbees/operations-center-it.git operations-center-it
                          fi
                          (
                            cd operations-center-it
                            git rev-parse --verify HEAD
                          )
                        }

                        if [ -z "$args" ]; then
                          echo "INTEGRATION_TESTS_ARGUMENTS is unset. skipping integration tests"
                          exit 0
                        fi

                        clone_oc_it
                        # Both wars should be in ./ (unstashed in the pipeline), moving to the project folder
                        mv je.war operations-center-it/casc-it/
                        mv jenkins-oc.war operations-center-it/casc-it/
                        cd operations-center-it
                        ls -la ./casc-it/
                        mvn -pl casc-it $INTEGRATION_TESTS_ARGUMENTS -DOPERATIONS_CENTER_IT_CC_LOCATION=./casc-it/jenkins-oc.war -DOPERATIONS_CENTER_IT_OC_LOCATION=./casc-it/je.war $MAVEN_ARGS -Dmaven.test.failure.ignore=true verify "$@"
                        '''
//                         sh './$INTEGRATION_TESTS_SCRIPT $INTEGRATION_TESTS_ARGUMENTS $MAVEN_ARGS -Dmaven.test.failure.ignore=true'
                    }
            }}
        }
        junit skipMarkingBuildUnstable: (INTEGRATION_TESTS_FATAL != 'true'),
                testResults: '**/*-reports/*.xml',
                allowEmptyResults: true,
                testDataPublishers: [[$class: 'AttachmentPublisher']]
    }
}

pipeline {
    agent none
    options {
        // global timeout to kill rogue builds
        timeout(time: 24, unit: 'HOURS')
        buildDiscarder(logRotator(
                numToKeepStr: "${env.CHANGE_ID == null ? '100' : '5'}",
                artifactNumToKeepStr: "${env.CHANGE_ID == null ? '5' : '1'}"
        ))
        skipStagesAfterUnstable()
        skipDefaultCheckout()
    }
    post {
        failure {
            // https://issues.jenkins-ci.org/browse/JENKINS-42688
            script {
                if (env.CHANGE_ID == null) {
                    slackSend color: 'danger', channel: SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                            message: "FAILURE: ${currentBuild.fullDisplayName}\nClick <${RUN_DISPLAY_URL}|here> to get more details."
                }
            }
        }
        unstable {
            // https://issues.jenkins-ci.org/browse/JENKINS-42688
            script {
                if (env.CHANGE_ID == null) {
                    slackSend color: 'warning', channel: SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                            message: "UNSTABLE: ${currentBuild.fullDisplayName}\nClick <${RUN_DISPLAY_URL}|here> to get more details"
                }
            }
        }
    }
    stages {
        stage('docker images') {
            parallel {
                stage('maven-openjdk8') {
                    steps {
                        script {
                            jdkConfig['jdk8'].agentImage.linux = buildDockerImage('jdk8')
                        }
                    }
                }
                stage('maven-openjdk11') {
                    steps {
                        script {
                            jdkConfig['jdk11'].agentImage.linux = buildDockerImage('jdk11')
                        }
                    }
                }
            }
        }
        stage('markerfile') {
            agent {
                kubernetes {
                    yaml readPipelineRelative('k8s-pod-definitions/' + K8S_MARKER_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                    defaultContainer 'maven'
                }
            }
            steps {
                deleteDir()
                checkout scm
                script {
                    withCredentials([usernamePassword(credentialsId: 'cloudbees-gaia-ro-g3', passwordVariable: 'GITHUB_OAUTH', usernameVariable: 'GITHUB_LOGIN')]) {
                        sh 'echo $GITHUB_OAUTH |gh auth login --with-token'
                    }
                    if (env.BRANCH_NAME ==~ /PR-.*/) {
                        // Don't do this on PRs
                    } else {
                        contributorsGitHubHandles = sh(returnStdout: true, script: '''
                        display_contributors() {
                            git rev-list $(git describe --tags --abbrev=0)..HEAD > tmp
                            while read i; do
                                gh api repos/{owner}/{repo}/commits/${i} --jq '.author.login';
                                gh api repos/{owner}/{repo}/commits/${i} --jq '.committer.login';
                            done < tmp
                        }
                        display_contributors | grep -v 'web-flow' | sort -u | grep "\\S" || true
                        ''').split('\n')
                        contributorsSlackID = contributorsGitHubHandles.collect { sdmId -> resolveSlackIDFromScmId(scmId: sdmId) }.findAll { s -> s != null }
                        echo 'Github contributors : ' + contributorsGitHubHandles
                        echo 'Slack contributors : ' + contributorsSlackID
                    }
                    // Marker file parsing CJP-6223
                    echo "Reading $MARKER_FILE as properties file"
                    def d = [POM_ONLY: 'false', WINDOWS: 'true']
                    def PROPS = readProperties defaults: d, file: MARKER_FILE
                    // END CJP-6223
                    overrides(PROPS)
                    overridesATH(PROPS)
                    boolean isRelease = ( sh(script: "git log --format=%s -1 | grep --fixed-string '[maven-release-plugin] prepare'", returnStatus: true) == 0 )
                    echo "Command result for 'is the current commit already a release? ': $isRelease"
                    if (isRelease || env.CHANGE_ID != null) {
                        echo "Skipping the release as the current commit is either a PR or already a release"
                        RELEASABLE = "false"
                    }
                    pom = readMavenPom file: 'pom.xml'
                    overridesMavenPom(PROPS, pom)
                    overridesURR(PROPS)
                    overridesPods(PROPS)
                    overridesSurefire(PROPS)
                    overridesIT(PROPS)
                }
            }
        }

        stage('pom') {
            when {
                beforeAgent true
                expression { POM_ONLY == "true" }
            }
            agent {
                kubernetes {
                    yaml readPipelineRelative('k8s-pod-definitions/' + K8S_POM_ONLY_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                    defaultContainer 'maven'
                }
            }
            steps {
                deleteDir()
                checkout scm
                withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3', publisherStrategy: 'EXPLICIT') {
                    withEnv(['MAVEN_ARGS='+GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS]) {
                        sh 'mvn ${MAVEN_ARGS} -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false install'
                    }
                }
            }
        }

        stage ('builds') {
            when {
                beforeAgent true
                expression { POM_ONLY == "false" }
            }
            parallel {
                stage ('linux-jdk8') {
                    agent none
                    when {
                        beforeAgent true
                        expression { JAVA8 == "true" }
                    }
                    steps {
                        script {
                            buildLinux('jdk8', 'jdk8' == DEFAULT_JDK)
                        }
                    }
                }
                stage ('windows-jdk8') {
                    agent {
                        kubernetes {
                            cloud 'gauntlet3-windows'
                            slaveConnectTimeout 10000
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_WINDOWS_PLUGIN_BUILD_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig['jdk11'].agentImage.windows).replace('${MAVEN_IMAGE}', jdkConfig['jdk8'].agentImage.windows)
                            defaultContainer 'maven'
                        }
                    }
                    when {
                        beforeAgent true
                        expression { WINDOWS_BUILD == "true" }
                        expression { JAVA8 == "true" }
                    }
                    steps {
                        script {
                            buildWindows('jdk8')
                        }
                    }
                }
                /*stage ('coverage-jdk8') {
                    agent none
                    when {
                        beforeAgent true
                        expression { RUN_JACOCO == "true" }
                        expression { JAVA8 == "true" }
                    }
                    steps {
                        script {
                            coverage('jdk8')
                        }
                    }
                }*/
                stage ('linux-jdk11') {
                    when {
                        beforeAgent true
                        expression { JAVA11 == "true" }
                    }
                    agent none
                    steps {
                        script {
                            buildLinux('jdk11', 'jdk11' == DEFAULT_JDK)
                        }
                    }
                }
//                 stage ('windows-jdk11') {
//                     agent {
//                         kubernetes {
//                             cloud 'gauntlet3-windows'
//                             slaveConnectTimeout 10000
//                             yaml readPipelineRelative('k8s-pod-definitions/' + K8S_WINDOWS_PLUGIN_BUILD_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig['jdk11'].agentImage.windows).replace('${MAVEN_IMAGE}', jdkConfig['jdk11'].agentImage.windows)
//                             defaultContainer 'maven'
//                         }
//                     }
//                     when {
//                         beforeAgent true
//                         expression { WINDOWS_BUILD == "true" }
//                         expression { JAVA11 == "true" }
//                     }
//                     steps {
//                         script {
//                             buildWindows('jdk11')
//                         }
//                     }
//                 }
                /*stage ('coverage-jdk11') {
                    agent none
                    when {
                        beforeAgent true
                        expression { RUN_JACOCO == "true" }
                        expression { JAVA11 == "true" }
                    }
                    steps {
                        script {
                            coverage('jdk11')
                        }
                    }
                }*/
                stage ('release-check') { // trying to avoid a failed release we could fix before... cf. CJP-7298
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_RELEASE_CHECK_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                            defaultContainer 'maven'
                        }
                    }
                    steps {
                        checkout scm
                        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3',
                                publisherStrategy: 'EXPLICIT') {
                            withEnv([
                                    'MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS,
                                    'SUREFIRE_FORK_COUNT='+SUREFIRE_FORK_COUNT
                            ]) {
                                sh 'mvn ${MAVEN_ARGS} -DskipTests -Dspotbugs.failOnError=false install -Dautochangelog.skip -Pcloudbees-internal-release -Dmaven.compiler.showDeprecation=true -Dmaven.compiler.showWarnings=true'
                            }
                        }

                        //The java tool scan all logs of all stages (including this one)
                        recordIssues(tools: [
                                taskScanner(
                                        includePattern: '**/*.java,**/*.js,**/*.less,**/*.jelly,**/*.xml,**/*.properties',
                                        excludePattern: '**/target/**,**/node_modules/**',
                                        highTags: 'FIXME',
                                        lowTags: 'XXX',
                                        normalTags: 'TODO'
                                ),
                                java()
                        ])
                    }
                }
                stage ('checkstyle') {
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_CHECKSTYLE_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                            defaultContainer 'maven'
                        }
                    }
                    when {
                        beforeAgent true
                        expression { CHECKSTYLE == "true" }
                    }
                    steps {
                        deleteDir()
                        checkout scm
                        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3', publisherStrategy: 'EXPLICIT') {
                            withEnv([
                                    'MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS,
                                    'SUREFIRE_FORK_COUNT='+SUREFIRE_FORK_COUNT
                            ]) {
                                sh 'mvn ${MAVEN_ARGS} -DforkCount=${SUREFIRE_FORK_COUNT} -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false checkstyle:checkstyle'
                            }
                        }
                        recordIssues enabledForFailure: true, aggregatingResults: true, tool: checkStyle(pattern: '**/target/checkstyle-result.xml')
                    }
                }
                stage ('unified-release') {
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_URR_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                            defaultContainer 'maven'
                        }
                    }
                    when {
                        beforeAgent true
                        expression { SKIP_URR_STAGE == "false" }
                    }
                    steps {
                        checkout scm
                        withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3',
                                  mavenOpts: '-Xmx2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Djansi.force=true',
                                  publisherStrategy: 'EXPLICIT') {
                          ansiColor('xterm') {
                            withEnv(['MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS,]) {
                                sh 'mvn ${MAVEN_ARGS} -Pquick-build -Dmaven.test.skip=true install'
                            }
                            // artifacts required for Whitesource scan. (we are not compiling tests or running them so no need to exclude them)
                            stash includes: '**/target/**', name: 'target-production'
                            // stash the HPIs in case we are not in the envelope see
                            // https://github.com/jenkinsci/acceptance-test-harness/blob/b82ca66f7aa03a7dab414fba9e28855bdf289d96/docs/SUT-VERSIONS.md#use-custom-plugin-file
                            sh '''find . -regex '.*/target/[^/]+.hpi' -exec mv {} . \\; '''
                            stash includes: '*.hpi', name: 'hpis-for-ath', allowEmpty: true

                            dir ('urr') {
                                git url: 'https://github.com/cloudbees/unified-release.git', credentialsId: 'cloudbees-gaia-ro-g3', branch: URR_BRANCH
                                withEnv(['MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS,
                                         'VERSION=' + VERSION,
                                         'RELEASE_ARTIFACT_IDS=' + RELEASE_ARTIFACT_IDS,
                                         'CJOC_ATH_PRODUCT='+CJOC_ATH_PRODUCT,
                                         'MASTER_ATH_PRODUCT='+MASTER_ATH_PRODUCT
                                ]) {
                                    sh '''#!/bin/bash -ex
                                    declare -a artifactIds=($RELEASE_ARTIFACT_IDS)
                                    for artifactId in "${artifactIds[@]}"; do
                                        mvn ${MAVEN_ARGS} -N com.cloudbees.maven.plugins:update-urr-hpi-maven-plugin:0.11:update-plugin -DpluginId=$artifactId -Dversion=$VERSION || echo "[WARNING] ignoring error as CAPifying a plugin is optional"
                                    done
                                    echo "---- URR diff -----"
                                    if ! git --no-pager diff --exit-code; then
                                        echo "-------------------"
                                        mvn ${MAVEN_ARGS} install -pl products/${CJOC_ATH_PRODUCT},products/${MASTER_ATH_PRODUCT} -T 2
                                        mv products/${CJOC_ATH_PRODUCT}/target/*.war jenkins-oc.war
                                        mv products/${MASTER_ATH_PRODUCT}/target/*.war je.war
                                    else
                                        echo "-------------------"
                                        touch SKIP_URR_STAGE
                                    fi
                                    '''
                                }
                                script {
                                    def skipUrrStage = fileExists 'SKIP_URR_STAGE'
                                    if (skipUrrStage) {
                                        SKIP_URR_STAGE = 'true'
                                    } else {
                                        stash includes: 'je.war', name: 'je-war'
                                        stash includes: 'jenkins-oc.war', name: 'jenkins-oc-war'
                                    }
                                }
                            }
                          }
                        }
                    }
                }
            }
        }

        stage('Acceptance Tests') {
            parallel {
                stage ('integration') {
                    agent none
                    when {
                        beforeAgent true
                        expression { INTEGRATION_TESTS_SCRIPT != "" }
                        expression { SKIP_INTEGRATION_TESTS == "false" }
                    }
                    steps {
                        script {
                            runIntegration(DEFAULT_JDK)
                        }
                    }
                }
                stage("cjoc-ath") {
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_OC_ATH_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                        }
                    }
                    when {
                        beforeAgent true
                        expression { POM_ONLY == "false" }
                        expression { SKIP_URR_STAGE == "false" }
                        expression { RUN_CJOC_ATH == "true" }
                    }
                    steps {
                        git branch: URR_BRANCH, credentialsId: 'cloudbees-gaia-ro-g3', url: 'https://github.com/cloudbees/unified-release.git'
                        sshagent(['github-ssh']) {
                            sh '''
                                git config url.git@github.com:.insteadOf https://github.com/
                                git config url.git@github.com:.pushInsteadOf https://github.com/
                                git submodule init tests/operations-center-acceptance-test
                                git submodule update
                            '''
                        }
                        container('ath-run') {
                            dir('tests/operations-center-acceptance-test') {
                                withEnv(['ATH_VERSION=' + OC_ATH_VERSION]) {
                                    sh '''
                                        if [ -n "${ATH_VERSION}" ]; then
                                            git checkout ${ATH_VERSION}
                                        fi
                                    '''
                                }
                                withEnv(['JDK_VERSION=' + DEFAULT_JDK]) {
                                    sh '''
                                       if [ "${JDK_VERSION}" = "jdk11" ]; then
                                           /usr/bin/set-java.sh 11
                                       else
                                           # assume java 8
                                           /usr/bin/set-java.sh 8
                                       fi
                                    '''
                                }
                                unstash 'je-war'
                                unstash 'jenkins-oc-war'
                                unstash 'hpis-for-ath'
                                writeFile file: 'run-ath.sh', text: readPipelineRelative('utils/run-ath.sh')
                                writeFile file: 'vnc.sh', text: readPipelineRelative('utils/vnc.sh')
                                sh 'chmod u+x run-ath.sh'
                                realtimeJUnit(allowEmptyResults: true, testResults: '**/*-reports/TEST-*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]) {
                                    configFileProvider([configFile(fileId: 'MavenReadOnly', variable: 'SETTINGS')]) {
                                        withCredentials([usernamePassword(credentialsId: 'dockerhub-readonly', passwordVariable: 'DOCKERHUB_PASS', usernameVariable: 'DOCKERHUB_USER'),
                                                         file(credentialsId: 'ci-gpg-key', variable: 'CIGPGKEY')]) {
                                            withEnv(['CJOC_ATH_TESTS='+CJOC_ATH_TESTS,
                                                     'FORM_ELEMENT_PATH_VERSION='+FORM_ELEMENT_PATH_VERSION]){
                                                sh './run-ath.sh ${CJOC_ATH_TESTS} ${SETTINGS} "" "${DOCKERHUB_USER}" "${DOCKERHUB_PASS}"'
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                stage("ha") {
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_URR_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                            defaultContainer 'maven'
                        }
                    }
                    when {
                        beforeAgent true
                        expression { POM_ONLY == "false" }
                        expression { SKIP_URR_STAGE == "false" }
                        expression { RUN_HA_TEST == "true" }
                    }
                    steps {
                        git branch: URR_BRANCH, credentialsId: 'cloudbees-gaia-ro-g3', url: 'https://github.com/cloudbees/unified-release.git'
                        sshagent(['github-ssh']) {
                            sh '''
                                git config url.git@github.com:.insteadOf https://github.com/
                                git config url.git@github.com:.pushInsteadOf https://github.com/
                                git submodule init tests/operations-center-acceptance-test
                                git submodule update
                            '''
                        }
                        dir('tests/operations-center-acceptance-test') {
                            withEnv(['ATH_VERSION=' + OC_ATH_VERSION]) {
                                sh '''
                                    if [ -n "${ATH_VERSION}" ]; then
                                        git checkout ${ATH_VERSION}
                                    fi
                                '''
                            }
                            unstash 'je-war'
                            unstash 'jenkins-oc-war'
                            sh '''
                                cd ha-tests
                                ./test-ha.sh ../jenkins-oc.war
                                ./test-ha.sh clean
                                ./test-ha.sh ../je.war
                                echo "HA tests finished!"
                            '''
                        }
                    }
                }
                stage("controller-ath") {
                    agent {
                        kubernetes {
                            yaml readPipelineRelative('k8s-pod-definitions/' + K8S_CONTROLLERS_ATH_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)
                        }
                    }
                    when {
                        beforeAgent true
                        expression { POM_ONLY == "false" }
                        expression { SKIP_URR_STAGE == "false" }
                        expression { RUN_MASTER_ATH == "true" }
                    }
                    steps {
                        git branch: URR_BRANCH, credentialsId: 'cloudbees-gaia-ro-g3', url: 'https://github.com/cloudbees/unified-release.git'
                        sshagent(['github-ssh']) {
                            sh '''
                                git config url.git@github.com:.insteadOf https://github.com/
                                git config url.git@github.com:.pushInsteadOf https://github.com/
                                git submodule init tests/je-acceptance-test
                                git submodule update
                            '''
                        }
                        container('ath-run') {
                            dir('tests/je-acceptance-test') {
                                withEnv(['ATH_VERSION=' + JE_ATH_VERSION]) {
                                    sh '''
                                        if [ -n "${ATH_VERSION}" ]; then
                                            git checkout ${ATH_VERSION}
                                        fi
                                    '''
                                }
                                withEnv(['JDK_VERSION=' + DEFAULT_JDK]) {
                                    sh '''
                                       if [ "${JDK_VERSION}" = "jdk11" ]; then
                                           /usr/bin/set-java.sh 11
                                       else
                                           # assume java 8
                                           /usr/bin/set-java.sh 8
                                       fi
                                    '''
                                }

                                unstash 'je-war'
                                unstash 'hpis-for-ath'
                                sh 'mv je.war jenkins.war'
                                writeFile file: 'run-ath.sh', text: readPipelineRelative('utils/run-ath.sh')
                                writeFile file: 'vnc.sh', text: readPipelineRelative('utils/vnc.sh')
                                sh 'chmod u+x run-ath.sh'
                                realtimeJUnit(allowEmptyResults: true, testResults: '**/*-reports/TEST-*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]) {
                                    configFileProvider([configFile(fileId: 'MavenReadOnly', variable: 'SETTINGS')]) {
                                        withCredentials([usernamePassword(credentialsId: 'dockerhub-readonly', passwordVariable: 'DOCKERHUB_PASS', usernameVariable: 'DOCKERHUB_USER'),
                                                         file(credentialsId: 'ci-gpg-key', variable: 'CIGPGKEY')]) {
                                            withEnv(['MASTER_ATH_TESTS='+MASTER_ATH_TESTS,
                                                     'FORM_ELEMENT_PATH_VERSION='+FORM_ELEMENT_PATH_VERSION]) {
                                                sh './run-ath.sh ${MASTER_ATH_TESTS} ${SETTINGS} "" "${DOCKERHUB_USER}" "${DOCKERHUB_PASS}"'
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                stage("licensing-ath") {
                    agent none
                    when {
                        beforeAgent true
                        expression { POM_ONLY == "false" }
                        expression { SKIP_URR_STAGE == "false" }
                        expression { RUN_LICENSE_ATH == "true" }
                    }
                    steps {
                        podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/' + K8S_OC_ATH_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)) {
                            node (POD_LABEL) {
                                git branch: URR_BRANCH, credentialsId: 'cloudbees-gaia-ro-g3', url: 'https://github.com/cloudbees/unified-release.git'
                                sshagent(['github-ssh']) {
                                    sh '''
                                        git config url.git@github.com:.insteadOf https://github.com/
                                        git config url.git@github.com:.pushInsteadOf https://github.com/
                                        git submodule init tests/operations-center-acceptance-test
                                        git submodule update
                                    '''
                                }
                                container('ath-run') {
                                    dir('tests/operations-center-acceptance-test') {
                                        withEnv(['ATH_VERSION=' + OC_ATH_VERSION]) {
                                            sh '''
                                                if [ -n "${ATH_VERSION}" ]; then
                                                    git checkout ${ATH_VERSION}
                                                fi
                                            '''
                                        }
                                        withEnv(['JDK_VERSION=' + DEFAULT_JDK]) {
                                            sh '''
                                               if [ "${JDK_VERSION}" = "jdk11" ]; then
                                                   /usr/bin/set-java.sh 11
                                               else
                                                   # assume java 8
                                                   /usr/bin/set-java.sh 8
                                               fi
                                            '''
                                        }
                                        unstash 'je-war'
                                        unstash 'jenkins-oc-war'
                                        unstash 'hpis-for-ath'
                                        writeFile file: 'run-ath.sh', text: readPipelineRelative('utils/run-ath.sh')
                                        writeFile file: 'vnc.sh', text: readPipelineRelative('utils/vnc.sh')
                                        sh 'chmod u+x run-ath.sh'
                                        realtimeJUnit(allowEmptyResults: true, testResults: '**/*-reports/TEST-*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]) {
                                            configFileProvider([configFile(fileId: 'MavenReadOnly', variable: 'SETTINGS')]) {
                                                withCredentials([usernamePassword(credentialsId: 'dockerhub-readonly', passwordVariable: 'DOCKERHUB_PASS', usernameVariable: 'DOCKERHUB_USER'),
                                                                 file(credentialsId: 'ci-gpg-key', variable: 'CIGPGKEY')]) {
                                                    withEnv(['LICENSE_ATH_TESTS='+LICENSE_ATH_TESTS,
                                                             'FORM_ELEMENT_PATH_VERSION='+FORM_ELEMENT_PATH_VERSION]) {
                                                        sh './run-ath.sh licensing ${SETTINGS} ${LICENSE_ATH_TESTS} "${DOCKERHUB_USER}" "${DOCKERHUB_PASS}"'
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        stage ('release') {
            agent none
            when {
                expression { RELEASABLE == "true" }
            }
            steps {
                script {
                    try {
                        timeout(time: 16, unit: 'HOURS') {
                            def contributors = contributorsSlackID.collect { id -> '<@' + id + '>' }.join(' ')
                            rsp = slackSend color: 'warning', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                                      message: """Release `$RELEASE_ARTIFACTID:$RELEASE_VERSION`?
Contributors: ${contributors}
Click <${RUN_DISPLAY_URL}|here> to trigger or skip the release.
It will auto-skip in 16 hours.
"""
                            RELEASE_SLACK_CHANNEL = rsp.threadId

                            script {
                               // the new Declarative input block looks promising, but we can't use it for now.
                               // TODO: File JIRAs 1) input runs before when 2) no way to send the email before the input... for instance.
                              def approver = input message: "Release $RELEASE_VERSION?", submitterParameter:"approver"
                              echo "Release triggered by $approver"
                              APPROVER = approver
                              slackSend color: 'good', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                                        message: "Release of `$RELEASE_ARTIFACTID:$RELEASE_VERSION` approved by `$APPROVER`\n" +
                                                 "Release <${RUN_DISPLAY_URL}|build in progress>."
                            }
                        }
                    } catch (e) {
                        echo 'Skipping release due to timeout or manual abort'
                        slackSend color: 'good', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                                  message: "Release of `$RELEASE_ARTIFACTID:$RELEASE_VERSION` skipped"
                        return;
                    }
                    podTemplate(yaml: readPipelineRelative('k8s-pod-definitions/' + K8S_RELEASE_POD + '.yaml').replace('${AGENT_IMAGE}', jdkConfig[DEFAULT_JDK].agentImage.linux)) {
                        node(POD_LABEL) { container('maven') {
                            deleteDir()
                            checkout scm
                            sh "git checkout $BRANCH_NAME"
                            withEnv([
                                    'GIT_USERNAME='+GIT_USERNAME,
                                    'NOTIFICATION_TARGET='+NOTIFICATION_TARGET
                            ]) {
                                sh '''
                                    git config --global user.name "${GIT_USERNAME}"
                                    git config --global user.email "${NOTIFICATION_TARGET}"
                                    # Use ssh even if the initial clone used https
                                    git config url.git@github.com:.insteadOf https://github.com/
                                    git config url.git@github.com:.pushInsteadOf https://github.com/
                                '''
                            }
                            sshagent(['github-ssh']) {
                                sh "git fetch --tags"
                                withMaven(globalMavenSettingsConfig: 'maven-settings-nexus-internal-ci-build-jobs-g3', publisherStrategy: 'EXPLICIT') {
                                    withEnv(['MAVEN_ARGS=' + GLOBAL_MAVEN_OPTS + ' ' + MAVEN_EXTRA_OPTS,
                                             'SUREFIRE_FORK_COUNT='+SUREFIRE_FORK_COUNT]) {
                                        sh 'mvn ${MAVEN_ARGS} -DforkCount=${SUREFIRE_FORK_COUNT} -Dmaven.test.failure.ignore=true -Dspotbugs.failOnError=false release:prepare release:perform -Darguments="${MAVEN_ARGS}"'
                                    }
                                    writeFile file: 'promote-to-mct.sh', text: readPipelineRelative('utils/promote-to-mct.sh')
                                    writeFile file: 'create-urr-pr.sh', text: readPipelineRelative('utils/create-urr-pr.sh')
                                    writeFile file: 'get-released-jira-ids.groovy', text: readPipelineRelative('utils/get-released-jira-ids.groovy')
                                    writeFile file: 'security-release.groovy', text: readPipelineRelative('utils/security-release.groovy')
                                    writeFile file: 'update-jira.sh', text: readPipelineRelative('utils/update-jira.sh')
                                    writeFile file: 'create-release-notes-pr.sh', text: readPipelineRelative('utils/create-release-notes-pr.sh')
                                    writeFile file: 'generate-release-notes.groovy', text: readPipelineRelative('utils/generate-release-notes.groovy')
                                    sh 'chmod u+x promote-to-mct.sh create-urr-pr.sh get-released-jira-ids.groovy security-release.groovy update-jira.sh create-release-notes-pr.sh generate-release-notes.groovy'
                                    writeFile file: 'release-notes.mustache', text: readPipelineRelative('utils/release-notes.mustache')

                                    withCredentials([usernamePassword(credentialsId: 'cloudbees-gaia-ro-g3', passwordVariable: 'GITHUB_OAUTH', usernameVariable: 'GITHUB_LOGIN'),
                                                     usernamePassword(credentialsId: 'github-https', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER'),
                                                     usernamePassword(credentialsId: 'jira-astro', passwordVariable: 'JIRA_TOKEN', usernameVariable: 'USER')]) {
                                        def jira_ids
                                        configFileProvider([configFile(fileId: 'ivy_nexus-internal', targetLocation: 'grapeConfig.xml', variable: 'IVY_SETTINGS')]) {
                                            // Workaround grape issues
                                            sh 'rm -rf ~/.m2/repository/com/fasterxml/jackson/core'
                                            withEnv([
                                                    'CONTRIBUTORS=' + contributorsGitHubHandles.collect { s -> '@' + s}.join(' '),
                                                    'JIRA_PROJECT='+JIRA_PROJECT,
                                                    'RELEASE_ARTIFACT_IDS='+RELEASE_ARTIFACT_IDS,
                                                    'RELEASE_ARTIFACTID='+RELEASE_ARTIFACTID,
                                                    'RELEASE_NOTES_ID='+RELEASE_NOTES_ID,
                                                    'RELEASE_VERSION='+RELEASE_VERSION,
                                                    'DISPLAY_NAME='+DISPLAY_NAME,
                                                    'URR_BRANCH='+URR_BRANCH,
                                                    'SKIP_URR_PR='+SKIP_URR_PR,
                                                    'TEAM='+TEAM
                                            ]) {
                                                if (!(SKIP_PROMOTE == "true")) {
                                                    sh './promote-to-mct.sh --description ${RELEASE_ARTIFACTID}:${RELEASE_VERSION}'
                                                }
                                                sh './create-urr-pr.sh "${JIRA_PROJECT}" "${RELEASE_ARTIFACT_IDS}" "${RELEASE_VERSION}" "${URR_BRANCH}" "${SKIP_URR_PR}" "${CONTRIBUTORS}" "${TEAM}"'
                                                // this file was generated by the previous call to create-urr-pr.sh
                                                jira_ids = sh script: 'cat jira.txt', returnStdout: true
                                                jira_ids = jira_ids.trim()
                                                withEnv(['JIRA_IDS='+jira_ids]) {
                                                    if (jira_ids) {
                                                        sh './update-jira.sh "${JIRA_PROJECT}" "${JIRA_IDS}" "${RELEASE_ARTIFACTID}:${RELEASE_VERSION}" "${USER}:${JIRA_TOKEN}"'
                                                    }
                                                    sh './create-release-notes-pr.sh "${JIRA_PROJECT}" "${JIRA_IDS}" "${RELEASE_ARTIFACT_IDS}" "${RELEASE_NOTES_ID}" "${RELEASE_VERSION}" "${DISPLAY_NAME}" "${USER}" "${JIRA_TOKEN}"'
                                                }
                                            }
                                        }

                                        slackSend color: 'good', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot',
                                                  message: "`$RELEASE_ARTIFACTID:$RELEASE_VERSION` is released! (<${RUN_DISPLAY_URL}|build>)\n" +
                                                           "Released by `$APPROVER`\n" +
                                                           "\n" +
                                                           "Go to <https://github.com/cloudbees/unified-release/pulls|URR pulls> to see the URR PR\n" +
                                                           "And <https://github.com/cloudbees/docsite-cloudbees-release-notes/pulls|Release Notes pulls> for release notes PR\n" +
                                                           "Involved JIRA(s): ${jira_ids}"
                                    }
                                }
                            }
                        }}
                    }
                }
            }
            post {
                failure  { slackSend color: 'danger', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot', message: "Error releasing `$RELEASE_ARTIFACTID:$RELEASE_VERSION`\nPlease have a <${RUN_DISPLAY_URL}|look at the build>." }
                unstable { slackSend color: 'danger', channel: RELEASE_SLACK_CHANNEL, tokenCredentialId: 'cloudbees-slack-bot', message: "Error releasing `$RELEASE_ARTIFACTID:$RELEASE_VERSION`\nPlease have a <${RUN_DISPLAY_URL}|look at the build>." }
            }
        }
    }
}