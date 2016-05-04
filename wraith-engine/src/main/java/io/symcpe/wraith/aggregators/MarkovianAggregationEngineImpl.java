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
package io.symcpe.wraith.aggregators;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Utils;
import io.symcpe.wraith.aggregations.AggregationRejectException;
import io.symcpe.wraith.aggregations.Aggregator;
import io.symcpe.wraith.aggregations.CountingAggregator;
import io.symcpe.wraith.aggregations.StaleDataException;
import io.symcpe.wraith.store.AggregationStore;

/**
 * {@link MarkovianAggregationEngineImpl} aggregates the value, group values by a key either
 * put them together or count them.<br>
 * <br>
 * 
 * {@link MarkovianAggregationEngineImpl} is also fault tolerant by providing a flush feature
 * that periodically flushes results to a persistent store.<br>
 * <br>
 * 
 * {@link Aggregator}s are asked to be idempotent i.e. calling the same
 * aggregation operation with the same value should not have any effect in a
 * given window of time.
 * 
 * @author ambud_sharma
 */
public class MarkovianAggregationEngineImpl implements MarkovianAggregationEngine {

	private StaleDataException StaleDataException = new StaleDataException();
	private AggregationRejectException AggregationRejectException = new AggregationRejectException();
	private long jitterTolerance;
	private Map<String, Long> lastEmittedBucketMap;
	private SortedMap<String, Aggregator> aggregationMap;
	private SortedMap<String, Aggregator> flushAggregationMap;
	private Aggregator template;
	private AggregationStore store;

	/**
	 * {@link Aggregator} settings can be initialized with supplied
	 * configuration
	 * 
	 * @param conf
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public void initialize(Map<String, String> conf) throws Exception {
		lastEmittedBucketMap = new HashMap<String, Long>();
		aggregationMap = new TreeMap<>();
		flushAggregationMap = new TreeMap<>();
		jitterTolerance = Integer.parseInt(conf.getOrDefault(Constants.AGGREGATION_JITTER_TOLERANCE, "10")) * 1000;
		String agreggatorType = conf.get(Constants.AGGREGATOR_TYPE);
		template = (Aggregator) Class.forName(agreggatorType).newInstance();
		template.initialize(conf);
	}

	/**
	 * Aggregate the value for the key and notify the caller if this aggregation
	 * had an effect on the internal state or data structure the aggregator is
	 * using.
	 * 
	 * @param timestamp
	 * @param aggregationWindow
	 *            in seconds
	 * @param ruleActionId
	 * @param aggregationKey
	 * @param aggregationValue
	 * @return if the supplied value changed the aggregator state
	 * @throws AggregationRejectException
	 */
	public boolean aggregate(long timestamp, int aggregationWindow, String ruleActionId, String aggregationKey,
			Object aggregationValue) throws AggregationRejectException {
		checkStaleData(timestamp, ruleActionId);
		String key = Utils.createMapKey(timestamp, aggregationWindow, ruleActionId, aggregationKey);
		Aggregator aggregator = getAggregationMap().get(key);
		if (aggregator == null) {
			aggregator = template.getInstance();
			getAggregationMap().put(key, aggregator);
			getFlushMap().put(key, template.getInstance());
		}
		if (aggregator.disableLimitChecks() || (aggregator.size() < aggregator.getHardLimit())) {
			if (aggregator.add(aggregationValue)) {
				return getFlushMap().get(key).add(aggregationValue);
			} else {
				return false;
			}
		} else {
			throw AggregationRejectException;
		}
	}

	public void checkStaleData(long timestamp, String ruleActionId) throws StaleDataException {
		Long lastEmits = lastEmittedBucketMap.get(ruleActionId);
		if (lastEmits != null && (timestamp + jitterTolerance) <= lastEmits) {
			throw StaleDataException;
		}
	}

	/**
	 * Flush and commit data
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException {
		for (Entry<String, Aggregator> entry : getFlushMap().entrySet()) {
			if (store != null) {
				store.persist(entry.getKey(), entry.getValue());
			}
			entry.getValue().reset();
		}
	}

	/**
	 * Is this aggregator processing data for a supplied ruleActionId key
	 * 
	 * @param ruleActionId
	 * @return true if it is
	 */
	public boolean containsRuleActionId(String ruleActionId) {
		return getAggregationMap().containsKey(ruleActionId);
	}

	/**
	 * Emit the aggregates for a given ruleActionId and reset the counters for
	 * it
	 * 
	 * @param ruleActionId
	 * @throws IOException
	 */
	public void emit(int aggregationWindow, String ruleActionId, List<Entry<String, Long>> emits)
			throws IOException {
		flush();
		SortedMap<String, Aggregator> map = getAggregationMap().subMap(Utils.concat(ruleActionId, Constants.KEY_SEPARATOR),
				Utils.concat(ruleActionId, Constants.KEY_SEPARATOR, String.valueOf(Character.MAX_VALUE)));
		int lastTs = extractTsFromAggregationKey(map.lastKey());
		lastTs = lastTs - aggregationWindow - (int) (getJitterTolerance() / 1000);
		String val = Utils.intToString(lastTs);
		val = new StringBuilder(ruleActionId.length() + 3 + val.length()).append(ruleActionId)
				.append(Constants.KEY_SEPARATOR).append(val).append(Constants.KEY_SEPARATOR).append(Character.MAX_VALUE)
				.toString();
		map = getAggregationMap().subMap(ruleActionId, val);
		List<String> keysToRemove = new ArrayList<>(map.size());
		Set<Entry<String, Aggregator>> set = map.entrySet();
		for (Entry<String, Aggregator> entry : set) {
			keysToRemove.add(entry.getKey());
			if (template instanceof CountingAggregator) {
				emits.add(new AbstractMap.SimpleEntry<String, Long>(entry.getKey(),
						((CountingAggregator) entry.getValue()).getCardinality()));
			}
		}
		for (String key : keysToRemove) {
			getAggregationMap().remove(key);
		}
		getLastEmittedBucketMap().put(ruleActionId, ((long) lastTs) * 1000);
	}

	public static int extractTsFromAggregationKey(String key) {
		return Utils.stringToInt(key.split(Constants.KEY_SEPARATOR)[1]);
	}

	/**
	 * @return
	 */
	public final SortedMap<String, Aggregator> getAggregationMap() {
		return aggregationMap;
	}

	/**
	 * @return
	 */
	public final SortedMap<String, Aggregator> getFlushMap() {
		return flushAggregationMap;
	}

	/**
	 * @return the jitterTolerance
	 */
	public long getJitterTolerance() {
		return jitterTolerance;
	}

	/**
	 * @return the lastEmittedBucketMap
	 */
	public Map<String, Long> getLastEmittedBucketMap() {
		return lastEmittedBucketMap;
	}

	/**
	 * @return the flushAggregationMap
	 */
	public SortedMap<String, Aggregator> getFlushAggregationMap() {
		return flushAggregationMap;
	}

}