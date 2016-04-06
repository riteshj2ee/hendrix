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
import java.util.logging.Logger;

import com.google.gson.Gson;

import backtype.storm.metric.api.MeanReducer;
import backtype.storm.metric.api.MultiCountMetric;
import backtype.storm.metric.api.MultiReducedMetric;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.HendrixEvent;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleCommand;
import io.symcpe.wraith.rules.RulesEngineCaller;
import io.symcpe.wraith.rules.StatelessRulesEngine;

/**
 * Bolt implementing {@link StatelessRulesEngine}
 * 
 * @author ambud_sharma
 */
public class RulesEngineBolt extends BaseRichBolt implements RulesEngineCaller<Tuple, OutputCollector> {

	private static final long serialVersionUID = 1L;
	public static final String RULE_VALUE_SEPARATOR = "_";
	public static final int METRICS_FREQUENCY = 60;
	public static final String RULE_HIT_COUNT = "rule_hit_count";
	public static final String CONDITION_EFFICIENCY = "condition_efficiency";
	public static final String RULE_EFFICIENCY = "rule_efficiency";
	private transient Logger logger;
	private transient Gson gson;
	private transient StatelessRulesEngine<Tuple, OutputCollector> rulesEngine;
	private transient OutputCollector collector;
	private transient MultiReducedMetric ruleEfficiency;
	private transient MultiReducedMetric conditionEfficiency;
	private transient MultiCountMetric ruleHitCount;
	private transient boolean multiTenancyActive;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.multiTenancyActive = Boolean
				.parseBoolean(stormConf.getOrDefault(Constants.RULE_GROUP_ACTIVE, Constants.FALSE).toString());
		this.logger = Logger.getLogger(RulesEngineBolt.class.getName());
		UnifiedFactory factory = new UnifiedFactory();
		this.rulesEngine = new StatelessRulesEngine<Tuple, OutputCollector>(this, factory, factory);
		try {
			this.rulesEngine.initializeRules(stormConf);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.gson = new Gson();
		this.collector = collector;
		this.ruleEfficiency = new MultiReducedMetric(new MeanReducer());
		this.conditionEfficiency = new MultiReducedMetric(new MeanReducer());
		this.ruleHitCount = new MultiCountMetric();
		if (context != null) {
			context.registerMetric(RULE_EFFICIENCY, ruleEfficiency, METRICS_FREQUENCY);
			context.registerMetric(CONDITION_EFFICIENCY, conditionEfficiency, METRICS_FREQUENCY);
			context.registerMetric(RULE_HIT_COUNT, ruleHitCount, METRICS_FREQUENCY);
		}
		logger.info("Rules Engine Bolt initialized");
	}

	@Override
	public void execute(Tuple tuple) {
		if (Utils.isRuleSyncTuple(tuple)) {
			logger.info("Attempting to apply rule update:" + tuple.getValueByField(Constants.FIELD_RULE_CONTENT));
			RuleCommand ruleCommand = (RuleCommand) tuple.getValueByField(Constants.FIELD_RULE_CONTENT);
			try {
				logger.info("Received rule tuple with rule content:" + ruleCommand.getRuleContent());
				rulesEngine.updateRule(ruleCommand.getRuleGroup(), ruleCommand.getRuleContent(),
						ruleCommand.isDelete());
				logger.info("Applied rule update with rule content:" + ruleCommand.getRuleContent());
			} catch (Exception e) {
				// failed to update rule
				System.err.println("Failed to apply rule update:" + e.getMessage() + "\t"
						+ tuple.getValueByField(Constants.FIELD_RULE_CONTENT));
				StormContextUtil.emitErrorTuple(collector, tuple, RulesEngineBolt.class, tuple.toString(),
						"Failed to apply rule update", e);
			}
		} else {
			HendrixEvent event = (HendrixEvent) tuple.getValueByField(Constants.FIELD_EVENT);
			// call rules engine to evaluate this event and then trigger
			// appropriate actions
			if (multiTenancyActive) {
				rulesEngine.evaluateEventAgainstGroupedRules(this.collector, tuple, event);
			} else {
				rulesEngine.evaluateEventAgainstAllRules(this.collector, tuple, event);
			}
		}
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.ALERT_STREAM_ID, new Fields(Constants.FIELD_EVENT, Constants.FIELD_RULE_ID,
				Constants.FIELD_ACTION_ID, Constants.FIELD_ALERT_TARGET, Constants.FIELD_ALERT_MEDIA, Constants.FIELD_RULE_GROUP, Constants.FIELD_TIMESTAMP));
		StormContextUtil.declareErrorStream(declarer);
	}

	@Override
	public void emitAlert(OutputCollector eventCollector, Tuple eventContainer, Event outputEvent, short ruleId,
			short actionId, String target, String mediaType) {
		if(multiTenancyActive) {
			collector.emit(Constants.ALERT_STREAM_ID, eventContainer,
					new Values(outputEvent, ruleId, actionId, target, mediaType, outputEvent.getHeaders().get(Constants.FIELD_RULE_GROUP), 0L));
		}else {
			collector.emit(Constants.ALERT_STREAM_ID, eventContainer,
					new Values(outputEvent, ruleId, actionId, target, mediaType, null, 0L));
		}
	}

	@Override
	public void handleRuleNoMatch(OutputCollector eventCollector, Tuple eventContainer, Event inputEvent, Rule rule) {
		// TODO support type 2 alerts
	}

	@Override
	public void reportConditionEfficiency(Short ruleId, long executeTime) {
		conditionEfficiency.scope(ruleId.toString()).update(executeTime);
	}

	@Override
	public void reportRuleEfficiency(Short ruleId, long executeTime) {
		ruleEfficiency.scope(ruleId.toString()).update(executeTime);
	}

	@Override
	public void reportRuleHit(Short ruleId) {
		ruleHitCount.scope(ruleId.toString()).incr();
	}

	@Override
	public void emitActionErrorEvent(OutputCollector collector, Tuple eventContainer, Event actionErrorEvent) {
		StormContextUtil.emitErrorTuple(collector, eventContainer, RulesEngineBolt.class, gson.toJson(actionErrorEvent),
				"Rule action failed to fire", null);
	}

	/**
	 * @return the rulesEngine
	 */
	public StatelessRulesEngine<Tuple, OutputCollector> getRulesEngine() {
		return rulesEngine;
	}

}
