#!/usr/bin/env bash
set -ex
# infer version from existing file
targz="$(find . -name 'capnproto-c++-*.tar.gz')"
version="$(basename "$targz" | perl -pe 's/capnproto-c\+\+-(.+?)\.tar\.gz/$1/')"

tar zxf capnproto-c++-"$version".tar.gz
cd capnproto-c++-"$version"
./configure --prefix=/linux
make -j"$(nproc)"
make install
