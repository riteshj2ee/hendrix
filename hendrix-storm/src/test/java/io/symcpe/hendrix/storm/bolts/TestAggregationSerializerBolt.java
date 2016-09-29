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
		System.out.println(outputTuple);
		assertEquals("34_22_host1", outputTuple.get().get(0));
		
		JsonObject obj = new Gson().fromJson(outputTuple.get().get(1).toString(), JsonObject.class);
		
		assertEquals(143322L, obj.get(Constants.FIELD_TIMESTAMP).getAsLong());
		assertEquals("34_22", obj.get(Constants.FIELD_RULE_ACTION_ID).getAsString());
		assertEquals("host1", obj.get(Constants.FIELD_AGGREGATION_KEY).getAsString());
		assertEquals(100, obj.get(Constants.FIELD_AGGREGATION_WINDOW).getAsInt());
		assertEquals(true, obj.get(Constants.FIELD_STATE_TRACK).getAsBoolean());
	}
}
