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
package io.symcpe.hendrix.api.dao;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.ws.rs.NotFoundException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.google.gson.Gson;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.Queries;
import io.symcpe.hendrix.api.storage.AlertTemplates;
import io.symcpe.hendrix.api.storage.Tenant;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.actions.alerts.templated.TemplateCommand;
import io.symcpe.wraith.rules.validator.AlertTemplateValidator;

/**
 * @author ambud_sharma
 */
public class TemplateManager {

	private static final String HENDRIX_TEMPLATE_UPDATES_TXT = "~/hendrix/template-updates.txt";
	private static final Logger logger = Logger.getLogger(TemplateManager.class.getName());
	private static final String PARAM_TEMPLATE_ID = "templateId";
	private static final String PARAM_TENANT_ID = "tenantId";
	private static TemplateManager TEMPLATE_MANAGER = new TemplateManager();

	private TemplateManager() {
	}

	public static TemplateManager getInstance() {
		return TEMPLATE_MANAGER;
	}

	public String getTemplateContents(EntityManager em, String tenantId, boolean pretty) throws Exception {
		List<AlertTemplate> templates = new ArrayList<>();
		try {
			List<AlertTemplates> results = getTemplates(em, tenantId);
			for (AlertTemplates template : results) {
				if (template.getTemplateContent() != null) {
					templates.add(AlertTemplateSerializer.deserialize(template.getTemplateContent()));
				} else {
					templates.add(new AlertTemplate(template.getTemplateId(), "", "", "", "", ""));
				}
			}
			return AlertTemplateSerializer.serialize(templates, pretty);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load template contents for tenant:" + tenantId, e);
			throw e;
		}
	}

	/**
	 * @param em
	 * @param templateId
	 * @return
	 */
	public AlertTemplates getTemplate(EntityManager em, short templateId) throws Exception {
		try {
			AlertTemplates result = em.createNamedQuery(Queries.TEMPLATE_FIND_BY_ID, AlertTemplates.class)
					.setParameter("templateId", templateId).getSingleResult();
			return result;
		} catch (Exception e) {
			throw new NoResultException("Template:" + templateId + " not found");
		}
	}

	/**
	 * @param em
	 * @param tenantId
	 * @param templateId
	 * @return
	 */
	public AlertTemplates getTemplate(EntityManager em, String tenantId, short templateId) {
		try {
			AlertTemplates result = em.createNamedQuery(Queries.TEMPLATE_FIND_BY_ID, AlertTemplates.class)
					.setParameter("templateId", templateId).getSingleResult();
			return result;
		} catch (Exception e) {
			throw new NoResultException("Template:" + templateId + " not found");
		}
	}

	/**
	 * @param em
	 * @param tenantId
	 * @return
	 */
	public List<AlertTemplates> getTemplates(EntityManager em, String tenantId) {
		try {
			List<AlertTemplates> result = em.createNamedQuery(Queries.TEMPLATE_FIND_BY_TENANT_ID, AlertTemplates.class)
					.setParameter(PARAM_TENANT_ID, tenantId).getResultList();
			return result;
		} catch (Exception e) {
			throw new NoResultException("Templates for:" + tenantId + " not found");
		}
	}

	/**
	 * @param em
	 * @param templates
	 * @param tenant
	 * @return
	 * @throws Exception
	 */
	public short createNewTemplate(EntityManager em, AlertTemplates templates, Tenant tenant) throws Exception {
		if (templates == null) {
			return -1;
		}
		EntityTransaction t = em.getTransaction();
		try {
			t.begin();
			if (tenant == null) {
				logger.severe("Template group is null");
				return -1;
			} else {
				templates.setTenant(tenant);
			}
			em.persist(templates);
			em.flush();
			t.commit();
			logger.info("Created new template with template id:" + templates.getTemplateId());
			return templates.getTemplateId();
		} catch (Exception e) {
			if (t.isActive()) {
				t.rollback();
			}
			logger.log(Level.SEVERE, "Failed to create a new template", e);
			throw e;
		}
	}

	/**
	 * @param em
	 * @param templates
	 * @param tenant
	 * @param currTemplate
	 * @return
	 * @throws Exception
	 */
	public short saveTemplate(EntityManager em, AlertTemplates templates, Tenant tenant, AlertTemplate currTemplate,
			ApplicationManager am) throws Exception {
		if (currTemplate == null || templates == null || tenant == null) {
			logger.info("Template was null can't save");
			return -1;
		}
		AlertTemplateValidator validator = new AlertTemplateValidator();
		validator.validate(currTemplate);
		logger.info("Template is valid attempting to save");
		try {
			em.getTransaction().begin();
			if (templates.getTenant() == null) {
				templates.setTenant(tenant);
			}
			if (currTemplate.getTemplateId() > 0) {
				templates.setTemplateId(currTemplate.getTemplateId());
			}
			templates = em.merge(templates);
			em.flush();
			em.getTransaction().commit();
			currTemplate.setTemplateId(templates.getTemplateId());
			em.getTransaction().begin();
			templates.setTemplateContent(AlertTemplateSerializer.serialize(currTemplate, false));
			em.merge(templates);
			em.flush();
			logger.info("Template " + templates.getTemplateId() + ":" + templates.getTemplateContent() + " saved");
			// publish template to kafka
			sendTemplateToKafka(false, tenant.getTenant_id(), templates.getTemplateContent(), am);
			em.getTransaction().commit();
			logger.info("Completed Transaction for template " + templates.getTemplateId() + ":"
					+ templates.getTemplateContent() + "");
			return templates.getTemplateId();
		} catch (Exception e) {
			e.printStackTrace();
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			throw e;
		}
	}

