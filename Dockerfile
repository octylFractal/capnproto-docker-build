FROM quay.io/pypa/manylinux2014_x86_64:2020-01-31-3350900

# get sources
ADD https://capnproto.org/capnproto-c++-0.7.0.tar.gz /

COPY scripts/build.sh /
CMD ["/build.sh"]
