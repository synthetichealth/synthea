FROM jruby:9.1.6
MAINTAINER Carlton Duffett (duffett@mitre.org)

# The jRuby base image doesn't come wit git, so let's install it.
RUN apt-get update && apt-get install -y git

# Copy Synthea into /usr/src/synthea/. Only copies the essential files
# not ignored by .dockerignore.
RUN mkdir -p /usr/src/synthea
COPY . /usr/src/synthea/
WORKDIR /usr/src/synthea/

# Mount shared storage volume for Synthea's output. Unless the output directory
# in synthea.yml is set to this location and a data volume is mounted at runtime
# using volumes_from data written to this mount will not persist.
VOLUME ["/mnt/synthea"]

# Install Synthea's dependencies. The base ruby image comes pre-installed with
# gem, bundler, rake, etc. The .dockerignore file doesn't copy over the .git
# directory, so we need to initialize git again to keep bundler from complaining.
RUN git init && bundle install

# Invoke a single command using bash when the container is run. Command line
# args to the container will be passed to bash.
CMD ["/bin/bash", "-c"]
