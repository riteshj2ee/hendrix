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
package io.symcpe.wraith.conditions;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.symcpe.wraith.Required;
import io.symcpe.wraith.Utils;
import io.symcpe.wraith.conditions.logical.AndCondition;
import io.symcpe.wraith.conditions.logical.OrCondition;
import io.symcpe.wraith.conditions.relational.BricsRegexCondition;
import io.symcpe.wraith.conditions.relational.EqualsCondition;
import io.symcpe.wraith.conditions.relational.JavaRegexCondition;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;

/**
 * Gson Type adapter for {@link Condition} to serialize and deserialize
 * conditions.
 * 
 * @author ambud_sharma
 */
public class ConditionSerializer implements JsonSerializer<Condition>, JsonDeserializer<Condition> {

	public static final String TYPE = "type";
	public static final String PROPS = "props";

	public static void main(String[] args) {
		Condition one = new OrCondition(Arrays.asList((Condition) new EqualsCondition("header1", "val1"),
				(Condition) new JavaRegexCondition("header", "\\d+")));
		Condition two = new EqualsCondition("header2", (Number)Double.valueOf("2"));
		Condition condition = new AndCondition(Arrays.asList(one, two));
		GsonBuilder gsonBilder = new GsonBuilder();
		gsonBilder.registerTypeAdapter(Condition.class, new ConditionSerializer());
		Gson gson = gsonBilder.setPrettyPrinting().create();

		condition = gson.fromJson(gson.toJson(condition, Condition.class), Condition.class);
		
		System.out.println(((EqualsCondition)((AndCondition)condition).getConditions().get(1)).getValue().getClass());
		
		String val = "{\"condition\":{\"type\":\"EQUALS\",\"props\":{\"value\":5,\"headerKey\":\"severity_int\"}},\"actions\":[{\"type\":\"ALERT\",\"props\":{\"actionId\":0,\"target\":\"test_email_ITC-RE-Validation-A.1.2@gmail.com\",\"media\":\"mail\",\"body\":\"This is a rule of type ITC-RE-Validation-A.1.2\"}}],\"ruleId\":20,\"name\":\"ITC-RE-Validation-A.1.2. mrfeocinygimauqge.\",\"active\":true,\"description\":\"This is a rule of type A.1. Rule is firing when the condition is found\"}";
		SimpleRule rule = RuleSerializer.deserializeJSONStringToRule(val);
		System.out.println(((EqualsCondition)rule.getCondition()).getValue().getClass());
	}

	public Condition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		JsonObject jsonObject = json.getAsJsonObject();
		if(jsonObject.entrySet().isEmpty()) {
			throw new JsonParseException("Empty conditions are not allowed");
		}
		String type = jsonObject.get(TYPE).getAsString();
		if (Utils.CLASSNAME_REVERSE_MAP.containsKey(type)) {
			type = Utils.CLASSNAME_REVERSE_MAP.get(type);
		}
		JsonElement element = jsonObject.get(PROPS);
		try {
			Condition pojo = context.deserialize(element, Class.forName(type));
			if(pojo instanceof BricsRegexCondition) {
				BricsRegexCondition regex = ((BricsRegexCondition)pojo);
				regex.setRegex(regex.getRegex());
			}
			if(pojo instanceof JavaRegexCondition) {
				JavaRegexCondition regex = ((JavaRegexCondition)pojo);
				regex.setValue(regex.getValue());
			}
			List<Field> fields = new ArrayList<>();
			Utils.addDeclaredAndInheritedFields(Class.forName(type), fields);
			for (Field f : fields) {
				if (f.getAnnotation(Required.class) != null) {
					try {
						f.setAccessible(true);
						if (f.get(pojo) == null) {
							throw new JsonParseException("Missing required field in condition: " + f.getName());
						}
					} catch (IllegalArgumentException | IllegalAccessException ex) {
					}
				}
			}
			return pojo;
		} catch (ClassNotFoundException cnfe) {
			throw new JsonParseException("Unknown condition type: " + type, cnfe);
		}
	}

	public JsonElement serialize(Condition src, Type typeOfSrc, JsonSerializationContext context) {
		JsonObject result = new JsonObject();
		String type = src.getClass().getCanonicalName();
		if (Utils.CLASSNAME_FORWARD_MAP.containsKey(src.getClass().getCanonicalName())) {
			type = Utils.CLASSNAME_FORWARD_MAP.get(src.getClass().getCanonicalName());
		}
		result.add(TYPE, new JsonPrimitive(type));
		result.add(PROPS, context.serialize(src, src.getClass()));
		return result;
	}

}