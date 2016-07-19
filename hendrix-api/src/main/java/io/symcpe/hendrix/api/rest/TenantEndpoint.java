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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.security.RolesAllowed;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.wordnik.swagger.annotations.Api;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.dao.TenantManager;
import io.symcpe.hendrix.api.security.ACLConstants;
import io.symcpe.hendrix.api.security.BapiAuthorizationFilter;
import io.symcpe.hendrix.api.storage.ApiKey;
import io.symcpe.hendrix.api.storage.Tenant;

/**
 * REST endpoint for tenant operations
 * 
 * @author ambud_sharma
 */
@Path("/tenants")
@Api
public class TenantEndpoint {

	public static final String TENANT_ID = "tenantId";
	private static final Logger logger = Logger.getLogger(TenantEndpoint.class.getName());
	private static final String APIKEY = "apikey";
	private ApplicationManager am;

	public TenantEndpoint(ApplicationManager am) {
		this.am = am;
	}

	/**
	 * @return
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.READER_ROLE, ACLConstants.OPERATOR_ROLE, ACLConstants.SUPER_ADMIN_ROLE,
			ACLConstants.SUPER_ADMIN_ROLE })
	public List<Tenant> listTenants(@Context HttpHeaders headers) {
		if (am.getConfiguration().isEnableAuthorization()) {
			MultivaluedMap<String, String> requestHeaders = headers.getRequestHeaders();
			List<String> tList = new ArrayList<>();
			for (Entry<String, List<String>> entry : requestHeaders.entrySet()) {
				if (entry.getKey().startsWith(BapiAuthorizationFilter.ROLE_PREFIX)) {
					String[] split = entry.getKey().split(BapiAuthorizationFilter.ROLE_TENANT_SEPARATOR);
					if (split.length == 2) {
						tList.add(split[1]);
					}
				}
			}
			EntityManager em = am.getEM();
			try {
				return TenantManager.getInstance().getTenants(em, tList);
			} catch (Exception e) {
				throw new NotFoundException("No matching Tenants found");
			} finally {
				em.close();
			}
		} else {
			EntityManager em = am.getEM();
			try {
				return TenantManager.getInstance().getTenants(em);
			} catch (Exception e) {
				throw new NotFoundException("No Tenants found");
			} finally {
				em.close();
			}
		}
	}

	/**
	 * @param tenantId
	 * @return
	 */
	@Path("/{" + TENANT_ID + "}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.READER_ROLE, ACLConstants.OPERATOR_ROLE, ACLConstants.SUPER_ADMIN_ROLE,
			ACLConstants.SUPER_ADMIN_ROLE })
	public Tenant getTenant(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE, message = "Tenant ID can't be empty") String tenantId) {
		EntityManager em = am.getEM();
		try {
			return TenantManager.getInstance().getTenant(em, tenantId);
		} catch (Exception e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Tenants found").build());
		} finally {
			em.close();
		}
	}

	/**
	 * @param tenant
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE })
	public void createTenant(@NotNull(message = "Tenant information can't be empty") Tenant tenant) {
		if (!validateTenant(tenant)) {
			throw new BadRequestException("Tenant info is invalid");
		}
		EntityManager em = am.getEM();
		try {
			if (TenantManager.getInstance().getTenant(em, tenant.getTenant_id()) != null) {
				throw new BadRequestException(Response.status(400)
						.entity("Tenant with tenant id:" + tenant.getTenant_id() + " already exists").build());
			}
		} catch (NoResultException e) {
		} catch (Exception e) {
		}
		try {
			TenantManager.getInstance().createTenant(em, tenant);
			logger.info("Created new tenant:" + tenant);
			ApiKey apiKey = TenantManager.getInstance().createApiKey(em, tenant.getTenant_id());
			logger.info("Created API Key for tenant:" + tenant + "\t" + apiKey);
		} catch (EntityExistsException e) {
			throw new BadRequestException(Response.status(400)
					.entity("Tenant with tenant id:" + tenant.getTenant_id() + " already exists").build());
		} catch (Exception e) {
			throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	/**
	 * @param tenantId
	 */
	@Path("/{" + TENANT_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE })
	public void deleteTenant(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			Tenant tenant = TenantManager.getInstance().deleteTenant(em, tenantId, am);
			logger.info("Deleted tenant:" + tenant);
		} catch (Exception e) {
			throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
		} finally {
			em.close();
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
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE })
	public void updateTenant(
			@NotNull(message = "Tenant ID can't be empty") @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull(message = "Tenant information can't be empty") Tenant tenant) {
		if (!validateTenant(tenant)) {
			throw new BadRequestException("Tenant info is invalid");
		}
		EntityManager em = am.getEM();
		try {
			tenant = TenantManager.getInstance().updateTenant(em, tenantId, tenant.getTenant_name());
			logger.info("Updated tenant:" + tenant);
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Tenants found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		} finally {
			em.close();
		}
	}

	public static boolean validateTenant(Tenant tenant) {
		if (tenant == null || tenant.getTenant_id() == null || tenant.getTenant_name() == null
				|| tenant.getTenant_id().isEmpty() || tenant.getTenant_name().isEmpty()) {
			return false;
		}
		if (tenant.getTenant_id().length() > Tenant.TENANT_ID_MAX_SIZE) {
			return false;
		}
		if (tenant.getTenant_name().length() > Tenant.TENANT_NAME_MAX_SIZE) {
			return false;
		}
		return true;
	}

	@Path("/{" + TENANT_ID + "}/apikey")
	@POST
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE })
	public ApiKey createApiKey(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			ApiKey apiKey = TenantManager.getInstance().createApiKey(em, tenantId);
			logger.info("Created Apikey for tenant:" + tenantId);
			return apiKey;
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Tenants found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		} finally {
			em.close();
		}
	}

	@Path("/{" + TENANT_ID + "}/apikey/{" + APIKEY + " : .+}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE })
	public String deleteApiKey(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(APIKEY) @Size(min = 1, max = ApiKey.APIKEY_LENGTH) String apiKey) {
		EntityManager em = am.getEM();
		try {
			TenantManager.getInstance().deleteApiKey(em, tenantId, apiKey);
			logger.info("Delete Apikey for tenant:" + tenantId);
			return apiKey;
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Apikey found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		} finally {
			em.close();
		}
	}

	@Path("/{" + TENANT_ID + "}/apikey")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	public List<ApiKey> getApiKey(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			Tenant tenant = TenantManager.getInstance().getTenant(em, tenantId);
			List<ApiKey> apiKeys = TenantManager.getInstance().getApiKeys(em, tenant);
			return apiKeys;
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Apikey found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		} finally {
			em.close();
		}
	}

	@Path("/{" + TENANT_ID + "}/apikey")
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	public ApiKey updateApiKey(
			@NotNull @PathParam(TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull(message = "Apikey information can't be empty") ApiKey key) {
		EntityManager em = am.getEM();
		try {
			Tenant tenant = TenantManager.getInstance().getTenant(em, tenantId);
			return TenantManager.getInstance().updateApiKey(em, tenant, key);
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("No Apikey found").build());
			} else {
				throw new BadRequestException(Response.status(400).entity(e.getMessage()).build());
			}
		} finally {
			em.close();
		}
	}

}
