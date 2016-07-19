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
		@NamedQuery(name = Queries.TENANT_FIND_BY_ID, query = "SELECT t FROM Tenant t where t.tenant_id=:tenantId"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_IDS, query = "SELECT t FROM Tenant t where t.tenant_id in :tenantIds"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_NAMES, query = "SELECT t FROM Tenant t where t.tenant_name in :tenantNames"),
		@NamedQuery(name = Queries.TENANT_FIND_BY_NAME, query = "SELECT t FROM Tenant t where t.tenant_name like :tenantName"),
		@NamedQuery(name = Queries.TENANT_DELETE_BY_ID, query = "DELETE FROM Tenant t where t.tenant_id=:tenantId"),
		@NamedQuery(name = Queries.TENANT_FILTERED, query = "SELECT t FROM Tenant t where t.tenant_id IN :tenants") })
public class Tenant implements Serializable {

	private static final long serialVersionUID = 1L;
	public static final int TENANT_ID_MAX_SIZE = 50;
	public static final int TENANT_NAME_MAX_SIZE = 100;

	@Id
	@Column(name = "tenant_id", length = TENANT_ID_MAX_SIZE)
	private String tenant_id;

	@Column(name = "tenant_name", length = TENANT_NAME_MAX_SIZE)
	private String tenant_name;

	// bi-directional many-to-one association to RulesTable
	@OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
	@JsonIgnore
	private List<Rules> rulesTables;

	@OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
	@JsonIgnore
	private List<AlertTemplates> templates;
	
	@OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
	@JsonIgnore
	private List<ApiKey> apiKeys;

	public Tenant() {
	}

	/**
	 * @return the tenant_id
	 */
	public String getTenant_id() {
		return tenant_id;
	}

	/**
	 * @return the tenant_name
	 */
	public String getTenant_name() {
		return tenant_name;
	}

	/**
	 * @param tenant_name the tenant_name to set
	 */
	public void setTenant_name(String tenant_name) {
		this.tenant_name = tenant_name;
	}

	/**
	 * @param tenant_id the tenant_id to set
	 */
	public void setTenant_id(String tenant_id) {
		this.tenant_id = tenant_id;
	}

	@JsonIgnore
	public List<Rules> getRulesTables() {
		return this.rulesTables;
	}

	public void setRulesTables(List<Rules> rulesTables) {
		this.rulesTables = rulesTables;
	}

	/**
	 * @return the templates
	 */
	@JsonIgnore
	public List<AlertTemplates> getTemplates() {
		return templates;
	}

	/**
	 * @param templates
	 *            the templates to set
	 */
	public void setTemplates(List<AlertTemplates> templates) {
		this.templates = templates;
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

	public AlertTemplates addTemplates(AlertTemplates alertTemplates) {
		getTemplates().add(alertTemplates);
		alertTemplates.setTenant(this);
		return alertTemplates;
	}

	public AlertTemplates removeTemplates(AlertTemplates alertTemplates) {
		getTemplates().remove(alertTemplates);
		alertTemplates.setTenant(null);
		return alertTemplates;
	}
	
	public ApiKey addApiKey(ApiKey apiKey) {
		getApiKeys().add(apiKey);
		apiKey.setTenant(this);
		return apiKey;
	}

	public ApiKey removeApiKey(ApiKey apiKey) {
		getApiKeys().remove(apiKey);
		apiKey.setTenant(null);
		return apiKey;
	}

	/**
	 * @return the apiKeys
	 */
	public List<ApiKey> getApiKeys() {
		return apiKeys;
	}

	/**
	 * @param apiKeys the apiKeys to set
	 */
	public void setApiKeys(List<ApiKey> apiKeys) {
		this.apiKeys = apiKeys;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Tenant [tenantId=" + tenant_id + ", tenantName=" + tenant_name + "]";
	}

}