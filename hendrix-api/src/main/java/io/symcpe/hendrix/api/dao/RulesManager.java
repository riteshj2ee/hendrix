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
package io.symcpe.hendrix.api.dao;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.google.gson.Gson;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Queries;
import io.symcpe.hendrix.api.storage.Rules;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.templated.TemplatedAlertAction;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleCommand;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.rules.validator.RuleValidator;

/**
 * Persistence manager for {@link Rules}
 * 
 * @author ambud_sharma
 */
public class RulesManager {

	private static final String HENDRIX_RULE_UPDATES_TXT = "~/hendrix/rule-updates.txt";
	private static final String PARAM_TENANT_ID = "tenantId";
	private static final String PARAM_RULE_ID = "ruleId";
	private static final Logger logger = Logger.getLogger(RulesManager.class.getCanonicalName());
	private static RulesManager RULES_MANAGER = new RulesManager();;

	private RulesManager() {
	}

	public static RulesManager getInstance() {
		return RULES_MANAGER;
	}

	public short createNewRule(EntityManager em, Rules dbRule, Tenant tenant) throws Exception {
		if (dbRule == null) {
			logger.info("Rule was null can't save");
			return -1;
		}
		EntityTransaction t = em.getTransaction();
		try {
			t.begin();
			if (tenant == null) {
				logger.severe("Rule group is null");
				return -1;
			} else {
				dbRule.setTenant(tenant);
			}
			em.persist(dbRule);
			em.flush();
			t.commit();
			logger.info("Created new rule with rule id:" + dbRule.getRuleId());
			return dbRule.getRuleId();
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to create a new rule", e);
			throw e;
		}
	}

	public short saveRule(EntityManager em, Rules dbRule, Tenant tenant, Rule currRule, ApplicationManager am)
			throws Exception {
		if (currRule == null || dbRule == null || tenant == null) {
			logger.info("Rule was null can't save");
			return -1;
		}
		RuleValidator.getInstance().validate(currRule);
		for (Action action : currRule.getActions()) {
			if (action instanceof TemplatedAlertAction) {
				TemplateManager.getInstance().getTemplate(em, tenant.getTenantId(),
						((TemplatedAlertAction) action).getTemplateId());
			}
		}
		logger.info("Rule is valid attempting to save");
		try {
			em.getTransaction().begin();
			if (dbRule.getTenant() == null) {
				dbRule.setTenant(tenant);
			}
			if (currRule.getRuleId() > 0) {
				dbRule.setRuleId(currRule.getRuleId());
			}
			dbRule = em.merge(dbRule);
			em.flush();
			em.getTransaction().commit();
			currRule.setRuleId(dbRule.getRuleId());
			em.getTransaction().begin();
			dbRule.setRuleContent(RuleSerializer.serializeRuleToJSONString(currRule, false));
			em.merge(dbRule);
			em.flush();
			logger.info("Rule " + dbRule.getRuleId() + ":" + dbRule.getRuleContent() + " saved");
			// publish rule to kafka
			sendRuleToKafka(false, tenant.getTenantId(), dbRule.getRuleContent(), am);
			em.getTransaction().commit();
			logger.info("Completed Transaction for rule " + dbRule.getRuleId() + ":" + dbRule.getRuleContent() + "");
			return dbRule.getRuleId();
		} catch (Exception e) {
			e.printStackTrace();
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			throw e;
		}
	}

	public void sendRuleToKafka(boolean delete, String tenantId, Rule rule, ApplicationManager am)
			throws InterruptedException, ExecutionException, IOException {
		sendRuleToKafka(delete, tenantId, RuleSerializer.serializeRuleToJSONString(rule, false), am);
	}

