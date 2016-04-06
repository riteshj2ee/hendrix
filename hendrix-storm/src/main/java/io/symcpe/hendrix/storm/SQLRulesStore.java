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

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.rules.validator.RuleValidator;
import io.symcpe.wraith.rules.validator.ValidationException;
import io.symcpe.wraith.store.RulesStore;

/**
 * SQL Database based {@link RulesStore} so that transactional support for Rules
 * can be provided.
 * 
 * @author ambud_sharma
 */
public class SQLRulesStore implements RulesStore {

	public static final String RSTORE_SQL_URL = "rstore.sql.url";
	public static final String RSTORE_SQL_DB = "rstore.sql.db";
	public static final String RSTORE_SQL_TABLE = "rstore.sql.table";
	public static final String COLUMN_RULE_ID = "rule_id";
	public static final String COLUMN_RULE_CONTENT = "rule_content";
	private static final String COLUMN_TENANT_ID = "tenant_id";
	public static final String RSTORE_TENANT_FILTER = "rstore.sql.tenant.filter";
	private static final Logger logger = LoggerFactory.getLogger(SQLRulesStore.class);
	private Connection conn;
	private String url;
	private String dbName;
	private String username;
	private String password;
	private String table;
	private String[] tenants;

	public SQLRulesStore() {
	}

	@Override
	public void initialize(Map<String, String> conf) {
		this.url = conf.get(RSTORE_SQL_URL);
		this.dbName = conf.get(RSTORE_SQL_DB);
		this.table = conf.get(RSTORE_SQL_TABLE);
		this.username = conf.get(Constants.RSTORE_USERNAME);
		this.password = conf.get(Constants.RSTORE_PASSWORD);
		if (conf.get(RSTORE_TENANT_FILTER) != null) {
			this.tenants = conf.get(RSTORE_TENANT_FILTER).toString().split(",");
		}
	}

	@Override
	public void connect() throws IOException {
		try {
			conn = DriverManager.getConnection(url, username, password);
		} catch (SQLException e) {
			throw new IOException(e);
		}
		this.password = null;
	}

	@Override
	public Map<Short, Rule> listRules() throws IOException {
		Map<Short, Rule> rules = new HashMap<>();
		try {
			PreparedStatement st = null;
			if (this.tenants == null) {
				st = conn.prepareStatement("select * from " + dbName + "." + table + "");
			} else {
				st = conn.prepareStatement("select * from " + dbName + "." + table + " where tenant_id in (?)");
				st.setString(1, StringUtils.join(tenants));
			}
			ResultSet resultSet = st.executeQuery();
			int counter = 0;
			while (resultSet.next()) {
				SimpleRule rule = RuleSerializer.deserializeJSONStringToRule(resultSet.getString(COLUMN_RULE_CONTENT));
				short ruleId = resultSet.getShort(COLUMN_RULE_ID);
				if (rule != null && ruleId == rule.getRuleId()) {
					try {
						RuleValidator.getInstance().validate(rule);
					} catch (ValidationException e) {
						// TODO ignore rules that don't pass validation
					}
					rules.put(ruleId, rule);
					counter++;
					logger.debug("Adding rule:" + rule.getRuleId() + "/" + rule.getName());
				} else {
					logger.error("Dropping rule, RuleId(PK) mismatch with Rule content RuleId");
				}
			}
			logger.info("Loaded " + counter + " rules from the database");
			resultSet.close();
			st.close();
		} catch (SQLException e) {
			throw new IOException(e);
		}
		return rules;
	}

	@Override
	public void disconnect() throws IOException {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				throw new IOException(e);
			}
		}
	}

	@Override
	public Map<String, Map<Short, Rule>> listGroupedRules() throws IOException {
		Map<String, Map<Short, Rule>> rules = new HashMap<>();
		try {
			PreparedStatement st = null;
			if (this.tenants == null) {
				st = conn.prepareStatement("select * from " + dbName + "." + table + "");
			} else {
				st = conn.prepareStatement("select * from " + dbName + "." + table + " where tenant_id in (?)");
				st.setString(1, StringUtils.join(tenants));
			}
			ResultSet resultSet = st.executeQuery();
			int counter = 0;
			while (resultSet.next()) {
				SimpleRule rule = RuleSerializer.deserializeJSONStringToRule(resultSet.getString(COLUMN_RULE_CONTENT));
				short ruleId = resultSet.getShort(COLUMN_RULE_ID);
				String tenantId = resultSet.getString(COLUMN_TENANT_ID);
				if (tenantId == null) {
					// rules with tenantIds are not allowed
					continue;
				}
				if (rule != null && ruleId == rule.getRuleId()) {
					try {
						RuleValidator.getInstance().validate(rule);
					} catch (ValidationException e) {
						// TODO ignore rules that don't pass validation
						continue;
					}
					if (!rules.containsKey(tenantId)) {
						rules.put(tenantId, new LinkedHashMap<>());
					}
					rules.get(tenantId).put(ruleId, rule);
					counter++;
					logger.debug("Adding rule:" + rule.getRuleId() + "/" + rule.getName());
				} else {
					logger.error("Dropping rule, RuleId(PK) mismatch with Rule content RuleId");
				}
			}
			logger.info("Loaded " + counter + " rules from the database");
			resultSet.close();
			st.close();
		} catch (SQLException e) {
			throw new IOException(e);
		}
		return rules;
	}

}
