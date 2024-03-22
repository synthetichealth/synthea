ARG BASE_IMAGE=ubuntu:22.04
FROM ${BASE_IMAGE}

ARG USER=dev

WORKDIR /work
SHELL ["/bin/bash", "-euxo", "pipefail", "-c"]

# setup timezone
RUN apt-get update;\
  apt-get install -yq tzdata;\
  ln -fs /usr/share/zoneinfo/America/New_York /etc/localtime;\
  dpkg-reconfigure -f noninteractive tzdata;

# Set the locale
RUN apt-get install -yq locales;
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
  locale-gen
ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8 

# install OS deps
RUN set -eux;\
  apt-get install -y --no-install-recommends ca-certificates curl git jq make unzip zip;
RUN set -eux;\
  apt-get install -y --no-install-recommends libfreetype-dev libfreetype6 fontconfig fonts-dejavu;\
  rm -rf /var/lib/apt/lists/*;

# run as non-root user
RUN id -u ${USER} &>/dev/null || useradd -ms /bin/bash ${USER}
USER ${USER}
WORKDIR /home/${USER}/work

COPY --chown=${USER}:${USER} ./ ./synthea

# install hermit and binary deps
COPY --chown=${USER}:${USER} bin/ bin/
ENV PATH=/home/${USER}/work/bin:${PATH}
RUN set -eux;\
  hermit shell-hooks;\
  hermit install

COPY --chown=${USER}:${USER} ./ ./synthea
RUN set -eux;\
  cd synthea;\
  make install;\
  make build;

# Synthea repo: https://github.com/synthetichealth/synthea
# ./synthea/run_synthea -m congestive_heart_failure -c settings-chf.yml -p 10000
# ./synthea/run_synthea -c settings-chf.yml -p 10000
