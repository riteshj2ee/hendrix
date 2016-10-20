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
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;

import org.apache.kafka.clients.producer.ProducerRecord;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Queries;
import io.symcpe.hendrix.api.Utils;
import io.symcpe.hendrix.api.storage.ApiKey;
import io.symcpe.hendrix.api.storage.Tenant;

/**
 * Persistence manager for {@link Tenant}s
 * 
 * @author ambud_sharma
 */
public class TenantManager {

	public static final String CREATED_APIKEY = "created_apiKey";
	public static final String DISABLED_APIKEY = "disabled_apiKey";
	public static final String ENABLED_APIKEY = "enabled_apiKey";
	public static final String DELETED_APIKEY = "deleted_apiKey";
	private static final Logger logger = Logger.getLogger(TenantManager.class.getCanonicalName());
	private static final TenantManager TENANT_MANAGER = new TenantManager();
	private static final String HENDRIX_APIKEY_UPDATES_TXT = "apikeyupdates.txt";

	private TenantManager() {
	}

	public static TenantManager getInstance() {
		return TENANT_MANAGER;
	}

	/**
	 * Create tenant
	 * 
	 * @param em
	 * @param tenant
	 * @throws Exception
	 */
	public void createTenant(EntityManager em, Tenant tenant) throws Exception {
		if (tenant == null) {
			throw new NullPointerException("Tenant can't be empty");
		}
		EntityTransaction t = em.getTransaction();
		try {
			t.begin();
			em.persist(tenant);
			em.flush();
			t.commit();
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to create tenant:" + tenant, e);
			throw e;
		}
	}

	public Tenant deleteTenant(EntityManager em, String tenantId, ApplicationManager am) throws Exception {
		Tenant tenant = getTenant(em, tenantId);
		if (tenant != null) {
			EntityTransaction t = em.getTransaction();
			try {
				RulesManager.getInstance().deleteRules(em, tenant, am);
				TemplateManager.getInstance().deleteTemplates(em, tenant, am);
				TenantManager.getInstance().deleteApiKeys(em, tenant, am);
				t.begin();
				tenant.setRulesTables(null);
				tenant.setTemplates(null);
				em.remove(tenant);
				em.flush();
				t.commit();
				return tenant;
			} catch (Exception e) {
				if (t.isActive()) {
					t.rollback();
				}
				logger.log(Level.SEVERE, "Failed to delete tenant:" + tenant, e);
				throw e;
			}
		} else {
			throw new EntityNotFoundException("Tenant not found");
		}
	}

	/**
	 * Update tenant, only name can be updated
	 * 
	 * @param em
	 * @param tenantId
	 * @param tenantName
	 * @return
	 * @throws Exception
	 */
	public Tenant updateTenant(EntityManager em, String tenantId, String tenantName) throws Exception {
		Tenant tenant = getTenant(em, tenantId);
		if (tenant != null) {
			EntityTransaction t = em.getTransaction();
			try {
				t.begin();
				tenant.setTenant_name(tenantName);
				em.merge(tenant);
				em.flush();
				t.commit();
				return tenant;
			} catch (Exception e) {
				if (t.isActive()) {
					t.rollback();
				}
				logger.log(Level.SEVERE, "Failed to update tenant:" + tenant, e);
				throw e;
			}
		} else {
			throw new EntityNotFoundException("Tenant not found");
		}
	}

	/**
	 * Get all tenants with tenant ids matching the list
	 * 
	 * @param em
	 * @param tenants
	 * @return
	 * @throws Exception
	 */
	public List<Tenant> getTenants(EntityManager em, List<String> tenants) throws Exception {
		return em.createNamedQuery(Queries.TENANT_FILTERED, Tenant.class).setParameter("tenants", tenants)
				.getResultList();
	}

	/**
	 * Get Tenant by tenant id
	 * 
	 * @param em
	 * @param tenantId
	 * @return
	 * @throws Exception
	 */
	public Tenant getTenant(EntityManager em, String tenantId) throws Exception {
		return em.createNamedQuery(Queries.TENANT_FIND_BY_ID, Tenant.class).setParameter("tenantId", tenantId)
				.getSingleResult();
	}

	/**
	 * Get all tenants by tenant name
	 * 
	 * @param em
	 * @param tenantName
	 * @return
	 * @throws Exception
	 */
	public List<Tenant> getTenantsByName(EntityManager em, String tenantName) throws Exception {
		return em.createNamedQuery(Queries.TENANT_FIND_BY_NAME, Tenant.class).setParameter("tenantName", tenantName)
				.getResultList();
	}

	/**
	 * Get all tenants
	 * 
	 * @param em
	 * @return
	 */
	public List<Tenant> getTenants(EntityManager em) {
		return em.createNamedQuery(Queries.TENANT_FIND_ALL, Tenant.class).getResultList();
	}

	/**
	 * Delete apikey
	 * 
	 * @param em
	 * @param tenantId
	 * @param apiKey
	 * @throws Exception
	 */
	public void deleteApiKey(EntityManager em, String tenantId, String apiKey, ApplicationManager am) throws Exception {
		Tenant tenant = getTenant(em, tenantId);
		if (tenant == null) {
			throw new NullPointerException("Tenant can't be empty");
		}
		EntityTransaction t = em.getTransaction();
		try {
			ApiKey key = getApiKey(em, tenant, apiKey);
			t.begin();
			em.remove(key);
			sendApikeyToKafka(am, DELETED_APIKEY, tenant.getTenant_id(), key.getApikey());
			em.flush();
			t.commit();
			logger.info("Deleted Apikey:" + apiKey + " for Tenant:" + tenantId);
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to create tenant:" + tenant, e);
			throw e;
		}
	}

