#!/bin/bash
############################################
#
# Author: Ambud Sharma
#
############################################

source ./hendrix-env.sh

set -eu

# Create required kafka topics
$KAFKA_HOME/bin/kafka-topics.sh --create --topic logTopic --zookeeper $ZK_SERVER
$KAFKA_HOME/bin/kafka-topics.sh --create --topic metricTopic --zookeeper $ZK_SERVER
$KAFKA_HOME/bin/kafka-topics.sh --create --topic alertTopic --zookeeper $ZK_SERVER
$KAFKA_HOME/bin/kafka-topics.sh --create --topic ruleTopic --zookeeper $ZK_SERVER
$KAFKA_HOME/bin/kafka-topics.sh --create --topic templateTopic --zookeeper $ZK_SERVER

echo "Topics created!"