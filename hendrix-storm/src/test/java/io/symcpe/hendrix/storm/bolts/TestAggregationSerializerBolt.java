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

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.MockTupleHelpers;
import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.aggregations.StateAggregationAction;
import io.symcpe.wraith.conditions.Condition;
import io.symcpe.wraith.conditions.relational.EqualsCondition;
import io.symcpe.wraith.conditions.relational.ExistsCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;

/**
 * Unit test aggregation serializer
 * 
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestAggregationSerializerBolt {

	@Mock
	private OutputCollector collector;
	@Mock
	private Tuple tuple;

	@Test
	public void testBasicSerialization() {
		AggregationSerializerBolt bolt = new AggregationSerializerBolt();
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, collector);
		when(tuple.getLongByField(Constants.FIELD_TIMESTAMP)).thenReturn(143322L);
		when(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID)).thenReturn("34_22");
		when(tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY)).thenReturn("host1");
		when(tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW)).thenReturn(100);
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		when(tuple.contains(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		bolt.execute(tuple);
		verify(collector, times(1)).ack(tuple);
	}

	@Test
	public void testJSONSerialization() {
		AggregationSerializerBolt bolt = new AggregationSerializerBolt();
		Map<String, String> conf = new HashMap<>();
		final AtomicReference<Values> outputTuple = new AtomicReference<Values>(null);
		OutputCollector mockCollector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[1];
				outputTuple.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		bolt.prepare(conf, null, mockCollector);
		when(tuple.getLongByField(Constants.FIELD_TIMESTAMP)).thenReturn(143322L);
		when(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID)).thenReturn("34_22");
		when(tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY)).thenReturn("host1");
		when(tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW)).thenReturn(100);
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		when(tuple.contains(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		bolt.execute(tuple);
		verify(mockCollector, times(1)).ack(tuple);
		assertEquals("34_22_host1", outputTuple.get().get(0));

		JsonObject obj = new Gson().fromJson(outputTuple.get().get(1).toString(), JsonObject.class);

		assertEquals(143322L, obj.get(Constants.FIELD_TIMESTAMP).getAsLong());
		assertEquals("34_22", obj.get(Constants.FIELD_RULE_ACTION_ID).getAsString());
		assertEquals("host1", obj.get(Constants.FIELD_AGGREGATION_KEY).getAsString());
		assertEquals(100, obj.get(Constants.FIELD_AGGREGATION_WINDOW).getAsInt());
		assertEquals(true, obj.get(Constants.FIELD_STATE_TRACK).getAsBoolean());
	}

	@Test
	public void testIntegrationWithRulesEngineBolt() {
		RulesEngineBolt reBolt = new RulesEngineBolt();
		Condition condition = new ExistsCondition("host");
		EqualsCondition stateCondition = new EqualsCondition("value", 5);
		Action action = new StateAggregationAction((short) 0, "host", 10, stateCondition);
		Rule testRule = new SimpleRule((short) 1233, "testRule", true, condition, action);
		Map<String, String> stormConf = new HashMap<>();
		stormConf.put(Constants.RSTORE_TYPE, TestStore.class.getCanonicalName());

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
		reBolt.prepare(stormConf, null, mockCollector);
		
		// prime rule engine with a state based rule
		String ruleString = RuleSerializer.serializeRuleToJSONString(testRule, false);
		reBolt.execute(MockTupleHelpers.mockRuleTuple(false, "test", ruleString));

		Event event = new UnifiedFactory().buildEvent();
		long ts = System.currentTimeMillis();
		event.getHeaders().put(Constants.FIELD_TIMESTAMP, ts);
		event.getHeaders().put("host", "test11");
		event.getHeaders().put("value", 5.0);
		action.actOnEvent(event);
		reBolt.execute(MockTupleHelpers.mockEventTuple(event));

		Values values = processedEventContainer.get();
		System.err.println("Integration test event:" + values);
		when(tuple.getLongByField(Constants.FIELD_TIMESTAMP)).thenReturn((Long) values.get(1));
		when(tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW)).thenReturn((Integer) values.get(2));
		when(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID)).thenReturn((String) values.get(3));
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn((Boolean) values.get(0));
		when(tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY)).thenReturn((String) values.get(4));
		
		when(tuple.contains(Constants.FIELD_STATE_TRACK)).thenReturn(true);

		AggregationSerializerBolt bolt = new AggregationSerializerBolt();
		Map<String, String> conf = new HashMap<>();
		mockCollector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[1];
				processedEventContainer.set((Values) newEvent);
				System.out.println("Aggregation serializer fired:" + newEvent);
				return new ArrayList<>();
			}
		});
		bolt.prepare(conf, null, mockCollector);
		bolt.execute(tuple);
		Values values2 = processedEventContainer.get();
		System.out.println("Serialized state tracking:"+values2);
		verify(mockCollector, times(1)).ack(tuple);
		JsonObject obj = new Gson().fromJson(values2.get(1).toString(), JsonObject.class);
		assertEquals(values.get(1), obj.get(Constants.FIELD_TIMESTAMP).getAsLong());
		assertEquals(values.get(2), obj.get(Constants.FIELD_AGGREGATION_WINDOW).getAsInt());
		assertEquals(values.get(3), obj.get(Constants.FIELD_RULE_ACTION_ID).getAsString());
		assertEquals(values.get(0), obj.get(Constants.FIELD_STATE_TRACK).getAsBoolean());
		assertEquals(values.get(4), obj.get(Constants.FIELD_AGGREGATION_KEY).getAsString());
	}
}
