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
package io.symcpe.hendrix.storm;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.AuthorizationException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.generated.StormTopology;
import backtype.storm.topology.BoltDeclarer;
import backtype.storm.topology.TopologyBuilder;
import io.symcpe.hendrix.storm.bolts.AlertViewerBolt;
import io.symcpe.hendrix.storm.bolts.AlertingEngineBolt;
import io.symcpe.hendrix.storm.bolts.ErrorBolt;
import io.symcpe.hendrix.storm.bolts.FileWriterBolt;
import io.symcpe.hendrix.storm.bolts.JSONTranslatorBolt;
import io.symcpe.hendrix.storm.bolts.PrinterBolt;
import io.symcpe.hendrix.storm.bolts.RuleTranslatorBolt;
import io.symcpe.hendrix.storm.bolts.RulesEngineBolt;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.ZkHosts;
import storm.kafka.bolt.KafkaBolt;
import storm.kafka.bolt.selector.DefaultTopicSelector;

/**
 * Wiring of Hendrix Storm Topology
 * 
 * @author ambud_sharma
 */
public class HendrixTopology {

	public static final String LOCAL = "local";
	private static final Logger logger = Logger.getLogger(HendrixTopology.class.getName());
	private TopologyBuilder builder;
	private Properties config;
	private StormTopology topology;
	private String topologyName;

	/**
	 * Attach and configure Kafka Spouts
	 */
	public void attachAndConfigureKafkaSpouts() {
		String id = topologyName;
		String zkRoot = "/";
		String topicName = config.getProperty(Constants.KAFKA_TOPIC_NAME, Constants.DEFAULT_TOPIC_NAME);
		String zkConnectionStr = config.getProperty(Constants.KAFKA_ZK_CONNECTION, Constants.DEFAULT_ZOOKEEPER);
		SpoutConfig spoutConf = new SpoutConfig(new ZkHosts(zkConnectionStr), topicName, zkRoot, id);
		builder.setSpout(Constants.TOPOLOGY_KAFKA_SPOUT, new KafkaSpout(spoutConf));

		topicName = config.getProperty(Constants.KAFKA_RULES_TOPIC_NAME, Constants.DEFAULT_RULES_TOPIC);
		spoutConf = new SpoutConfig(new ZkHosts(zkConnectionStr), topicName, zkRoot, id);
		builder.setSpout(Constants.TOPOLOGY_RULE_SYNC_SPOUT, new KafkaSpout(spoutConf)).setMaxTaskParallelism(
				Integer.parseInt(config.getProperty(Constants.KAFKA_SPOUT_PARALLELISM, Constants.PARALLELISM_ONE)));
	}

	/**
	 * Attach and configure File Spouts for local testing
	 */
	public void attachAndConfigureFileSpouts() {
		builder.setSpout(Constants.TOPOLOGY_KAFKA_SPOUT, new FileLogReaderSpout()).setMaxTaskParallelism(1);
		builder.setSpout(Constants.TOPOLOGY_RULE_SYNC_SPOUT, new SpoolingFileSpout()).setMaxTaskParallelism(1);
	}

