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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.hendrix.storm.bolts.TemplatedAlertingEngineBolt;
import io.symcpe.wraith.Constants;
import io.symcpe.wraith.actions.alerts.Alert;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.actions.alerts.templated.TemplateCommand;
import io.symcpe.wraith.store.StoreFactory;
import io.symcpe.wraith.store.TemplateStore;

/**
 * 
 * 
 * @author ambud_sharma
 */
public class SuppressionBolt extends BaseRichBolt {

	public static final String DELIVERY_STREAM = "deliveryStream";
	private static final Logger logger = Logger.getLogger(SuppressionBolt.class.getName());
	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient StoreFactory storeFactory;
	private transient Map<Short, AlertTemplate> templateMap;
	private transient Map<Short, MutableInt> counter;
	private transient long globalCounter = 1;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public final void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		this.templateMap = new HashMap<>();
		this.counter = new HashMap<>();
		this.storeFactory = new UnifiedFactory();
		try {
			initTemplates(stormConf);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void initTemplates(Map<String, String> conf) throws Exception {
		TemplateStore store = null;
		try {
			store = storeFactory.getTemplateStoreStore(conf.get(Constants.TSTORE_TYPE), conf);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw e;
		}
		try {
			store.connect();
			Map<Short, AlertTemplate> temp = store.getAllTemplates();
			if (temp != null) {
				this.templateMap.putAll(temp);
			}
			logger.info("Fetched " + templateMap.size() + " alert templates from the store");
			store.disconnect();
		} catch (IOException e) {
			throw e;
		}
	}

	@Override
	public final void execute(Tuple tuple) {
		if (tuple.contains(Constants.FIELD_ALERT)) {
			Alert alert = (Alert) tuple.getValueByField(Constants.FIELD_ALERT);
			AlertTemplate template = templateMap.get(alert.getId());
			if (template != null) {
				if (globalCounter % template.getThrottleDuration() == 0 || !counter.containsKey(alert.getId())) {
					counter.put(alert.getId(), new MutableInt());
				}
				MutableInt result = counter.get(alert.getId());
				if (result.incrementAndGet() <= template.getThrottleLimit()) {
					collector.emit(DELIVERY_STREAM, tuple, new Values(alert));
				} else {
					// else just drop the alert
					logger.fine("Suppression alert for:" + alert.getId() + ":\t" + alert);
				}
			} else {
				logger.severe("Template for alert not found for templateid:" + alert.getId());
			}
		} else if (Utils.isTickTuple(tuple)) {
			globalCounter++;
		} else if (Utils.isTemplateSyncTuple(tuple)) {
			logger.info(
					"Attempting to apply template update:" + tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT));
			TemplateCommand templateCommand = (TemplateCommand) tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT);
			try {
				logger.info("Received template tuple with template content:" + templateCommand.getTemplate());
				updateTemplate(templateCommand.getRuleGroup(), templateCommand.getTemplate(),
						templateCommand.isDelete());
				logger.info("Applied template update with template content:" + templateCommand.getTemplate());
			} catch (Exception e) {
				// failed to update rule
				System.err.println("Failed to apply template update:" + e.getMessage() + "\t"
						+ tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT));
				StormContextUtil.emitErrorTuple(collector, tuple, TemplatedAlertingEngineBolt.class, tuple.toString(),
						"Failed to apply rule update", e);
			}
		}
		collector.ack(tuple);
	}

	public void updateTemplate(String ruleGroup, String templateJson, boolean delete) {
		try {
			AlertTemplate template = AlertTemplateSerializer.deserialize(templateJson);
			templateMap.put(template.getTemplateId(), template);
		} catch (Exception e) {
			// logger.log(Level.SEVERE, "Alert template error", e);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(DELIVERY_STREAM, new Fields(Constants.FIELD_ALERT));
		StormContextUtil.declareErrorStream(declarer);
	}

	/**
	 * @return the collector
	 */
	protected OutputCollector getCollector() {
		return collector;
	}

	/**
	 * @return the templateMap
	 */
	protected Map<Short, AlertTemplate> getTemplateMap() {
		return templateMap;
	}

	/**
	 * @return the counter
	 */
	protected Map<Short, MutableInt> getCounter() {
		return counter;
	}

	/**
	 * @return the globalCounter
	 */
	protected long getGlobalCounter() {
		return globalCounter;
	}

}
