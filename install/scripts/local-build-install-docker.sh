#!/bin/bash
#
# Author: Ambud Sharma
# 
# Purpose: To build and install Hendrix and run it on docker
#

if [ -z $JAVA_HOME ]; then
	echo "JAVA_HOME not set"
	exit
fi

docker run -d -p 5000:5000 --restart=always --name registry registry:2 
export DOCKER_REGISTRY=localhost:5000

cd ../..
# Build the code and copy artifacts
mvn clean package

HVERSION=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\['`

docker-compose -p hendrix up -d -f ../conf/local/docker-compose.yml

# Download Apache Storm to launch topologies
#wget http://apache.cs.utah.edu/storm/apache-storm-0.10.1/apache-storm-0.10.1.tar.gz
#tar xf apache-storm-0.10.1.tar.gz
#mv apache-storm-0.10.1 storm