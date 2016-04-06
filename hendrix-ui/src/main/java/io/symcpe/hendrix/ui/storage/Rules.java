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
package io.symcpe.hendrix.ui.storage;

import java.io.Serializable;
import javax.persistence.*;

import io.symcpe.hendrix.ui.Queries;

/**
 * The persistent class for the rules_table database table.
 * 
 * @author ambud_sharma
 */
@Entity
@Table(name = "rules_table")
@NamedQueries({ 
		@NamedQuery(name = Queries.RULES_FIND_ALL, query = "SELECT r FROM Rules r"),
		@NamedQuery(name = Queries.RULES_STATS, query = "SELECT r.tenant.tenantName,count(r) FROM Rules r group by r.tenant.tenantName"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_ID, query = "SELECT r FROM Rules r where r.tenant.tenantId=:tenantId"),
		@NamedQuery(name = Queries.RULES_FIND_BY_ID, query = "SELECT r FROM Rules r where r.ruleId=:ruleId"),
		@NamedQuery(name = Queries.RULES_FIND_BY_ID_AND_TENANT, query = "SELECT r FROM Rules r where r.ruleId=:ruleId and r.tenant.tenantId=:tenantId"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_IDS, query = "SELECT r FROM Rules r where r.tenant.tenantId in :tenantIds"),
		@NamedQuery(name = Queries.RULES_FIND_ALL_BY_TENANT_NAME, query = "SELECT r FROM Rules r where r.tenant.tenantName=:tenantName"),
		@NamedQuery(name = Queries.RULES_DELETE_BY_ID, query = "DELETE FROM Rules r where r.ruleId=:ruleId"),
		@NamedQuery(name = Queries.RULES_LATEST_RULE_ID, query = "SELECT r.ruleId from Rules r order by r.ruleId desc"),
		@NamedQuery(name = Queries.RULES_ENABLE_DISABLE_RULE, query = "UPDATE Rules r set r.ruleContent=:ruleContent where r.ruleId=:ruleId")
})
public class Rules implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "rule_id")
	@GeneratedValue(strategy=GenerationType.AUTO)
	private short ruleId;

	@Column(name = "rule_content", length = 40960)
	private String ruleContent;

	// bi-directional many-to-one association to Tenant
	@ManyToOne(cascade=CascadeType.ALL)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	public Rules() {
	}

	public short getRuleId() {
		return this.ruleId;
	}

	public void setRuleId(short ruleId) {
		this.ruleId = ruleId;
	}

	public String getRuleContent() {
		return this.ruleContent;
	}

	public void setRuleContent(String ruleContent) {
		this.ruleContent = ruleContent;
	}

	public Tenant getTenant() {
		return this.tenant;
	}

	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}

}