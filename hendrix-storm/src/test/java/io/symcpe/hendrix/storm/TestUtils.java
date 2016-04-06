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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.AlertAction;
import io.symcpe.wraith.conditions.relational.EqualsCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleCommand;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;

/**
 * Helper utils for reading configs and events
 * 
 * @author ambud_sharma
 */
public class TestUtils {

	private TestUtils() {
	}

	public static List<String> linesFromFiles(String fileName) throws IOException {
		List<String> lines = new ArrayList<>();
		File eventFile = new File(fileName);
		BufferedReader reader = new BufferedReader(new FileReader(eventFile));
		String temp = null;
		while ((temp = reader.readLine()) != null) {
			lines.add(temp);
		}
		reader.close();
		return lines;
	}

	@SuppressWarnings("unchecked")
	public static Event stringToEvent(String eventJson) {
		Event event = new UnifiedFactory().buildEvent();
		Gson gson = new Gson();
		event.getHeaders()
				.putAll((Map<String, Object>) gson.fromJson(eventJson, new TypeToken<HashMap<String, Object>>() {
				}.getType()));
		return event;
	}

	public static void main(String[] args) {
		List<Rule> rules = new ArrayList<>();
		
		SimpleRule rule = new SimpleRule((short)1, "rule1", true, new EqualsCondition("tenant_id", "e8eb4bb008904d7eba86c44dd33646ed"), new Action[]{ new AlertAction((short)0, "simpleemail@symantec.com", "email", "test $host") });
		rules.add(rule);
		rule = new SimpleRule((short)2, "rule2", true, new EqualsCondition("tenant_id", "e8eb4bb008904d7eba86c44dd33646ed"), new Action[]{ new AlertAction((short)0, "simpleemail@symantec.com", "email", "test $host") });
		rules.add(rule);
		
		RuleCommand cmd = new RuleCommand();
		cmd.setRuleGroup("e8eb4bb008904d7eba86c44dd33646ed");
		cmd.setRuleContent(RuleSerializer.serializeRulesToJSONString(rules, false));
		
		System.out.println(new Gson().toJson(cmd));
	}
	
}