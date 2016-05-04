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
package io.symcpe.hendrix.storm.bolts.helpers;

import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import io.symcpe.hendrix.storm.HendrixEvent;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.wraith.Constants;

/**
 * Allows alerted events to be intercepted and sent to the UI for live views.
 * 
 * Compliant with REST schema of baseurl/{ruleid} with the JSON payload of event headers.
 * 
 * @author ambud_sharma
 */
public class AlertViewerBolt extends BaseRichBolt {

	private static final String UI_ENDPOINT = "ui.endpoint";
	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient String uiEndpoint;
	private transient long counter;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		if(stormConf.get(UI_ENDPOINT)!=null) {
			this.uiEndpoint = stormConf.get(UI_ENDPOINT).toString();
		}else {
			this.uiEndpoint = "http://localhost:8080/ROOT/api/receive/";
		}
	}

	@Override
	public void execute(Tuple tuple) {
		short ruleId = 0;
		try {
			CloseableHttpClient client = Utils.buildClient(this.uiEndpoint, 3000, 3000);
			ruleId = tuple.getShortByField(Constants.FIELD_RULE_ID);
			
			String endPoint = uiEndpoint+ruleId;
			
			HendrixEvent event = (HendrixEvent)tuple.getValueByField(Constants.FIELD_EVENT);
			HttpPost req = new HttpPost(endPoint);
			req.setEntity(new StringEntity(new Gson().toJson(event.getHeaders()), ContentType.APPLICATION_JSON));
			CloseableHttpResponse resp = client.execute(req);
			client.close();
			counter++;
			if(counter%1000==0) {
				System.out.println(endPoint+"\t"+resp.getStatusLine().getStatusCode()+"\t"+EntityUtils.toString(resp.getEntity()));
				System.err.println("Alerts sent to UI:"+counter);
			}
		} catch (Exception e) {
			StormContextUtil.emitErrorTuple(collector, tuple, AlertViewerBolt.class, tuple.toString(), "Failed to send alert to UI", e);
		}
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		StormContextUtil.declareErrorStream(declarer);
	}

}
