#!/usr/bin/env bash
set -ex

docker build -t octylfractal/capnproto-linux .
docker run -v /root/.gradle:/root/.gradle \
           -v /root/.m2:/root/.m2 \
           octylfractal/capnproto-linux \
           /gradlew --stacktrace publishToMavenLocal
