# global service name
SERVICE                 := ciitizen-core

#######################################################################
#                 OVERRIDE THIS TO MATCH YOUR PROJECT                 #
#######################################################################

# Most applications have their own method of maintaining a version number.
# Override this command to populate the
APP_VERSION             := v1.3.1-511
# Gradle example:
# APP_VERSION             := $(shell echo `grep "versionName" build.gradle | awk '{print $2}'`)

# Builds should be repeatable, therefore we need a method to reference the git
# sha where a version came from.
GIT_VERSION             := $(shell echo `git describe --match=NeVeRmAtCh --always --dirty`)
FULL_VERSION            := v$(APP_VERSION)-g$(GIT_VERSION)

# location for artifact repositories
HELM_REPO_NAME          := ciitizen-helm
HELM_REPO_URL           := s3://$(HELM_REPO_NAME)/charts
DOCKER_INT_REGISTRY     := registry.ciitizen.net

# Check for required dev tools installed
preflight:
	which docker
	which helm
	helm repo list | grep $(HELM_REPO_NAME)

# Clean build artifacts. Override for your project
clean:
	rm -fr ./dist/*
	./gradlew clean --no-daemon

clean-all: clean
	docker rmi -f $(docker images -a -q)
	docker rm -f $(docker ps -a -q)

# Build docker images and tag them consistently
build:
	docker build -t $(SERVICE) .
	docker tag $(SERVICE) $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	docker tag $(SERVICE) $(DOCKER_INT_REGISTRY)/$(SERVICE):latest

#######################################################################
#          OVERRIDE this to match your docker/service config          #
#######################################################################

# Run the docker image local for development and testing. This is may be
# specific to each project, however, it should run with the minimal of inputs.
run: build
	docker run -it -p 8080:8080 -v /tmp:/etc/app/indexes $(SERVICE)

# Publish artifacts to shared repositories. This includeds the docker image as
# well as  the helm chart for coordinated deployment on a k8s infrastructure.
release: build helm-chart
	@echo $(DISPLAY_BOLD)"Publishing container to $(DOCKER_INT_REPO) registry"$(DISPLAY_RESET)
	docker -D push $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	docker -D push $(DOCKER_INT_REGISTRY)/$(SERVICE):latest
	@echo $(DISPLAY_BOLD)"Publishing chart to $(SERVICE) registry"$(DISPLAY_RESET)
	helm s3 push --force ./dist/$(SERVICE)-$(FULL_VERSION).tgz $(HELM_REPO_NAME)
	docker run -u root -v ${PWD}:/home/gradle -v ${PWD}/.gradle:/home/gradle/.gradle gradle:jdk8 gradle uploadArchives

#######################################################################
#                      OVERRIDE the HELM_FILES                        #
#######################################################################

# NOTE this will change when we have different environments
HELM_FILES := ./values.yaml

# Build the helm chart package
# the -f commands tell helm to use the files to populate template variables
# in the deployment. These are your configuration files and secrets
helm-chart:
	helm lint --debug $(foreach f, $(HELM_FILES), -f $(f)) .
	helm package . -d ./dist --debug --version $(FULL_VERSION) --app-version $(FULL_VERSION)


# Deploy the helm chart on the k8s cluster
CHART_INSTALLED := $(shell helm list | grep $(SERVICE) | grep -v FAILED)
deploy:
ifeq ($(CHART_INSTALLED),)
	helm delete --purge $(SERVICE) || :
	helm install . --wait --name $(SERVICE) --version $(FULL_VERSION) $(foreach f, $(HELM_FILES),-f $(f))
else
	helm upgrade --wait --recreate-pods --install $(SERVICE) --version $(FULL_VERSION) $(foreach f, $(HELM_FILES), -f $(f)) .
endif

version:
	@echo $(FULL_VERSION)


.PHONY:  build
