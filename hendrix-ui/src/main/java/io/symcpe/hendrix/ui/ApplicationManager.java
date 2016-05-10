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
package io.symcpe.hendrix.ui;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

import io.symcpe.hendrix.ui.rules.RulesManager;
import io.symcpe.hendrix.ui.rules.TemplateManager;
import io.symcpe.hendrix.ui.rules.TenantManager;
import io.symcpe.hendrix.ui.validations.VelocityValidator;
import io.symcpe.wraith.rules.validator.RuleValidator;
import io.symcpe.wraith.rules.validator.Validator;

/**
 * JSF Application Manager
 * 
 * @author ambud_sharma
 */
@ManagedBean(name = "am", eager = true)
@ApplicationScoped
public class ApplicationManager implements Serializable {

	private static final String API_URL = "api.url";
	private static final long serialVersionUID = 1L;
	public static final boolean LOCAL = Boolean.parseBoolean(System.getProperty("local", "true"));
	private static final String PROP_CONFIG_FILE = "hendrixConfig";
	private Properties config;
	private String ruleTopicName;
	private String baseUrl;
	private int connectTimeout;
	private int requestTimeout;

	public ApplicationManager() {
	}

	@PostConstruct
	public void init() {
		config = new Properties(System.getProperties());
		baseUrl = config.getProperty(API_URL, "http://localhost:9000/api");
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
		RulesManager.getInstance().init(this);
		TenantManager.getInstance().init(this);
		TemplateManager.getInstance().init(this);
	}
	
	public void addRuleValidators(Properties config) {
		List<Validator<?>> validators = Arrays.asList(new VelocityValidator());
		RuleValidator.getInstance().configure(validators);
	}

	/**
	 * @return ruleTopicName
	 */
	public String getRuleTopicName() {
		return ruleTopicName;
	}

	/**
	 * @return the connectTimeout
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * @return the requestTimeout
	 */
	public int getRequestTimeout() {
		return requestTimeout;
	}

	/**
	 * @return the baseUrl
	 */
	public String getBaseUrl() {
		return baseUrl;
	}
}
