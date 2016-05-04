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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.HendrixEvent;
import io.symcpe.hendrix.storm.MockTupleHelpers;
import io.symcpe.hendrix.storm.TestUtils;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.templated.TemplatedAlertAction;
import io.symcpe.wraith.conditions.Condition;
import io.symcpe.wraith.conditions.relational.ExistsCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;

/**
 * Test rules engine bolt
 * 
 * @author ambud_sharma
 */
public class TestRulesEngineBolt {

	private Rule testRule;
	private Map<String, String> stormConf;
	private List<String> events;

	@Before
	public void before() {
		events = new ArrayList<>();
		Condition condition = new ExistsCondition("host");
		Action action = new TemplatedAlertAction((short) 1, (short) 1);
		testRule = new SimpleRule((short) 1233, "testRule", true, condition, action);
		stormConf = new HashMap<>();
		stormConf.put(Constants.RSTORE_TYPE, TestStore.class.getCanonicalName());
	}

	@Test
	public void testRuleInitialization() {
		String ruleString = RuleSerializer.serializeRuleToJSONString(testRule, false);
		System.out.println("Sending rule:" + ruleString);
		RulesEngineBolt bolt = new RulesEngineBolt();
		bolt.prepare(stormConf, null, MockTupleHelpers.mockBasicCollector());
		bolt.execute(MockTupleHelpers.mockRuleTuple(false, null, ruleString));
		Map<Short, Rule> rules = bolt.getRulesEngine().getRuleMap();

		assertEquals(1, rules.size());
		assertTrue(rules.containsKey((short) 1233));
		assertEquals(testRule, rules.get((short) 1233));
	}
	
	@Test
	public void testRuleDeletionEvent() {
		String ruleString = RuleSerializer.serializeRuleToJSONString(testRule, false);
		System.out.println("Sending rule:" + ruleString);
		RulesEngineBolt bolt = new RulesEngineBolt();
		bolt.prepare(stormConf, null, MockTupleHelpers.mockBasicCollector());
		bolt.execute(MockTupleHelpers.mockRuleTuple(false, null, ruleString));
		Map<Short, Rule> rules = bolt.getRulesEngine().getRuleMap();
		bolt.execute(MockTupleHelpers.mockRuleTuple(true, null, ruleString));
		assertEquals(0, rules.size());
	}

	@Test
	public void testRuleExecution() throws IOException {
		List<String> lines = TestUtils.linesFromFiles("src/test/resources/events.json");
		events.addAll(lines);
		String ruleString = RuleSerializer.serializeRuleToJSONString(testRule, false);
		RulesEngineBolt bolt = new RulesEngineBolt();
		final AtomicReference<Values> processedEventContainer = new AtomicReference<Values>(null);
		OutputCollector mockCollector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[2];
				processedEventContainer.set((Values) newEvent);
				System.out.println("Rule fired:" + newEvent);
				return new ArrayList<>();
			}
		});
		bolt.prepare(stormConf, null, mockCollector);
		bolt.execute(MockTupleHelpers.mockRuleTuple(false, null, ruleString));
		for (String line : events) {
			Event testEvent = TestUtils.stringToEvent(line);
			if(line.contains("host")) {
				assertTrue(testRule.getCondition().matches(testEvent));
			}else {
				assertTrue(!testRule.getCondition().matches(testEvent));
			}
			Tuple input = MockTupleHelpers.mockEventTuple(testEvent);
			bolt.execute(input);
			HendrixEvent processedEvent = (HendrixEvent) processedEventContainer.get().get(0);
			assertTrue(processedEvent.getHeaders().containsKey(Constants.FIELD_ALERT_TEMPLATE_ID));
			assertEquals((short)1, processedEvent.getHeaders().get(Constants.FIELD_ALERT_TEMPLATE_ID));
			verify(mockCollector, times(1)).ack(input);
		}
	}

}
