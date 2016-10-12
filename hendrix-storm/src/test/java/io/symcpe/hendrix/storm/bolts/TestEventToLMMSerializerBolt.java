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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

/**
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestEventToLMMSerializerBolt{

	@Mock
	private OutputCollector collector;
	@Mock
	private Tuple tuple;

	@Test
	public void testPrepare() {
		EventToLMMSerializerBolt bolt = new EventToLMMSerializerBolt();
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, collector);
		assertNotNull(bolt.getGson());
		assertNotNull(bolt.getCollector());
	}
	
	@Test
	public void testExecute() {
		EventToLMMSerializerBolt bolt = new EventToLMMSerializerBolt();
		
		Event event = new UnifiedFactory().buildEvent();
		
		final AtomicReference<Values> outputTuple = new AtomicReference<Values>(null);
		OutputCollector mockCollector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[1];
				outputTuple.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		
		@SuppressWarnings("deprecation")
		long ts = new Date(116, 10, 10).getTime();
		String fakeTenant = UUID.randomUUID().toString().replace("-", ""); 
		
		event.getHeaders().put(Constants.FIELD_TIMESTAMP, ts);
		event.getHeaders().put(Constants.FIELD_RULE_GROUP, fakeTenant);
		when(tuple.getValueByField(Constants.FIELD_EVENT)).thenReturn(event);
		
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, mockCollector);
		bolt.execute(tuple);
		
		verify(mockCollector, times(1)).ack(tuple);
		Values values = outputTuple.get();
		
		assertEquals(fakeTenant, values.get(0));
		JsonObject json = new Gson().fromJson((String) values.get(1), JsonObject.class);
		
		assertEquals("2016-11-10T00:00:00.000Z", json.get(EventToLMMSerializerBolt.TIMESTAMP).getAsString());
		assertEquals(fakeTenant, json.get(EventToLMMSerializerBolt.TENANT_ID).getAsString());
		
	}

}
