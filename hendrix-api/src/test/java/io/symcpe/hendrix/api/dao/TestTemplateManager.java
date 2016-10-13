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
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.rules.validator.ValidationException;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTemplateManager {

	private static final String TEST_TENANT = "test-tenant";
	private static final String TENANT_ID_1 = "z341mmd3ifaasdjm23midijjiro";
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
	private static short id;
	private Tenant tenant;

	static {
		System.setProperty("org.jboss.logging.provider", "jdk");
		System.setProperty("derby.stream.error.field", DerbyUtil.class.getCanonicalName() + ".DEV_NULL");
		System.setProperty("local", "false");
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
		tenant.setTenant_id(TENANT_ID_1);
		tenant.setTenant_name(TEST_TENANT);
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
				CompletableFuture.completedFuture(new RecordMetadata(new TopicPartition("templateTopic", 2), 1, 1)));
	}

	@After
	public void after() {
		em.close();
	}

	@Test
	public void testGetTemplate() throws Exception {
		AlertTemplates templates = new AlertTemplates();
		tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
		id = TemplateManager.getInstance().createNewTemplate(em, templates, tenant).getTemplateId();
		AlertTemplates template = TemplateManager.getInstance().getTemplate(em, tenant.getTenant_id(), id);
		assertEquals(id, template.getTemplateId());
	}

	@Test
	public void testSaveTemplate() throws Exception {
		AlertTemplate tpl = new AlertTemplate();
		tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
		AlertTemplates template = TemplateManager.getInstance().getTemplate(em, tenant.getTenant_id(), id);
		tpl.setTemplateId(template.getTemplateId());
		tpl.setBody("test");
		tpl.setDestination("test@xyz.com");
		tpl.setMedia("mail");
		tpl.setTemplateName("Test");
		tpl.setThrottleDuration(2);
		tpl.setThrottleLimit(2);
		short id = TemplateManager.getInstance().saveTemplate(em, template, template.getTenant(), tpl, am).getTemplateId();
		assertEquals(id, template.getTemplateId());
	}

	@Test
	public void testZDeleteTemplate() throws Exception {
		TemplateManager.getInstance().deleteTemplate(em, TENANT_ID_1, id, am);
		try {
			TemplateManager.getInstance().getTemplate(em, tenant.getTenant_id(), id);
			fail("Not reachable");
		} catch (Exception e) {
		}
		TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
	}

	@Test
	public void testBadTemplate() throws Exception {
		AlertTemplate tpl = new AlertTemplate();
		Tenant tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
		short id = TemplateManager.getInstance().createNewTemplate(em, new AlertTemplates(), tenant).getTemplateId();
		AlertTemplates template = TemplateManager.getInstance().getTemplate(em, tenant.getTenant_id(), id);
		tpl.setTemplateId(template.getTemplateId());
		tpl.setBody("test");
		tpl.setDestination("test");
		tpl.setMedia("test");
		tpl.setTemplateName("Test");
		tpl.setThrottleDuration(2);
		tpl.setThrottleLimit(2);
		try {
			id = TemplateManager.getInstance().saveTemplate(em, template, template.getTenant(), tpl, am).getTemplateId();
			fail("Not reachable, bad template must be validated");
		} catch (ValidationException e) {
		}
	}
	
	@Test
	public void testZZDeleteteAllTemplatesBadRequest() throws Exception {
		AlertTemplate tpl = new AlertTemplate();
		Tenant tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
		AlertTemplates templates = new AlertTemplates();
		short id = TemplateManager.getInstance().createNewTemplate(em, templates, tenant).getTemplateId();
		tpl.setTemplateId(id);
		tpl.setBody("test");
		tpl.setDestination("test@xyz.com");
		tpl.setMedia("mail");
		tpl.setTemplateName("Test");
		tpl.setThrottleDuration(2);
		tpl.setThrottleLimit(2);
		id = TemplateManager.getInstance().saveTemplate(em, templates, templates.getTenant(), tpl, am).getTemplateId();
		assertEquals(id, templates.getTemplateId());
		Rule rul = new SimpleRule((short)0, "simple-rule2", true, new EqualsCondition("host", "symcpe2"),
				new Action[] { new TemplatedAlertAction((short) 0, id) });
		RulesManager.getInstance().saveRule(em, new Rules(), tenant, rul, am);
		try {
			TemplateManager.getInstance().deleteTemplates(em, templates.getTenant(), am);
			fail("Can't reach here this request should fail");
		} catch (Exception e) {
		}
		tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
	}

	@Test
	public void testZZ2DeleteteAllTemplates() throws Exception {
		AlertTemplate tpl = new AlertTemplate();
		Tenant tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
		AlertTemplates templates = new AlertTemplates();
		short id = TemplateManager.getInstance().createNewTemplate(em, templates, tenant).getTemplateId();
		tpl.setTemplateId(id);
		tpl.setBody("test");
		tpl.setDestination("test@xyz.com");
		tpl.setMedia("mail");
		tpl.setTemplateName("Test");
		tpl.setThrottleDuration(2);
		tpl.setThrottleLimit(2);
		id = TemplateManager.getInstance().saveTemplate(em, templates, templates.getTenant(), tpl, am).getTemplateId();
		assertEquals(id, templates.getTemplateId());
		TemplateManager.getInstance().deleteTemplates(em, templates.getTenant(), am);
		try {
			List<AlertTemplates> results = TemplateManager.getInstance().getTemplates(em, templates.getTenant().getTenant_id());
			assertEquals(0, results.size());
		} catch (Exception e) {
		}
		tenant = TemplateManager.getInstance().getTenant(em, TENANT_ID_1);
	}
}
