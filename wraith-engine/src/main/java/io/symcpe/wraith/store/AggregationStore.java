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
package io.symcpe.wraith.store;

import java.io.IOException;
import java.util.Set;

import com.clearspring.analytics.stream.cardinality.ICardinality;

import io.symcpe.wraith.aggregations.Aggregator;

/**
 * An idempotent read store to be used for Wraith aggregation operations such that
 * when values are committed to the system, they are stored such that when read they need to
 * be idempotent as in duplicates are ignored.
 * <br><br>
 * E.g. Counting aggregation of a field should not be done such that recounting the same value in
 * the event of a re-play is not possible. Use structures like Sets, BloomFilters etc to accomplish this.
 * 
 * @author ambud_sharma
 */
public interface AggregationStore extends Store {
	
	/**
	 * Add a count value for an entity to a timeseries
	 * 
	 * @param timestamp
	 * @param entity
	 * @param count
	 * @throws IOException
	 */
	public void putValue(long timestamp, String entity, long count) throws IOException;
	
	/**
	 * Add a count value for an entity to a timeseries
	 * 
	 * @param timestamp
	 * @param entity
	 * @param count
	 * @throws IOException
	 */
	public void putValue(long timestamp, String entity, int count) throws IOException;

	/**
	 * Persist entity and aggregator
	 * 
	 * @param entity
	 * @param aggregator
	 * @throws IOException
	 */
	public void persist(String entity, Aggregator aggregator) throws IOException;
	
	/**
	 * Put set of value to an existing set for an entity
	 * @param entity
	 * @param values
	 * @throws IOException
	 */
	public void mergeSetValues(String entity, Set<Object> values) throws IOException;
	
	/**
	 * Put set of value to an existing set for an entity
	 * 
	 * @param entity
	 * @param values
	 * @throws IOException
	 */
	public void mergeSetIntValues(String entity, Set<Integer> values) throws IOException;
	
	/**
	 * Put byte array value to dataset
	 * 
	 * @param entity
	 * @param value
	 * @throws IOException
	 */
	public void putValue(String entity, ICardinality value) throws IOException;
	
}