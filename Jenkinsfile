#!/usr/bin/env groovy

pipeline {
  options {
    timeout(time: 10, unit: 'MINUTES')
  }
  parameters {
    string (name: 'GIT_BRANCH', defaultValue: 'master', description: 'Git branch to build')
  }
  agent {
    node {
      label 'master'
      customWorkspace './synthea'
    }
  } // single node jenkins
  environment {
    S3_CREDENTIALS = credentials('DOCKER_HELM_S3')
    AWS_ACCESS_KEY_ID = "${env.S3_CREDENTIALS_USR}"
    AWS_SECRET_ACCESS_KEY = "${env.S3_CREDENTIALS_PSW}"
    AWS_DEFAULT_REGION = 'us-west-2'
  }
  stages {
    stage('Git clone and setup') {
      steps {
        echo 'Checkout code'
        checkout scm
        echo 'Setup Helm'
        sh 'helm init --client-only || :'
        sh 'helm plugin install https://github.com/hypnoglow/helm-s3.git --version 0.6.0 || :'
        sh 'helm repo add ciitizen-helm s3://ciitizen-helm/charts || :'
      }
    }
    stage('Build and test') {
      steps {
        echo 'Build docker image and run unit tests'
        sh 'make build'
      }
    }
    stage('release') {
      when {
        branch 'master'
      }
      steps {
        echo 'Package and push image and chart artifacts'
        sh 'make release'
      }
    }
    stage('deploy') {
      when {
        branch 'master'
      }
      steps {
        echo 'Deploy to staging'
        sh 'make deploy'
      }
    }
  }
}
