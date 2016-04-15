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
package io.symcpe.hendrix.api.rest;

import java.util.List;
import java.util.logging.Logger;

import javax.persistence.EntityExistsException;
import javax.persistence.NoResultException;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.symcpe.hendrix.api.rules.TenantManager;
import io.symcpe.hendrix.api.storage.Tenant;

/**
 * REST endpoint for tenant operations
 * 
 * @author ambud_sharma
 */
@Path("/tenants")
public class TenantEndpoint {

	public static final String TENANT_ID = "tenantId";
	private static final Logger logger = Logger.getLogger(TenantEndpoint.class.getName());

	/**
	 * @return
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public List<Tenant> listTenants() {
		try {
			return TenantManager.getInstance().getTenants();
		} catch (Exception e) {
			throw new NotFoundException("No Tenants found");
		}
	}

	/**
	 * @param tenantId
	 * @return
	 */
	@Path("/{" + TENANT_ID + "}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public Tenant getTenant(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE, message="Tenant ID can't be empty") String tenantId) {
		try {
			return TenantManager.getInstance().getTenant(tenantId);
		} catch (Exception e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Tenants found").build());
		}
	}

	/**
	 * @param tenant
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public void createTenant(@NotNull(message="Tenant information can't be empty") Tenant tenant) {
		// TODO validate tenant
		if(!validateTenant(tenant)) {
			throw new BadRequestException("Tenant info is invalid");
		}
		try {
			if (TenantManager.getInstance().getTenant(tenant.getTenantId()) != null) {
				throw new BadRequestException(Response.status(400)
						.entity("Tenant with tenant id:" + tenant.getTenantId() + " already exists").build());
			}
		} catch (NoResultException e) {
		} catch (Exception e) {
		}
		try {
			TenantManager.getInstance().createTenant(tenant);
			logger.info("Created new tenant:" + tenant);
		} catch (EntityExistsException e) {
			throw new BadRequestException(Response.status(400)
					.entity("Tenant with tenant id:" + tenant.getTenantId() + " already exists").build());
		} catch (Exception e) {
			throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
		}
	}

	/**
	 * @param tenantId
	 */
	@Path("/{" + TENANT_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	public void deleteTenant(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		try {
			Tenant tenant = TenantManager.getInstance().deleteTenant(tenantId);
			logger.info("Deleted tenant:" + tenant);
		} catch (Exception e) {
			throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
		}
	}

	/**
	 * @param tenantId
	 * @param tenant
	 */
	@Path("/{" + TENANT_ID + "}")
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public void updateTenant(
			@NotNull(message="Tenant ID can't be empty") @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull(message="Tenant information can't be empty") Tenant tenant) {
		if(!validateTenant(tenant)) {
			throw new BadRequestException("Tenant info is invalid");
		}
		try {
			tenant = TenantManager.getInstance().updateTenant(tenantId, tenant.getTenantName());
			logger.info("Updated tenant:" + tenant);
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Tenants found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		}
	}

	public static boolean validateTenant(Tenant tenant) {
		if (tenant == null || tenant.getTenantId() == null || tenant.getTenantName() == null
				|| tenant.getTenantId().isEmpty() || tenant.getTenantName().isEmpty()) {
			return false;
		}
		if(tenant.getTenantId().length()>Tenant.TENANT_ID_MAX_SIZE) {
			return false;
		}
		if(tenant.getTenantName().length()>Tenant.TENANT_NAME_MAX_SIZE) {
			return false;
		}
		return true;
	}
	
}
