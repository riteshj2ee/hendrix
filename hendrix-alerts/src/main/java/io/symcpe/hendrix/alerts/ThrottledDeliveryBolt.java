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


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.symantec.cpe.volta.storm.base.util.StormContextUtil;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.actions.alerts.Alert;

public abstract class ThrottledDeliveryBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient MessageDigest md;
	private transient Map<String, Integer> thresholdsMap;

	@SuppressWarnings("rawtypes")
	@Override
	public final void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.thresholdsMap = new HashMap<>();
		this.collector = collector;
		try {
			this.md = MessageDigest.getInstance("md5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public final void execute(Tuple tuple) {
		Alert alert = (Alert) tuple.getValueByField(Constants.FIELD_ALERT);
		String hash = new String(this.md.digest((alert.getRuleGroup()+"__"+alert.getTarget()).getBytes()));
		Integer counter = thresholdsMap.get(hash);
		if(counter == null) {
			counter = 0;
		}
		counter = counter + 1;
		thresholdsMap.put(hash, counter);
		collector.ack(tuple);
	}
	
	public abstract void deliverAlert(Alert alert) throws Exception;

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		StormContextUtil.declareErrorStream(declarer);
	}

}
