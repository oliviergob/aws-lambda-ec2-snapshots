package com.gobslog.ec2.snapshot;

public class ParseException extends Exception {

	/** Serial Version UID*/
	private static final long serialVersionUID = 6801093892625490964L;
	
	private String message = null;

	public ParseException(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
