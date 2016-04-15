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
package io.symcpe.hendrix.api.storage;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.symcpe.hendrix.api.Queries;

/**
 * The persistent class for the tenant database table.
 */
@Entity
@Table(name = "tenant")
@NamedQueries({ @NamedQuery(name = Queries.TENANT_FIND_ALL, query = "SELECT t FROM Tenant t"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_ID, query = "SELECT t FROM Tenant t where t.tenantId=:tenantId"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_IDS, query = "SELECT t FROM Tenant t where t.tenantId in :tenantIds"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_NAMES, query = "SELECT t FROM Tenant t where t.tenantName in :tenantNames"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_NAME, query = "SELECT t FROM Tenant t where t.tenantName like :tenantName"),
		@NamedQuery(name = Queries.TENANT_DELETE_BY_ID, query = "DELETE FROM Tenant t where t.tenantId=:tenantId") })
public class Tenant implements Serializable {

	private static final long serialVersionUID = 1L;
	public static final int TENANT_ID_MAX_SIZE = 32;
	public static final int TENANT_NAME_MAX_SIZE = 100;

	@Id
	@Column(name = "tenant_id", length = TENANT_ID_MAX_SIZE)
	private String tenantId;

	@Column(name = "tenant_name", length = TENANT_NAME_MAX_SIZE)
	private String tenantName;

	// bi-directional many-to-one association to RulesTable
	@OneToMany(mappedBy = "tenant", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private List<Rules> rulesTables;

	public Tenant() {
	}

	public String getTenantId() {
		return this.tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getTenantName() {
		return this.tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	@JsonIgnore
	public List<Rules> getRulesTables() {
		return this.rulesTables;
	}

	public void setRulesTables(List<Rules> rulesTables) {
		this.rulesTables = rulesTables;
	}

	public Rules addRulesTable(Rules rulesTable) {
		getRulesTables().add(rulesTable);
		rulesTable.setTenant(this);

		return rulesTable;
	}

	public Rules removeRulesTable(Rules rulesTable) {
		getRulesTables().remove(rulesTable);
		rulesTable.setTenant(null);

		return rulesTable;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Tenant [tenantId=" + tenantId + ", tenantName=" + tenantName + ", rulesTables=" + rulesTables + "]";
	}

}