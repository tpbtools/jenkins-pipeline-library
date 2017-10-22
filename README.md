# Jenkins Pipeline Library

[![Build Status](http://jenkins.redpandaci.com/buildStatus/icon?job=red-panda-ci/jenkins-pipeline-library/develop)](http://jenkins.redpandaci.com/job/red-panda-ci/job/jenkins-pipeline-library/job/develop/) [![Build Status](https://travis-ci.org/red-panda-ci/jenkins-pipeline-library.svg?branch=develop)](https://travis-ci.org/red-panda-ci/jenkins-pipeline-library)

## Description

Library with a set of helpers to be used in Jenkins Scripted or Declarative Pipelines

This helpers are designed to be used in "Multibranch Pipeline" Jenkins job type, with "git flow" release cycle and at least with the following branches:

* develop
* master

## Usage

Add this line at the top of your Jenkinsfile

    @Library('github.com/red-panda-ci/jenkins-pipeline-library') _

Then you can use the helpers in your script

* Scripted Pipeline Example

TBD

* Declarative Pipeline example (Android)

```groovy
#!groovy

@Library('github.com/red-panda-ci/jenkins-pipeline-library') _

// Initialize cfg
cfg = jplConfig('project-alias', 'android', 'JIRAPROJECTKEY', [hipchat:'The-Project,Jenkins QA', slack:'#the-project,#integrations', email:'the-project@example.com,dev-team@example.com,qa-team@example.com'])

// The pipeline
pipeline {

    agent none

    stages {
        stage ('Checkout') {
            agent { label 'docker' }
            steps  {
                jplBuild(cfg)
            }
        }
        stage ('Docker push') {
            agent { label 'docker' }
            steps  {
                jplDockerPush(cfg, 'the-project/docker-image', 'https://registry.hub.docker.com', 'docker-hub-credentials', 'dockerfile-path')
            }
        }
        stage ('Build') {
            agent { label 'docker' }
            steps  {
                jplBuild(cfg)
            }
        }
        stage('Test') {
            agent { label 'docker' }
            when { expression { (env.BRANCH_NAME == 'develop') || env.BRANCH_NAME.startsWith('PR-') } }
            steps {
                jplSonarScanner(cfg)
            }
        }
        stage ('Sign') {
            agent { label 'docker' }
            when { branch 'release/*' }
            steps  {
                jplSigning(cfg, "git@github.org:the-project/sign-repsotory.git", "the-project", "app/build/outputs/apk/the-project-unsigned.apk")
                archiveArtifacts artifacts: "**/*-signed.apk", fingerprint: true, allowEmptyArchive: false
            }
        }
        stage ('Release confirm') {
            when { branch 'release/*' }
            steps {
                jplPromoteBuild(cfg)
            }
        }
        stage ('Release finish') {
            agent { label 'docker' }
            when { branch 'release/*' }
            steps {
                jplCloseRelease(cfg)
            }
        }
        stage ('PR Clean') {
            agent { label 'docker' }
            when { branch 'PR-*' }
            steps {
                deleteDir()
            }
        }
    }

    post {
        always {
            jplPostBuild(cfg)
        }
    }

    options {
        buildDiscarder(logRotator(artifactNumToKeepStr: '20',artifactDaysToKeepStr: '30'))
        disableConcurrentBuilds()
        skipDefaultCheckout()
        timeout(time: 1, unit: 'DAYS')
    }
}
```

## Helpers set

### jplConfig

Global static var with the pipeline configuration related to "jpl" library

Should be initialized at the begining of the script

* cfg = jplConfig("project_name", "targetPlatform", "jiraProjectKey", [ hipchat: "recipients", slack: "recipients", email: "recipients"] )

After that, you can access to the cfg hashmap values individually

    [...]

    jplConfig.laneName = "laneName"
    jplConfig.versionSuffix = "versionSuffix"
    jplConfig.targetPlatform = "targetPlatform"

    [...]

If you review the comments on vars/jplConfig.groovy you can see all definitions in the comments

### jplAppetizeUpload

Upload package to appetize

Parameters:

* cfg jplConfig class object
* String packageFile File name to upload
* String app App ID
* String token Appetize token

cfg usage:

* cfg.appetize[:] hashmap
* cfg.versionSuffix

### jplAppliveryUpload

Upload package to applivery

Parameters:

* cfg jplConfig class object
* String packageFile File name to upload. Should be an iOS / Android app artifact.
* String app App id
* String token Applivery account token

cfg usage:

* cfg.applivery[:] hashmap
* cfg.versionSuffix

### jplBuild

Build iOS / Android app with Fastlane

* Android app will build using docker into Jenkins
* iOS app will build with fastlane directly

Both builds are based on jpl project configuration

Parameters:

* cfg jplConfig class object
* string command What is the command to be executed in the build
  Example: "./gradlew clean assembleDebug"

cfg usage:

* cfg.targetPlatform

### jplCheckoutSCM

Checkout SCM

Get the code from SCM and init / update submodules
Leave the repository on the actual branch, instead of "deatached"

Parameters:

* cfg jplConfig class object
* String repository Repository URL (https://... git@...)
                    Leave it blank to use "checkout scm" command (multibranch project)
* String branch Branch name (blank for HEAD)

cfg usage:

* cfg.targetPlatform

This function will do some things for you based on the target platform:

* "android". Prepare the workspace to build within native Docker of the Jenkins:
  * Get the contents of the repository https://github.com/red-panda-ci/ci-scripts on the ci-scripts/.jenkins_library repository
* "ios" (TBD)
* "hybrid" (TBD)
* "backend" (TBD)

Also, they execute for the jplValidateCommitMessages on Pull Request, breaking the build if the messages don't complaint with the parse rules

### jplCloseRelease

Close release (Branch "release/*")

Merge code from release/vX.Y.Z to "master" and "develop", then "push" to the repository.
Create new tag with "vX.Y.Z" to the commit

The function uses "git promote" script

Fails if your repository is not in a "release/*" branch

Parameters:

* cfg jplConfig class object

cfg usage:

* cfg.notify
* cfg.recipients

### jplDockerPush

Docker image build & push to registry

Parameters:

* cfg jplConfig class object
* String dockerImageName Name of the docker image, defaults to cfg.projectName
* String dockerRegistryURL The URL of the docker registry. Defaults to <https://registry.hub.docker.com>
* String dockerRegistryJenkinsCredentials Jenkins credentials for the docker registry
* String dockerfilePath The path where the Dockerfile is placed, default to the root path of the repository

cfg usage:

* cfg.projectName

### jplIE

Integration Events (IE) management

Parameters:

* cfg jplConfig class object

cfg usage:

* cfg.ie.*

Rules:

* The Integration Event line should start with '@ie'
* The event can have multiple parameters: "parameter1", "parameter2", etc.
* Every parameter can have multiple options, starting with "+" or "-": "+option1 -option2"
  * If an option starts with "+" means the parameter "must have" the option
  * If an option starts with "-" means the option "should not be" in the parameter
* You can't use an option after the command. It's mandatory to use a parameter

Example:

  "@ie command parameter1 +option1 -option2 parameter2 +option1 +option2 -option3"

Commands:

* "fastlane": use multiple fastlane lanes, at least one. You can add multiple parameters in each

  Examples:

    "@ie fastlane develop"
    "@ie fastlane develop quality"
    "@ie fastlane develop -applivery +appetize quality +applivery -appetize"

* "gradlew": use gradle wrapper tasks

  Examples:

    "@ie gradlew clean assembleDebug"

### jplJIRA.checkProjectExists

Check if the project exists.
Finish job with error if

* The project doen't exist in JIRA, and
* JIRA_FAIL_ON_ERROR env variable (or failOnError parameter) is set on "true"

Parameters:

* cfg jplConfig object

cfg usage:

* cfg.jira.*

### jplJIRA.openIssue

Open jira issue

Parameters:

* cfg jplConfig object
* String summary The summary of the issue (blank for default summaty)
* String description: The ddscription of the issue (blank for default summaty)

cfg usage:

* cfg.jira.*

### jplNotify

Notify using multiple methods: hipchat, slack, email

Parameters:

* cfg jplConfig class object
* String summary The summary of the message (blank to use defaults)
* String message The message itself (blank to use defaults)

cfg usage:

* cfg.recipients.*

### jplPostBuild

Post build tasks

Parameters:

* cfg jplConfig class object

cfg usage:

* cfg.targetPlatform
* cfg.notify
* cfg.jiraProjectKey

Place the jplPostBuild(cfg) line into the "post" block of the pipeline like this

```groovy
    post {
        always {
            jplPostBuild(cfg)
        }
    }
```

### jplPromoteBuild

Promote build to next steps, waiting for user input

Parameters:

* cfg jplConfig class object
* String message User input message, defaults to "Promote Build"
* String message User input description, defaults to "Check to promote the build, leave uncheck to finish the build without promote"

cfg usage:

* cfg.promoteBuild

### jplPromoteCode

Promote code on release

Merge code from upstream branch to downstream branch, then make "push" to the repository

The function uses "git promote" script of <https://github.com/red-panda-ci/git-promote>

Parameters:

* cfg jplConfig class object
* String updateBranch The branch "source" of the merge
* String downstreamBranch The branch "target" of the merge

### jplSigning

App signing management

Parameters:

* cfg jplConfig class object
* String signingRepository The repository (github, bitbucket, whatever) where the signing information lives
* String signingPath Relative path to locate the signing data within signing repository
* String artifactPath Path to the artifact file to be signed, relative form the build workspace

cfg usage:

* cfg.signing.*

Notes:

* The artifactPath must be an unsigned APK, it's name should match the pattern "*-unsigned.apk"
* Your Jenkins instance must have read access to the repository containing signing data
* The signed artifact will be placed on the same route of the artifact to be signed, and named "*-signed.apk"
* The repository structure sould be like this:

    * Must have a "credentials.json" file with this content:

        ```json
        {
            "STORE_PASSWORD": "store_password_value",
            "KEY_ALIAS": "key_alias_value",
            "KEY_PASSWORD": "key_password_value",
            "ARTIFACT_SHA1": "D7:22:FF:...."
        }
        ```

    * Must have a "keystore.jks", as the signing keystore file

    Both file should be placed in the a repository path, wich is informed with the "signingPath" parameter

### jplSonarScanner

Launch SonarQube scanner

Parameters:

* cfg jplConfig class object

cfg usage:

* cfg.sonar.*

To use the jplSonarScanner() tool:

* Configure Jenkins with SonarQube >= 6.2
* Configure a webhook in Sonar to your jenkins URL <your-jenkins-instance>/sonar-webhook/ (<https://jenkins.io/doc/pipeline/steps/sonar/#waitforqualitygate-wait-for-sonarqube-analysis-to-be-completed-and-return-quality-gate-status)>

### jplValidateCommitMessages

Validate commit messages on PR's using <https://github.com/willsoto/validate-commit> project

* Check a concrete quantity of commits on the actual PR on the code repository
* Breaks the build if any commit don'w follow the preset rules

Parameters:

* cfg jplConfig class object
* int quantity Number of commits to check
* String preset Preset to use in validation

Should be one of the supported presets of the willsoto validate commit project:

* angular
* atom
* eslint
* ember
* jquery
* jshint

cfg usage:

* cfg.commitValidation.*

## Dependencies

You should consider the following configurations:

### Jenkins service

* Install this plugins:
  * AnsiColor
  * Bitbucket Branch Source
  * Bitbucket Plugin
  * Blue Ocean
  * Github Branch Source
  * Github Plugin
  * Git Plugin
  * HipChat, if you want to use hipchat as notification channel
  * JIRA Pipeline Steps, if you want to use a JIRA project
  * Pipeline
  * Pipeline Utility Steps, if you want to sign android APK's artifacts with jplSigning
  * Slack Notification, if you want to use Slack as notification channel
  * SonarQube Scanner, if you want to use SonerQube as quality gate with jplSonarScanner
  * Timestamper
* Configure Jeknins in "Configuration" main menu option
  * Enable the checkbox "Environment Variables" and add the following environment variables with each integration key:
    * APPETIZE_TOKEN
    * APPLIVERY_TOKEN
    * APPETIZE_TOKEN
    * JIRA_SITE
  * Put the correct Slack, Hipchat and JIRA credentials in their place (read the howto's of the related Jenkins plugins)

## Testing

You can run all tests in your workstations as if they run in Jenkins. The tests are executed in "jenkins-dind" docker image <https://hub.docker.com/r/redpandaci/jenkins-dind/> and uses a docker container with a time box run of 300 seconds. After the tests, or reaching the timeout, the container will be destroyed

```bash
$ bin/test.sh
# Start jenkins as a time-boxed daemon container, running for max 300 seconds with id be376b46f66fc44cb4ee7ad6fb64e6cd5a94fdd8e3e79d89fcb61a4415580c53
# Copy jenkins configuration
# Preparing jpl code for testing
Switched to a new branch 'release/v9.9.9'
M	vars/jplPromoteCode.groovy
M	vars/jplSonarScanner.groovy
Switched to a new branch 'jpl-test'
M	vars/jplPromoteCode.groovy
M	vars/jplSonarScanner.groovy
# Wait for jenkins service to be initialized
# Download jenkins cli
# Run jplCheckoutSCM Test...
Started jplCheckoutSCM #1
Completed jplCheckoutSCM #1 : SUCCESS
# Run jplDockerPush Test...
Started jplDockerPush #1
Completed jplDockerPush #1 : SUCCESS
# Run jplPromoteBuild Test...
Started jplPromoteBuild #1
Completed jplPromoteBuild #1 : ABORTED
# Stop jenkins daemon container
be376b46f66fc44cb4ee7ad6fb64e6cd5a94fdd8e3e79d89fcb61a4415580c53
```

If you are a library developer, you can use "local test" options for the test cript. In this case, the container open "8080" port to jenkins-dind with "redpanda/redpanda" credentials, so you can open the Jenkins in a web browser in <http://localhost:8080>

After the test, the container will not be destroyed, so you must destroy it by yourself with "docker rm -f CONTAINER_ID"

With "local test" options the script will test the library code that you're working on, taking changes of all helpers.

```bash
$ bin/test.sh local test
# Start jenkins as a time-boxed daemon container, running for max 0 seconds with id 525dc9e86eb6abae8fbda933eb94fe4c8e463f761ae24c9a2dd5f16d2b09c9b5
# Copy jenkins configuration
# Preparing jpl code for testing
Switched to a new branch 'release/v9.9.9'
Switched to a new branch 'jpl-test'
M	vars/jplPromoteCode.groovy
M	vars/jplSonarScanner.groovy
M	vars/jplPromoteCode.groovy
M	vars/jplSonarScanner.groovy
# Local test requested: Commit local jpl changes
[jpl-test f8a6dea] test within docker
 2 files changed, 5 insertions(+), 3 deletions(-)
# Wait for jenkins service to be initialized
# Download jenkins cli
# Run jplCheckoutSCM Test...
Started jplCheckoutSCM #1
Completed jplCheckoutSCM #1 : SUCCESS
# Run jplDockerPush Test...
Started jplDockerPush #1
Completed jplDockerPush #1 : SUCCESS
# Run jplPromoteBuild Test...
Started jplPromoteBuild #1
Completed jplPromoteBuild #1 : ABORTED
$ echo $?
0
$ docker ps -a
CONTAINER ID        IMAGE                     COMMAND                  CREATED             STATUS              PORTS                            NAMES
525dc9e86eb6        redpandaci/jenkins-dind   "timeout 0 /usr/bi..."   2 minutes ago       Up About a minute   22/tcp, 0.0.0.0:8080->8080/tcp   stupefied_keller
$ docker kill stupefied_keller 
stupefied_keller
$ docker ps -a
CONTAINER ID        IMAGE               COMMAND             CREATED             STATUS              PORTS               NAMES
```