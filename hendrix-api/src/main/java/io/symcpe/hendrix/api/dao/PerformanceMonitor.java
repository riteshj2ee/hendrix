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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
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
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CollectionConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.dropwizard.lifecycle.Managed;

/**
 * Hendrix performance monitor receives performance stats from the topologies
 * over Syslog UDP and stores them in-memory to be visualized by users.
 * 
 * @author ambud_sharma
 */
public class PerformanceMonitor implements Managed {

	private Ignite ignite;
	private int channelSize;
	private CollectionConfiguration colCfg;
	private IgniteSet<String> channelCache;
	private PerfMonChannel localChannel;
	private SyslogUDPSource source;
	private ExecutorService eventProcessor;
	
	public void initIgniteCache() {
		Ignition.setClientMode(false);
		ignite = Ignition.start();
		colCfg = new CollectionConfiguration();
		colCfg.setCollocated(true);
		colCfg.setBackups(1);
		channelCache = ignite.set("ruleEfficiency", colCfg);
		
		colCfg = new CollectionConfiguration();
		colCfg.setCollocated(true);
		colCfg.setBackups(1);
		channelSize = Integer.parseInt(System.getProperty("channel.capacity", "100"));
	}

	public void initSyslogServer() {
		eventProcessor = Executors.newSingleThreadExecutor();
		source = new SyslogUDPSource();
		localChannel = new PerfMonChannel();
		localChannel.start();
		eventProcessor.submit(()->{
			Event event = null;
			while(true) {
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
		String seriesName = obj.get("name").getAsString();
		if(seriesName.startsWith("mcm.rule.efficiency")) {
			channelCache.add(seriesName);
		}
		IgniteQueue<Entry<Long, Number>> queue = ignite.queue(seriesName, channelSize, colCfg);
		queue.add(new AbstractMap.SimpleEntry<Long, Number>(ts, obj.get("value").getAsNumber()));
	}

	/**
	 * @return
	 */
	public Map<String, List<Entry<Long, Number>>> getRuleEfficiencySeries() {
		Map<String, List<Entry<Long, Number>>> efficiencySeries = new HashMap<>();
		for (String seriesName : channelCache) {
			IgniteQueue<Entry<Long, Number>> queue = ignite.queue(seriesName, channelSize, colCfg);
			efficiencySeries.put(seriesName, queueToList(queue));
		}
		return efficiencySeries;
	}
	
	/**
	 * @param queue
	 * @return
	 */
	public List<Entry<Long, Number>> queueToList(Queue<Entry<Long, Number>> queue) {
		List<Entry<Long, Number>> list = new ArrayList<>();
		for (Entry<Long, Number> entry : queue) {
			list.add(entry);
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