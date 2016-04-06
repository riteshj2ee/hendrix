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
package io.symcpe.wraith;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.symcpe.wraith.conditions.ConditionSerializer;

/**
 * Utils class for wraith-crux
 * 
 * @author ambud_sharma
 */
public class Utils {

	public static final Charset UTF8 = Charset.forName("utf-8");
	public static final String PROP_NAMING_MAP = "naming.map";
	public static final Map<String, String> CLASSNAME_FORWARD_MAP = new HashMap<>();
	public static final Map<String, String> CLASSNAME_REVERSE_MAP = new HashMap<>();

	static {
		String map = System.getProperty(PROP_NAMING_MAP);
		InputStream stream;
		try {
			if (map == null) {
				stream = ConditionSerializer.class.getClassLoader().getResourceAsStream("naming.default");
				System.out.println("Loading default naming convection:" + stream);
			} else {
				stream = new FileInputStream(new File(map));
				System.out.println("Found naming map configuration:" + map);
			}
			if (stream != null) {
				List<String> lines = Utils.readAllLinesFromStream(stream);
				for (String line : lines) {
					String[] entry = line.split("=");
					CLASSNAME_FORWARD_MAP.put(entry[0], entry[1]);
					CLASSNAME_REVERSE_MAP.put(entry[1], entry[0]);
				}
			}else {
				System.out.println("Couldn't load the default naming resource");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected Utils() {
	}

	public static byte[] eventToBytes(Event event) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream stream = new ObjectOutputStream(bytes);
			stream.writeObject(event);
			return bytes.toByteArray();
		} catch (IOException e) {
			// should be an unreachable state
			return null;
		}
	}

	public static List<String> readAllLinesFromStream(InputStream stream) throws IOException {
		List<String> lines = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF8));
		try {
			String temp = null;
			while ((temp = reader.readLine()) != null) {
				if(temp.trim().isEmpty()) {
					continue;
				}
				lines.add(temp);
			}
		} finally {
			reader.close();
		}
		return lines;
	}

	public static void addDeclaredAndInheritedFields(Class<?> c, Collection<Field> fields) {
	    fields.addAll(Arrays.asList(c.getDeclaredFields())); 
	    Class<?> superClass = c.getSuperclass(); 
	    if (superClass != null) { 
	        addDeclaredAndInheritedFields(superClass, fields); 
	    }       
	}
}