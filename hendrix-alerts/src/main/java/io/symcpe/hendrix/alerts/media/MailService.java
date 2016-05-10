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
package io.symcpe.hendrix.alerts.media;

import java.security.Security;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.sun.mail.smtp.SMTPTransport;

import io.symcpe.wraith.actions.alerts.Alert;

/**
 * Provides methods for send emails.
 */
public class MailService {
	
	private static final String ALERT_MAIL_USE_SSL = "alert.mail.use.ssl";
	private static final String ALERT_MAIL_USE_AUTH = "alert.mail.smtps.auth";
	private static final String ALERT_MAIL_HOST = "alert.mail.smtps.host";
	private static final String ALERT_MAIL_PORT = "alert.mail.smtp.port";
	private static final String ALERT_MAIL_USER = "alert.mail.smtps.auth.user";
	private static final String ALERT_MAIL_PASS = "alert.mail.smtps.auth.pass";
	private static final String ALERT_MAIL_SMTP_SOCKET_FACTORY_CLASS = "alert.mail.smtp.socketFactory.class";
	private static final String ALERT_MAIL_SOCKET_FACTORY_PORT = "alert.mail.smtp.socketFactory.port";
	private String host;
	private String username;
	private String password;
	private Session session;
	private SMTPTransport transport;
	private int port;
	private Logger logger;

	/**
	 * Creates service according provided storm topology config.
	 */
	public MailService(Map<String, String> conf) {
		logger = Logger.getLogger(MailService.class);
		boolean useAuth = Boolean.valueOf((String) conf.get(ALERT_MAIL_USE_AUTH));
		boolean useSsl = Boolean.valueOf((String) conf.get(ALERT_MAIL_USE_SSL));

		username = useAuth ? (String) conf.get(ALERT_MAIL_USER) : StringUtils.EMPTY;
		password = useAuth ? (String) conf.get(ALERT_MAIL_PASS) : StringUtils.EMPTY;

		host = (String) conf.get(ALERT_MAIL_HOST);
		port = Integer.valueOf((String) conf.get(ALERT_MAIL_PORT));

		session = createSession(useAuth, useSsl, host, port, username, password, conf);

		String transportString = useSsl ? "smtps" : "smtp";
		try {
			transport = (SMTPTransport) session.getTransport(transportString);
		} catch (NoSuchProviderException e) {
			String message = "Can't initialise Mail bolt. Can't make transport for " + transportString;
			logger.fatal(message, e);
			throw new IllegalArgumentException(message);
		}
	}

	private void setMessage(MimeMessage msg, Alert alert) {
		try {
			msg.setFrom(new InternetAddress("LMM@symantec.com"));
			// send only to non-null recipients
			if (alert.getTarget() != null) {
				msg.setRecipients(Message.RecipientType.TO, alert.getTarget());
				msg.setSubject(alert.getSubject());
				msg.setContent(alert.getBody(), "text/html");
				msg.setSentDate(new Date(alert.getTimestamp()));
			}
		} catch (MessagingException e) {
			logger.error("Error when trying to create new e-mail.", e);
		}
	}

	/**
	 * Sends message.
	 *
	 * @return true if message sends correctly
	 */
	public boolean sendMail(Alert alert) {
		final MimeMessage msg = new MimeMessage(session);
		if (logger.isDebugEnabled()) {
			logger.debug("MAIL BOLT: " + alert);
		}
		try {
			setMessage(msg, alert);
			transport.connect(host, port, username, password);
			transport.sendMessage(msg, msg.getAllRecipients());
			return true;
		} catch (MessagingException e) {
			logger.error("Error when trying to send e-mail via " + host + ":" + port + ". Tenant ID: "
					+ alert.getRuleGroup(), e);
			return false;
		}
	}

	@SuppressWarnings("restriction")
	private Session createSession(boolean useAuth, boolean useSsl, String host, int port, String username,
			String password, Map<String, String> conf) {
		Properties properties = new Properties();
		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);

		properties.put("mail.smtp.auth", useAuth);
		if (useAuth) {
			if (useSsl) {
				properties.put("mail.smtps.auth.user", username);
				properties.put("mail.smtps.auth.pass", password);
			} else {
				properties.put("mail.smtp.user", username);
				properties.put("mail.smtp.password", password);
			}
		}

		properties.put("mail.smtp.starttls.enable", useSsl);
		if (useSsl) {
			String socketFactoryClass = (String) conf.get(ALERT_MAIL_SMTP_SOCKET_FACTORY_CLASS);
			boolean socketFactoryFallback = Boolean
					.valueOf((String) conf.get(ALERT_MAIL_SMTP_SOCKET_FACTORY_CLASS));
			int socketFactoryPort = Integer.valueOf((String) conf.get(ALERT_MAIL_SOCKET_FACTORY_PORT));

			Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
			properties.put("mail.smtp.socketFactory.class", socketFactoryClass);
			properties.put("mail.smtp.socketFactory.fallback", socketFactoryFallback);
			properties.put("mail.smtp.socketFactory.port", socketFactoryPort);
		}

		Session session = Session.getInstance(properties);

		return session;
	}

	/**
	 * Closes email transport.
	 */
	public void close() {
		try {
			transport.close();
		} catch (MessagingException e) {
			logger.error("Error when trying to close connection to smtp server.", e);
		}
	}
}
