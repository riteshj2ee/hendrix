#!/bin/bash
#
# Author: Ambud Sharma
# 
# Purpose: Run script for API
#
#export MYSQL_ADDRESS=`getent hosts $MYSQL_ADDRESS | awk '{ print $1 }'`

envsubst < /opt/hendrix/template.properties > /opt/hendrix/config.properties

export hendrixConfig=/opt/hendrix/config.properties

java -jar /usr/local/hendrix/api.jar server /opt/hendrix/config.yaml
#tail -f /var/log/*