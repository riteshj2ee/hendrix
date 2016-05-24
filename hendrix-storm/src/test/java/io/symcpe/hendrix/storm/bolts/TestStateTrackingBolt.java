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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.MockTupleHelpers;
import io.symcpe.wraith.Utils;

/**
 * Unit tests for {@link StateTrackingBolt}
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestStateTrackingBolt {

	@Mock
	private OutputCollector mockCollector;
	@Mock
	private Tuple tuple;
	@Mock
	private TopologyContext contex;

	@Before
	public void before() throws IOException {
	}

	@Test
	public void testInitialize() {
		StateTrackingBolt bolt = new StateTrackingBolt();
		when(contex.getThisTaskIndex()).thenReturn(1);
		bolt.prepare(new HashMap<>(), contex, mockCollector);
		assertEquals(mockCollector, bolt.getCollector());
		assertEquals(1, bolt.getStateTrackingEngine().getTaskId());
		bolt.cleanup();
	}

	@Test
	public void testTrackStateEmit() {
		StateTrackingBolt bolt = new StateTrackingBolt();
		when(contex.getThisTaskIndex()).thenReturn(1);
		final AtomicReference<Values> processedEventContainer = new AtomicReference<Values>(null);
		OutputCollector collector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[1];
				processedEventContainer.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		Map<String, String> conf = new HashMap<>();
		conf.put(StateTrackingBolt.STATE_FLUSH_BUFFER_SIZE, "1");
		bolt.prepare(conf, contex, collector);
		when(tuple.getSourceStreamId()).thenReturn(Constants.STATE_STREAM_ID);
		when(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID))
				.thenReturn(Utils.combineRuleActionId((short) 2, (short) 2));
		when(tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW)).thenReturn(10);
		when(tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY)).thenReturn("series1");
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		when(tuple.getLongByField(Constants.FIELD_TIMESTAMP)).thenReturn(1464038054645L);
		bolt.execute(tuple);
		assertEquals(1, bolt.getStateTrackingEngine().getAggregationMap().size());
		when(tuple.getSourceStreamId()).thenReturn(Constants.TICK_STREAM_ID);
		bolt.execute(tuple);
		bolt.execute(tuple);
		bolt.execute(tuple);
		assertEquals(0, bolt.getStateTrackingEngine().getAggregationMap().size());
		when(tuple.getSourceStreamId()).thenReturn(Constants.STATE_STREAM_ID);
		bolt.execute(tuple);
		verify(collector, times(4)).ack(tuple);
		bolt.cleanup();
	}

	public void testUntrackStateEmit() {
		StateTrackingBolt bolt = new StateTrackingBolt();
		when(contex.getThisTaskIndex()).thenReturn(1);
		final AtomicReference<Values> processedEventContainer = new AtomicReference<Values>(null);
		OutputCollector collector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[1];
				processedEventContainer.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		bolt.prepare(new HashMap<>(), contex, collector);
		when(tuple.getSourceStreamId()).thenReturn(Constants.STATE_STREAM_ID);
		when(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID))
				.thenReturn(Utils.combineRuleActionId((short) 2, (short) 2));
		when(tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW)).thenReturn(10);
		when(tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY)).thenReturn("series1");
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn(true);
		when(tuple.getLongByField(Constants.FIELD_TIMESTAMP)).thenReturn(1464038054645L);
		bolt.execute(tuple);
		assertEquals(1, bolt.getStateTrackingEngine().getAggregationMap().size());
		when(tuple.getSourceStreamId()).thenReturn(Constants.TICK_STREAM_ID);
		bolt.execute(tuple);
		assertEquals(1, bolt.getStateTrackingEngine().getAggregationMap().size());
		when(tuple.getSourceStreamId()).thenReturn(Constants.STATE_STREAM_ID);
		when(tuple.getBooleanByField(Constants.FIELD_STATE_TRACK)).thenReturn(false);
		bolt.execute(tuple);
		assertEquals(0, bolt.getStateTrackingEngine().getAggregationMap().size());
		when(tuple.getSourceStreamId()).thenReturn(Constants.TICK_STREAM_ID);
		bolt.execute(tuple);
		assertEquals(0, bolt.getStateTrackingEngine().getAggregationMap().size());
		bolt.execute(tuple);
		assertEquals(0, bolt.getStateTrackingEngine().getAggregationMap().size());
		bolt.cleanup();
	}

}