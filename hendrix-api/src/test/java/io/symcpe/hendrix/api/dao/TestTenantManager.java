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
import static org.junit.Assert.assertNotNull;
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
import javax.persistence.Persistence;

import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
import io.symcpe.hendrix.api.storage.ApiKey;
import io.symcpe.hendrix.api.storage.Tenant;

/**
 * Unit test for {@link TenantManager}
 * 
 * @author ambud_sharma
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTenantManager {

	private static final String TENANT_ID = "32342342342";
//	private static final String CONNECTION_STRING = "jdbc:derby:target/rules.db;create=true";
	private static final String CONNECTION_STRING = "jdbc:hsqldb:mem:target/rules.db";
	private static final String TARGET_RULES_DB = "target/rules.db";
	private static EntityManagerFactory emf;
	private EntityManager em;
	@Mock
	private KafkaProducer<String, String> producer;
	@Mock
	private ApplicationManager am;

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
	}

	@Before
	public void before() {
		em = emf.createEntityManager();
		when(am.getEM()).thenReturn(em);
		when(am.getRuleTopicName()).thenReturn("ruleTopic");
		when(am.getApiKeyTopic()).thenReturn("apikeyTopic");
		when(am.getKafkaProducer()).thenReturn(producer);

		when(producer.send(any())).thenReturn(
				CompletableFuture.completedFuture(new RecordMetadata(new TopicPartition("ruleTopic", 2), 1, 1)));
	}

	@Test
	public void testCreateTenant() throws Exception {
		Tenant tenant = new Tenant();
		tenant.setTenant_id(TENANT_ID);
		tenant.setTenant_name("simple-tenant");
		TenantManager.getInstance().createTenant(em, tenant);
		tenant = TenantManager.getInstance().getTenant(em, TENANT_ID);
		assertEquals(TENANT_ID, tenant.getTenant_id());
	}

	@Test
	public void testCreateTenantApiKey() throws Exception {
		ApiKey apiKey = TenantManager.getInstance().createApiKey(em, TENANT_ID, am);
		assertNotNull(apiKey);
		Tenant tenant = TenantManager.getInstance().getTenant(em, TENANT_ID);
		List<ApiKey> list = TenantManager.getInstance().getApiKeys(em, tenant);
		assertEquals(1, list.size());
		verify(producer, times(1)).send(any());
	}

	@Test
	public void testCreateTenantApiKeyDelete() throws Exception {
		ApiKey apiKey = TenantManager.getInstance().createApiKey(em, TENANT_ID, am);
		assertNotNull(apiKey);
		Tenant tenant = TenantManager.getInstance().getTenant(em, TENANT_ID);
		
		List<ApiKey> list = TenantManager.getInstance().getApiKeys(em, tenant);
		assertEquals(2, list.size());
		verify(producer, times(1)).send(any());
		
		apiKey = TenantManager.getInstance().getApiKey(em, tenant, apiKey.getApikey());
		apiKey.setDescription("hello");
		TenantManager.getInstance().updateApiKey(em, tenant, apiKey, am);
		verify(producer, times(2)).send(any());
		
		apiKey = TenantManager.getInstance().getApiKey(em, tenant, apiKey.getApikey());
		assertEquals("hello", apiKey.getDescription());
		
		TenantManager.getInstance().deleteApiKey(em, TENANT_ID, apiKey.getApikey(), am);
		list = TenantManager.getInstance().getApiKeys(em, tenant);
		assertEquals(1, list.size());
		verify(producer, times(3)).send(any());
	}
	
	@Test
	public void testCreateTenantNegative() throws Exception {
		Tenant tenant = new Tenant();
		tenant.setTenant_id("32342342342234514322534t34352345234523452342344573657657486784768567956785678234234234");
		tenant.setTenant_name("simple-tenant");
		try {
			TenantManager.getInstance().createTenant(em, tenant);
			fail("Can't create this tenant");
		} catch (Exception e) {
		}
	}

	@Test
	public void testCupdateTenant() throws Exception {
		TenantManager.getInstance().updateTenant(em, TENANT_ID, "simple-tenant2");
		Tenant tenant = TenantManager.getInstance().getTenant(em, TENANT_ID);
		assertEquals(TENANT_ID, tenant.getTenant_id());
		assertEquals("simple-tenant2", tenant.getTenant_name());
	}

	@Test
	public void testDeleteTenant() throws Exception {
		TenantManager.getInstance().deleteTenant(em, TENANT_ID, am);
		verify(producer, times(1)).send(any());
	}

	@Test
	public void testDeleteZNoApiKeys() throws Exception {
		try {
			Tenant tenant = TenantManager.getInstance().getTenant(em, TENANT_ID);
			TenantManager.getInstance().getApiKeys(em, tenant);
			fail("Exception  should be thrown");
		} catch (Exception e) {
		}
	}

}
