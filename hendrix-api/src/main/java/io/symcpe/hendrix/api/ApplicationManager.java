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
package io.symcpe.hendrix.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.PartitionInfo;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.symcpe.hendrix.api.rest.AlertReceiver;
import io.symcpe.hendrix.api.rules.RulesManager;
import io.symcpe.hendrix.api.rules.TenantManager;
import io.symcpe.hendrix.api.validations.VelocityValidator;
import io.symcpe.wraith.rules.validator.RuleValidator;
import io.symcpe.wraith.rules.validator.Validator;

/**
 * Entry point class for Dropwizard app
 * 
 * @author ambud_sharma
 */
public class ApplicationManager extends Application<AppConfig> implements Daemon {
	
	public static final boolean LOCAL = Boolean.parseBoolean(System.getProperty("local", "true"));
	private static final String PROP_CONFIG_FILE = "hendrixConfig";
	private Properties config;
	private EntityManagerFactory factory;
	private EntityManager em;
	private String ruleTopicName;
	private KafkaProducer<String, String> producer;
	private String[] args;
	
	public void init() {
		config = new Properties(System.getProperties());
		if (System.getenv(PROP_CONFIG_FILE) != null) {
			try {
				config.load(new FileInputStream(System.getenv(PROP_CONFIG_FILE)));
			} catch (IOException e) {
				throw new RuntimeException("Configuration file not loaded", e);
			}
		} else {
			try {
				config.load(ApplicationManager.class.getClassLoader().getResourceAsStream("default-config.properties"));
			} catch (IOException e) {
				throw new RuntimeException("Default configuration file not loaded", e);
			}
		}
		try {
			Utils.createDatabase(config.getProperty("javax.persistence.jdbc.url"),
					config.getProperty("javax.persistence.jdbc.db", "hendrix"),
					config.getProperty("javax.persistence.jdbc.user"),
					config.getProperty("javax.persistence.jdbc.password"),
					config.getProperty("javax.persistence.jdbc.driver"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		config.setProperty("javax.persistence.jdbc.url", config.getProperty("javax.persistence.jdbc.url")
				+ config.getProperty("javax.persistence.jdbc.db", "hendrix"));
		AlertReceiver.getInstance();
		factory = Persistence.createEntityManagerFactory("hendrix", config);
		EntityManager em = factory.createEntityManager();
		System.out.println("Rules stats" + em.createNamedQuery("Rules.stats").getResultList());
		em.close();
		RulesManager.getInstance().init(this);
		TenantManager.getInstance().init(this);
		if (!LOCAL) {
			initKafkaConnection();
		}
		
	}
	
	public void addRuleValidators(Properties config) {
		List<Validator<?>> validators = Arrays.asList(new VelocityValidator());
		RuleValidator.getInstance().configure(validators);
	}

	public void initKafkaConnection() {
		producer = new KafkaProducer<>(config);
		System.out.println("Validating kafka connectivity");
		List<PartitionInfo> partitions = producer.partitionsFor(ruleTopicName);
		for (PartitionInfo partitionInfo : partitions) {
			System.out.println(partitionInfo.topic() + "\t" + partitionInfo.leader().toString());
		}
	}

	public EntityManager getEM() {
		if (em == null) {
			em = factory.createEntityManager();
		}
		return em;
	}

	/**
	 * @return producer
	 */
	public KafkaProducer<String, String> getKafkaProducer() {
		return producer;
	}

	/**
	 * @return ruleTopicName
	 */
	public String getRuleTopicName() {
		return ruleTopicName;
	}
	
	public static void main(String[] args) throws Exception {
		new ApplicationManager().run(args);
	}

	@Override
	public void initialize(Bootstrap<AppConfig> bootstrap) {
		super.initialize(bootstrap);
	}
	
	@Override
	public void run(AppConfig configuration, Environment environment) throws Exception {
		
	}
	
	@Override
	public void init(DaemonContext context) throws DaemonInitException, Exception {
		args = context.getArguments();
	}

	@Override
	public void start() throws Exception {
		List<String> arguments = new ArrayList<>();
		if(args.length >= 1) {
			for(String arg : args) {
				arguments.add(arg);
			}
		}
		arguments.add(0, "server");
		main(arguments.toArray(new String[1]));
	}

	@Override
	public void stop() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

}
