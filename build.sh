#!/usr/bin/env bash
set -ex

mkdir -p build/linux
docker build -t octylfractal/capnproto-linux .
docker run --mount type=bind,source="$(pwd)"/build/linux,target=/linux octylfractal/capnproto-linux
