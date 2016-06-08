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
package io.symcpe.hendrix.ui.alerts;

import java.io.Serializable;
import java.util.Map;
import java.util.Queue;

/**
 * Proxy to backend for alert receiver functionality
 * 
 * @author ambud_sharma
 */
public class AlertReceiver implements Serializable {

	private static final long serialVersionUID = 1L;
	private static AlertReceiver instance = new AlertReceiver();

	private AlertReceiver() {
	}

	public static AlertReceiver getInstance() {
		return instance;
	}

	public void addChannel(Short ruleId) {
		if (ruleId != null) {
		}
	}

	public boolean publishEvent(short ruleId, Map<String, Object> event) {
		return false;
	}

	/**
	 * @param ruleId
	 * @return
	 */
	public Queue<Map<String, Object>> getChannel(short ruleId) {
		return null;
	}

	/**
	 * @param ruleId
	 */
	public void closeChannel(short ruleId) {
		
	}

}