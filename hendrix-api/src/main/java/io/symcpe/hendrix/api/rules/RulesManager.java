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
package io.symcpe.hendrix.api.rules;

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
import io.symcpe.hendrix.api.Utils;
import io.symcpe.hendrix.api.storage.Rules;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.Action;
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

	private static final String PARAM_TENANT_ID = "tenantId";
	private static final String PARAM_RULE_ID = "ruleId";
	private static final Logger logger = Logger.getLogger(RulesManager.class.getCanonicalName());
	private static RulesManager RULES_MANAGER = new RulesManager();;
	private ApplicationManager am;

	private RulesManager() {
	}

	public static RulesManager getInstance() {
		return RULES_MANAGER;
	}

	public void init(ApplicationManager am) {
		this.am = am;
	}

	public short createNewRule(Rules dbRule, Tenant tenant) throws Exception {
		if (dbRule == null) {
			logger.info("Rule was null can't save");
			return -1;
		}
		EntityManager em = am.getEM();
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

	public short saveRule(Rules dbRule, Tenant tenant, Rule currRule) throws Exception {
		if (currRule == null || dbRule == null || tenant == null) {
			logger.info("Rule was null can't save");
			return -1;
		}
		RuleValidator.getInstance().validate(currRule);
		logger.info("Rule is valid attempting to save");
		EntityManager em = am.getEM();
		try {
			em.getTransaction().begin();
			dbRule.setTenant(tenant);
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
			sendRuleToKafka(false, tenant.getTenantId(), dbRule.getRuleContent());
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

	public void sendRuleToKafka(boolean delete, String tenantId, Rule rule)
			throws InterruptedException, ExecutionException, IOException {
		sendRuleToKafka(delete, tenantId, RuleSerializer.serializeRuleToJSONString(rule, false));
	}

	public void sendRuleToKafka(boolean delete, String tenantId, String ruleJson)
			throws InterruptedException, ExecutionException, IOException {
		RuleCommand cmd = new RuleCommand(tenantId, delete, ruleJson);
		String cmdJson = new Gson().toJson(cmd);
		if (!ApplicationManager.LOCAL) {
			KafkaProducer<String, String> producer = am.getKafkaProducer();
			producer.send(new ProducerRecord<String, String>(am.getRuleTopicName(), cmdJson)).get();
		} else {
			PrintWriter pr = new PrintWriter(new FileWriter("/tmp/rule-updates.txt", true));
			pr.println(cmdJson);
			pr.close();
		}
	}

	public Rules getRule(short ruleId) {
		EntityManager em = am.getEM();
		try {
			Rules resultResult = em.createNamedQuery(Queries.RULES_FIND_BY_ID, Rules.class)
					.setParameter(PARAM_RULE_ID, ruleId).getSingleResult();
			return resultResult;
		} catch (Exception e) {
			throw new NoResultException("Rule:" + ruleId + " not found");
		}
	}

	public Rules getRule(String tenantId, short ruleId) throws Exception {
		EntityManager em = am.getEM();
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

	public Rule getRuleObj(short ruleId) throws Exception {
		Rules rule = getRule(ruleId);
		if (rule.getRuleContent() != null) {
			return RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
		} else {
			return new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {});
		}
	}

	public void deleteRule(String tenantId, short ruleId) throws Exception {
		EntityManager em = am.getEM();
		EntityTransaction transaction = em.getTransaction();
		try {
			Rules rule = getRule(tenantId, ruleId);
			if (rule == null) {
				throw new NotFoundException();
			}
			transaction.begin();
			String ruleContent = rule.getRuleContent();
			em.createNamedQuery(Queries.RULES_DELETE_BY_ID).setParameter(PARAM_RULE_ID, ruleId).executeUpdate();
			if (ruleContent != null) {
				sendRuleToKafka(true, rule.getTenant().getTenantId(), ruleContent);
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

	public void deleteRules(String tenantId) throws Exception {
		EntityManager em = am.getEM();
		EntityTransaction transaction = em.getTransaction();
		try {
			transaction.begin();
			List<Rules> rules = getRules(tenantId);
			if (rules != null) {
				for (Rules rule : rules) {
					em.createNamedQuery(Queries.RULES_DELETE_BY_ID).setParameter("ruleId", rule.getRuleId())
							.executeUpdate();
					em.detach(rule);
					if (rule.getRuleContent() != null) {
						sendRuleToKafka(true, rule.getTenant().getTenantId(), rule.getRuleContent());
					}
					logger.info("Deleting rule:" + rule.getRuleId() + " for tenant id:" + tenantId);
				}
			}
			em.flush();
			transaction.commit();
			logger.info("All rules for tenant:" + tenantId);
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

	public void disableAllRules(String tenantId) throws Exception {
		try {
			List<Rules> rules = getRules(tenantId);
			if (rules == null) {
				throw new NoResultException("No rules for tenant");
			}
			for (Rules rule : rules) {
				try {
					enableDisableRule(false, tenantId, rule.getRuleId());
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

	public List<Rule> getRuleObjects(String tenantId) throws Exception {
		List<Rule> rules = new ArrayList<>();
		try {
			List<Rules> results = getRules(tenantId);
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

	public List<Rules> getRules(String tenantId) throws Exception {
		Tenant tenant = getTenant(tenantId);
		if (tenant == null) {
			throw new NoResultException("Tenant not found");
		}
		try {
			return am.getEM().createNamedQuery(Queries.RULES_FIND_ALL_BY_TENANT_ID, Rules.class)
					.setParameter(PARAM_TENANT_ID, tenantId).getResultList();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rules for tenant:" + tenantId, e);
			throw e;
		}
	}

	public List<String> getRuleContents(String tenantId, boolean pretty, int filter) throws Exception {
		List<String> rules = new ArrayList<>();
		try {
			List<Rules> results = getRules(tenantId);
			for (Rules rule : results) {
				switch (filter) {
				case 1:
					if (rule.getRuleContent() != null) {
						if (pretty) {
							rules.add(Utils.getPrettyRuleJson(rule.getRuleContent()));
						} else {
							rules.add(rule.getRuleContent());
						}
					}
					break;
				case 2:
					if (rule.getRuleContent() != null) {
						SimpleRule ruleObj = RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
						if (ruleObj.isActive()) {
							if (pretty) {
								rules.add(Utils.getPrettyRuleJson(rule.getRuleContent()));
							} else {
								rules.add(rule.getRuleContent());
							}
						}
					}
					break;
				case 3:
					if (rule.getRuleContent() != null) {
						SimpleRule ruleObj = RuleSerializer.deserializeJSONStringToRule(rule.getRuleContent());
						if (!ruleObj.isActive()) {
							if (pretty) {
								rules.add(Utils.getPrettyRuleJson(rule.getRuleContent()));
							} else {
								rules.add(rule.getRuleContent());
							}
						}
					}
					break;
				case 4:
					if (rule.getRuleContent() == null) {
						rules.add(RuleSerializer.serializeRuleToJSONString(
								new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}), pretty));
					}
					break;
				default:
					if (rule.getRuleContent() != null) {
						if (pretty) {
							rules.add(Utils.getPrettyRuleJson(rule.getRuleContent()));
						} else {
							rules.add(rule.getRuleContent());
						}
					} else {
						rules.add(RuleSerializer.serializeRuleToJSONString(
								new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}), pretty));
					}
					break;
				}
			}
			return rules;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load rule contents for tenant:" + tenantId, e);
			throw e;
		}
	}

	public Rules enableDisableRule(boolean ruleState, String tenantId, short ruleId) throws Exception {
		try {
			Rules rules = getRule(tenantId, ruleId);
			if (rules != null) {
				if (rules.getRuleContent() != null) {
					Rule rule = RuleSerializer.deserializeJSONStringToRule(rules.getRuleContent());
					rule.setActive(ruleState);
					saveRule(rules, rules.getTenant(), rule);
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

	public Tenant getTenant(String tenantId) throws Exception {
		try {
			EntityManager em = am.getEM();
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