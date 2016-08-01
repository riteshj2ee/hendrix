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
import java.net.InetAddress;
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
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.server.validation.ValidationFeature;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.symcpe.hendrix.api.dao.AlertReceiver;
import io.symcpe.hendrix.api.dao.PerformanceMonitor;
import io.symcpe.hendrix.api.hc.DBHealthCheck;
import io.symcpe.hendrix.api.hc.KafkaHealthCheck;
import io.symcpe.hendrix.api.rest.PerfMonEndpoint;
import io.symcpe.hendrix.api.rest.RestReceiver;
import io.symcpe.hendrix.api.rest.RulesEndpoint;
import io.symcpe.hendrix.api.rest.TemplateEndpoint;
import io.symcpe.hendrix.api.rest.TenantEndpoint;
import io.symcpe.hendrix.api.security.BapiAuthorizationFilter;
import io.symcpe.hendrix.api.validations.VelocityValidator;
import io.symcpe.wraith.rules.validator.RuleValidator;
import io.symcpe.wraith.rules.validator.Validator;

/**
 * Entry point class for Dropwizard app
 * 
 * @author ambud_sharma
 */
public class ApplicationManager extends Application<AppConfig> implements Daemon {

	private static final String APIKEY_TOPIC_NAME = "apikey.topic.name";
	private static final String TEMPLATE_TOPIC_NAME = "template.topic.name";
	private static final String RULE_TOPIC_NAME = "rule.topic.name";
	private static final String IGNITE_DISOVERY_ADDRESS = "ignite.discovery.address";
	private static final String JAVAX_PERSISTENCE_JDBC_URL = "javax.persistence.jdbc.url";
	private static final String JAVAX_PERSISTENCE_JDBC_DRIVER = "javax.persistence.jdbc.driver";
	private static final String JAVAX_PERSISTENCE_JDBC_PASSWORD = "javax.persistence.jdbc.password";
	private static final String JAVAX_PERSISTENCE_JDBC_USER = "javax.persistence.jdbc.user";
	private static final String JAVAX_PERSISTENCE_JDBC_DB = "javax.persistence.jdbc.db";
	public static final boolean LOCAL = Boolean.parseBoolean(System.getProperty("local", "false"));
	private static final String PROP_CONFIG_FILE = "hendrixConfig";
	private Properties config;
	private EntityManagerFactory factory;
	private String ruleTopicName;
	private String templateTopicName;
	private String apiKeyTopic;
	private KafkaProducer<String, String> producer;
	private String[] args;
	private PerformanceMonitor perfMonitor;
	private Ignite ignite;
	private AlertReceiver alertReceiver;
	private AppConfig configuration;

