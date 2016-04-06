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

import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.wraith.rules.RuleCommand;

/**
 * Converts {@link RuleCommand} JSON to object before it's sent to
 * {@link RulesEngineBolt}
 * 
 * @author ambud_sharma
 */
public class RuleTranslatorBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient Logger logger;
	private transient OutputCollector collector;
	private transient Gson gson;
	private transient Type type;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.logger = Logger.getLogger(RuleTranslatorBolt.class.getName());
		this.collector = collector;
		this.gson = new Gson();
		type = new TypeToken<RuleCommand>() {
		}.getType();
		logger.info("Rules Translator Bolt initialized");
	}

	@Override
	public void execute(Tuple input) {
		try {
			logger.info("Translating rule command:"+input.getString(0));
			RuleCommand ruleCommandJson = gson.fromJson(input.getString(0), type);
			if (ruleCommandJson != null) {
				collector.emit(Constants.RULE_STREAM_ID, new Values(ruleCommandJson));
			} else {
				throw new NullPointerException("Rule command is null, unable to parse:" + input.getString(0));
			}
		} catch (Exception e) {
			System.err.println("Bad rule update");
			StormContextUtil.emitErrorTuple(collector, input, JSONTranslatorBolt.class, "JSON to RuleWrapper issue",
					input.getString(0), e);
		}
		collector.ack(input);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.RULE_STREAM_ID, new Fields(Constants.FIELD_RULE_CONTENT));
		StormContextUtil.declareErrorStream(declarer);
	}

}