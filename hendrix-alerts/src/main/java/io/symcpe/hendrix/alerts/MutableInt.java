package io.symcpe.hendrix.alerts;

import java.io.Serializable;

public class MutableInt implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private int val;
	
	public int incrementAndGet() {
		return ++val;
	}

	/**
	 * @return the val
	 */
	public int getVal() {
		return val;
	}

	/**
	 * @param val the val to set
	 */
	public void setVal(int val) {
		this.val = val;
	}
	
}
