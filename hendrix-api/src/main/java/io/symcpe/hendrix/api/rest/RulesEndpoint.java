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

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Utils;
import io.symcpe.hendrix.api.dao.RulesManager;
import io.symcpe.hendrix.api.dao.TenantManager;
import io.symcpe.hendrix.api.security.ACLConstants;
import io.symcpe.hendrix.api.storage.Rules;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.rules.validator.ValidationException;

/**
 * REST endpoint for {@link Rule} CRUD operations
 * 
 * @author ambud_sharma
 */
@Path("/rules")
public class RulesEndpoint {

	private static final String RULE_ID = "ruleId";
	private static String BUILD_NUMBER;
	private static String VERSION;
	private ApplicationManager am;

	public RulesEndpoint(ApplicationManager am) {
		this.am = am;
	}

	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public String getVersion() {
		// try {
		// if (BUILD_NUMBER == null) {
		// BUILD_NUMBER = Manifests.read("buildNumber");
		// }
		// if (VERSION == null) {
		// VERSION = Manifests.read("version");
		// }
		// } catch (Exception e) {
		// }
		return "Version:" + VERSION + " Build:" + BUILD_NUMBER;
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	public String listRules(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@DefaultValue("false") @QueryParam("pretty") boolean pretty,
			@DefaultValue("0") @QueryParam("filter") int filter) {
		EntityManager em = am.getEM();
		try {
			return RulesManager.getInstance().getRuleContents(em, tenantId, pretty, filter);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error fetching rules:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@POST
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	public short createRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		RulesManager mgr = RulesManager.getInstance();
		Tenant tenant;
		EntityManager em = am.getEM();
		try {
			tenant = mgr.getTenant(em, tenantId);
		} catch (Exception e) {
			em.close();
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Tenant not found").build());
		}
		try {
			return mgr.createNewRule(em, new Rules(), tenant);
		} catch (Exception e) {
			throw new InternalServerErrorException();
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + RULE_ID + "}")
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	public short putRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE, message = "Tenant ID must be under 100 characters") String tenantId,
			@NotNull(message = "Rule ID can't be empty") @PathParam(RULE_ID) short ruleId,
			@HeaderParam("Accept-Charset") @DefaultValue("utf-8") String encoding,
			@NotNull(message = "Rule JSON can't be empty") @Encoded String ruleJson) {
		EntityManager em = am.getEM();
		if (ruleJson != null && ruleJson.length() > Rules.MAX_RULE_LENGTH) {
			throw new BadRequestException(Response.status(Status.BAD_REQUEST).entity("Rule is too big").build());
		}

		if (!Utils.isCharsetMisInterpreted(ruleJson, encoding)) {
			throw new BadRequestException(
					Response.status(Status.BAD_REQUEST).entity("Rule JSON must be UTF-8 compliant").build());
		}

		RulesManager mgr = RulesManager.getInstance();
		Tenant tenant;
		try {
			tenant = mgr.getTenant(em, tenantId);
		} catch (Exception e) {
			em.close();
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Tenant not found").build());
		}
		SimpleRule rule = null;
		try {
			rule = RuleSerializer.deserializeJSONStringToRule(ruleJson);
			if (rule == null) {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST).entity("Unable to parse rule").build());
			}
			if (rule.getRuleId() != ruleId) {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST).entity("Rule id in path doesn't match the rule").build());
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
						.entity("Expecting a singel rule object not an array").build());
			} else {
				throw new BadRequestException(
						Response.status(Status.BAD_REQUEST).entity(e.getLocalizedMessage()).build());
			}
		}
		try {
			Rules ruleContainer = new Rules();
			if (rule.getRuleId() > 0) {
				try {
					Rules temp = mgr.getRule(em, tenant.getTenantId(), rule.getRuleId());
					if (temp != null) {
						ruleContainer = temp;
					}
				} catch (NoResultException e) {
					// rule doesn't exit, will save it as a new rule
				}
			}
			return mgr.saveRule(em, ruleContainer, tenant, rule, am);
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

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + RULE_ID + "}/enable")
	@PUT
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	public String enableRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(RULE_ID) short ruleId) {
		EntityManager em = am.getEM();
		try {
			return RulesManager.getInstance().enableDisableRule(em, true, tenantId, ruleId, am).getRuleContent();
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error enabling rule:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + RULE_ID + "}/disable")
	@PUT
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	public String disableRule(@NotNull @PathParam(TenantEndpoint.TENANT_ID) String tenantId,
			@NotNull @PathParam(RULE_ID) short ruleId) {
		EntityManager em = am.getEM();
		try {
			return RulesManager.getInstance().enableDisableRule(em, false, tenantId, ruleId, am).getRuleContent();
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error disabling rule:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + RULE_ID + "}")
	@GET
	@Produces({ MediaType.TEXT_HTML })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE,
			ACLConstants.READER_ROLE })
	public String getRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(RULE_ID) short ruleId, @DefaultValue("false") @QueryParam("pretty") boolean pretty) {
		Rules rule = null;
		EntityManager em = am.getEM();
		try {
			rule = RulesManager.getInstance().getRule(em, tenantId, ruleId);
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
			} else {
				throw new BadRequestException();
			}
		} finally {
			em.close();
		}
		if (rule.getRuleContent() != null) {
			if (pretty) {
				return Utils.getPrettyRuleJson(rule.getRuleContent());
			} else {
				return rule.getRuleContent();
			}
		} else {
			return RuleSerializer.serializeRuleToJSONString(
					new SimpleRule(rule.getRuleId(), "", false, null, new Action[] {}), pretty);
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/{" + RULE_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE, ACLConstants.OPERATOR_ROLE })
	public void deleteRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId,
			@NotNull @PathParam(RULE_ID) short ruleId) {
		EntityManager em = am.getEM();
		try {
			RulesManager.getInstance().deleteRule(em, tenantId, ruleId, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error deleting rule:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE })
	public void deleteAllRules(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			Tenant tenant = TenantManager.getInstance().getTenant(em, tenantId);
			RulesManager.getInstance().deleteRules(em, tenant, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error deleting all rules for tenant:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

	@Path("/{" + TenantEndpoint.TENANT_ID + "}/disable")
	@PUT
	@Produces({ MediaType.APPLICATION_JSON })
	@RolesAllowed({ ACLConstants.SUPER_ADMIN_ROLE, ACLConstants.ADMIN_ROLE })
	public void disableAllRule(
			@NotNull @PathParam(TenantEndpoint.TENANT_ID) @Size(min = 1, max = Tenant.TENANT_ID_MAX_SIZE) String tenantId) {
		EntityManager em = am.getEM();
		try {
			RulesManager.getInstance().disableAllRules(em, tenantId, am);
		} catch (NoResultException e) {
			throw new NotFoundException(Response.status(Status.NOT_FOUND).entity("Entity not found").build());
		} catch (Exception e) {
			throw new InternalServerErrorException(
					Response.serverError().entity("Error disabling all rules for tenant:" + e.getMessage()).build());
		} finally {
			em.close();
		}
	}

}