	public void sendRuleToKafka(boolean delete, String tenantId, String ruleJson, ApplicationManager am)
			throws InterruptedException, ExecutionException, IOException {
		RuleCommand cmd = new RuleCommand(tenantId, delete, ruleJson);
		String cmdJson = new Gson().toJson(cmd);
		if (!ApplicationManager.LOCAL) {
			KafkaProducer<String, String> producer = am.getKafkaProducer();
			producer.send(new ProducerRecord<String, String>(am.getRuleTopicName(), cmdJson)).get();
		} else {
			PrintWriter pr = new PrintWriter(
					new FileWriter(HENDRIX_RULE_UPDATES_TXT.replaceFirst("^~", System.getProperty("user.home")), true));
			pr.println(cmdJson);
			pr.close();
		}
	}

	public Rules getRule(EntityManager em, short ruleId) {
		try {
			Rules resultResult = em.createNamedQuery(Queries.RULES_FIND_BY_ID, Rules.class)
					.setParameter(PARAM_RULE_ID, ruleId).getSingleResult();
			return resultResult;
		} catch (Exception e) {
			throw new NoResultException("Rule:" + ruleId + " not found");
		}
	}

	public Rules getRule(EntityManager em, String tenantId, short ruleId) throws Exception {
		try {
			Rules resultResult = em.createNamedQuery(Queries.RULES_FIND_BY_ID_AND_TENANT, Rules.class)
					.setParameter(PARAM_RULE_ID, ruleId).setParameter(PARAM_TENANT_ID, tenantId).getSingleResult();
			return resultResult;
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				logger.log(Level.SEVERE, "Rule:" + ruleId + " not found for tenantId:" + tenantId);
			} else {
				logger.log(Level.SEVERE, "Error getting rule:" + ruleId + " for tenantId:" + tenantId, e);
			}
			throw e;
		}
	}

	public Rule getRuleObject(EntityManager em, short ruleId) throws Exception {
		Rules rule = getRule(em, ruleId);
		if (rule.getRuleContent() != null) {
			return RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
		} else {
			return new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {});
		}
	}

	public void deleteRule(EntityManager em, String tenantId, short ruleId, ApplicationManager am) throws Exception {
		EntityTransaction transaction = em.getTransaction();
		try {
			Rules rule = getRule(em, tenantId, ruleId);
			if (rule == null) {
				throw new NotFoundException();
			}
			transaction.begin();
			String ruleContent = rule.getRuleContent();
			em.createNamedQuery(Queries.RULES_DELETE_BY_ID).setParameter(PARAM_RULE_ID, ruleId).executeUpdate();
			if (ruleContent != null) {
				sendRuleToKafka(true, rule.getTenant().getTenantId(), ruleContent, am);
			}
			transaction.commit();
			logger.info("Deleted rule:" + ruleId);
		} catch (Exception e) {
			if (transaction.isActive()) {
				transaction.rollback();
			}
			if (e instanceof NoResultException) {
				logger.log(Level.SEVERE, "Rule " + ruleId + " not found");
			} else {
				logger.log(Level.SEVERE, "Failed to delete rule", e);
			}
			throw e;
		}
	}

	public void deleteRules(EntityManager em, Tenant tenant, ApplicationManager am) throws Exception {
		EntityTransaction transaction = em.getTransaction();
		try {
			transaction.begin();
			List<Rules> rules = getRules(em, tenant.getTenantId());
			if (rules != null) {
				for (Rules rule : rules) {
					em.remove(rule);
					if (rule.getRuleContent() != null) {
						sendRuleToKafka(true, rule.getTenant().getTenantId(), rule.getRuleContent(), am);
					}
					logger.info("Deleting rule:" + rule.getRuleId() + " for tenant id:" + tenant);
				}
			}
			em.flush();
			transaction.commit();
			logger.info("All rules for tenant:" + tenant);
		} catch (Exception e) {
			if (transaction.isActive()) {
				transaction.rollback();
			}
			if (!(e instanceof NoResultException)) {
				logger.log(Level.SEVERE, "Failed to delete rule", e);
			}
			throw e;
		}
	}

	public void disableAllRules(EntityManager em, String tenantId, ApplicationManager am) throws Exception {
		try {
			List<Rules> rules = getRules(em, tenantId);
			if (rules == null) {
				throw new NoResultException("No rules for tenant");
			}
			for (Rules rule : rules) {
				try {
					enableDisableRule(em, false, tenantId, rule.getRuleId(), am);
					logger.info("Disabled rule:" + rule.getRuleId());
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Couldn't disable rule:" + rule.getRuleId() + " reason:" + e.getMessage());
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to disable all rules for tenant:" + tenantId, e);
			throw e;
		}
	}

	public List<Rule> getRuleObjects(EntityManager em, String tenantId) throws Exception {
		List<Rule> rules = new ArrayList<>();
		try {
			List<Rules> results = getRules(em, tenantId);
			for (Rules rule : results) {
				if (rule.getRuleContent() != null) {
					rules.add(RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent()));
				} else {
					rules.add(new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}));
				}
			}
			return rules;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rule objects for tenant:" + tenantId, e);
			throw e;
		}
	}

	public List<Rules> getRules(EntityManager em, String tenantId) throws Exception {
		Tenant tenant = getTenant(em, tenantId);
		if (tenant == null) {
			throw new NoResultException("Tenant not found");
		}
		try {
			return em.createNamedQuery(Queries.RULES_FIND_ALL_BY_TENANT_ID, Rules.class)
					.setParameter(PARAM_TENANT_ID, tenantId).getResultList();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rules for tenant:" + tenantId, e);
			throw e;
		}
	}

	public String getRuleContents(EntityManager em, String tenantId, boolean pretty, int filter) throws Exception {
		List<Rule> rules = new ArrayList<>();
		try {
			List<Rules> results = getRules(em, tenantId);
			for (Rules rule : results) {
				switch (filter) {
				case 1:
					if (rule.getRuleContent() != null) {
						rules.add(RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent()));
					}
					break;
				case 2:
					if (rule.getRuleContent() != null) {
						SimpleRule ruleObj = RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
						if (ruleObj.isActive()) {
							rules.add(ruleObj);
						}
					}
					break;
				case 3:
					if (rule.getRuleContent() != null) {
						SimpleRule ruleObj = RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
						if (!ruleObj.isActive()) {
							rules.add(ruleObj);
						}
					}
					break;
				case 4:
					if (rule.getRuleContent() == null) {
						rules.add(new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}));
					}
					break;
				default:
					if (rule.getRuleContent() != null) {
						rules.add(RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent()));
					} else {
						rules.add(new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}));
					}
					break;
				}
			}
			return RuleSerializer.serializeRulesToJSONString(rules, pretty);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rule contents for tenant:" + tenantId, e);
			throw e;
		}
	}

	public Rules enableDisableRule(EntityManager em, boolean ruleState, String tenantId, short ruleId,
			ApplicationManager am) throws Exception {
		try {
			Rules rules = getRule(em, tenantId, ruleId);
			if (rules != null) {
				if (rules.getRuleContent() != null) {
					Rule rule = RuleSerializer.deserializeJSONStringToRule(rules.getRuleContent());
					rule.setActive(ruleState);
					saveRule(em, rules, rules.getTenant(), rule, am);
				} else {
					throw new BadRequestException("Cannot enable/disable empty rule:" + ruleId);
				}
			} else {
				throw new NoResultException("Rule not found");
			}
			return rules;
		} catch (BadRequestException | NoResultException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to enabled disable rule" + ruleId + "\t" + tenantId, e);
			throw e;
		}
	}

	public Tenant getTenant(EntityManager em, String tenantId) throws Exception {
		try {
			return em.createNamedQuery(Queries.TENANT_FIND_BY_ID, Tenant.class).setParameter(PARAM_TENANT_ID, tenantId)
					.getSingleResult();
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				logger.log(Level.SEVERE, "Tenant id:" + tenantId + " not found");
			} else {
				logger.log(Level.SEVERE, "Failed to get tenant id:" + tenantId, e);
			}
			throw e;
		}
	}
}