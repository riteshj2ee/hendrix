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

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.MockTupleHelpers;

/**
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestAggregationClassifierBolt {

	@Mock
	private OutputCollector collector;
	@Mock
	private Tuple tuple;

	@Test
	public void testPrepare() {
		AggregationClassifierBolt bolt = new AggregationClassifierBolt();
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, collector);
		assertNotNull(bolt.getGson());
		assertNotNull(bolt.getCollector());
	}
	
	@Test
	public void testStateEventDeserialization() {
		String input = "{\"_t\":1476297512224,\"_agw\":10,\"_ri\":\"BNEAAA\\u003d\\u003d\",\"_a\":\"test11\",\"_st\":true}"; 
		AggregationClassifierBolt bolt = new AggregationClassifierBolt();
		final AtomicReference<Values> outputTuple = new AtomicReference<Values>(null);
		final AtomicReference<String> outputStream = new AtomicReference<String>(null);
		OutputCollector mockCollector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object stream = invocation.getArguments()[0];
				outputStream.set((String) stream);
				Object newEvent = invocation.getArguments()[2];
				outputTuple.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, mockCollector);
		when(tuple.getString(0)).thenReturn(input);
		bolt.execute(tuple);
		verify(mockCollector, times(1)).ack(tuple);
		assertEquals(Constants.STATE_STREAM_ID, outputStream.get());
		Values values = outputTuple.get();
		assertEquals(true, values.get(0));
		assertEquals(1476297512224L, values.get(1));
		assertEquals(10, values.get(2));
		assertEquals("BNEAAA\u003d\u003d", values.get(3));
		assertEquals("test11", values.get(4));
	}

}
