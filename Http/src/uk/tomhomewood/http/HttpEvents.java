package uk.tomhomewood.http;

import java.io.File;

import android.os.Bundle;

/**
 * An interface for listening for HTTP events.
 */
public interface HttpEvents{
	
	/**
	 * Called when there is new progress to report during a file upload ({@link Http#executePostRequest(Integer, String, File, int, Bundle)}).
	 * This may be called multiple times during the upload, but there will always be at least 100ms between succesive calls, to avoid
	 * spamming the UI thread. One exception to this rule is that this event is sent when the last set of bytes have been uploaded,
	 * irrespective of when the previous event was sent.
	 * @param requestCode		The integer code provided when the request was executed.
	 * @param bytesUploaded		The number of bytes uploaded so far.
	 * @param fileSize			The size of the file being uploaded, in bytes
	 * @param extras			The {@link Bundle} of extras that was provided when the requested was executed. This will be null if no extras were provided.
	 */
	public void fileUploadProgress(Integer requestCode, long bytesUploaded, long fileSize, Bundle extras);

	/**
	 * Called when an HTTP request has been successfully completed. This event will only be called if an
	 * integer request code was provided when the request was executed and the response from the server is not empty.
	 * @param requestCode		The integer code provided when the request was executed.
	 * @param responseText		The response text from the request.
	 * @param extras			The {@link Bundle} of extras that was provided when the requested was executed. This will be null if no extras were provided.
	 */
	public void httpRequestComplete(int requestCode, String responseText, Bundle extras);

	/**
	 * Called when a request encountered an error. This event will only be called if an
	 * integer request code was provided when the request was executed.
	 * @param requestCode		The integer code provided when the request was executed.
	 * @param errorCode			An integer code describing what type of error occurred. This will be one of the ERROR_xx constants defined in this class.
	 * @param extras			The {@link Bundle} of extras that was provided when the requested was executed. This will be null if no extras were provided.
	 */
	public void httpError(int requestCode, int errorCode, Bundle extras);
}
