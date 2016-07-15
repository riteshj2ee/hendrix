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
import javax.persistence.*;

import io.symcpe.hendrix.api.Queries;

/**
 * The persistent class for the rules_table database table.
 */
@Entity
@Table(name = "rules_table")
@NamedQueries({ @NamedQuery(name = Queries.RULES_FIND_ALL, query = "SELECT r FROM Rules r"),
		@NamedQuery(name = Queries.RULES_STATS, query = "SELECT r.tenant.tenant_name,count(r) FROM Rules r group by r.tenant.tenant_name"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_ID, query = "SELECT r FROM Rules r where r.tenant.tenant_id=:tenantId"),
		@NamedQuery(name = Queries.RULES_FIND_BY_ID, query = "SELECT r FROM Rules r where r.ruleId=:ruleId"),
		@NamedQuery(name = Queries.RULES_FIND_BY_ID_AND_TENANT, query = "SELECT r FROM Rules r where r.ruleId=:ruleId and r.tenant.tenant_id=:tenantId"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_IDS, query = "SELECT r FROM Rules r where r.tenant.tenant_id in :tenantIds"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_NAME, query = "SELECT r FROM Rules r where r.tenant.tenant_name=:tenantName"),
		@NamedQuery(name = Queries.RULES_DELETE_BY_ID, query = "DELETE FROM Rules r where r.ruleId=:ruleId"),
		@NamedQuery(name = Queries.RULES_LATEST_RULE_ID, query = "SELECT r.ruleId from Rules r order by r.ruleId desc"),
		@NamedQuery(name = Queries.RULES_BY_TEMPLATE_ID_BY_TENANT, query = "SELECT r.ruleId from Rules r where r.tenant.tenant_id=:tenantId and r.ruleContent like :template"),
		@NamedQuery(name = Queries.RULES_ENABLE_DISABLE_RULE, query = "UPDATE Rules r set r.ruleContent=:ruleContent where r.ruleId=:ruleId") })
public class Rules implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final int MAX_RULE_LENGTH = 32000;

	@Id
	@Column(name = "rule_id")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private short ruleId;

	@Column(name = "rule_content", length = Rules.MAX_RULE_LENGTH)
	private String ruleContent;

	// bi-directional many-to-one association to Tenant
	@ManyToOne()
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	public Rules() {
	}

	/**
	 * @return the ruleId
	 */
	public short getRuleId() {
		return ruleId;
	}

	/**
	 * @param ruleId the ruleId to set
	 */
	public void setRuleId(short ruleId) {
		this.ruleId = ruleId;
	}

	/**
	 * @return the ruleContent
	 */
	public String getRuleContent() {
		return ruleContent;
	}

	/**
	 * @param ruleContent the ruleContent to set
	 */
	public void setRuleContent(String ruleContent) {
		this.ruleContent = ruleContent;
	}

	/**
	 * @return the tenant
	 */
	public Tenant getTenant() {
		return tenant;
	}

	/**
	 * @param tenant the tenant to set
	 */
	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}
}