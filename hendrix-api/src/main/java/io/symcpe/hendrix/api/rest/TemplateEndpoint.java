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

import javax.annotation.security.RolesAllowed;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.JsonParseException;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Utils;
import io.symcpe.hendrix.api.dao.TemplateManager;
import io.symcpe.hendrix.api.dao.TenantManager;
import io.symcpe.hendrix.api.security.ACLConstants;
import io.symcpe.hendrix.api.storage.AlertTemplates;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.rules.validator.ValidationException;

/**
 * @author ambud_sharma
 */
@Path("/templates")
@Api
public class TemplateEndpoint {

	private static final String TEMPLATE_ID = "test";
	private ApplicationManager am;

	public TemplateEndpoint(ApplicationManager am) {
		this.am = am;
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	@ApiOperation(value = "List templates", notes = "List templates for the supplied Tenant ID", response = AlertTemplate.class, responseContainer="List")
	public String listTemplates(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@DefaultValue("false") @QueryParam("pretty") boolean pretty) {
		EntityManager em = am.getEM();
		try {
			return TemplateManager.getInstance().getTemplateContents(em, tenantId, pretty);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error fetching templates:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@POST
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	@ApiOperation(value = "Create template", notes = "Create an empty for the supplied Tenant ID", response = Short.class)
	public short createTemplate(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		TemplateManager mgr = TemplateManager.getInstance();
		Tenant tenant;
		EntityManager em = am.getEM();
		try {
			tenant = mgr.getTenant(em, tenantId);
		} catch (Exception e) {
			em.close();
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Tenant not found").build());
		}
		try {
			return mgr.createNewTemplate(em, new AlertTemplates(), tenant);
		} catch (Exception e) {
			throw new InternalServerErrorException();
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + TEMPLATE_ID + "}")
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	@ApiOperation(value = "Update template", notes = "Update template for the supplied Tenant ID and Template ID", response = Short.class)
	public short putTemplate(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE, message = "Tenant ID must be under 100 characters") String tenantId,
			@NotNull(message = "Template ID can't be empty") @PathParam(TEMPLATE_ID) short templateId,
			@HeaderParam("Accept-Charset") @DefaultValue("utf-8") String encoding,
			@NotNull(message = "Template JSON can't be empty") @Encoded String templateJson) {
		EntityManager em = am.getEM();
		if (templateJson != null && templateJson.length() > AlertTemplates.MAX_TEMPLATE_LENGTH) {
			throw new BadRequestException(Response.status(Status.BAD_REQUEST).entity("Template is too big").build());
		}

		if (!Utils.isCharsetMisInterpreted(templateJson, encoding)) {
			throw new BadRequestException(
					Response.status(Status.BAD_REQUEST).entity("Template JSON must be UTF-8 compliant").build());
		}

		TemplateManager mgr = TemplateManager.getInstance();
		Tenant tenant;
		try {
			tenant = mgr.getTenant(em, tenantId);
		} catch (Exception e) {
			em.close();
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Tenant not found").build());
		}
		AlertTemplate template = null;
		try {
			template = AlertTemplateSerializer.deserialize(templateJson);
			if (template == null) {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST).entity("Unable to parse template").build());
			}
			if (template.getTemplateId() != templateId) {
				throw new BadRequestException(Response.status(Status.BAD_REQUEST)
						.entity("Template id in path doesn't match the template").build());
			}
		} catch (BadRequestException e) {
			throw e;
		} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
			em.close();
			if (e.getMessage().contains("NumberFormat") || (e instanceof NumberFormatException)) {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST)
								.entity("Invalid number "
										+ e.getLocalizedMessage().replace("java.lang.NumberFormatException", ""))
								.build());
			} else if (e.getMessage().contains("Malformed")) {
				throw new BadRequestException(Response.status(Status.BAD_REQUEST).entity("Invalid JSON").build());
			} else if (e.getMessage().contains("IllegalStateException")) {
				throw new BadRequestException(Response.status(Status.BAD_REQUEST)
						.entity("Expecting a singel template object not an array").build());
			} else {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST).entity(e.getLocalizedMessage()).build());
			}
		}
		try {
			AlertTemplates templateContainer = new AlertTemplates();
			if (template.getTemplateId() > 0) {
				try {
					AlertTemplates temp = mgr.getTemplate(em, tenant.getTenantId(), template.getTemplateId());
					if (temp != null) {
						templateContainer = temp;
					}
				} catch (NoResultException e) {
					// template doesn't exit, will save it as a new template
				}
			}
			return mgr.saveTemplate(em, templateContainer, tenant, template, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (ValidationException e) {
			throw new BadRequestException(Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build());
		} catch (Exception e) {
			throw new InternalServerErrorException(Response.serverError().entity(e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + TEMPLATE_ID + "}")
	@GET
	@Produces({ MediaType.TEXT_HTML })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	@ApiOperation(value = "Get template", notes = "Get template for the supplied Tenant ID and Template ID", response = AlertTemplate.class)
	public String getTemplate(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(TEMPLATE_ID) short templateId,
			@DefaultValue("false") @QueryParam("pretty") boolean pretty) {
		AlertTemplate template = null;
		EntityManager em = am.getEM();
		try {
			template = TemplateManager.getInstance().getTemplateObj(em, tenantId, templateId);
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
			} else {
				throw new BadRequestException();
			}
		} finally {
			em.close();
		}
		return AlertTemplateSerializer.serialize(template, pretty);
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + TEMPLATE_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	@ApiOperation(value = "Delete template", notes = "Delete template for the supplied Tenant ID and Template ID, template can only be deleted if there are no rules associated with them")
	public void deleteTemplate(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(TEMPLATE_ID) short templateId) {
		EntityManager em = am.getEM();
		try {
			TemplateManager.getInstance().deleteTemplate(em, tenantId, templateId, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error deleting template:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE })
	@ApiOperation(value = "Delete all templates", notes = "Delete all templates for the supplied Tenant ID, templates can only be deleted if there are no rules associated with them")
	public void deleteAllTemplates(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			Tenant tenant = TenantManager.getInstance().getTenant(em, tenantId);
			TemplateManager.getInstance().deleteTemplates(em, tenant, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error deleting all templates for tenant:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}
}