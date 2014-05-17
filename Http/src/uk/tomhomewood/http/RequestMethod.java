package uk.tomhomewood.http;

public enum RequestMethod {

	GET("GET"),
	DELETE("DELETE"),
	PATCH("PATCH"),
	POST("POST"),
	PUT("PUT");

	public String stringValue;
	
	RequestMethod(String stringValue){
		this.stringValue = stringValue;
	}
}
