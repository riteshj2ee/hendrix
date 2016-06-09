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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.flume.Channel;
import org.apache.flume.ChannelException;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.Transaction;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.source.SyslogUDPSource;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.CollectionConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.dropwizard.lifecycle.Managed;
import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.storage.Point;

/**
 * Hendrix performance monitor receives performance stats from the topologies
 * over Syslog UDP and stores them in-memory to be visualized by users.
 * 
 * @author ambud_sharma
 */
public class PerformanceMonitor implements Managed {

	private int channelSize;
	private IgniteCache<String, Set<String>> seriesLookup;
	private PerfMonChannel localChannel;
	private SyslogUDPSource source;
	private ExecutorService eventProcessor;
	private Ignite ignite;
	private CacheConfiguration<String, Set<String>> cacheCfg;
	private CollectionConfiguration cfg;
	private CollectionConfiguration colCfg;

	public PerformanceMonitor(ApplicationManager applicationManager) {
		ignite = applicationManager.getIgnite();
	}

	public void initIgniteCache() {
		cacheCfg = new CacheConfiguration<>();
		cacheCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
		cacheCfg.setName("ruleEfficiency");
		cacheCfg.setBackups(2);
		seriesLookup = ignite.getOrCreateCache(cacheCfg);

		colCfg = new CollectionConfiguration();
		colCfg.setCollocated(true);
		colCfg.setBackups(1);
		channelSize = Integer.parseInt(System.getProperty("channel.capacity", "100"));
		
		cfg = new CollectionConfiguration();
		cfg.setBackups(1);
		cfg.setCacheMode(CacheMode.REPLICATED);
	}

	public void initSyslogServer() {
		eventProcessor = Executors.newSingleThreadExecutor();
		source = new SyslogUDPSource();
		localChannel = new PerfMonChannel();
		localChannel.start();
		eventProcessor.submit(() -> {
			Event event = null;
			while (true) {
				try {
					event = localChannel.take();
					event(event);
					Thread.sleep(10);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		LocalChannelSelector selector = new LocalChannelSelector();
		selector.setChannels(Arrays.asList(localChannel));
		ChannelProcessor processor = new ChannelProcessor(selector);
		source.setChannelProcessor(processor);
		source.setName("syslogSource");
		Context context = new Context();
		context.put("host", "0.0.0.0");
		context.put("port", "5140");
		source.configure(context);
		source.start();
		System.err.println("Syslog server initalized");
	}

	@Override
	public void start() throws Exception {
		initIgniteCache();
		initSyslogServer();
	}

	@Override
	public void stop() throws Exception {
		source.stop();
		eventProcessor.shutdownNow();
	}

	public void event(Event event) throws Exception {
		long ts = Long.parseLong(event.getHeaders().get("timestamp"));
		String message = new String(event.getBody());
		message = message.substring(message.indexOf('{'));
		// Send the Event to the external repository.
		Gson gson = new Gson();
		JsonElement b = gson.fromJson(message, JsonElement.class);
		JsonObject obj = b.getAsJsonObject();
		String seriesName = obj.get("seriesName").getAsString();
		if (seriesName.startsWith("mcm")) {
			String tenantId = obj.get("tenantId").getAsString();
			String ruleId = obj.get("ruleId").getAsString();
			Set<String> tenants = seriesLookup.get(seriesName);
			if (tenants == null) {
				tenants = new HashSet<>();
				seriesLookup.put(seriesName, tenants);
			}
			if (tenants.add(seriesName + "_" + tenantId)) {
				seriesLookup.put(seriesName, tenants);
			}
			IgniteSet<String> set = ignite.set(seriesName + "_" + tenantId, cfg);
			set.add(ruleId);
			IgniteQueue<Entry<Long, Number>> queue = ignite.queue(ruleId, channelSize, colCfg);
			if(queue.size()>=channelSize) {
				queue.remove();
			}
			queue.add(new AbstractMap.SimpleEntry<Long, Number>(ts, obj.get("value").getAsNumber()));
		}else if(seriesName.startsWith("cm")) {
			IgniteQueue<Entry<Long, Number>> queue = ignite.queue(seriesName, channelSize, colCfg);
			queue.add(new AbstractMap.SimpleEntry<Long, Number>(ts, obj.get("value").getAsNumber()));
			if(queue.size()>=channelSize) {
				queue.remove();
			}
		}
	}

	/**
	 * @param seriesName
	 * @param tenantId
	 * @return
	 */
	public Map<String, List<Point>> getSeriesForTenant(String seriesName, String tenantId) {
		Map<String, List<Point>> efficiencySeries = new HashMap<>();
		Set<String> set = seriesLookup.get(seriesName);
		if (set != null && !set.isEmpty()) {
			for (String series : set) {
				if (series.contains(tenantId)) {
					IgniteSet<String> rules = ignite.set(series, cfg);
					for (String ruleId : rules) {
						IgniteQueue<Entry<Long, Number>> queue = ignite.queue(ruleId, channelSize, colCfg);
						efficiencySeries.put(ruleId, queueToList(queue));
					}
				}
			}
		} else {
			System.out.println("Perf stats not found for tenantId:" + tenantId);
		}
		return efficiencySeries;
	}
	
	/**
	 * @param seriesName
	 * @return
	 */
	public List<Point> getSeries(String seriesName) {
		return queueToList(ignite.queue(seriesName, channelSize, colCfg));
	}

	/**
	 * @param queue
	 * @return
	 */
	public List<Point> queueToList(Queue<Entry<Long, Number>> queue) {
		List<Point> list = new ArrayList<>();
		for (Entry<Long, Number> entry : queue) {
			list.add(new Point(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	public static class LocalChannelSelector implements ChannelSelector {

		private List<Channel> channels;

		@Override
		public void setName(String name) {
		}

		@Override
		public String getName() {
			return "localChannelSelector";
		}

		@Override
		public void configure(Context context) {
		}

		@Override
		public void setChannels(List<Channel> channels) {
			this.channels = channels;
		}

		@Override
		public List<Channel> getRequiredChannels(Event event) {
			return channels;
		}

		@Override
		public List<Channel> getOptionalChannels(Event event) {
			return new ArrayList<>();
		}

		@Override
		public List<Channel> getAllChannels() {
			return channels;
		}

	}

	public static class PerfMonChannel implements Channel {

		private ArrayBlockingQueue<Event> eventQueue;

		private LifecycleState state;

		public PerfMonChannel() {
		}

		@Override
		public void start() {
			eventQueue = new ArrayBlockingQueue<>(1000);
			state = LifecycleState.START;
		}

		@Override
		public void stop() {
			state = LifecycleState.STOP;
		}

		@Override
		public LifecycleState getLifecycleState() {
			return state;
		}

		@Override
		public void setName(String name) {
		}

		@Override
		public String getName() {
			return PerfMonChannel.class.getName();
		}

		@Override
		public void put(Event event) throws ChannelException {
			try {
				eventQueue.put(event);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public Event take() throws ChannelException {
			try {
				return eventQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public Transaction getTransaction() {
			return new org.apache.flume.channel.PseudoTxnMemoryChannel.NoOpTransaction();
		}

	}

}