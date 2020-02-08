FROM quay.io/pypa/manylinux2014_x86_64:2020-01-31-3350900

RUN yum install -y java-latest-openjdk-headless wget

COPY setup.sh /
RUN /setup.sh
COPY . /

CMD ["/gradlew", "--stacktrace", "publish"]
