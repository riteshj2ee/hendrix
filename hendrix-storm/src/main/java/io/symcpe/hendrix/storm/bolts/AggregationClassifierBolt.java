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
package io.symcpe.hendrix.storm.bolts;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;

/**
 * @author ambud_sharma
 */
public class AggregationClassifierBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient Gson gson;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		this.gson = new Gson();
	}

	@Override
	public void execute(Tuple tuple) {
		String message = tuple.getString(0);
		JsonObject obj = gson.fromJson(message, JsonObject.class);
		if (obj.has(Constants.FIELD_STATE_TRACK)) {
			// emit to state tracking stream
			collector.emit(Constants.STATE_STREAM_ID, tuple,
					new Values(obj.get(Constants.FIELD_STATE_TRACK).getAsBoolean(),
							obj.get(Constants.FIELD_TIMESTAMP).getAsLong(),
							obj.get(Constants.FIELD_AGGREGATION_WINDOW).getAsInt(),
							obj.get(Constants.FIELD_RULE_ACTION_ID).getAsString(),
							obj.get(Constants.FIELD_AGGREGATION_KEY).getAsString()));
		} else if (obj.has(Constants.FIELD_AGGREGATION_VALUE)) {
			obj.get(Constants.FIELD_AGGREGATION_VALUE).getAsString();
		}
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.STATE_STREAM_ID,
				new Fields(Constants.FIELD_STATE_TRACK, Constants.FIELD_TIMESTAMP, Constants.FIELD_AGGREGATION_WINDOW,
						Constants.FIELD_RULE_ACTION_ID, Constants.FIELD_AGGREGATION_KEY));
	}

}
