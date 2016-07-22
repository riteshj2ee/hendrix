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
package io.symcpe.hendrix.interceptor.aws;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.symcpe.wraith.Event;
import io.symcpe.wraith.EventFactory;

/**
 * @author ambud_sharma
 */
public class VPCFlowLogParser {

	// http://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/flow-logs.html#flow-log-records
	private static final String FLOW_RECORD_REGEX = "(\\d)" // version
			+ "\\s(.*)" // account-id
			+ "\\s(.*-.*)" // interface-id
			+ "\\s(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})" // srcaddr
			+ "\\s(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})" // dstaddr
			+ "\\s(\\d{1,5})" // srcport
			+ "\\s(\\d{1,5})" // dstport
			+ "\\s(\\d{1,3})" // protocol
			+ "\\s(\\d+)" // packets
			+ "\\s(\\d+)" // bytes
			+ "\\s(\\d+)" // start
			+ "\\s(\\d+)" // end
			+ "\\s(ACCEPT|REJECT)" // action
			+ "\\s(OK|NODATA|SKIPDATA)"; // log-status
	private static final Pattern FLOW_RECORD_PATTERN = Pattern.compile(FLOW_RECORD_REGEX);
	private Gson gson;

	public VPCFlowLogParser() {
		gson = new Gson();
	}

	public List<Event> parseFlowLogJson(EventFactory factory, String json) {
		List<Event> events = new ArrayList<>();
		JsonObject flowLogs = gson.fromJson(json, JsonObject.class);
		String logGroup = flowLogs.get("logGroup").getAsString();
		String logStream = flowLogs.get("logStream").getAsString();
		for (JsonElement element : flowLogs.get("logEvents").getAsJsonArray()) {
			Event event = factory.buildEvent();
			JsonObject obj = element.getAsJsonObject();
			event.getHeaders().put("logGroup", logGroup);
			event.getHeaders().put("logStream", logStream);
			event.getHeaders().put("@timestamp", obj.get("timestamp").getAsLong());
			event.getHeaders().put("id", obj.get("id").getAsString());
			parseToRecord(event, obj.get("message").getAsString());
		}
		return events;
	}

	public static void parseToRecord(Event event, String line) {
		Matcher matcher = FLOW_RECORD_PATTERN.matcher(line);
		if (matcher.matches()) {
			event.getHeaders().put("version", Short.parseShort(matcher.group(1)));
			event.getHeaders().put("account-id", matcher.group(2));
			event.getHeaders().put("interface-id", matcher.group(3));
			if (matcher.group(14).charAt(0) == 'O') {
				event.getHeaders().put("srcaddr", matcher.group(4));
				event.getHeaders().put("dstaddr", matcher.group(5));
				event.getHeaders().put("srcport", Integer.parseInt(matcher.group(6)));
				event.getHeaders().put("dstport", Integer.parseInt(matcher.group(7)));
				event.getHeaders().put("protocol", (byte) matcher.group(8).charAt(0));
				event.getHeaders().put("packets", Integer.parseInt(matcher.group(9)));
				event.getHeaders().put("bytes", Integer.parseInt(matcher.group(10)));
			}
			event.getHeaders().put("start", Integer.parseInt(matcher.group(11)));
			event.getHeaders().put("end", Integer.parseInt(matcher.group(12)));
			event.getHeaders().put("accepted", matcher.group(13).equals("ACCEPT"));
			event.getHeaders().put("log-status", (byte) matcher.group(14).charAt(0));
		}
	}

	public static VPCFlowLogRecord parseToRecord(String line) {
		Matcher matcher = FLOW_RECORD_PATTERN.matcher(line);
		if (matcher.matches()) {
			VPCFlowLogRecord record = new VPCFlowLogRecord();
			record.setVersion(Short.parseShort(matcher.group(1)));
			record.setAccountId(matcher.group(2));
			record.setInterfaceId(matcher.group(3));
			if (matcher.group(14).charAt(0) == 'O') {
				record.setSrcAddr(matcher.group(4));
				record.setDstAddr(matcher.group(5));
				record.setSrcPort(Integer.parseInt(matcher.group(6)));
				record.setDstPort(Integer.parseInt(matcher.group(7)));
				record.setProtocol((byte) matcher.group(8).charAt(0));
				record.setPackets(Integer.parseInt(matcher.group(9)));
				record.setBytes(Integer.parseInt(matcher.group(10)));
			}
			record.setStartTs(Integer.parseInt(matcher.group(11)));
			record.setEndTs(Integer.parseInt(matcher.group(12)));
			record.setAccepted(matcher.group(13).equals("ACCEPT"));
			record.setLogStatus((byte) matcher.group(14).charAt(0));
			return record;
		} else {
			return null;
		}
	}

}
