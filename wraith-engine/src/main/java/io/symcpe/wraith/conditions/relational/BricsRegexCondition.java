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
package io.symcpe.wraith.conditions.relational;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import io.symcpe.wraith.Required;
import io.symcpe.wraith.conditions.AbstractSimpleCondition;

/**
 * Brics Automaton is more efficient than Java Regex but supports a 
 * smaller range of capabilities.
 * 
 * @author ambud_sharma
 */
public class BricsRegexCondition extends AbstractSimpleCondition {

	private static final long serialVersionUID = 1L;
	private transient RegExp pattern;
	private transient Automaton automaton;
	@Required
	private String regex;

	public BricsRegexCondition(String headerKey, String regex) {
		super(headerKey);
		this.regex = regex;
		if(regex!=null) {
			this.pattern = new RegExp(regex);
		}
	}

	@Override
	public boolean satisfiesCondition(Object value) {
		if (automaton == null) {
			automaton = this.pattern.toAutomaton();
		}
		if (value instanceof String) {
			return automaton.run(value.toString());
		}
		return false;
	}

	/**
	 * @return the regex
	 */
	public String getRegex() {
		return regex;
	}

	/**
	 * @return the regex
	 */
	public RegExp getPattern() {
		return pattern;
	}

	/**
	 * @return the automaton
	 */
	public Automaton getAutomaton() {
		return automaton;
	}

	/**
	 * @param automaton
	 *            the automaton to set
	 */
	public void setAutomaton(Automaton automaton) {
		this.automaton = automaton;
	}

	/**
	 * @param regex the regex to set
	 */
	public void setRegex(String regex) {
		this.regex = regex;
		this.pattern = new RegExp(regex);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getHeaderKey() + " matches " + regex;
	}

}