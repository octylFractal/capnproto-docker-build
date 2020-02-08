#!/usr/bin/env bash
set -ex

version="0.7.0"

wget https://capnproto.org/capnproto-c++-"$version".tar.gz
wget https://github.com/capnproto/capnproto-java/archive/master.zip

### extract capnproto
tar zxf capnproto-c++-"$version".tar.gz

### extract capnproto-java
unzip master.zip
