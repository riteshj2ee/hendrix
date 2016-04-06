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
package io.symcpe.hendrix.ui.rules;

import java.io.Serializable;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;

import org.primefaces.context.RequestContext;

import io.symcpe.hendrix.ui.storage.Tenant;

/**
 * JSF Tenant bean
 * 
 * @author ambud_sharma
 */
@ManagedBean(name = "tb")
@SessionScoped
public class TenantBean implements Serializable {

	private static final long serialVersionUID = 1L;

	private boolean enableEdit;
	private Tenant tenant;

	public TenantBean() {
	}

	@PostConstruct
	public void init() {
		tenant = new Tenant();
	}

	public void addTenant() {
		tenant = new Tenant();
		enableEdit = true;
		RequestContext.getCurrentInstance().execute("location.reload();");
	}

	public void onClose() {
		enableEdit = false;
	}

	public void save() {
		try {
			try {
				if (TenantManager.getInstance().getTenant(tenant.getTenantId()) != null) {
					updateTenant();
				}
			} catch (Exception e) {
				newTenant();
			}
			enableEdit = false;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void newTenant() {
		try {
			TenantManager.getInstance().createTenant(tenant);
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Unable to create new tenant", e.getMessage()));
		}
	}

	public void updateTenant() {
		try {
			TenantManager.getInstance().updateTenant(tenant.getTenantId(), tenant.getTenantName());
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Unable to update tenant", e.getMessage()));
		}
	}

	public void deleteTenant(String tenantId) {
		try {
			TenantManager.getInstance().deleteTenant(tenantId);
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Unable to delete tenant", e.getMessage()));
		}
	}

	public void changeCurrentTenant(String tenantId) {
		try {
			tenant = TenantManager.getInstance().getTenant(tenantId);
			enableEdit = true;
			RequestContext.getCurrentInstance().execute("location.reload();");
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Unable to find tenant", e.getMessage()));
		}
	}

	public List<Tenant> getTenants() {
		return TenantManager.getInstance().getTenants();
	}

	/**
	 * @return the tenant
	 */
	public Tenant getTenant() {
		return tenant;
	}

	/**
	 * @param tenant
	 *            the tenant to set
	 */
	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}

	/**
	 * @return the enableEdit
	 */
	public boolean isEnableEdit() {
		return enableEdit;
	}

}
