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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

import com.google.gson.Gson;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.HendrixEvent;
import io.symcpe.wraith.Event;

/**
 * Converts {@link HendrixEvent} to LMM event
 * 
 * @author ambud_sharma
 */
public class EventToLMMSerializerBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	public static final String TIMESTAMP = "@timestamp";
	public static final String TENANT_ID = "tenant_id";
	private transient Gson gson;
	private transient SimpleDateFormat formatter;
	private transient OutputCollector collector;
	private transient Calendar calendar;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		this.gson = new Gson();
		this.formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");// 2016-10-13T03:58:59.612Z
		this.formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		this.calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	}

	@Override
	public void execute(Tuple tuple) {
		Event event = (Event) tuple.getValueByField(Constants.FIELD_EVENT);
		Long ts = (Long) event.getHeaders().get(Constants.FIELD_TIMESTAMP);
		calendar.setTimeInMillis(ts);
		event.getHeaders().put(TIMESTAMP, formatter.format(calendar.getTime()));
		event.getHeaders().put(TENANT_ID, event.getHeaders().get(Constants.FIELD_RULE_GROUP));
		String eventJson = gson.toJson(event.getHeaders());
		collector.emit(tuple, new Values(event.getHeaders().get(TENANT_ID), eventJson));
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields(Constants.KEY, Constants.VALUE));
	}

	/**
	 * @return the gson
	 */
	protected Gson getGson() {
		return gson;
	}

	/**
	 * @return the formatter
	 */
	protected SimpleDateFormat getFormatter() {
		return formatter;
	}

	/**
	 * @return the collector
	 */
	protected OutputCollector getCollector() {
		return collector;
	}

}