	/**
	 * @param em
	 * @param templateId
	 * @return
	 * @throws Exception
	 */
	public AlertTemplate getTemplateObj(EntityManager em, String tenantId, short templateId) throws Exception {
		AlertTemplates template = getTemplate(em, templateId);
		if (template.getTemplateContent() != null) {
			return AlertTemplateSerializer.deserialize(template.getTemplateContent());
		} else {
			return new AlertTemplate(templateId, "", "", "", "", "");
		}
	}

	/**
	 * @param em
	 * @param tenantId
	 * @param templateId
	 * @throws Exception
	 */
	public void deleteTemplate(EntityManager em, String tenantId, short templateId, ApplicationManager am)
			throws Exception {
		EntityTransaction transaction = em.getTransaction();
		TenantManager.getInstance().getTenant(em, tenantId);
		try {
			AlertTemplates template = getTemplate(em, tenantId, templateId);
			if (template == null) {
				throw new NotFoundException();
			}
			List<Short> result = null;
			try {
				result = RulesManager.getInstance().getRuleByTemplateId(em, tenantId, templateId);
			} catch (Exception e) {
			}
			if(result!=null && result.size()>0) {
				throw new Exception("Can't delete template when it has rules referring to it:"+result.toString());
			}
			transaction.begin();
			String templateContent = template.getTemplateContent();
			em.createNamedQuery(Queries.TEMPLATE_DELETE_BY_ID).setParameter(PARAM_TEMPLATE_ID, templateId)
					.executeUpdate();
			if (templateContent != null) {
				sendTemplateToKafka(true, template.getTenant().getTenant_id(), templateContent, am);
			}
			transaction.commit();
			logger.info("Deleted template:" + templateId);
		} catch (Exception e) {
			if (transaction.isActive()) {
				transaction.rollback();
			}
			if (e instanceof NoResultException) {
				logger.log(Level.SEVERE, "Template " + templateId + " not found");
			} else {
				logger.log(Level.SEVERE, "Failed to delete template", e);
			}
			throw e;
		}
	}

	/**
	 * @param em
	 * @param tenant
	 * @throws Exception
	 */
	public void deleteTemplates(EntityManager em, Tenant tenant, ApplicationManager am) throws Exception {
		EntityTransaction transaction = em.getTransaction();
		try {
			transaction.begin();
			List<AlertTemplates> templates = getTemplates(em, tenant.getTenant_id());
			if (templates != null) {
				for (AlertTemplates template : templates) {
					List<Short> result = null;
					try {
						result = RulesManager.getInstance().getRuleByTemplateId(em, tenant.getTenant_id(), template.getTemplateId());
					} catch (Exception e) {
					}
					if(result!=null && result.size()>0) {
						throw new Exception("Can't delete template when it has rules referring to it:"+result.toString());
					}
					em.remove(template);
					if (template.getTemplateContent() != null) {
						sendTemplateToKafka(true, template.getTenant().getTenant_id(), template.getTemplateContent(),
								am);
					}
					logger.info("Deleting template:" + template.getTemplateId() + " for tenant id:" + tenant);
				}
			}
			em.flush();
			transaction.commit();
			logger.info("All templates for tenant:" + tenant);
		} catch (Exception e) {
			if (transaction.isActive()) {
				transaction.rollback();
			}
			if (!(e instanceof NoResultException)) {
				logger.log(Level.SEVERE, "Failed to delete template", e);
			}
			throw e;
		}
	}

	/**
	 * @param delete
	 * @param tenantId
	 * @param template
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public void sendTemplateToKafka(boolean delete, String tenantId, AlertTemplate template, ApplicationManager am)
			throws InterruptedException, ExecutionException, IOException {
		sendTemplateToKafka(delete, tenantId, AlertTemplateSerializer.serialize(template, false), am);
	}

	/**
	 * @param delete
	 * @param tenantId
	 * @param templateJson
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public void sendTemplateToKafka(boolean delete, String tenantId, String templateJson, ApplicationManager am)
			throws InterruptedException, ExecutionException, IOException {
		TemplateCommand cmd = new TemplateCommand(tenantId, delete, templateJson);
		String cmdJson = new Gson().toJson(cmd);
		if (!ApplicationManager.LOCAL) {
			KafkaProducer<String, String> producer = am.getKafkaProducer();
			producer.send(new ProducerRecord<String, String>(am.getTemplateTopicName(), cmdJson)).get();
		} else {
			PrintWriter pr = new PrintWriter(new FileWriter(
					HENDRIX_TEMPLATE_UPDATES_TXT.replaceFirst("^~", System.getProperty("user.home")), true));
			pr.println(cmdJson);
			pr.close();
		}
	}

	/**
	 * @param em
	 * @param tenantId
	 * @return
	 * @throws Exception
	 */
	public Tenant getTenant(EntityManager em, String tenantId) throws Exception {
		try {
			return em.createNamedQuery(Queries.TENANT_FIND_BY_ID, Tenant.class).setParameter(PARAM_TENANT_ID, tenantId)
					.getSingleResult();
		} catch (Exception e) {
			if (e instanceof NoResultException) {
				logger.log(Level.SEVERE, "Tenant id:" + tenantId + " not found");
			} else {
				logger.log(Level.SEVERE, "Failed to get tenant id:" + tenantId, e);
			}
			throw e;
		}
	}

}