	/**
	 * Attach and configure bolts
	 */
	public void attachAndConfigureBolts() {
		builder.setBolt(Constants.TOPOLOGY_TRANSLATOR_BOLT, new JSONTranslatorBolt())
				.shuffleGrouping(Constants.TOPOLOGY_KAFKA_SPOUT).setMaxTaskParallelism(Integer.parseInt(
						config.getProperty(Constants.TRANSLATOR_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

		builder.setBolt(Constants.WRAITH_COMPONENT, new RuleTranslatorBolt())
				.shuffleGrouping(Constants.TOPOLOGY_RULE_SYNC_SPOUT).setMaxTaskParallelism(Integer.parseInt(config
						.getProperty(Constants.RULE_TRANSLATOR_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

		builder.setBolt(Constants.TOPOLOGY_RULES_BOLT, new RulesEngineBolt())
				.shuffleGrouping(Constants.TOPOLOGY_TRANSLATOR_BOLT)
				.allGrouping(Constants.WRAITH_COMPONENT, Constants.RULE_STREAM_ID)
				.setMaxTaskParallelism(Integer.parseInt(
						config.getProperty(Constants.RULES_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

		if (Boolean.parseBoolean(config.getProperty(Constants.ENABLE_ALERT_VIEWER, Constants.FALSE))) {
			builder.setBolt(Constants.ALERT_VIEWER_BOLT, new AlertViewerBolt())
					.shuffleGrouping(Constants.TOPOLOGY_RULES_BOLT, Constants.ALERT_STREAM_ID)
					.setMaxTaskParallelism(Integer.parseInt(
							config.getProperty(Constants.TRANSLATOR_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));
		}

		builder.setBolt(Constants.TOPOLOGY_ALERT_BOLT, new AlertingEngineBolt())
				.shuffleGrouping(Constants.TOPOLOGY_RULES_BOLT, Constants.ALERT_STREAM_ID)
				.allGrouping(Constants.WRAITH_COMPONENT, Constants.RULE_STREAM_ID)
				.setMaxTaskParallelism(Integer.parseInt(
						config.getProperty(Constants.ALERT_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

		builder.setBolt(Constants.ERROR_BOLT, new ErrorBolt())
				.shuffleGrouping(Constants.TOPOLOGY_TRANSLATOR_BOLT, Constants.ERROR_STREAM)
				.shuffleGrouping(Constants.WRAITH_COMPONENT, Constants.ERROR_STREAM)
				.shuffleGrouping(Constants.TOPOLOGY_RULES_BOLT, Constants.ERROR_STREAM)
				.shuffleGrouping(Constants.TOPOLOGY_ALERT_BOLT, Constants.ERROR_STREAM);

		// send alerts to file if running in local mode, send to kafka in
		// distributed mode
		if (config.getProperty(LOCAL) == null) {
			builder.setBolt(Constants.TOPOLOGY_ALERT_KAFKA_BOLT,
					new KafkaBolt<String, String>()
							.withTopicSelector(new DefaultTopicSelector(
									config.getProperty(Constants.KAFKA_ALERT_TOPIC, "alertTopic")))
							.withTupleToKafkaMapper(new AlertTupleMapper()))
					.shuffleGrouping(Constants.TOPOLOGY_ALERT_BOLT, Constants.ALERT_STREAM_ID)
					.setMaxTaskParallelism(Integer.parseInt(
							config.getProperty(Constants.KAFKA_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

		} else {
			builder.setBolt(Constants.TOPOLOGY_ALERT_KAFKA_BOLT, new FileWriterBolt())
					.shuffleGrouping(Constants.TOPOLOGY_ALERT_BOLT, Constants.ALERT_STREAM_ID)
					.setMaxTaskParallelism(Integer.parseInt(
							config.getProperty(Constants.KAFKA_BOLT_PARALLELISM_HINT, Constants.PARALLELISM_ONE)));

			BoltDeclarer printerBolt = builder.setBolt(Constants.KAFKA_ERROR_BOLT, new PrinterBolt());

			if (config.containsKey("printTranslator")) {
				printerBolt.shuffleGrouping(Constants.TOPOLOGY_TRANSLATOR_BOLT);
			}

			if (config.containsKey("printValidator")) {
				printerBolt.shuffleGrouping(Constants.TOPOLOGY_VALIDATION_BOLT);
			}

			if (config.containsKey("printRuleTranslator")) {
				printerBolt.shuffleGrouping(Constants.WRAITH_COMPONENT, Constants.RULE_STREAM_ID);
			}

			if (config.containsKey("printAlerts")) {
				printerBolt.shuffleGrouping(Constants.TOPOLOGY_ALERT_BOLT, Constants.ALERT_STREAM_ID);
			}

			if (config.containsKey("printKafkaSpout")) {
				printerBolt.shuffleGrouping(Constants.TOPOLOGY_KAFKA_SPOUT);
			}

			if (config.containsKey("printErrors")) {
				printerBolt.shuffleGrouping(Constants.ERROR_BOLT, Constants.KAFKA_ERROR_STREAM);
			}
		}

	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		if (args.length < 1) {
			System.err.println("Must specify topology configuration file path");
			System.exit(-1);
		}
		Properties props = new Properties();
		props.load(new FileInputStream(args[0]));
		HendrixTopology topology = new HendrixTopology();
		topology.setConfiguration(props);
		try {
			topology.initialize();
		} catch (Exception e) {
			System.err.println("Failed to initialize the topology:" + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
		if (props.getProperty(LOCAL) != null) {
			if (props.getProperty(FileLogReaderSpout.LOG_DIR) == null) {
				System.out.println("Must have log directory to read data from");
				return;
			}
			LocalCluster localStorm = new LocalCluster();
			localStorm.submitTopology(topology.getTopologyName(), props, topology.getTopology());
		} else {
			try {
				StormSubmitter.submitTopology(topology.getTopologyName(), props, topology.getTopology());
			} catch (AlreadyAliveException | InvalidTopologyException | AuthorizationException e) {
				logger.log(Level.SEVERE, "Error submitted the topology", e);
			}
		}
	}

//	@Override
	public void initialize() throws Exception {
		builder = new TopologyBuilder();
		topologyName = config.getProperty(Constants.TOPOLOGY_NAME, "Hendrix");
		if (config.getProperty(LOCAL) != null) {
			attachAndConfigureFileSpouts();
		} else {
			attachAndConfigureKafkaSpouts();
		}
		attachAndConfigureBolts();
		topology = builder.createTopology();
	}

//	@Override
	public void setConfiguration(Properties configuration) {
		this.config = configuration;
	}

//	@Override
	public StormTopology getTopology() {
		return topology;
	}

	/**
	 * @return the topologyName
	 */
	protected String getTopologyName() {
		return topologyName;
	}

	/**
	 * @return the builder
	 */
	public TopologyBuilder getBuilder() {
		return builder;
	}

	/**
	 * @param builder
	 *            the builder to set
	 */
	public void setBuilder(TopologyBuilder builder) {
		this.builder = builder;
	}

}
