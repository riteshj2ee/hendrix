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
import io.symcpe.hendrix.api.Queries;

import com.wordnik.swagger.annotations.ApiModelProperty;

@Entity
@Table(name = "api_key")
@NamedQueries({
	@NamedQuery(name=Queries.API_KEYS_BY_TENANT,query="SELECT a FROM ApiKey a where a.tenant.tenant_id=:tenantId"),
	@NamedQuery(name=Queries.API_KEY_BY_ID,query="SELECT a FROM ApiKey a where a.apikey=:apikey and a.tenant.tenant_id=:tenantId")
})
public class ApiKey implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(required = true, value = "api key")
	@Id
	@Column(name = "apikey", nullable = false, length = 200)
	private String apikey;
	
	@ApiModelProperty(value = "enabled")
	private Boolean enabled;

	@ApiModelProperty(value = "description")
	@Column(name = "description", length = 500)
	private String description;
	
	@ManyToOne()
	@JoinColumn(name = "tenant_id")
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

	@Column(name = "enabled", length = 100)
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

}
