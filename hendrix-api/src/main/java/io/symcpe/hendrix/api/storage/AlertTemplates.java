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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.symcpe.hendrix.api.Queries;

/**
 * 
 * 
 * @author ambud_sharma
 */
@Entity
@Table(name = "alert_template")
@NamedQueries({
	@NamedQuery(name = Queries.TEMPLATE_FIND_ALL, query = "SELECT t FROM AlertTemplates t"),
	@NamedQuery(name = Queries.TEMPLATE_FIND_BY_ID, query = "SELECT t FROM AlertTemplates t where t.templateId=:templateId and t.tenant.tenant_id=:tenantId"),
	@NamedQuery(name = Queries.TEMPLATE_FIND_BY_TENANT_ID, query = "SELECT t FROM AlertTemplates t where t.tenant.tenant_id=:tenantId"),
	@NamedQuery(name = Queries.TEMPLATE_DELETE_BY_ID, query = "DELETE FROM AlertTemplates t where t.templateId=:templateId"),
})
public class AlertTemplates implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final int MAX_TEMPLATE_LENGTH = 32000;

	@Id
	@Column(name = "template_id")
	@GenericGenerator(name = "sequence_rule_id", strategy = "io.symcpe.hendrix.api.dao.TemplateIdGenerator")
	@GeneratedValue(generator = "sequence_rule_id")
	private short templateId;

	@Column(name = "template_content", length = AlertTemplates.MAX_TEMPLATE_LENGTH)
	private String templateContent;

	// bi-directional many-to-one association to Tenant
	@ManyToOne()
	@JoinColumn(name = "tenant_id")
	@JsonIgnore
	private Tenant tenant;
	
	public AlertTemplates() {
	}

	/**
	 * @return the templateId
	 */
	public short getTemplateId() {
		return templateId;
	}

	/**
	 * @param templateId the templateId to set
	 */
	public void setTemplateId(short templateId) {
		this.templateId = templateId;
	}

	/**
	 * @return the templateContent
	 */
	public String getTemplateContent() {
		return templateContent;
	}

	/**
	 * @param templateContent the templateContent to set
	 */
	public void setTemplateContent(String templateContent) {
		this.templateContent = templateContent;
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
