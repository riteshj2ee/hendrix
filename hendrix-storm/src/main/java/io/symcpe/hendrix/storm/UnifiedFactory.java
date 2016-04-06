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
package io.symcpe.hendrix.storm;

import java.util.HashMap;
import java.util.Map;

import io.symcpe.wraith.Event;
import io.symcpe.wraith.EventFactory;
import io.symcpe.wraith.store.RulesStore;
import io.symcpe.wraith.store.StoreFactory;

/**
 * Unified factory implementation for Hendrix
 * 
 * @author ambud_sharma
 */
public class UnifiedFactory implements StoreFactory, EventFactory {
	
	@Override
	public RulesStore getRulesStore(String type, Map<String, String> stormConf) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		Map<String, String> conf = new HashMap<>();
		for(Object key:stormConf.keySet()) {
			if(key.toString().startsWith("rstore.")) {
				conf.put(key.toString(), stormConf.get(key).toString());
			}
		}
		RulesStore store = (RulesStore) Class.forName(type).newInstance();
		store.initialize(conf);
		return store;
	}

	@Override
	public Event buildEvent() {
		return new HendrixEvent();
	}

}