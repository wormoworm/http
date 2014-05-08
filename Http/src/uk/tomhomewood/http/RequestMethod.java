package uk.tomhomewood.http;

public enum RequestMethod {

	GET("GET"),
	POST("POST"),
	DELETE("DELETE");

	public String stringValue;
	
	RequestMethod(String stringValue){
		this.stringValue = stringValue;
	}
}
