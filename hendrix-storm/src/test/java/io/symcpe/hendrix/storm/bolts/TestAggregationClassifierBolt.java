package io.symcpe.hendrix.storm.bolts;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Tuple;

@RunWith(MockitoJUnitRunner.class)
public class TestAggregationClassifierBolt {

	@Mock
	private OutputCollector collector;
	@Mock
	private Tuple tuple;

	@Test
	public void testBasicClassifier() {
		AggregationClassifierBolt bolt = new AggregationClassifierBolt();
		Map<String, String> conf = new HashMap<>();
		bolt.prepare(conf, null, collector);
		
	}

}
