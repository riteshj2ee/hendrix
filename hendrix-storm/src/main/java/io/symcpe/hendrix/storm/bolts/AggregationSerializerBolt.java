package io.symcpe.hendrix.storm.bolts;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;

public class AggregationSerializerBolt extends BaseRichBolt {

	public static final String VALUE = "value";
	public static final String KEY = "key";
	private static final long serialVersionUID = 1L;
	private transient Gson gson;
	private transient OutputCollector collector;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		gson = new Gson();
	}

	@Override
	public void execute(Tuple tuple) {
		JsonObject obj = new JsonObject();
		obj.addProperty(Constants.FIELD_TIMESTAMP, tuple.getLongByField(Constants.FIELD_TIMESTAMP));
		obj.addProperty(Constants.FIELD_AGGREGATION_WINDOW,
				tuple.getIntegerByField(Constants.FIELD_AGGREGATION_WINDOW));
		obj.addProperty(Constants.FIELD_RULE_ACTION_ID, tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID));
		obj.addProperty(Constants.FIELD_AGGREGATION_KEY, tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY));

		if (tuple.contains(Constants.FIELD_AGGREGATION_VALUE)) {
			obj.addProperty(Constants.FIELD_RULE_ACTION_ID,
					tuple.getValueByField(Constants.FIELD_AGGREGATION_VALUE).toString());
		}
		
		if(tuple.contains(Constants.FIELD_STATE_TRACK)) {
			obj.addProperty(Constants.FIELD_STATE_TRACK, tuple.getBooleanByField(Constants.FIELD_STATE_TRACK));
		}
		collector.emit(tuple, new Values(tuple.getStringByField(Constants.FIELD_RULE_ACTION_ID) + "_"
				+ tuple.getStringByField(Constants.FIELD_AGGREGATION_KEY), gson.toJson(obj)));
		collector.ack(tuple);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields(KEY, VALUE));
	}

}
