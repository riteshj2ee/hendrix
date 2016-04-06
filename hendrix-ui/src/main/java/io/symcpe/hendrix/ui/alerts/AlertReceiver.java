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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.CollectionConfiguration;

/**
 * Backend for alert receiver functionality
 * 
 * @author ambud_sharma
 */
public class AlertReceiver implements Serializable {

	private static final String CHANNELS = "channels";
	private static final long serialVersionUID = 1L;
	private static AlertReceiver instance = new AlertReceiver();
	private int channelSize = 0;
	private Ignite ignite;
	private CollectionConfiguration colCfg;
	private IgniteCache<String, Integer> channelCache;

	private AlertReceiver() {
		Ignition.setClientMode(false);
		ignite = Ignition.start();
		CacheConfiguration<String, Integer> cacheCfg = new CacheConfiguration<>();
		cacheCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
		cacheCfg.setName(CHANNELS);
		cacheCfg.setBackups(2);
		channelCache = ignite.getOrCreateCache(cacheCfg);
		System.out.println("Channel cache:"+channelCache);

		colCfg = new CollectionConfiguration();
		colCfg.setCollocated(true);
		colCfg.setBackups(1);
		channelSize = Integer.parseInt(System.getProperty("channel.capacity", "100"));
	}

	public static AlertReceiver getInstance() {
		return instance;
	}

	public void addChannel(Short ruleId) {
		if (ruleId != null) {
			String ruleIdStr = String.valueOf(ruleId);
			if (!channelCache.containsKey(ruleIdStr)) {
				IgniteQueue<Map<String, Object>> queue = ignite.queue(ruleIdStr, channelSize, colCfg);
				if (queue != null) {
					channelCache.put(ruleIdStr, 1);
					System.out.println("Adding channel for :" + ruleId);
				} else {
					System.out.println("Failed to create channel for rule:" + ruleId);
				}
			} else {
				Integer result = channelCache.get(ruleIdStr);
				channelCache.put(ruleIdStr, result+1);
				System.out.println("Channel for rule:" + ruleId + " is already open");
			}
		}
	}

	public boolean publishEvent(short ruleId, Map<String, Object> event) {
		if (channelCache.containsKey(String.valueOf(ruleId))) {
			IgniteQueue<Object> channel = ignite.queue(String.valueOf(ruleId), channelSize, colCfg);
			if (channel != null) {
				if (channel.size() >= channelSize) {
					channel.take(); // evict event
				}
				channel.put(event);
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	/**
	 * @return the channelSize
	 */
	public int getChannelSize() {
		return channelSize;
	}

	/**
	 * @param ruleId
	 * @return
	 */
	public Queue<Map<String, Object>> getChannel(short ruleId) {
		if (channelCache.containsKey(String.valueOf(ruleId))) {
			return ignite.queue(String.valueOf(ruleId), channelSize, colCfg);
		} else {
			return null;
		}
	}

	/**
	 * @param ruleId
	 */
	public void closeChannel(short ruleId) {
		String ruleIdStr = String.valueOf(ruleId);
		if(channelCache.containsKey(ruleIdStr)) {
			Integer val = channelCache.get(ruleIdStr);
			if(val>0) {
				channelCache.put(ruleIdStr, val-1);
			}else {
				channelCache.remove(ruleIdStr);
				IgniteQueue<Map<String, Object>> queue = ignite.queue(ruleIdStr, channelSize, colCfg);
				queue.close();
			}
		}else {
			// do nothing
		}
	}

}