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
package io.symcpe.wraith.actions.aggregations;

import java.util.Map;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.Action;

/**
 * Aggregation Action defines abstractly aggregation capabilities in the system
 * 
 * @author ambud_sharma
 */
public abstract class AggregationAction implements Action {
	
	private static final long serialVersionUID = 1L;
	private short actionId;
	private String aggregationHeaderKey;
	private String aggregationHeaderValueKey;
	private int aggregationWindow;
	
	public AggregationAction(short actionId, String aggregationHeaderKey, String aggregationHeaderValueKey, int aggregationWindow) {
		this.actionId = actionId;
		this.aggregationHeaderKey = aggregationHeaderKey;
		this.aggregationHeaderValueKey = aggregationHeaderValueKey;
		this.aggregationWindow = aggregationWindow;
	}
	
	@Override
	public Event actOnEvent(Event inputEvent) {
		Map<String, Object> headers = inputEvent.getHeaders();
		Object aggregationKey = headers.get(getAggregationHeaderKey());
		Object aggregationValue = headers.get(getAggregationHeaderValueKey());
		if(aggregationKey==null || aggregationValue==null) {
			return null;
		}
		headers.put(Constants.FIELD_AGGREGATION_KEY, aggregationKey.toString());
		headers.put(Constants.FIELD_AGGREGATION_VALUE, aggregationValue);
		postProcessEvent(inputEvent);
		return inputEvent;
	}

	public abstract void postProcessEvent(Event inputEvent);

	@Override
	public ACTION_TYPE getActionType() {
		return ACTION_TYPE.AGGREGATION;
	}
	
	public String getAggregationHeaderKey() {
		return aggregationHeaderKey;
	}

	/**
	 * @return the aggregationHeaderValueKey
	 */
	public String getAggregationHeaderValueKey() {
		return aggregationHeaderValueKey;
	}
	
	@Override
	public short getActionId() {
		return actionId;
	}

	/**
	 * @return the aggregationWindow
	 */
	public int getAggregationWindow() {
		return aggregationWindow;
	}

	@Override
	public void setActionId(short actionId) {
		this.actionId = actionId;
	}
}
