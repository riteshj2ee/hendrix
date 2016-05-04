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
package io.symcpe.wraith.aggregations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.symcpe.wraith.Constants;
import io.symcpe.wraith.Utils;
import io.symcpe.wraith.aggregators.MarkovianAggregationEngineImpl;

/**
 * All tests for Aggregations
 * 
 * @author ambud_sharma
 */
public class TestAggregationEngine {

	private Map<String, String> conf;

	@Before
	public void before() {
		conf = new HashMap<>();
	}

	@Test
	public void testFineCountingAggregator() throws Exception {
		CountingEngine aggregationEngine = new CountingEngine();
		conf.put(Constants.COUNTER_TYPE, FineCountingAggregator.class.getName());
		aggregationEngine.initialize(conf);
		String ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1233);
		for (int i = 0; i < 10; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < 15; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertEquals(10, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size());
		assertEquals(15, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size());

		ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1234);
		for (int i = 0; i < 10; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < 15; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertEquals(10, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size());
		assertEquals(15, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size());
	}

	@Test
	public void testFineCountingAggregatorLimit() throws Exception {
		CountingEngine aggregationEngine = new CountingEngine();
		int limit = 100000;
		conf.put(Constants.COUNTER_TYPE, FineCountingAggregator.class.getName());
		conf.put(Constants.AGGREGATIONS_FCOUNT_LIMIT, String.valueOf(limit));
		aggregationEngine.initialize(conf);
		String ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1233);
		int count = 1000000;
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertEquals(limit, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size());
		assertEquals(limit, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size());

	}

	@Test
	public void testCoarseCountingAggregator() throws Exception {
		CountingEngine aggregationEngine = new CountingEngine();
		int count = 1000000;
		String ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1233);
		conf.put(Constants.COUNTER_TYPE, CoarseCountingAggregator.class.getName());
		aggregationEngine.initialize(conf);
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertTrue((((double) aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size()) / count) > 0.9);
		assertTrue((((double) aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size()) / count) > 0.9);

		ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1234);
		aggregationEngine.initialize(conf);
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertTrue((((double) aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size()) / count) > 0.9);
		assertTrue((((double) aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size()) / count) > 0.9);
	}

	@Test
	public void testSetAggregator() throws Exception {
		MarkovianAggregationEngineImpl aggregationEngine = new MarkovianAggregationEngineImpl();
		int count = 10000;
		String ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1233);
		conf.put(Constants.AGGREGATOR_TYPE, SetAggregator.class.getName());
		conf.put(Constants.AGGREGATIONS_SET_LIMIT, String.valueOf(count));
		aggregationEngine.initialize(conf);
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", "hey" + i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", "hey" + i);
			} catch (AggregationRejectException e) {
			}
		}
		assertEquals(count, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size());
		assertEquals(count, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size());
	}

	@Test
	public void testSetAggregatorLimit() throws Exception {
		MarkovianAggregationEngineImpl aggregationEngine = new MarkovianAggregationEngineImpl();
		int count = 1000000;
		int limit = 500000;
		conf.put(Constants.AGGREGATOR_TYPE, SetAggregator.class.getName());
		conf.put(Constants.AGGREGATIONS_SET_LIMIT, String.valueOf(limit));
		aggregationEngine.initialize(conf);
		String ruleActionId = Utils.combineRuleActionId((short) 12, (short) 1233);
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello", i);
			} catch (AggregationRejectException e) {
			}
		}
		for (int i = 0; i < count; i++) {
			try {
				aggregationEngine.aggregate(1, 1, ruleActionId, "1233_hello2", i);
			} catch (AggregationRejectException e) {
			}
		}
		assertEquals(limit, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello")).size());
		assertEquals(limit, aggregationEngine.getAggregationMap()
				.get(Utils.createMapKey(1, 1, ruleActionId, "1233_hello2")).size());
	}
	
}
