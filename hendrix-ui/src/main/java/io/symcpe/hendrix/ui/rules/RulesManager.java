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
package io.symcpe.hendrix.ui.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.NoResultException;
import javax.ws.rs.BadRequestException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import io.symcpe.hendrix.ui.ApplicationManager;
import io.symcpe.hendrix.ui.Utils;
import io.symcpe.hendrix.ui.storage.Tenant;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.validator.RuleValidator;
import javassist.NotFoundException;

/**
 * Persistence manager for {@link Rule}s
 * 
 * @author ambud_sharma
 */
public class RulesManager {

	private static final Logger logger = Logger.getLogger(RulesManager.class.getCanonicalName());
	private static final String RULES_URL = "/rules";
	private static RulesManager RULES_MANAGER = new RulesManager();
	private ApplicationManager am;

	private RulesManager() {
	}

	public static RulesManager getInstance() {
		return RULES_MANAGER;
	}

	public void init(ApplicationManager am) {
		this.am = am;
	}

	public short createNewRule(Tenant tenant) throws Exception {
		CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(), am.getRequestTimeout());
		HttpPost post = new HttpPost(am.getBaseUrl() + RULES_URL + "/" + tenant.getTenantId());
		CloseableHttpResponse resp = client.execute(post);
		String result = EntityUtils.toString(resp.getEntity());
		return Short.parseShort(result);
	}

	public short saveRule(Tenant tenant, Rule currRule) throws Exception {
		if (currRule == null || tenant == null) {
			logger.info("Rule was null can't save");
			return -1;
		}
		RuleValidator.getInstance().validate(currRule);
		logger.info("Rule is valid attempting to save");
		CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(), am.getRequestTimeout());
		HttpPut put = new HttpPut(
				am.getBaseUrl() + RULES_URL + "/" + tenant.getTenantId() + "/" + currRule.getRuleId());
		StringEntity entity = new StringEntity(RuleSerializer.serializeRuleToJSONString(currRule, false),
				ContentType.APPLICATION_JSON);
		put.setEntity(entity);
		CloseableHttpResponse resp = client.execute(put);
		String result = EntityUtils.toString(resp.getEntity());
		return Short.parseShort(result);
	}

	public Rule getRule(String tenantId, short ruleId) throws Exception {
		CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(), am.getRequestTimeout());
		HttpGet get = new HttpGet(am.getBaseUrl() + RULES_URL + "/" + tenantId + "/" + ruleId);
		CloseableHttpResponse resp = client.execute(get);
		String ruleStr = EntityUtils.toString(resp.getEntity());
		if (Utils.validateStatus(resp)) {
			return RuleSerializer.deserializeJSONStringToRule(ruleStr);
		} else {
			throw new NotFoundException("Result not found");
		}
	}

	public void deleteRule(String tenantId, short ruleId) throws Exception {
		try {
			CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(),
					am.getRequestTimeout());
			HttpDelete delete = new HttpDelete(am.getBaseUrl() + RULES_URL + "/" + tenantId + "/" + ruleId);
			CloseableHttpResponse resp = client.execute(delete);
			if (!Utils.validateStatus(resp)) {
				throw new Exception("status code:" + resp.getStatusLine().getStatusCode());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to delete rule:" + ruleId, e);
			throw e;
		}
	}

	public void deleteRules(String tenantId) throws Exception {
		try {
			CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(),
					am.getRequestTimeout());
			HttpDelete delete = new HttpDelete(am.getBaseUrl() + RULES_URL + "/" + tenantId);
			CloseableHttpResponse resp = client.execute(delete);
			if (!Utils.validateStatus(resp)) {
				throw new Exception("status code:" + resp.getStatusLine().getStatusCode());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to delete all rules:" + tenantId, e);
			throw e;
		}
	}

	public void disableAllRules(String tenantId) throws Exception {
		try {
			CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(),
					am.getRequestTimeout());
			HttpPut put = new HttpPut(am.getBaseUrl() + RULES_URL + "/" + tenantId + "/disable");
			CloseableHttpResponse resp = client.execute(put);
			if (!Utils.validateStatus(resp)) {
				throw new Exception("status code:" + resp.getStatusLine().getStatusCode());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to disable all rules:" + tenantId, e);
			throw e;
		}
	}

	public List<Rule> getRuleObjects(String tenantId) throws Exception {
		List<Rule> rules = new ArrayList<>();
		try {
			CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(),
					am.getRequestTimeout());
			HttpGet get = new HttpGet(am.getBaseUrl() + RULES_URL + "/" + tenantId);
			CloseableHttpResponse resp = client.execute(get);
			String ruleStr = EntityUtils.toString(resp.getEntity());
			if (Utils.validateStatus(resp)) {
				rules.addAll(Arrays.asList(RuleSerializer.deserializeJSONStringToRules(ruleStr)));
			} else {
				throw new NotFoundException("Result not found");
			}
			return rules;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rule objects for tenant:" + tenantId, e);
			throw e;
		}
	}

	public Rule enableDisableRule(boolean ruleState, String tenantId, short ruleId) throws Exception {
		Rule rule = getRule(tenantId, ruleId);
		try {
			CloseableHttpClient client = Utils.buildClient(am.getBaseUrl(), am.getConnectTimeout(),
					am.getRequestTimeout());
			HttpPut put = new HttpPut(
					am.getBaseUrl() + RULES_URL + "/" + tenantId + "/" + ruleId + "/" + (rule.isActive() ? "disable"
							: "enable"));
			CloseableHttpResponse resp = client.execute(put);
			if (!Utils.validateStatus(resp)) {
				throw new Exception("status code:" + resp.getStatusLine().getStatusCode());
			}
			return rule;
		} catch (BadRequestException | NoResultException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to enabled disable rule" + ruleId + "\t" + tenantId, e);
			throw e;
		}
	}

}