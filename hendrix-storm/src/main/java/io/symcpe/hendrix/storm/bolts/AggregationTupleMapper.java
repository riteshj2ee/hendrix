package io.symcpe.hendrix.storm.bolts;

import backtype.storm.tuple.Tuple;
import storm.kafka.bolt.mapper.TupleToKafkaMapper;

public class AggregationTupleMapper implements TupleToKafkaMapper<String, String> {

	private static final long serialVersionUID = 1L;

	@Override
	public String getKeyFromTuple(Tuple tuple) {
		return tuple.getStringByField(AggregationSerializerBolt.KEY);
	}

	@Override
	public String getMessageFromTuple(Tuple tuple) {
		return tuple.getStringByField(AggregationSerializerBolt.VALUE);
	}

}
