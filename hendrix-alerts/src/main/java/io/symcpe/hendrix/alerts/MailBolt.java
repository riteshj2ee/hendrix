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

import java.util.Map;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import io.symcpe.hendrix.alerts.media.MailService;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.actions.alerts.Alert;

/**
 * @author ambud_sharma
 */
public class MailBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient MailService mailService;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		this.mailService = new MailService(stormConf);
	}

	@Override
	public void execute(Tuple tuple) {
		Alert alert = (Alert) tuple.getValueByField(Constants.FIELD_ALERT);
		if(!mailService.sendMail(alert)) {
			StormContextUtil.emitErrorTuple(collector, tuple, MailBolt.class, alert.toString(), "Failed to send mail", null);
		}
		collector.ack(tuple);
	}
	
	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		StormContextUtil.declareErrorStream(declarer);
	}

}
