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
package io.symcpe.hendrix.api.dao;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.CollectionConfiguration;

import io.dropwizard.lifecycle.Managed;
import io.symcpe.hendrix.api.ApplicationManager;

/**
 * Backend for alert receiver functionality
 * 
 * @author ambud_sharma
 */
public class AlertReceiver implements Managed {

	private static final String DEFAULT_CHANNEL_CAPACITY = "100";
	private static final String CHANNEL_CAPACITY = "channel.capacity";
	private static final Logger logger = Logger.getLogger(AlertReceiver.class.getName());
	private static final String CHANNELS = "channels";
	private int channelSize = 0;
	private Ignite ignite;
	private CollectionConfiguration colCfg;
	private IgniteCache<String, Integer> channelCache;

	public AlertReceiver(ApplicationManager am) {
		this.ignite = am.getIgnite();
	}
	
	public void initializeCache() throws Exception {
		CacheConfiguration<String, Integer> cacheCfg = new CacheConfiguration<>();
		cacheCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
		cacheCfg.setName(CHANNELS);
		cacheCfg.setBackups(2);
		channelCache = ignite.getOrCreateCache(cacheCfg);
		logger.info("Channel cache:"+channelCache);

		colCfg = new CollectionConfiguration();
		colCfg.setCollocated(true);
		colCfg.setBackups(1);
		channelSize = Integer.parseInt(System.getProperty(CHANNEL_CAPACITY, DEFAULT_CHANNEL_CAPACITY));
		logger.info("Starting alert receiver");
	}
	
	@Override
	public void start() throws Exception {
		initializeCache();
	}
	
	@Override
	public void stop() throws Exception {
	}

	/**
	 * @param ruleId
	 */
	public void openChannel(Short ruleId) {
		if (ruleId != null) {
			String ruleIdStr = String.valueOf(ruleId);
			if (!channelCache.containsKey(ruleIdStr)) {
				IgniteQueue<Map<String, Object>> queue = ignite.queue(ruleIdStr, channelSize, colCfg);
				if (queue != null) {
					channelCache.put(ruleIdStr, 1);
					logger.info("Adding channel for :" + ruleId);
				} else {
					logger.info("Failed to create channel for rule:" + ruleId);
				}
			} else {
				Integer result = channelCache.get(ruleIdStr);
				channelCache.put(ruleIdStr, result+1);
				logger.info("Channel for rule:" + ruleId + " is already open");
			}
		}
	}

	/**
	 * @param ruleId
	 * @param event
	 * @return
	 */
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
			if(val>1) {
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

	/**
	 * @return the ignite
	 */
	protected Ignite getIgnite() {
		return ignite;
	}

	/**
	 * @return the colCfg
	 */
	protected CollectionConfiguration getColCfg() {
		return colCfg;
	}

	/**
	 * @return the channelCache
	 */
	protected IgniteCache<String, Integer> getChannelCache() {
		return channelCache;
	}

}