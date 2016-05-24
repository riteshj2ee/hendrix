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
package io.symcpe.wraith.rules;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.EventFactory;
import io.symcpe.wraith.PerformantException;
import io.symcpe.wraith.Utils;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.aggregations.AggregationAction;
import io.symcpe.wraith.rules.validator.RuleValidator;
import io.symcpe.wraith.rules.validator.ValidationException;
import io.symcpe.wraith.store.RulesStore;
import io.symcpe.wraith.store.StoreFactory;

/**
 * A simple single threaded rule engine implementation for Wraith. This
 * implementation can then be combined with a stream / batch processing
 * framework to create a working Real-time or passive Rules-Engine for
 * {@link Event}s.<br>
 * <br>
 * 
 * The rules engine connects to a configured {@link RulesStore} for initial rule
 * loading but any subsequent changes to rules are delivered as events, removing
 * need for any future external calls or I/O blocks.<br>
 * This a mechanism guarantees that all the {@link StatelessRulesEngine}
 * instances operate in sync of the actual rules states and that periodic data
 * store lookups to check changes is never needed.<br>
 * <br>
 * 
 * The data-structures used are not thread-safe therefore all methods are
 * expected to be called synchronously.
 * 
 * @author ambud_sharma
 */
public class StatelessRulesEngine<K, C> {

	private static final Logger logger = LoggerFactory.getLogger(StatelessRulesEngine.class);
	private Map<Short, Rule> ruleMap;
	private Map<String, Map<Short, Rule>> ruleGroupMap;
	private RulesEngineCaller<K, C> caller;
	private EventFactory eventFactory;
	private StoreFactory storeFactory;
	private boolean ruleGroupsActive;
	private int hashSize;

	public StatelessRulesEngine(RulesEngineCaller<K, C> caller, EventFactory eventFactory, StoreFactory storeFactory) {
		this.caller = caller;
		this.eventFactory = eventFactory;
		this.storeFactory = storeFactory;
	}

	/**
	 * Load {@link Rule}s into the engine on start
	 * 
	 * @param conf
	 * @throws Exception
	 */
	public void initializeRules(Map<String, String> conf) throws Exception {
		ruleGroupsActive = Boolean.parseBoolean(conf.getOrDefault(Constants.RULE_GROUP_ACTIVE, Constants.FALSE));
		hashSize = Integer.parseInt(conf.getOrDefault(Constants.RULE_HASH_INIT_SIZE, Constants.DEFAULT_RULE_HASH_SIZE));
		if (!ruleGroupsActive) {
			this.ruleMap = new LinkedHashMap<>(hashSize);
		} else {
			this.ruleGroupMap = new HashMap<>(hashSize);
		}
		RulesStore store = null;
		try {
			store = storeFactory.getRulesStore(conf.get(Constants.RSTORE_TYPE), conf);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw e;
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
			logger.error("Failed to load rules from store, reason:" + e.getMessage());
			throw e;
		}
	}
	
	public static void updateRuleMap(Map<Short, Rule> ruleMap, String ruleJson, boolean delete) throws ValidationException {
		SimpleRule rule = RuleSerializer.deserializeJSONStringToRule(ruleJson);
		try {
			RuleValidator.getInstance().validate(rule);
		} catch (ValidationException e) {
			// ignore rules that don't pass validation
			throw e;
		}
		if (!delete) {
			ruleMap.put(rule.getRuleId(), rule);
		} else {
			ruleMap.remove(rule.getRuleId());
		}
	}