	/**
	 * Get all apikeys for tenant
	 * 
	 * @param em
	 * @param tenant
	 * @return
	 * @throws Exception
	 */
	public List<ApiKey> getApiKeys(EntityManager em, Tenant tenant) throws Exception {
		return em.createNamedQuery(Queries.API_KEYS_BY_TENANT, ApiKey.class)
				.setParameter("tenantId", tenant.getTenant_id()).getResultList();
	}

	/**
	 * Get apikey
	 * 
	 * @param em
	 * @param tenant
	 * @param apiKey
	 * @return
	 * @throws Exception
	 */
	public ApiKey getApiKey(EntityManager em, Tenant tenant, String apiKey) throws Exception {
		return em.createNamedQuery(Queries.API_KEY_BY_ID, ApiKey.class).setParameter("apikey", apiKey)
				.setParameter("tenantId", tenant.getTenant_id()).getSingleResult();
	}

	/**
	 * Event source apikey CRUD to kafka
	 * 
	 * @param am
	 * @param operation
	 * @param tenantId
	 * @param apiKey
	 * @throws IOException 
	 */
	public void sendApikeyToKafka(ApplicationManager am, String operation, String tenantId, String apiKey) throws IOException {
		if (!ApplicationManager.LOCAL) {
			am.getKafkaProducer().send(new ProducerRecord<String, String>(am.getApiKeyTopic(),
					Utils.buildEvent(operation, tenantId, apiKey).toString()));
			logger.info("Wrote rule update to Kafka");
		} else {
			PrintWriter pr = new PrintWriter(
					new FileWriter(HENDRIX_APIKEY_UPDATES_TXT.replaceFirst("^~", System.getProperty("user.home")), true));
			pr.println(Utils.buildEvent(operation, tenantId, apiKey).toString());
			pr.close();
		}
		
	}

	/**
	 * Update apikey, only enabled and description can be updated.
	 * 
	 * @param em
	 * @param tenant
	 * @param key
	 * @return
	 * @throws Exception
	 */
	public ApiKey updateApiKey(EntityManager em, Tenant tenant, ApiKey key, ApplicationManager am) throws Exception {
		EntityTransaction t = em.getTransaction();
		try {
			ApiKey apiKey = getApiKey(em, tenant, key.getApikey());
			apiKey.setDescription(key.getDescription());
			apiKey.setEnabled(key.getEnabled());
			t.begin();
			em.merge(apiKey);
			if (apiKey.getEnabled()) {
				sendApikeyToKafka(am, ENABLED_APIKEY, tenant.getTenant_id(), apiKey.getApikey());
			} else {
				sendApikeyToKafka(am, DISABLED_APIKEY, tenant.getTenant_id(), apiKey.getApikey());
			}
			em.flush();
			t.commit();
			logger.info("Updated Apikey:" + key.getApikey() + " for tenant:" + tenant.getTenant_id());
			return apiKey;
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to update apikey:" + key.getApikey(), e);
			throw e;
		}
	}

	/**
	 * Create apikey
	 * 
	 * @param em
	 * @param tenantId
	 * @return
	 * @throws Exception
	 */
	public ApiKey createApiKey(EntityManager em, String tenantId, ApplicationManager am) throws Exception {
		Tenant tenant = getTenant(em, tenantId);
		if (tenant == null) {
			throw new NullPointerException("Tenant can't be empty");
		}
		EntityTransaction t = em.getTransaction();
		try {
			String apiKey = UUID.randomUUID().toString();
			ApiKey key = new ApiKey(apiKey, true);
			key.setTenant(tenant);
			t.begin();
			em.persist(key);
			sendApikeyToKafka(am, CREATED_APIKEY, tenant.getTenant_id(), key.getApikey());
			em.flush();
			t.commit();
			logger.info("Created api key for tenant:" + tenantId);
			return key;
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to create apikey for tenant:" + tenant, e);
			throw e;
		}
	}

	/**
	 * Delete all api keys for tenant
	 * 
	 * @param em
	 * @param tenant
	 * @throws Exception
	 */
	public void deleteApiKeys(EntityManager em, Tenant tenant, ApplicationManager am) throws Exception {
		EntityTransaction transaction = em.getTransaction();
		try {
			transaction.begin();
			List<ApiKey> apiKeys = getApiKeys(em, tenant);
			for (ApiKey key : apiKeys) {
				em.remove(key);
				sendApikeyToKafka(am, DELETED_APIKEY, tenant.getTenant_id(), key.getApikey());
			}
			em.flush();
			transaction.commit();
			logger.info("Deleted all apikeys for tenant:" + tenant);
		} catch (Exception e) {
			if (transaction.isActive()) {
				transaction.rollback();
			}
			if (!(e instanceof NoResultException)) {
				logger.log(Level.SEVERE, "Failed to delete apikey", e);
			}
			throw e;
		}
	}

}