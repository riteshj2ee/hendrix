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
package io.symcpe.hendrix.api.hc;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.codahale.metrics.health.HealthCheck;

import io.symcpe.hendrix.api.ApplicationManager;

/**
 * @author ambud_sharma
 */
public class KafkaHealthCheck extends HealthCheck {

	private ApplicationManager am;
	private String hostAddress;

	public KafkaHealthCheck(ApplicationManager am, String hostAddress) {
		this.am = am;
		this.hostAddress = hostAddress;
	}

	@Override
	protected Result check() throws Exception {
		try {
			KafkaProducer<String, String> producer = am.getKafkaProducer();
			producer.send(new ProducerRecord<String, String>(am.getRuleTopicName(),
					"Health check message:" + System.currentTimeMillis() + "\t from:" + hostAddress)).get();
			return Result.healthy();
		} catch (Exception e) {
			return Result.unhealthy(e);
		}
	}

}
