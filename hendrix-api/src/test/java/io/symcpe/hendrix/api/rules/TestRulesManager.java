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
package io.symcpe.hendrix.api.rules;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.*;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.DerbyUtil;

@RunWith(MockitoJUnitRunner.class)
public class TestRulesManager {

	private static final String CONNECTION_STRING = "jdbc:derby:target/rules.db;create=true";
	private static final String CONNECTION_NC_STRING = "jdbc:derby:target/rules.db;";
	private static final String TARGET_RULES_DB = "target/rules.db";
	private static EntityManagerFactory emf;
	private EntityManager em;
	@Mock
	private ApplicationManager am;

	static {
		System.setProperty("derby.stream.error.field", DerbyUtil.class.getCanonicalName() + ".DEV_NULL");
	}

	public TestRulesManager() {
	}

	@BeforeClass
	public static void beforeClass() throws IOException {
		Properties config = new Properties(System.getProperties());
		File db = new File(TARGET_RULES_DB);
		if (db.exists()) {
			FileUtils.deleteDirectory(db);
		}
		config.setProperty("javax.persistence.jdbc.url", CONNECTION_STRING);
		try{
			emf = Persistence.createEntityManagerFactory("hendrix", config);
		}catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	@Before
	public void before() {
		em = emf.createEntityManager();
		when(am.getEM()).thenReturn(em);
	}
	
	@Test
	public void testGetAll() {
		
	}

}
