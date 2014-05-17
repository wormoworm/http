package uk.tomhomewood.http;

import java.io.File;

import android.os.Bundle;

/**
 * An interface for listening for HTTP events.
 */
public interface HttpEvents{
	
	/**
	 * Called when there is new progress to report during a file upload or download.
	 * This may be called multiple times during the process, but there will always be at least 100ms between successive calls, to avoid
	 * spamming the UI thread. One exception to this rule is that this event is sent when the last set of bytes have been processed,
	 * irrespective of when the previous event was sent.
	 * @param requestCode		The integer code provided when the request was executed.
	 * @param bytesTotal		The size of the file being processed, in bytes.
	 * @param bytesProcessed	The number of bytes processed so far.
	 * @param extras			The {@link Bundle} of extras that was provided when the requested was executed. This will be null if no extras were provided.
	 */
	public void newProgress(int requestCode, long bytesTotal, long bytesProcessed, Bundle extras);
	
	public void fileDownloaded(int requestCode, File downloadedFile, Bundle extras);

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
