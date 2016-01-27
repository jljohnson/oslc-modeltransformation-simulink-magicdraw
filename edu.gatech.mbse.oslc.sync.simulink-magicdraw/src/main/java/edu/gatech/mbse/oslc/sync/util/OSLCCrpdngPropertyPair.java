package edu.gatech.mbse.oslc.sync.util;

public class OSLCCrpdngPropertyPair {
	private String property1URI;
	private String property2URI;
	public OSLCCrpdngPropertyPair(String property1uri, String property2uri) {
		super();
		property1URI = property1uri;
		property2URI = property2uri;
	}
	public String getProperty1URI() {
		return property1URI;
	}
	public void setProperty1URI(String property1uri) {
		property1URI = property1uri;
	}
	public String getProperty2URI() {
		return property2URI;
	}
	public void setProperty2URI(String property2uri) {
		property2URI = property2uri;
	}

}
