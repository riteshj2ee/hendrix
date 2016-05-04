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
package io.symcpe.hendrix.storm.bolts;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.validation.DateInterceptor;
import io.symcpe.hendrix.storm.validation.ValidationException;
import io.symcpe.hendrix.storm.validation.ValidationInterceptor;

/**
 * Intercepts and validates data so that it can be sent downstream
 * 
 * @author ambud_sharma
 */
public class InterceptionBolt extends BaseRichBolt {

	private static final String INTERCEPTORS = "interceptors";
	private static final long serialVersionUID = 1L;
	private transient OutputCollector collector;
	private transient ValidationInterceptor interceptor;
	private Gson gson;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		if (stormConf.containsKey(INTERCEPTORS)) {
			try {
				String[] vals = stormConf.get(INTERCEPTORS).toString().split(",");
				ValidationInterceptor temp = getInstance(vals[0]);
				temp.configure(stormConf);
				this.interceptor = temp;
				for (int i = 1; i < vals.length; i++) {
					ValidationInterceptor temp2 = getInstance(vals[i]);
					temp2.configure(stormConf);
					temp.setNext(temp2);
					temp = temp2;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			interceptor = new DateInterceptor();
			interceptor.configure(stormConf);
		}
		this.gson = new Gson();
	}

	@Override
	public void execute(Tuple input) {
		String jsonStr = input.getString(0);
		try {
			JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
			try {
				interceptor.validate(json);
				collector.emit(input, new Values(gson.toJson(json)));
			} catch (ValidationException e) {
				StormContextUtil.emitErrorTuple(collector, input, InterceptionBolt.class, jsonStr, e.getMessage(), e);
			}
		} catch (Exception e) {
			StormContextUtil.emitErrorTuple(collector, input, InterceptionBolt.class, jsonStr, e.getMessage(), e);
		}
		collector.ack(input);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields(Constants.FIELD_EVENT));
		StormContextUtil.declareErrorStream(declarer);
	}

	public static ValidationInterceptor getInstance(String interceptorFQCN)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		return (ValidationInterceptor) Class.forName(interceptorFQCN).newInstance();
	}

}
