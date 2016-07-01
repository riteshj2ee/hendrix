#!/bin/bash
#
# Author: Ambud Sharma
# 
# Purpose: To build and install hendrix2 and run it on docker
#
set -eu

if [ -z $JAVA_HOME ]; then
	echo "JAVA_HOME not set"
	exit
fi

export MYSQL_ROOT_PASSWORD=root

# Cleanup all orphan untagged images
# docker rmi $(docker images | grep "^<none>" | awk "{print $3}")

# Stop any existing revisions of the containers
docker-compose -p hendrix2 -f ../conf/local/docker-compose.yml stop -t 2

# Remove any existing revisions of the containers
docker-compose -p hendrix2 -f ../conf/local/docker-compose.yml rm --force

# docker ps -a | grep 'registry' | awk '{print $1}' | xargs docker rm -f

# docker run -d -p 5000:5000 --restart=always --name registry registry:2 
export DOCKER_REGISTRY=localhost:5000

cd ../..
# Build the code and copy artifacts
# mvn -DskipTests clean package

export HVERSION=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\['`

echo "HVERSION is $HVERSION"

cd install/scripts
docker-compose -p hendrix2 -f ../conf/local/docker-compose.yml up -d

docker-compose -p hendrix2 -f ../conf/local/docker-compose.yml scale kafka=5
docker-compose -p hendrix2 -f ../conf/local/docker-compose.yml scale supervisor=3

# Download Apache Storm to launch topologies
# wget http://apache.cs.utah.edu/storm/apache-storm-0.10.1/apache-storm-0.10.1.tar.gz -O ../target/storm.tar.gz

cd ../target/
tar xf storm.tar.gz
# mv apache-storm-0.10.1 storm

while ! nc -z localhost 49627;do echo "Checking nimbus availability";sleep 1;done

./storm/bin/storm jar -c nimbus.host=localhost -c nimbus.thrift.port=49627 ../../hendrix-storm/target/hendrix-storm-$HVERSION-jar-with-dependencies.jar org.apache.storm.flux.Flux --remote ../conf/remote/rules.yaml --filter ../conf/remote/config.properties