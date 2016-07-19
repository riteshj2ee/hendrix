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
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wordnik.swagger.annotations.ApiModelProperty;

import io.symcpe.hendrix.api.Queries;

/**
 * @author ambud_sharma
 */
@Entity
@Table(name = "api_key")
@NamedQueries({
	@NamedQuery(name=Queries.API_KEYS_BY_TENANT,query="SELECT a FROM ApiKey a where a.tenant.tenant_id=:tenantId"),
	@NamedQuery(name=Queries.API_KEY_BY_ID,query="SELECT a FROM ApiKey a where a.apikey=:apikey and a.tenant.tenant_id=:tenantId")
})
public class ApiKey implements Serializable {

	public static final int APIKEY_LENGTH = 200;

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(required = true, value = "api key")
	@Id
	@Column(name = "apikey", nullable = false, length = APIKEY_LENGTH)
	private String apikey;
	
	@ApiModelProperty(value = "enabled")
	@Column(name = "enabled")
	private Boolean enabled;

	@ApiModelProperty(value = "description")
	@Column(name = "description", length = 500)
	private String description;
	
	@ManyToOne()
	@JoinColumn(name = "tenant_id")
	@JsonIgnore
	private Tenant tenant;

	public ApiKey(String apiKey, boolean enabled) {
        this.apikey = apiKey;
        this.enabled = enabled;
    }

	public ApiKey(String apikey, boolean enabled, String description) {
        this.apikey = apikey;
        this.enabled = enabled;
        this.description = description;
    }

	public ApiKey() {
    }

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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

	/**
	 * @return the apikey
	 */
	public String getApikey() {
		return apikey;
	}

	/**
	 * @param apikey the apikey to set
	 */
	public void setApikey(String apikey) {
		this.apikey = apikey;
	}
	
	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ApiKey [apikey=" + apikey + ", enabled=" + enabled + ", description=" + description + ", tenant="
				+ tenant + "]";
	}

}
