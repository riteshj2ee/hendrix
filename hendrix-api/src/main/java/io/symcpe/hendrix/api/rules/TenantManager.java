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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityTransaction;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Queries;
import io.symcpe.hendrix.api.storage.Tenant;

/**
 * Persistence manager for {@link Tenant}s
 * 
 * @author ambud_sharma
 */
public class TenantManager {

	private static final Logger logger = Logger.getLogger(TenantManager.class.getCanonicalName());
	private static final TenantManager TENANT_MANAGER = new TenantManager();
	private ApplicationManager am;

	private TenantManager() {
	}

	public static TenantManager getInstance() {
		return TENANT_MANAGER;
	}

	public void init(ApplicationManager am) {
		this.am = am;
	}

	public void createTenant(Tenant tenant) throws Exception {
		if (tenant == null) {
			throw new NullPointerException("Tenant can't be empty");
		}
		EntityManager em = am.getEM();
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

	public Tenant deleteTenant(String tenantId) throws Exception {
		Tenant tenant = getTenant(tenantId);
		if (tenant != null) {
			EntityManager em = am.getEM();
			EntityTransaction t = em.getTransaction();
			try {
				RulesManager.getInstance().deleteRules(tenantId);
				t.begin();
				if (em.createNamedQuery(Queries.TENANT_DELETE_BY_ID).setParameter("tenantId", tenantId)
						.executeUpdate() != 1) {
					throw new Exception("Tenant not deleted");
				}
				em.detach(tenant);
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

	public Tenant updateTenant(String tenantId, String tenantName) throws Exception {
		Tenant tenant = getTenant(tenantId);
		if (tenant != null) {
			EntityManager em = am.getEM();
			EntityTransaction t = em.getTransaction();
			try {
				t.begin();
				tenant.setTenantName(tenantName);
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

	public Tenant getTenant(String tenantId) throws Exception {
		EntityManager em = am.getEM();
		return em.createNamedQuery(Queries.TENANT_FIND_BY_ID, Tenant.class).setParameter("tenantId", tenantId)
				.getSingleResult();
	}

	public List<Tenant> getTenantsByName(String tenantName) throws Exception {
		EntityManager em = am.getEM();
		return em.createNamedQuery(Queries.TENANT_FIND_BY_NAME, Tenant.class).setParameter("tenantName", tenantName)
				.getResultList();
	}

	public List<Tenant> getTenants() {
		EntityManager em = am.getEM();
		return em.createNamedQuery(Queries.TENANT_FIND_ALL, Tenant.class).getResultList();
	}

}