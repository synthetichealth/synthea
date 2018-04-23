#######################################################################
#                              Base Node                              #
#######################################################################
FROM gradle:jdk8-alpine as base

#######################################################################
#                            Dependencies                             #
#######################################################################
FROM base AS dependencies

# Create the build directory and set the permissions
# properly so that Gradle can actually generate the
# build output without crashing.
USER root
RUN apk add --no-cache curl tar bash procps
ENV HOME /home/gradle
ENV GRADLE_USER_HOME="${HOME}/.gradle"

# Go back to the Gradle user, copy the build scripts in and
# resolve all necessary dependencies.
# This is done separately so editing the source code doesn't
# cause the dependencies to be redownloaded.
USER gradle
WORKDIR ${HOME}
RUN mkdir -p "${HOME}/build/output"
RUN mkdir -p "${HOME}/.gradle"
RUN mkdir -p "${HOME}/.cache"

COPY --chown=gradle:gradle config/ "${HOME}"/config
COPY --chown=gradle:gradle src/ "${HOME}/src"
COPY --chown=gradle:gradle *.gradle ${HOME}/
# COPY --chown=gradle:gradle gradle.* ${HOME}/
# COPY --chown=gradle:gradle gradle/ ${HOME}/gradle
RUN chown -R gradle:gradle "${HOME}"
RUN chmod -R 0776 "${HOME}"

#######################################################################
#                                Test                                 #
#######################################################################
FROM dependencies AS test
# Copy the source code in and build it
# TODO(grant): this seems to duplicate downloads that the
# install already does
#RUN gradle bundleWithDependencies integrationTest --stacktrace --no-daemon
RUN gradle test

#######################################################################
#                               Release                               #
#######################################################################

FROM openjdk:jre-alpine as release
ARG RUNTIME_USER=server

# Create the user that will be used to run the product and set up the directory it'll reside in.
RUN addgroup -S -g 1001 ${RUNTIME_USER}
RUN mkdir /srv/rt
RUN adduser -D -S -H -G ${RUNTIME_USER} -u 1001 -s /bin/false -h /srv/rt ${RUNTIME_USER}
RUN chown -R ${RUNTIME_USER}:${RUNTIME_USER} /srv/rt

# Copy the launcher script in.
# This serves to ensure that all necessary dependencies end up on the classpath.
ADD run.sh /srv/rt/
RUN chmod +x /srv/rt/run.sh

# Switch to the runtime user and copy the product in.
USER $RUNTIME_USER
COPY --from=test --chown=server:server /home/gradle/build/output/ /srv/rt/
COPY --from=test --chown=server:server /home/gradle/src/resources/ /srv/rt/src/resources/
WORKDIR /srv/rt

# Run the built product when the container launches
CMD "/srv/rt/run.sh"
