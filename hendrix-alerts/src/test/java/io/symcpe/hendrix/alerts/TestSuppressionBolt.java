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
package io.symcpe.hendrix.alerts;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.MockTupleHelpers;
import io.symcpe.hendrix.storm.bolts.TestAlertingEngineBolt;
import io.symcpe.hendrix.storm.bolts.TestStore;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.actions.alerts.Alert;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;

/**
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
public class TestSuppressionBolt {

	@Mock
	private OutputCollector mockCollector;
	@Mock
	private Tuple input;
	private Map<String, String> conf;

	@Before
	public void before() throws IOException {
		conf = new HashMap<>();
		conf.put(TestAlertingEngineBolt.TEMPLATE_CONTENT,
				AlertTemplateSerializer.serialize(new AlertTemplate[] {
						new AlertTemplate((short) 0, "t1", "t1@xyz.com", "mail", "t1", "hello t1", 5, 2),
						new AlertTemplate((short) 1, "t2", "t2@xyz.com", "mail", "t2", "hello t2", 10, 1) }));
		conf.put(Constants.TSTORE_TYPE, TestStore.class.getName());
	}

	@Test
	public void testPrepare() {
		SuppressionBolt bolt = new SuppressionBolt();
		bolt.prepare(conf, null, mockCollector);
		assertEquals(mockCollector, bolt.getCollector());
		assertEquals(2, bolt.getTemplateMap().size());
	}

	@Test
	public void testSuppressionMocked() {
		SuppressionBolt bolt = new SuppressionBolt();
		final AtomicReference<Values> processedEventContainer = new AtomicReference<Values>(null);
		OutputCollector collector = MockTupleHelpers.mockCollector(new Answer<Object>() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object newEvent = invocation.getArguments()[2];
				processedEventContainer.set((Values) newEvent);
				return new ArrayList<>();
			}
		});
		conf.put(TestAlertingEngineBolt.TEMPLATE_CONTENT, AlertTemplateSerializer.serialize(new AlertTemplate[] {
				new AlertTemplate((short) 0, "t1", "t1@xyz.com", "mail", "t1", "hello t1", 5, 2) }));
		bolt.prepare(conf, null, collector);
		Alert alert = new Alert();
		alert.setId((short) 0);
		alert.setBody("test");
		alert.setTimestamp(3253454235L);
		when(input.contains(Constants.FIELD_ALERT)).thenReturn(true);
		when(input.getValueByField(Constants.FIELD_ALERT)).thenReturn(alert);
		int i = 0;
		for (i = 0; i < 2; i++) {
			bolt.execute(input);
			assertEquals(i + 1, bolt.getCounter().get(alert.getId()).getVal());
			verify(collector, times(i + 1)).emit(eq(SuppressionBolt.DELIVERY_STREAM), eq(input), any());
			assertEquals(alert, (Alert) processedEventContainer.get().get(0));
			verify(collector, times(i + 1)).ack(input);
		}
		bolt.execute(input);
		assertEquals(i+1, bolt.getCounter().get(alert.getId()).getVal());
		verify(collector, times(i + 1)).ack(input);
	}

}
