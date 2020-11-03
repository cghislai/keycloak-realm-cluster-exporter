#!groovy

pipeline {
    agent any
    parameters {
        string(name: 'ALT_DEPLOYMENT_REPOSITORY', defaultValue: '', description: 'Alternative deployment repo')
        string(name: 'MAVEN_INSTALLATION', defaultValue: 'maven', description: 'Maven installation to use')
        string(name: 'MAVEN_SETTINGS', defaultValue: 'nexus-mvn-settings', description: 'Maven settings to use')
        string(name: 'DOCKER_REPO', defaultValue: 'eu.gcr.io/lifeislife-cloud', description: 'Alternative deployment repo')
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('Build & publish') {
            steps {
                script {
                    env.MVN_ARGS = ""
                    if (params.ALT_DEPLOYMENT_REPOSITORY != '') {
                        env.MVN_ARGS = "-DaltDeploymentRepository=${params.ALT_DEPLOYMENT_REPOSITORY}"
                    }
                    env.MVN_PHASE = "deploy"
                }
                withMaven(maven: "${params.MAVEN_INSTALLATION}", mavenSettingsConfig: "${params.MAVEN_SETTINGS}") {
                    sh "mvn ${env.MVN_ARGS}  clean ${env.MVN_PHASE}"
                    script {
                        VERSION = sh(script: 'JENKINS_MAVEN_AGENT_DISABLED=true mvn help:evaluate -Dexpression=project.version -q -DforceStdout | tail -n1', returnStdout: true).trim()
                    }
                    stash(name: 'target', includes: 'target/*.jar')
                }
            }
        }
        stage('Build & publish docker image') {
            when {
                anyOf {
                    environment name: 'BRANCH_NAME', value: 'master'
                    environment name: 'BRANCH_NAME', value: 'dev'
                }
            }
            agent {
                label 'docker'
            }
            steps {
                container('docker') {
                    unstash(name: 'target')
                    script {
                        def image = docker.build("${params.DOCKER_REPO}/keycloak-realm-cluster-exporter:${VERSION}")
                        image.push()
                        image.push("${BRANCH_NAME}-latest")
                    }
                }
            }
        }
    }
    post {
        failure {
            mail(
                    to: 'charlyghislain@gmail.com', cc: 'yannick@valuya.be',
                    subject: "Build failed: keycloak realm exporter $BRANCH_NAME ${BUILD_NUMBER}",
                    body: "See job at ${BUILD_URL}"
            )
        }
    }
}
