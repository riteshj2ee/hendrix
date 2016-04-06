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

/**
 * @author ambud_sharma
 */
public abstract class Constants {
	
	public static final String KEY_SEPARATOR = "_";
	public static final String FIELD_TIMESTAMP = "_t";
	public static final String FIELD_EVENT_ID = "_i";
	public static final String FIELD_RULE_ID = "_ri";
	public static final String FIELD_ACTION_ID = "_ai";
	public static final String ERROR_STREAM_ID = "st_err";
	public static final String RSTORE_TYPE = "rstore.type";
	public static final String RSTORE_PASSWORD = "rstore.password";
	public static final String RSTORE_USERNAME = "rstore.username";
	public static final String RULE_HASH_INIT_SIZE = "rule.hash.init.size";
	public static final String DEFAULT_RULE_HASH_SIZE = "1000";
	public static final String FIELD_RULE_CONTENT = "rul";
	public static final String FIELD_EVENT = "e";
	public static final String ACTION_FAIL = "act_fail";
	
	public static final String FIELD_ALERT_TARGET = "target";
	public static final String FIELD_ALERT_MEDIA = "media";
	public static final String FIELD_ALERT_BODY = "body";
	public static final String FIELD_ALERT = "alert";
	public static final String RULE_GROUP_ACTIVE = "rule.group.active";
	public static final String FIELD_RULE_GROUP = "_rg";
	public static final String FALSE = "false";
	public static final String TRUE = "true";
	
}
