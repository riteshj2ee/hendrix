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

import java.io.Serializable;
import java.util.Map;

/**
 * Blueprint of an event and is a derived version of an Event from Apache Flume.
 * <br><br>
 * Each event consists of a set of headers, represented as a {@link Map} or {@link String} keys and
 * {@link Object} values accompanied by a raw {@link Byte} array body.
 * 
 * @author ambud_sharma
 */
public interface Event extends Serializable {
	
	public static final String HEADER_STORE_EVENT = "store";
	public static final String HEADER_EVENT_TYPE = "t";
	public static final String HEADER_EVENT_ERROR_TYPE = "e.t";
	public static final String HEADER_EVENT_ERROR_FIELD = "e.f";
	public static final String HEADER_EVENT_ERROR_VALUE = "e.v";
	public static final String ERROR_EVENT_TYPE = "err";

	public Map<String, Object> getHeaders();
	
	public void setHeaders(Map<String, Object> headers);
	
	public byte[] getBody();
	
	public void setBody(byte[] body);
	
}
