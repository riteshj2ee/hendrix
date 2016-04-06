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

import io.symcpe.wraith.Event;
import io.symcpe.wraith.Required;

/**
 * A partial condition that checks if the supplied header exists, if yes then delegates
 * the logic downstream.
 * 
 * @author ambud_sharma
 */
public abstract class AbstractSimpleCondition implements Condition {
	
	private static final long serialVersionUID = 1L;
	@Required
	private String headerKey;

	public AbstractSimpleCondition(String headerKey) {
		this.headerKey = headerKey;
	}

	@Override
	public final boolean matches(Event event) {
		Object value = event.getHeaders().get(headerKey);
		if(value!=null) {
			return satisfiesCondition(value);
		}
		return false;
	}
	
	public abstract boolean satisfiesCondition(Object value);

	/**
	 * @return header key
	 */
	public String getHeaderKey() {
		return headerKey;
	}

	/**
	 * @param headerKey the headerKey to set
	 */
	public void setHeaderKey(String headerKey) {
		this.headerKey = headerKey;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "AbstractSimpleCondition [headerKey=" + headerKey + "]";
	}

}
