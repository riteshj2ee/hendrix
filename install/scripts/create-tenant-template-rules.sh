#!/bin/bash

set -x

curl -H 'Content-Type: application/json' -XPOST localhost:9000/api/tenants -d '{ "tenant_name":"test","tenant_id":"test" }'

TEMPLATE=`curl -XPOST localhost:9000/api/tenants/test/templates/`

TEMPLATE_ID=`echo $TEMPLATE| jq '.templateId'`

curl -H 'Content-Type: application/json' -XPUT localhost:9000/api/tenants/test/templates/$TEMPLATE_ID -d '{"templateId":'$TEMPLATE_ID',"templateName":"rest","destination":"test@xyz.com","media":"mail","subject":"test","body":"test","throttleDuration":300,"throttleLimit":1}'

RULE=`curl -XPOST localhost:9000/api/tenants/test/rules/`
RULE_ID=`echo $RULE | jq '.ruleId'`

echo "Created rule id: $RULE_ID"

curl -H 'Content-Type: application/json' -XPUT localhost:9000/api/tenants/test/rules/$RULE_ID -d '{ "condition": { "type": "EQUALS", "props": { "value": "app1.symcpe.io", "key": "host" } }, "actions":[{"type":"STATE","props":{"stateCondition":{"type":"EQUALS","props":{"value":"app1.symcpe.io","key":"host"}},"actionId":0,"aggregationKey":"host","aggregationWindow":30}}], "ruleId": "'$RULE_ID'", "name": "State tracking rule", "active": true, "description": "test"}'

STATE_RULE_ID=$RULE_ID

RULE=`curl -XPOST localhost:9000/api/tenants/test/rules/`
RULE_ID=`echo $RULE | jq '.ruleId'`

echo "Created rule id: $RULE_ID"

curl -H 'Content-Type: application/json' -XPUT localhost:9000/api/tenants/test/rules/$RULE_ID -d '{"condition":{"type":"EQUALS","props":{"value":'$STATE_RULE_ID',"key":"_r"}},"actions":[{"type":"ALERT","props":{"actionId":0,"templateId":'$TEMPLATE_ID'}}], "ruleId": "'$RULE_ID'", "name": "State alerting rule", "active": true, "description": "test"}'