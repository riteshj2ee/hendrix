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
package io.symcpe.wraith.rules;

import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.Action;

/**
 * An wireframe for caller of RulesEngine so that the RulesEngine can notify of
 * activities to the caller in an event driven manner via these callbacks.
 * 
 * @author ambud_sharma
 */
public interface RulesEngineCaller<K, C> {

	/**
	 * Handle emission of an {@link Action} error which happens when an action
	 * can't be applied
	 * 
	 * @param eventCollector
	 * @param eventContainer
	 * @param actionErrorEvent
	 */
	public void emitActionErrorEvent(C eventCollector, K eventContainer, Event actionErrorEvent);

	/**
	 * Handle alert {@link Action}s
	 * 
	 * @param eventCollector
	 * @param eventContainer
	 * @param outputEvent
	 * @param ruleId
	 * @param actionId
	 * @param target
	 * @param mediaType
	 */
	public void emitAlert(C eventCollector, K eventContainer, Event outputEvent, short ruleId, short actionId, String target, String mediaType);
	
	/**
	 * Handle if rule doesn't match for an event
	 * 
	 * @param eventCollector
	 * @param eventContainer
	 * @param inputEvent
	 * @param rule
	 */
	public void handleRuleNoMatch(C eventCollector, K eventContainer, Event inputEvent, Rule rule);

	/**
	 * Report if the there's a match for ruleId. <br>
	 * <br>
	 * To be used for performance statistics.
	 * 
	 * @param ruleId
	 */
	public void reportRuleHit(Short ruleId);

	/**
	 * Report time taken to execute the supplied rule id <br>
	 * <br>
	 * To be used for performance statistics.
	 * 
	 * @param ruleId
	 * @param executeTime
	 */
	public void reportRuleEfficiency(Short ruleId, long executeTime);

	/**
	 * Report time taken to execute the condition for the supplied rule id <br>
	 * <br>
	 * To be used for performance statistics.
	 * 
	 * @param ruleId
	 * @param executeTime
	 */
	public void reportConditionEfficiency(Short ruleId, long executeTime);

}
