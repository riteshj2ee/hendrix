package io.symcpe.hendrix.storm.bolts;

import java.util.Map;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import io.symcpe.hendrix.storm.Constants;

public class AggregationClassifierBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void execute(Tuple tuple) {
		String string = tuple.getString(0);
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.STATE_STREAM_ID,
				new Fields(Constants.FIELD_STATE_TRACK, Constants.FIELD_TIMESTAMP, Constants.FIELD_AGGREGATION_WINDOW,
						Constants.FIELD_RULE_ACTION_ID, Constants.FIELD_AGGREGATION_KEY));
	}

}
