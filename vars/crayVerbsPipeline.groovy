// Copyright (c) 2018. Cray Inc. All rights reserved.
// Libfabric Verbs provider Jenkins Pipeline file

// This pipeline uses shared pipeline libraries for the following
// functions:
//   -- launch
//   -- publish

def populateEnvironment = load('ci/cray/populateEnvironment.groovy')
def checkAssertions = load('ci/cray/checkAssertions.groovy')
def buildTemporary = load('ci/cray/buildTemporary.groovy')
def testUnit = load('ci/cray/testUnit.groovy')
def testSmoke = load('ci/cray/testSmoke.groovy')
def testFunctional = load('ci/cray/testFunctional.groovy')
def testFabtests = load('ci/cray/testFabtests.groovy')
def deployInstall = load('ci/cray/deployInstall.groovy')
def deployLatest = load('ci/cray/deployLatest.groovy')
def testSystem = load('ci/cray/testSystem.groovy')
def testApplication = load('ci/cray/testApplication.groovy')
def deployStable = load('ci/cray/deployStable.groovy')
def deployTags = load('ci/cray/deployTags.groovy')

def call(Map pipelineParams) {
    pipeline {
        options {
            // Generic build options
            timeout (time: 30, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '5'))
            // Build options
            disableConcurrentBuilds()
            skipStagesAfterUnstable()
            timestamps()
        }
        agent {
            node {
                label pipelineParams.agent
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    echo "[populating environment]"
                    script {
                        populateEnvironment 
                    }

                    echo "[checking assertions]"
                    script {
                        checkAssertions
                    }
                }
            }
            stage('Build') {
                steps {
                    script {
                        buildTemporary
                    }
                }
            }
            stage('Test: Phase 1') {
                failFast true
                parallel {
                    stage('Unit tests') {
                        steps {
                            script {
                                testUnit
                            }
                        }
                    }
                    stage('Smoke tests') {
                        environment {
                            LD_LIBRARY_PATH = "$TMP_INSTALL_PATH/lib:$LD_LIBRARY_PATH"
                        }
                        steps {
                            script {
                                testSmoke
                            }
                        }
                    }
                    stage('Functional tests') {
                        steps {
                            script {
                                testFunctional
                            }
                        }
                    }
                    stage('Fabtests') {
                        environment {
                            LD_LIBRARY_PATH = "$TMP_INSTALL_PATH/lib:$LD_LIBRARY_PATH"
                        }
                        steps {
                            script {
                                testFabtests
                            }
                        }
                    }
                }
            }
            stage("Deploy: Install") {
                steps {
                    script {
                        deployInstall
                    }
                }
            }
            stage("Deploy: latest") {
                when {
                    expression { env.BRANCH_NAME == 'master' }
                }
                steps {
                    script {
                        deployLatest
                    }
                }
            }
            stage("Test: Phase 2") {
                failFast true
                parallel {
                    stage("System tests") {
                        steps {
                            script {
                                testSystem
                            }
                        }
                    }
                    stage("Application tests") {
                        environment {
                            MPIR_CVAR_OFI_USE_PROVIDER = 'verbs'
                        }
                        steps {
                            script {
                                testApplication
                            }
                        }
                    }
                }
            }
            stage("Deploy: Stable") {
                when {
                    expression { env.BRANCH_NAME == 'master' }
                }
                steps {
                    script {
                        deployStable
                    }
                }
            }
            stage("Deploy: Tags") {
                when {
                    buildingTag()
                }
                steps {
                    script {
                        deployTags
                    }
                }
           }
        }
        environment {
            GIT_SHORT_COMMIT = "$GIT_COMMIT"
            TMP_INSTALL_PATH = pwd tmp: true
            ROOT_BUILD_PATH = "pipelineParams.ROOT_BUILD_PATH"
            FABTEST_PATH = "${ROOT_BUILD_PATH + '/fabtests/stable'}"
            LIBFABRIC_BUILD_PATH = "${ROOT_BUILD_PATH + '/libfabric'}"
            OMB_BUILD_PATH = "${ROOT_BUILD_PATH + '/osu-micro-benchmarks/5.4.2/libexec/osu-micro-benchmarks/mpi'}"
        }
    }
}
