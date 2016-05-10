/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.hendrix.alerts;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.actions.alerts.Alert;

/**
 * @author ambud_sharma
 */
public class HttpCallbackBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient AlertDeliveryException exception = new AlertDeliveryException("Non 200 status code returned");

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void execute(Tuple tuple) {
		Alert alert = (Alert) tuple.getValueByField(Constants.FIELD_ALERT);
		try {
			CloseableHttpClient client = Utils.buildClient(alert.getTarget(), 3000, 3000);
			HttpPost request = new HttpPost(alert.getTarget());
			StringEntity body = new StringEntity(alert.getBody(), ContentType.APPLICATION_JSON);
			request.addHeader("content-type", "application/json");
			request.setEntity(body);
			HttpResponse response = client.execute(request);
			EntityUtils.consume(response.getEntity());
			int statusCode = response.getStatusLine().getStatusCode();
			if(statusCode<200 && statusCode>=300) {
				throw exception;
			}
			client.close();
		} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
			StormContextUtil.emitErrorTuple(collector, tuple, HttpCallbackBolt.class, alert.toString(), "Failed to make http callback", e);
		} catch (AlertDeliveryException e) {
			StormContextUtil.emitErrorTuple(collector, tuple, HttpCallbackBolt.class, alert.toString(), "Bad code returned from http callback", e);
		}
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		StormContextUtil.declareErrorStream(declarer);
	}

}
