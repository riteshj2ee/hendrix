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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for VPC flow log parsing
 * 
 * @author ambud_sharma
 */
public class TestVPCFlowLogParser {
	
	@Test
	public void testRecordParsing() {
		String val = "2 123456789101 eni-g123abcd 100.100.10.2 50.90.30.21 43895 443 6 13 1836 1469126675 1469126733 ACCEPT OK";
		VPCFlowLogRecord record = VPCFlowLogParser.parseToRecord(val);
		assertNotNull(record);
		assertEquals((short)2, record.getVersion());
		assertEquals("123456789101", record.getAccountId());
		assertEquals("eni-g123abcd", record.getInterfaceId());
		assertEquals("100.100.10.2", record.getSrcAddr());
		assertEquals("50.90.30.21", record.getDstAddr());
		assertEquals(43895, record.getSrcPort());
		assertEquals(443, record.getDstPort());
		assertEquals((byte)"6".charAt(0), record.getProtocol());
		assertEquals(13, record.getPackets());
		assertEquals(1836, record.getBytes());
		assertEquals(1469126675, record.getStartTs());
		assertEquals(1469126733, record.getEndTs());
		assertEquals((byte)"O".charAt(0), record.getLogStatus());
	}

}
