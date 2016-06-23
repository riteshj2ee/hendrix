#!/bin/bash
#
# Author: Ambud Sharma
# 
# Purpose: To build and install Hendrix and run it on docker
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
docker-compose -p hendrix -f ../conf/local/docker-compose.yml stop -t 2

# Remove any existing revisions of the containers
docker-compose -p hendrix -f ../conf/local/docker-compose.yml rm --force

docker ps -a | grep 'registry' | awk '{print $1}' | xargs docker rm -f

docker run -d -p 5000:5000 --restart=always --name registry registry:2 
export DOCKER_REGISTRY=localhost:5000

cd ../..
# Build the code and copy artifacts
#mvn -DskipTests clean package

export HVERSION=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\['`

cd install/scripts
docker-compose -p hendrix -f ../conf/local/docker-compose.yml up -d 

# Download Apache Storm to launch topologies
#wget http://apache.cs.utah.edu/storm/apache-storm-0.10.1/apache-storm-0.10.1.tar.gz
#tar xf apache-storm-0.10.1.tar.gz
#mv apache-storm-0.10.1 storm