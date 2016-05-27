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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import backtype.storm.metric.api.MultiCountMetric;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.aggregations.StateTrackingEngine;
import io.symcpe.wraith.aggregators.AggregationRejectException;

/**
 * Bolt implementation of {@link StateTrackingEngine}
 * 
 * @author ambud_sharma
 */
public class StateTrackingBolt extends BaseRichBolt {

	private static final String _METRIC_STATE_HIT = "mcm.state.hit";
	private static final int DEFAULT_STATE_FLUSH_BUFFER_SIZE = 1000;
	public static final String STATE_FLUSH_BUFFER_SIZE = "state.flush.buffer.size";
	private static final long serialVersionUID = 1L;
	private Logger logger;
	private transient StateTrackingEngine stateTrackingEngine;
	private transient OutputCollector collector;
	private transient List<Tuple> buffer;
	private transient int bufferSize;
	private transient UnifiedFactory unifiedFactory;
	private transient MultiCountMetric stateHit;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.logger = Logger.getLogger(StateTrackingBolt.class.getName());
		this.collector = collector;
		if (stormConf.containsKey(STATE_FLUSH_BUFFER_SIZE)) {
			bufferSize = Integer.parseInt(stormConf.get(STATE_FLUSH_BUFFER_SIZE).toString());
		} else {
			bufferSize = DEFAULT_STATE_FLUSH_BUFFER_SIZE;
		}
		this.buffer = new ArrayList<>(bufferSize);
		unifiedFactory = new UnifiedFactory();
		stateTrackingEngine = new StateTrackingEngine(unifiedFactory);
		int taskId = context.getThisTaskIndex();
		try {
			stateTrackingEngine.initialize(stormConf, taskId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		stateHit = new MultiCountMetric();
		if (context != null) {
			context.registerMetric(_METRIC_STATE_HIT, stateHit, Constants.METRICS_FREQUENCY);
		}
	}

	@Override
	public void execute(Tuple tuple) {
		if (!Utils.isWraithTickTuple(tuple)) {
			try {
				if (buffer.size() >= bufferSize) {
					stateTrackingEngine.flush();
					for (Tuple t : buffer) {
						collector.ack(t);
					}
				}
				stateHit.scope(Utils.separateRuleActionId(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID)).getKey().toString()).incr();
				if (tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)) {
					logger.fine("State tracking true:" + tuple);
					stateTrackingEngine.track(tuple.getLongByField(Constants.FIELD_TIMESTAMP),
							tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW),
							tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID),
							tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY));
				} else {
					logger.fine("State tracking false:" + tuple);
					stateTrackingEngine.untrack(tuple.getLongByField(Constants.FIELD_TIMESTAMP),
							tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW),
							tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID),
							tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY));
				}
			} catch (AggregationRejectException e) {
				StormContextUtil.emitErrorTuple(collector, tuple, StateTrackingBolt.class, "",
						"State tracking rejected", e);
			} catch (IOException e) {
				for (Tuple t : buffer) {
					collector.fail(t);
				}
				StormContextUtil.emitErrorTuple(collector, tuple, StateTrackingBolt.class, "",
						"State tracking flush failed", e);
			}
			buffer.add(tuple);
		} else {
			String ruleActionId = tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID);
			Entry<Short, Short> ruleActionIdSeparates = Utils.separateRuleActionId(ruleActionId);
			try {
				List<Entry<String, Long>> aggregateHeaders = new ArrayList<>();
				emitAndResetAggregates((int) tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW), ruleActionId,
						aggregateHeaders);
				if (!aggregateHeaders.isEmpty()) {
					for (Entry<String, Long> result : aggregateHeaders) {
						String[] keyParts = Utils.splitMapKey(result.getKey());
						Event event = unifiedFactory.buildEvent();
						event.getHeaders().put(Constants.FIELD_AGGREGATION_KEY, keyParts[keyParts.length - 1]);
						event.getHeaders().put(Constants.FIELD_TIMESTAMP, System.currentTimeMillis());
						event.getHeaders().put(Constants.FIELD_RULE_ID, ruleActionIdSeparates.getKey());
						event.getHeaders().put(Constants.FIELD_ACTION_ID, ruleActionIdSeparates.getValue());
						collector.emit(Constants.AGGREGATION_OUTPUT_STREAM, tuple, new Values(event));
					}
				} else {
					logger.warning("No state aggregations to emit:" + stateTrackingEngine.getAggregationMap());
				}
			} catch (Exception e) {
				// throw e;
			}
			collector.ack(tuple);
		}
	}

	/**
	 * @param ruleActionId
	 * @param aggregateHeaders
	 * @throws IOException
	 */
	public void emitAndResetAggregates(int aggregationWindow, String ruleActionId,
			List<Entry<String, Long>> aggregateHeaders) throws IOException {
		if (stateTrackingEngine.containsRuleActionId(ruleActionId)) {
			stateTrackingEngine.emit(aggregationWindow, ruleActionId, aggregateHeaders);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.AGGREGATION_OUTPUT_STREAM, new Fields(Constants.FIELD_EVENT));
		StormContextUtil.declareErrorStream(declarer);
	}

	@Override
	public void cleanup() {
		try {
			stateTrackingEngine.cleanup();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return the collector
	 */
	protected OutputCollector getCollector() {
		return collector;
	}

	/**
	 * @return engine
	 */
	protected StateTrackingEngine getStateTrackingEngine() {
		return stateTrackingEngine;
	}

}
