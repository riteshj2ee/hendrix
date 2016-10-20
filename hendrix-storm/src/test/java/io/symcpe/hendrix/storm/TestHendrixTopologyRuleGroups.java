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
package io.symcpe.hendrix.storm;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.apache.storm.flux.FluxBuilder;
import org.apache.storm.flux.model.ExecutionContext;
import org.apache.storm.flux.model.TopologyDef;
import org.apache.storm.flux.parser.FluxParser;
import org.junit.Before;
import org.junit.Test;

import backtype.storm.Config;

/**
 * Tests for topology
 * 
 * @author ambud_sharma
 */
public class TestHendrixTopologyRuleGroups {

	private Properties properties = new Properties();

	@Before
	public void before() throws IOException {
		properties.clear();
	}

	@Test
	public void testFluxTopology() throws IOException, IllegalAccessException, InstantiationException,
			ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
		String yaml = new File("").getAbsoluteFile().getParentFile().getAbsolutePath()+"/install/conf/remote/rules.yml";
		TopologyDef topologyDef = FluxParser.parseFile(yaml, true, true, null, true);
		assertNotNull(topologyDef);
		Config conf = FluxBuilder.buildConfig(topologyDef);
		ExecutionContext context = new ExecutionContext(topologyDef, conf);
		FluxBuilder.buildTopology(context);
	}
	
//	@Test
	public void testFluxTopologyLocal() throws IOException, IllegalAccessException, InstantiationException,
			ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
		String yaml = new File("").getAbsoluteFile().getParentFile().getAbsolutePath()+"/install/conf/remote/rules-single-node.yml";
		TopologyDef topologyDef = FluxParser.parseFile(yaml, true, true, null, true);
		assertNotNull(topologyDef);
		Config conf = FluxBuilder.buildConfig(topologyDef);
		ExecutionContext context = new ExecutionContext(topologyDef, conf);
		FluxBuilder.buildTopology(context);
	}
}