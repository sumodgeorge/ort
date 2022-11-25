# syntax=docker/dockerfile:1.4

# Use at least version 1.4 above to be able to use linked copies, see e.g.
# https://www.howtogeek.com/devops/how-to-accelerate-docker-builds-and-optimize-caching-with-copy-link/

# Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

FROM eclipse-temurin:11-jdk-alpine AS build

COPY . /usr/local/src/ort
WORKDIR /usr/local/src/ort

RUN apk --update add --no-cache \
    # Required to run the scripts.
    bash \
    # Required to run binaries linked against glibc (instead of musl libc).
    gcompat \
    # Required to run Node.
    libstdc++

ARG ORT_VERSION

# Preserve between builds whatever gets written to the Gradle user home to speed up incremental builds.
RUN --mount=type=cache,target=/tmp/.gradle/ \
    export GRADLE_USER_HOME=/tmp/.gradle/ && \
    scripts/import_proxy_certs.sh && \
    scripts/set_gradle_proxy.sh && \
    ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:installDist :helper-cli:startScripts

FROM vborja/asdf-alpine AS python

USER root
RUN apk --update add --no-cache gcc
USER asdf
RUN asdf plugin-add python && asdf install python 3.8.0

FROM python AS run

COPY --from=build --link /usr/local/src/ort/cli/build/install/ort /opt/ort

ARG ORT_VERSION

# Support to run the helper-cli like `docker run --entrypoint /opt/ort/bin/orth ort`.
COPY --from=build --link /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/
COPY --from=build --link /usr/local/src/ort/helper-cli/build/libs/helper-cli-$ORT_VERSION.jar /opt/ort/lib/

RUN /opt/ort/bin/ort requirements

ENTRYPOINT ["/opt/ort/bin/ort"]
