#!/usr/bin/env bash
set -ex

version="0.7.0"

wget https://capnproto.org/capnproto-c++-"$version".tar.gz

### extract capnproto
tar zxf capnproto-c++-"$version".tar.gz

### get capnproto-java
git clone https://github.com/capnproto/capnproto-java