	/**
	 * Rule updates are delivered synchronously by invoking this method.
	 * 
	 * @param ruleJson
	 */
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
		updateRuleMap(ruleMap, ruleJson, delete);
	}

	/**
	 * Evaluates all loaded rules against this event, one rule at a time.<br>
	 * <br>
	 * Deactive rules are ignored, if an {@link Event} matches a {@link Rule}
	 * then the {@link Action} of that rule are applied 1 {@link Action} at a
	 * time.
	 * 
	 * @param event
	 */
	public void evaluateEventAgainstAllRules(C eventCollector, K eventContainer, Event event) {
		if (!ruleGroupsActive) {
			for (Short ruleId : ruleMap.keySet()) {
				Rule rule = ruleMap.get(ruleId);
				evaluateEventAgainstRule(rule, eventCollector, eventContainer, event);
			}
		}
	}

	/**
	 * Evaluates all loaded rules against this event, one rule at a time.<br>
	 * <br>
	 * Deactive rules are ignored, if an {@link Event} matches a {@link Rule}
	 * then the {@link Action} of that rule are applied 1 {@link Action} at a
	 * time.
	 * 
	 * @param event
	 */
	public void evaluateEventAgainstGroupedRules(C eventCollector, K eventContainer, Event event) {
		if (ruleGroupsActive) {
			Map<Short, Rule> rules = ruleGroupMap.get(event.getHeaders().get(Constants.FIELD_RULE_GROUP));
			if (rules != null) {
				for (Short ruleId : rules.keySet()) {
					Rule rule = rules.get(ruleId);
					evaluateEventAgainstRule(rule, eventCollector, eventContainer, event);
				}
			}
		}
	}

	/**
	 * 
	 * @param rule
	 * @param eventCollector
	 * @param eventContainer
	 * @param event
	 */
	public void evaluateEventAgainstRule(Rule rule, C eventCollector, K eventContainer, Event event) {
		if (!rule.isActive()) {
			logger.debug("Rule:" + rule.getRuleId() + " is deactive");
			return;
		}
		long ruleStartTime = System.currentTimeMillis();
		long conditionTime = System.currentTimeMillis();
		boolean result = rule.getCondition().matches(event);
		conditionTime = System.currentTimeMillis() - conditionTime;
		if (result) {
			caller.reportRuleHit(rule.getRuleId());
			List<Action> actions = rule.getActions();
			for (Action action : actions) {
				applyRuleAction(eventCollector, eventContainer, event, rule, action);
			}
		} else {
			caller.handleRuleNoMatch(eventCollector, eventContainer, event, rule);
		}
		caller.reportRuleEfficiency(rule.getRuleId(), System.currentTimeMillis() - ruleStartTime);
		caller.reportConditionEfficiency(rule.getRuleId(), conditionTime);
	}

	/**
	 * Apply a give {@link Rule} {@link Action} on a {@link Event}
	 * 
	 * @param eventCollector
	 * @param eventContainer
	 * @param event
	 * @param rule
	 * @param action
	 */
	protected void applyRuleAction(C eventCollector, K eventContainer, Event event, Rule rule, Action action) {
		short ruleId = rule.getRuleId();
		Event outputEvent = action.actOnEvent(event);
		if (outputEvent == null) {
			Event actionErrorEvent = eventFactory.buildEvent();
			Map<String, Object> errorHeaders = actionErrorEvent.getHeaders();
			errorHeaders.put(Event.HEADER_EVENT_TYPE, Event.ERROR_EVENT_TYPE);
			errorHeaders.put(Event.HEADER_EVENT_ERROR_TYPE, Constants.ACTION_FAIL);
			errorHeaders.put(Event.HEADER_EVENT_ERROR_FIELD, ruleId);
			errorHeaders.put(Event.HEADER_EVENT_ERROR_VALUE, action.getActionId());
			actionErrorEvent.setBody(Utils.eventToBytes(event));
			caller.emitActionErrorEvent(eventCollector, eventContainer, actionErrorEvent);
			return;
		}

		switch (action.getActionType()) {
		case RAW_ALERT:
			caller.emitRawAlert(eventCollector, eventContainer, outputEvent, ruleId, action.getActionId(),
					outputEvent.getHeaders().get(Constants.FIELD_ALERT_TARGET).toString(),
					outputEvent.getHeaders().get(Constants.FIELD_ALERT_MEDIA).toString());
			break;
		case TEMPLATED_ALERT:
			caller.emitTemplatedAlert(eventCollector, eventContainer, outputEvent, ruleId, action.getActionId(),
					rule.getName(), (short) outputEvent.getHeaders().get(Constants.FIELD_ALERT_TEMPLATE_ID),
					(long) outputEvent.getHeaders().get(Constants.FIELD_TIMESTAMP));
			break;
		case AGGREGATION:
			// find the correct stream id based on the aggregation action class
			String ruleActionId = Utils.combineRuleActionId(ruleId, action.getActionId());
			caller.emitAggregationEvent(action.getClass(), eventCollector, eventContainer, event,
					(Long) event.getHeaders().get(Constants.FIELD_TIMESTAMP),
					((AggregationAction) action).getAggregationWindow(), ruleActionId,
					outputEvent.getHeaders().get(Constants.FIELD_AGGREGATION_KEY).toString(),
					outputEvent.getHeaders().get(Constants.FIELD_AGGREGATION_VALUE));
			break;
		case STATE:
			// find the correct stream id based on the aggregation action class
			String stateRuleActionId = Utils.combineRuleActionId(ruleId, action.getActionId());
			caller.emitStateTrackingEvent(eventCollector, eventContainer,
					(Boolean) event.getHeaders().get(Constants.FIELD_STATE_TRACK), event,
					(Long) event.getHeaders().get(Constants.FIELD_TIMESTAMP),
					((AggregationAction) action).getAggregationWindow(), stateRuleActionId,
					outputEvent.getHeaders().get(Constants.FIELD_AGGREGATION_KEY).toString());
			break;
		case NEW:
			outputEvent.getHeaders().put(Constants.FIELD_RULE_ID, ruleId);
			caller.emitNewEvent(eventCollector, eventContainer, event, outputEvent);
			break;
		case TAG:
			caller.emitTaggedEvent(eventCollector, eventContainer, outputEvent);
			break;
		case OMEGA:
			caller.emitOmegaActions(eventCollector, eventContainer, outputEvent);
		case ANOMD:
			caller.emitAnomalyAction(eventCollector, eventContainer,
					outputEvent.getHeaders().get(Constants.FIELD_ANOMALY_SERIES).toString(),
					(Number) outputEvent.getHeaders().get(Constants.FIELD_ANOMALY_VALUE));
		default:
			break;
		}
	}

	/**
	 * @return the ruleMap
	 */
	public Map<Short, Rule> getRuleMap() {
		return ruleMap;
	}

	/**
	 * @return the ruleGroupMap
	 */
	public Map<String, Map<Short, Rule>> getRuleGroupMap() {
		return ruleGroupMap;
	}

	/**
	 * @return the ruleGroupsActive
	 */
	public boolean isRuleGroupsActive() {
		return ruleGroupsActive;
	}

	/**
	 * @return the hashSize
	 */
	public int getHashSize() {
		return hashSize;
	}
}