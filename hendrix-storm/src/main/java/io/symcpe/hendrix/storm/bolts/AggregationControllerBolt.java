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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import backtype.storm.Config;
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
import io.symcpe.wraith.PerformantException;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.aggregations.AggregationAction;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleCommand;
import io.symcpe.wraith.rules.StatelessRulesEngine;
import io.symcpe.wraith.store.RulesStore;

/**
 * @author ambud_sharma
 */
public class AggregationControllerBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient Logger logger;
	private transient Map<Short, Rule> ruleMap;
	private transient Map<String, Map<Short, Rule>> ruleGroupMap;
	private transient OutputCollector collector;
	private transient long tickCounter;
	private transient boolean ruleGroupsActive;
	private transient int hashSize;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.logger = Logger.getLogger(AggregationControllerBolt.class.getName());
		this.collector = collector;
		this.tickCounter = 1;
		this.ruleGroupsActive = Boolean
				.parseBoolean(stormConf.getOrDefault(Constants.RULE_GROUP_ACTIVE, Constants.FALSE).toString());
		this.hashSize = Integer.parseInt(
				stormConf.getOrDefault(Constants.RULE_HASH_INIT_SIZE, Constants.DEFAULT_RULE_HASH_SIZE).toString());
		this.ruleMap = new LinkedHashMap<>(hashSize);
		if (!ruleGroupsActive) {
			this.ruleMap = new LinkedHashMap<>(hashSize);
		} else {
			this.ruleGroupMap = new HashMap<>(hashSize);
		}
		RulesStore store = null;
		try {
			store = new UnifiedFactory().getRulesStore(stormConf.get(Constants.RSTORE_TYPE).toString(), stormConf);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		try {
			store.connect();
			if (!ruleGroupsActive) {
				this.ruleMap.putAll(store.listRules());
			} else {
				this.ruleGroupMap.putAll(store.listGroupedRules());
			}
			store.disconnect();
		} catch (IOException e) {
			logger.severe("Failed to load rules from store, reason:" + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@Override
	public void execute(Tuple tuple) {
		if (Utils.isTickTuple(tuple)) {
			tickCounter++;
			if (!ruleGroupsActive) {
				for (Rule rule : ruleMap.values()) {
					sendEmissionsForRule(tuple, null, rule);
				}
			} else {
				for (String ruleGroup : ruleGroupMap.keySet()) {
					Map<Short, Rule> map = ruleGroupMap.get(ruleGroup);
					for (Rule rule : map.values()) {
						sendEmissionsForRule(tuple, ruleGroup, rule);
					}
				}
			}
		} else if (Utils.isRuleSyncTuple(tuple)) {
			logger.info(
					"Attempting to apply rule update:" + (tuple.getValueByField(Constants.FIELD_RULE_CONTENT) == null));
			RuleCommand ruleCommand = (RuleCommand) tuple.getValueByField(Constants.FIELD_RULE_CONTENT);
			try {
				logger.info("Received rule tuple with rule content:" + ruleCommand.getRuleContent());
				updateRule(ruleCommand.getRuleGroup(), ruleCommand.getRuleContent(), ruleCommand.isDelete());
				logger.info("Applied rule update with rule content:" + ruleCommand.getRuleContent());
			} catch (Exception e) {
				// failed to update rule
				System.err.println("Failed to apply rule update:" + e.getMessage() + "\t"
						+ tuple.getValueByField(Constants.FIELD_RULE_CONTENT));
				StormContextUtil.emitErrorTuple(collector, tuple, RulesEngineBolt.class, tuple.toString(),
						"Failed to apply rule update", e);
			}
		} else {
			// unknown tuple type
		}
		collector.ack(tuple);
	}

	public void sendEmissionsForRule(Tuple tuple, String ruleGroup, Rule rule) {
		List<AggregationAction> aggregationActions = filterAggregationActions(rule);
		if (aggregationActions != null) {
			for (AggregationAction action : aggregationActions) {
				if (tickCounter % action.getAggregationWindow() == 0) {
					collector.emit(Constants.TICK_STREAM_ID, tuple,
							new Values(Utils.combineRuleActionId(rule.getRuleId(), action.getActionId()),
									action.getAggregationWindow(), ruleGroup));
				}
			}
		}
	}

	public void updateRule(String ruleGroup, String ruleJson, boolean delete) throws Exception {
		Map<Short, Rule> ruleMap = this.ruleMap;
		if (ruleGroupsActive) {
			if (ruleGroup != null) {
				ruleMap = ruleGroupMap.get(ruleGroup);
				if (ruleMap == null) {
					ruleMap = new LinkedHashMap<>(hashSize);
					ruleGroupMap.put(ruleGroup, ruleMap);
				}
			} else {
				throw new PerformantException("Supplied rule group is null");
			}
		}
		if (ruleMap == null) {
			throw new PerformantException("Rule map not found for rule:" + ruleJson + "\trule-group:" + ruleGroup);
		}
		StatelessRulesEngine.updateRuleMap(ruleMap, ruleJson, delete);
	}

	public static List<AggregationAction> filterAggregationActions(Rule rule) {
		List<AggregationAction> actions = new ArrayList<>();
		for (Action action : rule.getActions()) {
			if (action instanceof AggregationAction) {
				actions.add((AggregationAction) action);
			}
		}
		return actions;
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.TICK_STREAM_ID, new Fields(Constants.FIELD_RULE_ACTION_ID,
				Constants.FIELD_AGGREGATION_WINDOW, Constants.FIELD_RULE_GROUP));
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		Config conf = new Config();
		// send tick tuples every second
		conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, 1);
		return conf;
	}

	/**
	 * @return the logger
	 */
	protected Logger getLogger() {
		return logger;
	}

	/**
	 * @return the ruleMap
	 */
	protected Map<Short, Rule> getRuleMap() {
		return ruleMap;
	}

	/**
	 * @return the ruleGroupMap
	 */
	protected Map<String, Map<Short, Rule>> getRuleGroupMap() {
		return ruleGroupMap;
	}

	/**
	 * @return the collector
	 */
	protected OutputCollector getCollector() {
		return collector;
	}

	/**
	 * @return the tickCounter
	 */
	protected long getTickCounter() {
		return tickCounter;
	}

	/**
	 * @return the ruleGroupsActive
	 */
	protected boolean isRuleGroupsActive() {
		return ruleGroupsActive;
	}

}