	public void init(AppConfig appConfiguration) {
		config = new Properties(System.getProperties());
		if (System.getenv(PROP_CONFIG_FILE) != null) {
			try {
				config.load(new FileInputStream(System.getenv(PROP_CONFIG_FILE)));
			} catch (IOException e) {
				throw new RuntimeException("Configuration file not loaded", e);
			}
		} else if (System.getProperty(PROP_CONFIG_FILE) != null) {
			try {
				config.load(new FileInputStream(System.getProperty(PROP_CONFIG_FILE)));
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
			Utils.createDatabase(config.getProperty(JAVAX_PERSISTENCE_JDBC_URL),
					config.getProperty(JAVAX_PERSISTENCE_JDBC_DB, "hendrix"),
					config.getProperty(JAVAX_PERSISTENCE_JDBC_USER),
					config.getProperty(JAVAX_PERSISTENCE_JDBC_PASSWORD),
					config.getProperty(JAVAX_PERSISTENCE_JDBC_DRIVER));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		config.setProperty(JAVAX_PERSISTENCE_JDBC_URL, config.getProperty(JAVAX_PERSISTENCE_JDBC_URL)
				+ config.getProperty(JAVAX_PERSISTENCE_JDBC_DB, "hendrix"));
		factory = Persistence.createEntityManagerFactory("hendrix", config);
		EntityManager em = factory.createEntityManager();
		System.out.println("Rules stats" + em.createNamedQuery("Rules.stats").getResultList());
		em.close();
		if (!LOCAL) {
			initKafkaConnection();
		}
	}

	public void addRuleValidators(Properties config) {
		List<Validator<?>> validators = Arrays.asList(new VelocityValidator());
		RuleValidator.getInstance().configure(validators);
	}

	public void initKafkaConnection() {
		ruleTopicName = config.getProperty(RULE_TOPIC_NAME, "ruleTopic");
		templateTopicName = config.getProperty(TEMPLATE_TOPIC_NAME, "templateTopic");
		apiKeyTopic = config.getProperty(APIKEY_TOPIC_NAME, "apikeyTopic");
		producer = new KafkaProducer<>(config);
	}

	public EntityManager getEM() {
		return factory.createEntityManager();
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

	public String getTemplateTopicName() {
		return templateTopicName;
	}

	public static void main(String[] args) throws Exception {
		new ApplicationManager().run(args);
	}

	@Override
	public void initialize(Bootstrap<AppConfig> bootstrap) {
		bootstrap.addBundle(new SwaggerBundle<AppConfig>() {
			@Override
			protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(AppConfig configuration) {
				SwaggerBundleConfiguration config = new SwaggerBundleConfiguration();
				config.setLicense("The Apache Software License, Version 2.0");
				config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.txt");
				config.setTitle("Hendrix API");
				config.setResourcePackage("io.symcpe.hendrix.api.rest");
				config.setDescription(
						"Hendrix API is allows CRUD operations for Rules, Tenants and Templates in Hendrix");
				return config;
			}
		});
		super.initialize(bootstrap);
	}

	@Override
	public void run(AppConfig configuration, Environment environment) throws Exception {
		this.configuration = configuration;
		init(configuration);
		environment.jersey().property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, false);
		environment.jersey().register(ValidationFeature.class);
		if (configuration.isEnableAuthorization()) {
			environment.jersey().register(new BapiAuthorizationFilter());
			environment.jersey().register(RolesAllowedDynamicFeature.class);
		}
		configureIgnite(configuration, environment);
		perfMonitor = new PerformanceMonitor(this);
		environment.lifecycle().manage(perfMonitor);
		alertReceiver = new AlertReceiver(this);
		environment.lifecycle().manage(alertReceiver);
		environment.jersey().register(new RulesEndpoint(this));
		environment.jersey().register(new TenantEndpoint(this));
		environment.jersey().register(new TemplateEndpoint(this));
		environment.jersey().register(new RestReceiver(this));
		environment.jersey().register(new PerfMonEndpoint(this));
		if (!LOCAL) {
			String hostAddress = InetAddress.getLocalHost().getHostAddress();
			environment.healthChecks().register("kafkaHC", new KafkaHealthCheck(this, hostAddress));
		}
		environment.healthChecks().register("dbHC", new DBHealthCheck(this));
	}

	protected void configureIgnite(AppConfig configuration2, Environment environment) {
		Ignition.setClientMode(false);
		IgniteConfiguration cfg = new IgniteConfiguration();
		cfg.setGridName("hendrix");
		cfg.setClientMode(false);
		cfg.setClockSyncFrequency(2000);
		TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();
		discoSpi.setAckTimeout(3000);
		discoSpi.setHeartbeatFrequency(2000);
		TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
		ipFinder.setAddresses(Arrays.asList(config.getProperty(IGNITE_DISOVERY_ADDRESS, "localhost")));
		discoSpi.setIpFinder(ipFinder);
		cfg.setDiscoverySpi(discoSpi);
		ignite = Ignition.start(cfg);
		System.err.println("\n\nIgnite using TCP static IP based discovery with address:"+config.getProperty(IGNITE_DISOVERY_ADDRESS, "localhost")+"\n\n");
	}

	@Override
	public void init(DaemonContext context) throws DaemonInitException, Exception {
		args = context.getArguments();
	}

	@Override
	public void start() throws Exception {
		List<String> arguments = new ArrayList<>();
		if (args.length >= 1) {
			for (String arg : args) {
				arguments.add(arg);
			}
		}
		arguments.add(0, "server");
		main(arguments.toArray(new String[1]));
	}

	@Override
	public void stop() throws Exception {
	}

	@Override
	public void destroy() {
	}

	/**
	 * @return
	 */
	public PerformanceMonitor getPerfMonitor() {
		return perfMonitor;
	}

	/**
	 * @return
	 */
	public Ignite getIgnite() {
		return ignite;
	}

	/**
	 * @return
	 */
	public AlertReceiver getAlertReceiver() {
		return alertReceiver;
	}

	/**
	 * @return the configuration
	 */
	public AppConfig getConfiguration() {
		return configuration;
	}

	/**
	 * @return the apiKeyTopic
	 */
	public String getApiKeyTopic() {
		return apiKeyTopic;
	}
}
