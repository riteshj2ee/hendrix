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
package io.symcpe.wraith;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.symcpe.wraith.Event;
import io.symcpe.wraith.EventFactory;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.store.RulesStore;
import io.symcpe.wraith.store.StoreFactory;
import io.symcpe.wraith.store.TemplateStore;

/**
 * TestFactory implementing Event and Store Factory
 * 
 * @author ambud_sharma
 */
public class TestFactory implements EventFactory, StoreFactory {

	public static final String RULES_CONTENT = "rules.content";

	@Override
	public Event buildEvent() {
		return new TestEvent();
	}

	@Override
	public RulesStore getRulesStore(String type, Map<String, String> conf) throws Exception {
		TestRulesStore store = new TestRulesStore();
		store.initialize(conf);
		return store;
	}
	
	public static class TestRulesStore implements RulesStore {
		
		private Map<Short, Rule> rules;
		private Map<String, Map<Short, Rule>> ruleGroups;
		
		public TestRulesStore() {
			rules = new HashMap<>();
			ruleGroups = new HashMap<>();
		}
		
		@Override
		public void initialize(Map<String, String> conf) {
			String rulesJson = conf.get(TestFactory.RULES_CONTENT);
			if(rulesJson!=null) {
				Rule[] ruleList = RuleSerializer.deserializeJSONStringToRules(rulesJson);
				for (Rule rule : ruleList) {
					rules.put(rule.getRuleId(), rule);
				}
			}
		}

		@Override
		public void connect() throws IOException {
		}

		@Override
		public void disconnect() throws IOException {
		}

		@Override
		public Map<Short, Rule> listRules() throws IOException {
			return rules;
		}

		@Override
		public Map<String, Map<Short, Rule>> listGroupedRules() throws IOException {
			return ruleGroups;
		}
		
	}

	@Override
	public TemplateStore getTemplateStoreStore(String type, Map<String, String> conf) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
