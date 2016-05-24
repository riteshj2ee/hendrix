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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;

import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.dom4j.rule.RuleManager;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.DerbyUtil;
import io.symcpe.hendrix.api.dao.RulesManager;
import io.symcpe.hendrix.api.dao.TemplateManager;
import io.symcpe.hendrix.api.dao.TenantManager;
import io.symcpe.hendrix.api.storage.AlertTemplates;
import io.symcpe.hendrix.api.storage.Rules;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.TemplatedAlertAction;
import io.symcpe.wraith.conditions.relational.EqualsCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;

/**
 * Unit tests for {@link RuleManager}
 * 
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestRulesManager {

	private static final String TEST_TENANT = "test-tenant";
	private static final String TENANT_ID_1 = "b341mmd3ifaasdjm23midijjiro";
	private static final String TENANT_ID_2 = "c341mmd3ifaasdjm23midijjiro";
	private static final String TENANT_ID_3 = "d341mmd3ifaasdjm23midijjiro";
	private static final String TENANT_ID_4 = "e341mmd3ifaasdjm23midijjiro";
	private static final String CONNECTION_STRING = "jdbc:derby:target/rules.db;create=true";
	// private static final String CONNECTION_NC_STRING =
	// "jdbc:derby:target/rules.db;";
	private static final String TARGET_RULES_DB = "target/rules.db";
	private static EntityManagerFactory emf;
	private EntityManager em;
	@Mock
	private KafkaProducer<String, String> producer;
	@Mock
	private ApplicationManager am;
	private static short ruleId;
	private static short templateId;

	static {
		System.setProperty("derby.stream.error.field", DerbyUtil.class.getCanonicalName() + ".DEV_NULL");
		System.setProperty("local", "false");
	}

	public TestRulesManager() {
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		Properties config = new Properties(System.getProperties());
		File db = new File(TARGET_RULES_DB);
		if (db.exists()) {
			FileUtils.deleteDirectory(db);
		}
		config.setProperty("javax.persistence.jdbc.url", CONNECTION_STRING);
		try {
			emf = Persistence.createEntityManagerFactory("hendrix", config);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		EntityManager em = emf.createEntityManager();

		Tenant tenant = new Tenant();
		tenant.setTenantId(TENANT_ID_1);
		tenant.setTenantName(TEST_TENANT);
		TenantManager.getInstance().createTenant(em, tenant);

		tenant = new Tenant();
		tenant.setTenantId(TENANT_ID_2);
		tenant.setTenantName(TEST_TENANT);
		TenantManager.getInstance().createTenant(em, tenant);

		tenant = new Tenant();
		tenant.setTenantId(TENANT_ID_3);
		tenant.setTenantName(TEST_TENANT);
		TenantManager.getInstance().createTenant(em, tenant);
		
		em.close();
	}

	@Before
	public void before() {
		em = emf.createEntityManager();
		when(am.getEM()).thenReturn(em);
		when(am.getRuleTopicName()).thenReturn("ruleTopic");
		when(am.getTemplateTopicName()).thenReturn("templateTopic");
		when(am.getKafkaProducer()).thenReturn(producer);

		when(producer.send(any())).thenReturn(
				CompletableFuture.completedFuture(new RecordMetadata(new TopicPartition("ruleTopic", 2), 1, 1)));
	}
	
	@After
	public void after() {
		em.close();
	}

	@Test
	public void testGetRules() throws Exception {
		List<Rules> rules = null;
		try {
			rules = RulesManager.getInstance().getRules(em, TENANT_ID_1);
			assertEquals(0, rules.size());
		} catch (NoResultException e) {
		}
		Rules rule = new Rules();
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_1);
		short ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);
		try {
			rules = RulesManager.getInstance().getRules(em, TENANT_ID_1);
			assertEquals(1, rules.size());
			assertEquals(ruleId, rules.get(0).getRuleId());
		} catch (NoResultException e) {
		}
		try {
			rules = RulesManager.getInstance().getRules(em, TENANT_ID_1 + "1");
			fail("Tenant doesn't exist");
		} catch (NoResultException e) {
		}
	}

	@Test
	public void testGetRuleObjects() throws Exception {
		Rules rule = new Rules();
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_2);
		short ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);
		try {
			List<Rule> rules = RulesManager.getInstance().getRuleObjects(em, TENANT_ID_2);
			assertEquals(1, rules.size());
			assertEquals(ruleId, rules.get(0).getRuleId());
			assertEquals("", rules.get(0).getName());
		} catch (NoResultException e) {
		}
		rule = new Rules();
		rule.setRuleContent(RuleSerializer.serializeRuleToJSONString(
				new SimpleRule(ruleId, "simple-rule", true, new EqualsCondition("host", "symcpe"),
						new Action[] { new TemplatedAlertAction((short) 0, templateId) }),
				false));
		tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_2);
		ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);
		try {
			List<Rule> rules = RulesManager.getInstance().getRuleObjects(em, TENANT_ID_2);
			assertEquals(2, rules.size());
			for (Rule rule2 : rules) {
				if (rule2.getRuleId() == ruleId) {
					assertEquals("simple-rule", rule2.getName());
					assertEquals("host", ((EqualsCondition) rule2.getCondition()).getkey());
				}
			}
		} catch (NoResultException e) {
		}
	}

	@Test
	public void testGetRule() throws Exception {
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_3);
		Rules rule = new Rules();
		short ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);
		Rules rule2 = RulesManager.getInstance().getRule(em, ruleId);
		assertEquals(ruleId, rule2.getRuleId());
	}

	@Test
	public void testRuleOperations1CreateTenant() throws Exception {
		Tenant tenant = new Tenant();
		tenant.setTenantId(TENANT_ID_4);
		tenant.setTenantName(TEST_TENANT);
		TenantManager.getInstance().createTenant(emf.createEntityManager(), tenant);
		
		tenant = TenantManager.getInstance().getTenant(em, TENANT_ID_4);
		AlertTemplates templates = new AlertTemplates();
		templateId = TemplateManager.getInstance().saveTemplate(em, templates, tenant,
				new AlertTemplate((short)0, "test", "test@xyz.com", "mail", "test", "test", 2, 2), am);
		System.err.println("Saving template:"+templateId);
	}

	@Test
	public void testRuleOperations2CreateAndSaveRule() throws Exception {
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_4);
		Rules rule = new Rules();
		ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);

		Rule rul = new SimpleRule(ruleId, "simple-rule1", true, new EqualsCondition("host", "symcpe"),
				new Action[] { new TemplatedAlertAction((short) 0, templateId) });
		short ruleId2 = RulesManager.getInstance().saveRule(em, rule, tenant, rul, am);
		assertEquals(ruleId, ruleId2);
		Rule ruleObj = RulesManager.getInstance().getRuleObject(em, ruleId);
		assertEquals(ruleId, ruleObj.getRuleId());
		assertEquals("simple-rule1", ruleObj.getName());
		verify(producer, times(1)).send(any());
		System.out.println(ruleId);
	}

	@Test
	public void testRuleOperations3DisableRule() throws Exception {
		RulesManager.getInstance().enableDisableRule(em, false, TENANT_ID_4, ruleId, am);
		verify(producer, times(1)).send(any());
		Rule ruleObj = RulesManager.getInstance().getRuleObject(em, ruleId);
		assertEquals(false, ruleObj.isActive());
	}

	@Test
	public void testRuleOperations4EnableRule() throws Exception {
		RulesManager.getInstance().enableDisableRule(em, true, TENANT_ID_4, ruleId, am);
		verify(producer, times(1)).send(any());
		Rule ruleObj = RulesManager.getInstance().getRuleObject(em, ruleId);
		assertEquals(true, ruleObj.isActive());
	}

	@Test
	public void testRuleOperations5DisableAllRules() throws Exception {
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_4);
		Rules rule = new Rules();
		ruleId = RulesManager.getInstance().createNewRule(em, rule, tenant);
		Rule rul = new SimpleRule(ruleId, "simple-rule2", true, new EqualsCondition("host", "symcpe2"),
				new Action[] { new TemplatedAlertAction((short) 0, templateId) });
		RulesManager.getInstance().saveRule(em, rule, tenant, rul, am);

		RulesManager.getInstance().disableAllRules(em, TENANT_ID_4, am);
		verify(producer, times(3)).send(any());
		List<Rule> rules = RulesManager.getInstance().getRuleObjects(em, TENANT_ID_4);
		for (Rule tmp : rules) {
			switch (tmp.getName()) {
			case "simple-rule1":
			case "simple-rule2":
				break;
			default:
				fail("Should only be 2 rules");
			}
		}
	}

	@Test
	public void testRuleOperations6DeleteRule() throws Exception {
		RulesManager.getInstance().deleteRule(em, TENANT_ID_4, ruleId, am);
		verify(producer, times(1)).send(any());
		List<Rule> rules = RulesManager.getInstance().getRuleObjects(em, TENANT_ID_4);
		assertEquals(1, rules.size());
	}

	@Test
	public void testRuleOperations7DeleteAllRules() throws Exception {
		Tenant tenant = RulesManager.getInstance().getTenant(em, TENANT_ID_4);
		RulesManager.getInstance().deleteRules(em, tenant, am);
		verify(producer, times(1)).send(any());
		try {
			RulesManager.getInstance().getRuleObjects(em, TENANT_ID_4);
			fail("Can't have any results");
		} catch (NoResultException e) {
		}
	}

}