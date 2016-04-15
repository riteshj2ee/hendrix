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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.*;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.TestFactory;
import io.symcpe.wraith.actions.alerts.AlertAction;
import io.symcpe.wraith.conditions.relational.EqualsCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.RulesEngineCaller;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.rules.StatelessRulesEngine;

/**
 * Tests for Stateless Rules Engine
 * 
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestStatelessRulesEngine {

	@Mock
	private RulesEngineCaller<Object, Object> caller;
	private TestFactory testFactory;
	private StatelessRulesEngine<Object, Object> engine;

	@Before
	public void before() {
		testFactory = new TestFactory();
		engine = new StatelessRulesEngine<>(caller, testFactory, testFactory);
	}

	@Test
	public void testInitializeRules() throws Exception {
		Map<String, String> conf = new HashMap<>();
		conf.put(TestFactory.RULES_CONTENT,
				RuleSerializer.serializeRulesToJSONString(Arrays.asList(new SimpleRule((short) 1122, "test1", true,
						new EqualsCondition("host", "val"), new AlertAction((short) 2, "test", "test", "test"))),
						false));
		engine.initializeRules(conf);
		Map<Short, Rule> map = engine.getRuleMap();
		assertEquals(1, map.size());
	}

	@Test
	public void testUpdateRule() throws Exception {
		// test RE with no pre-loaded rules
		engine.initializeRules(new HashMap<>());
		engine.updateRule(null,
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "val"), new AlertAction((short) 2, "test", "test", "test")), false),
				false);
		Map<Short, Rule> rule = engine.getRuleMap();
		assertEquals(1, rule.size());
		// test RE with pre-loaded rules
		engine = new StatelessRulesEngine<>(caller, testFactory, testFactory);
		Map<String, String> conf = new HashMap<>();
		conf.put(TestFactory.RULES_CONTENT,
				RuleSerializer.serializeRulesToJSONString(Arrays.asList(new SimpleRule((short) 1122, "test1", true,
						new EqualsCondition("host", "val"), new AlertAction((short) 2, "test", "test", "test"))),
						false));
		engine.initializeRules(conf);
		engine.updateRule(null,
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "val"), new AlertAction((short) 2, "test", "test", "test")), false),
				false);
		rule = engine.getRuleMap();
		assertEquals(2, rule.size());
	}

	@Test
	public void testUpdateRuleRuleGroup() throws Exception {
		// test RE with no pre-loaded rules
		HashMap<String, String> config = new HashMap<>();
		config.put(Constants.RULE_GROUP_ACTIVE, Constants.TRUE);
		engine.initializeRules(config);
		String ruleGroup = "test";
		engine.updateRule(ruleGroup,
				RuleSerializer.serializeRuleToJSONString(
						new SimpleRule((short) 1122, "test1", true, new EqualsCondition("host", "val"),
								new AlertAction((short) 2, ruleGroup, ruleGroup, ruleGroup)),
						false),
				false);
		Map<String, Map<Short, Rule>> group = engine.getRuleGroupMap();
		assertEquals(1, group.size());
		assertEquals(1, group.get(ruleGroup).size());
		// test RE with pre-loaded rules
		engine.initializeRules(config);
		engine.updateRule(ruleGroup,
				RuleSerializer.serializeRuleToJSONString(
						new SimpleRule((short) 1122, "test1", true, new EqualsCondition("host", "val"),
								new AlertAction((short) 0, ruleGroup, ruleGroup, ruleGroup)),
						false),
				false);
		engine.updateRule(ruleGroup,
				RuleSerializer.serializeRuleToJSONString(
						new SimpleRule((short) 1124, "test1", true, new EqualsCondition("host", "val"),
								new AlertAction((short) 0, ruleGroup, ruleGroup, ruleGroup)),
						false),
				false);
		group = engine.getRuleGroupMap();
		assertEquals(1, group.size());
		System.out.println(group);
		assertEquals(2, group.get(ruleGroup).size());
	}

	@Test
	public void testEvaluateEventAlert() throws Exception {
		new StatelessRulesEngine<>(caller, testFactory, testFactory);
		engine.initializeRules(new HashMap<>());
		Event event = testFactory.buildEvent();
		event.getHeaders().put("host", "abcd");
		engine.updateRule(null,
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abcd"), new AlertAction((short) 2, "test", "test", "test")),
						false),
				false);
		engine.evaluateEventAgainstAllRules(null, null, event);
		verify(caller).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test");
	}
	
	@Test
	public void testEvaluateEventAlertRuleUpdate() throws Exception {
		new StatelessRulesEngine<>(caller, testFactory, testFactory);
		Map<String, String> conf = new HashMap<>();
		conf.put(TestFactory.RULES_CONTENT,
				RuleSerializer.serializeRulesToJSONString(Arrays.asList(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abcd"), new AlertAction((short) 2, "test", "test1", "test"))),
						false));
		engine.initializeRules(conf);
		Map<Short, Rule> map = engine.getRuleMap();
		assertEquals(1, map.size());
		Event event = testFactory.buildEvent();
		event.getHeaders().put("host", "abcd");
		engine.evaluateEventAgainstAllRules(null, null, event);
		verify(caller).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test1");
		engine.updateRule(null,
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abcd"), new AlertAction((short) 2, "test", "test", "test")),
						false),
				false);
		engine.evaluateEventAgainstAllRules(null, null, event);
		verify(caller).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test");
	}
	
	@Test
	public void testEvaluateEventAlertRuleNoMatch() throws Exception {
		new StatelessRulesEngine<>(caller, testFactory, testFactory);
		Map<String, String> conf = new HashMap<>();
		conf.put(TestFactory.RULES_CONTENT,
				RuleSerializer.serializeRulesToJSONString(Arrays.asList(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abc"), new AlertAction((short) 2, "test", "test1", "test"))),
						false));
		engine.initializeRules(conf);
		Map<Short, Rule> map = engine.getRuleMap();
		assertEquals(1, map.size());
		Event event = testFactory.buildEvent();
		event.getHeaders().put("host", "abcd");
		engine.evaluateEventAgainstAllRules(null, null, event);
		verify(caller).handleRuleNoMatch(null, null, event, engine.getRuleMap().values().iterator().next());
		engine.updateRule(null,
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abcd"), new AlertAction((short) 2, "test", "test", "test")),
						false),
				false);
		engine.evaluateEventAgainstAllRules(null, null, event);
		verify(caller).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test");
	}
	
	@Test
	public void testEvaluateEventRuleGroup() throws Exception {
		new StatelessRulesEngine<>(caller, testFactory, testFactory);
		HashMap<String, String> conf = new HashMap<>();
		conf.put(Constants.RULE_GROUP_ACTIVE, "true");
		engine.initializeRules(conf);
		Event event = testFactory.buildEvent();
		event.getHeaders().put("host", "abcd");
		event.getHeaders().put(Constants.FIELD_RULE_GROUP, "rg1");
		engine.updateRule("rg1",
				RuleSerializer.serializeRuleToJSONString(new SimpleRule((short) 1123, "test1", true,
						new EqualsCondition("host", "abcd"), new AlertAction((short) 2, "test", "test", "test")),
						false),
				false);
		engine.evaluateEventAgainstGroupedRules(null, null, event);
		verify(caller, times(1)).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test");
		event.getHeaders().put(Constants.FIELD_RULE_GROUP, "rg2");
		engine.evaluateEventAgainstGroupedRules(null, null, event);
		verify(caller, times(1)).emitAlert(null, null, event, (short) 1123, (short) 0, "test", "test");
	}

}