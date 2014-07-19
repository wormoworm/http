package uk.tomhomewood.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.zip.DeflaterOutputStream;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class Http {
	private final static String TAG = "HTTP";
	
	private HttpEvents parentInterface;

	public static final int ERROR_EMPTY_RESPONSE = 1;
	public static final int ERROR_NO_CONNECTION = 2;
	public static final int ERROR_URL_INVALID = 3;
	public static final int ERROR_SERVER_ERROR = 4;
	public static final int ERROR_LOCAL_FILE_INVALID = 10;
	public static final int ERROR_RESPONSE_DATA_INVALID = 11;
	
	public static final int RESPONSE_STATUS_ERROR = 1;
	public static final int RESPONSE_STATUS_SUCCESS = 2;
	
	public static final int DEFAULT_RETRIES = 5;
	public static final int DEFAULT_TIMEOUT_SECONDS = 10;

	protected static final int CONNECT_TIMEOUT_MS = 3000;
	protected static final int READ_TIMEOUT_MS = 5000;
	
	private final int MIN_FILE_UPLOAD_INTERVAL_MS = 100;
	
	private Handler handler;
	
	private ConnectivityManager connectivityManager;
	private NetworkInfo networkInfo;
	
	private long lastFileProgressEventTimestamp;
	
	private boolean debugRequests = false;
	
	/**
	 * Constructor.
	 * @param context		The context of this {@link Http} object's parent class. 
	 * @param parent		If you implement the {@link HttpEvents} interface in this parent class, events will be sent
	 * 						to it by this {@link Http} object. If this is null, or you do not implement this interface,
	 * 						events will not be sent, but requests will still be executed.
	 */
	public Http(Context context, HttpEvents eventListener){
		connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		
		parentInterface = eventListener;
		handler = new Handler();
		setDebuggingEnabled(true);
	}
	
	/**
	 * Use this to check if there is an active network connection.
	 * @return		True if there is an active connection, false otherwise.
	 */
	public boolean isConnected(){
		networkInfo = connectivityManager.getActiveNetworkInfo();
		if(networkInfo!=null){
			return networkInfo.isConnected();
		}
		else{
			return false;
		}
	}
	
	/**
	 * Enables or disables debugging of request and response information. If debugging is enabled, information will be
	 * output to the Android system log. Debugging is disabled by default.
	 * @param enabled		True to enable debugging, false to disable it.
	 */
	public void setDebuggingEnabled(boolean enabled){
		debugRequests = enabled;
	}
	
	/**
	 * Executes an HTTP GET request to the specified address.
	 * @param requestCode		An integer code that is used to tag requests. This code is returned to you when 
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired. Set this to null if you do not
	 * 							wish to receive the {@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} event.
	 * @param address			The address to connect to.
	 * @param maximumRetries	How many times the connection should be retried before giving up.
	 * @param timeoutSeconds	How long to wait for a response before giving up and returning an error.
	 * @param allowCaching		Whether or not this request may return cached data.
	 * @param extras			An optional {@link Bundle} of data you wish to associate with this request. When this request is complete and
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired, this Bundle will be returned.
	 * 							This makes it easy to handle multiple events, even if they have the same request code. This Bundle may be null.
	 * @return					A string containing the server's response, or null if there was no response.
	 */
	public void executeGetRequest(final Integer requestCode, final String address, final int maximumRetries, final int timeoutSeconds, final boolean allowCaching, final Bundle extras) {
		if(isConnected()){
			Thread getRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					if(debugRequests){
						Log.d(TAG+" GET REQUEST:", address);
					}
					String responseString = null;
	
					URL url = null;
					HttpURLConnection urlConnection = null;
	
					try {
						url = new URL(address);
					}
					catch (MalformedURLException e) {}
					if(url!=null){
						try {
							urlConnection = (HttpURLConnection) url.openConnection();
							//urlConnection.setDoOutput(true);
							urlConnection.setRequestMethod("GET");
							urlConnection.setReadTimeout(timeoutSeconds * 1000);
							urlConnection.setConnectTimeout(timeoutSeconds * 1000);
							//urlConnection.setUseCaches(true);
		
							if(!allowCaching){
								urlConnection.addRequestProperty("Cache-Control", "no-cache");
							}
/*							
							int responseCode = urlConnection.getResponseCode();
							if(responseCode!=HttpURLConnection.HTTP_OK){		//Response code was not ok, output a log message
								Log.e(TAG, "Error executing GET request, response code was: "+responseCode);
							}
*/							
							responseString = readResponse(urlConnection);
						}
						catch (SocketTimeoutException e) {		//Reach here if the server didn't give us a socket
							//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
							if(maximumRetries>0){			//True if there is still at least one retry left
								Log.e(TAG, "Exception while executing request: "+e.toString());
								Log.d(TAG, "Retrying, retries remaining: "+maximumRetries);
								executeGetRequest(requestCode, address, maximumRetries - 1, timeoutSeconds, allowCaching, extras);
							}
							else{
								sendErrorEvent(requestCode, ERROR_SERVER_ERROR, extras);
							}
						}
						catch (IOException e) {
							Log.e(TAG, "Error executing GET request: "+e.toString());
							e.printStackTrace();
						}
						finally {
							urlConnection.disconnect();
						}
						if( responseString!=null){
							if(debugRequests){
								Log.d(TAG+" GET RESPONSE:", responseString);
							}
							sendRequestCompleteEvent(requestCode, responseString, extras);
						}
						else{
							sendErrorEvent(requestCode, ERROR_EMPTY_RESPONSE, extras);
						}
					}
					else{
						sendErrorEvent(requestCode, ERROR_URL_INVALID, extras);
					}
				}
			});
			getRequestThread.start();
		}
		else{
			sendErrorEvent(requestCode, ERROR_NO_CONNECTION, extras);
		}
	}
	
	/**
	 * Executes an HTTP POST request to the specified address, attaching the provided parameters as POST variables.
	 * @param requestCode		An integer code that is used to tag requests. This code is returned to you when 
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired. Set this to null if you do not
	 * 							wish to receive the {@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} event.
	 * @param address			The address to connect to.
	 * @param body				A String containing the body text to be sent. You may send POST parameters here, in the form: "var1=value1,var2=value2".
	 * @param maximumRetries	How many times the connection should be retried before giving up.
	 * @param allowCaching		Whether or not this request may return cached data.
	 * @param extras			An optional {@link Bundle} of data you wish to associate with this request. When this request is complete and
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired, this Bundle will be returned.
	 * 							This makes it easy to handle multiple events, even if they have the same request code. This Bundle may be null.
	 * @return					A string containing the server's response, or null if there was no response.
	 */
	public void executePostRequest(final Integer requestCode, final String address, final HashMap<String, String> headers, final String body, final int maximumRetries, final boolean allowCaching, final Bundle extras) {
		if(isConnected()){
			Thread postRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					if(debugRequests){
						Log.d(TAG+" POST REQUEST:", address);
						Log.d(TAG+" POST BODY:", body);
					}
					String responseString = null;
	
					URL url = null;
					HttpURLConnection urlConnection = null;
	
					try {
						url = new URL(address);
					}
					catch (MalformedURLException e) {}
					if(url!=null){
						try {
							urlConnection = (HttpURLConnection) url.openConnection();
							urlConnection.setConnectTimeout(2000);
							urlConnection.setReadTimeout(2500);
							
							urlConnection.setDoOutput(true);
							urlConnection.setRequestMethod("POST");
							
							if(headers!=null){
								addRequestHeadersToConnection(urlConnection, headers);
							}
							
							if(!allowCaching){
								urlConnection.addRequestProperty("Cache-Control", "no-cache");
							}
							
							urlConnection.setRequestProperty("Content-Type", "application/json");
							
							urlConnection.setFixedLengthStreamingMode(body.getBytes().length);
		
							PrintWriter out = new PrintWriter(urlConnection.getOutputStream());
							out.print(body);
							out.close();
							
							int responseCode = urlConnection.getResponseCode();
							if(!responseCodeOk(responseCode)){		//Response code was not ok, output a log message
								Log.e(TAG, "Error executing POST request, response code was: "+responseCode);
							}
							responseString = readResponse(urlConnection);
						}
						catch (Exception e) {		//Reach here if the server didn't give us a socket or some other exception occurred
							//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
							if(maximumRetries>0){			//True if there is still at least one retry left
								Log.e(TAG, "Exception while executing request: "+e.toString());
								Log.d(TAG, "Retrying, retries remaining: "+maximumRetries);
								executePostRequest(requestCode, address, headers, body, maximumRetries-1, allowCaching, extras);
							}
						}
						finally {
							urlConnection.disconnect();
						}
						
						if(responseString!=null){
							if(debugRequests){
								Log.d(TAG+" POST RESPONSE:", responseString);
							}
							sendRequestCompleteEvent(requestCode, responseString, extras);
						}
						else{
							sendErrorEvent(requestCode, ERROR_EMPTY_RESPONSE, extras);
						}
					}
					else{
						sendErrorEvent(requestCode, ERROR_URL_INVALID, extras);
					}
				}
			});
			postRequestThread.start();
		}
		else{
			sendErrorEvent(requestCode, ERROR_NO_CONNECTION, extras);
		}
	}

	private boolean responseCodeOk(int responseCode) {
		return 		responseCode==HttpURLConnection.HTTP_OK
				|| 	responseCode==HttpURLConnection.HTTP_CREATED
				||	responseCode==HttpURLConnection.HTTP_NO_CONTENT;
	}

	private void addRequestHeadersToConnection(HttpURLConnection urlConnection, HashMap<String, String> headers) {
		if(headers!=null){
			Iterator<Entry<String, String>> iterator = headers.entrySet().iterator();
			Entry<String, String> header;
			while(iterator.hasNext()){
				header = iterator.next();
				urlConnection.setRequestProperty(header.getKey(), header.getValue());
			}
		}
	}

	/**
	 * Executes an HTTP POST request to the specified address, attaching the provided file as a data stream.
	 * @param requestCode		An integer code that is used to tag requests. This code is returned to you when 
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired.
	 * @param address			The address to connect to.
	 * @param fileToUpload		The {@link File} object to be uploaded.
	 * @param maximumRetries	How many times the connection should be retried before giving up.
	 * @param allowCaching		Whether or not this request may return cached data.
	 * @param extras			An optional {@link Bundle} of data you wish to associate with this request. When this request is complete and
	 * 							{@link HttpEvents#httpRequestComplete(int, int, String, Bundle)} is fired, this Bundle will be returned.
	 * 							This makes it easy to handle multiple events, even if they have the same request code. This Bundle may be null.
	 * @return					A string containing the server's response, or null if there was no response.
	 */
	public void executePostRequest(final Integer requestCode, final String address, final File fileToUpload, final int maximumRetries, final boolean allowCaching, final Bundle extras) {
		if(isConnected()){
			lastFileProgressEventTimestamp = 0;
			Thread getRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					if(debugRequests){
						Log.d(TAG+" POST REQUEST:", address);
					}
					
					int fileLength = 0;
					
					int bufferLength = 4096;		//Default upload chunk size
					
					FileInputStream fileInputStream;
					URL url = null;
	
					try {
						url = new URL(address);
					}
					catch (MalformedURLException e) {}
					if(url!=null){
						try{		
							fileLength = (int) fileToUpload.length();		//Get the file length. Because this is an int, 2GB is the maximum file size
							if(!fileToUpload.exists()){
								Log.e(TAG, "File does not exist, path: "+fileToUpload.getAbsolutePath());
								sendErrorEvent(requestCode, ERROR_LOCAL_FILE_INVALID, extras);
							}
							byte[] buffer = new byte[bufferLength];
							fileInputStream = new FileInputStream(fileToUpload);
							
							HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();	//Make a new URL connection
							urlConnection.setReadTimeout(60000);	//Set a timeout of 60 seconds for reading the response from the server
							urlConnection.setDoOutput(true);		//We need to output data
							//connection.setDoInput(true);		//We need to recieve data
							urlConnection.setRequestMethod("POST");	//POST form method
							urlConnection.setRequestProperty("Content-encoding", "deflate");
							urlConnection.setRequestProperty("Content-type", "application/octet-stream");
		
							urlConnection.setRequestProperty("Content-Language", "en-GB");
							
							if(!allowCaching){
								urlConnection.addRequestProperty("Cache-Control", "no-cache");
							}
							//connection.setChunkedStreamingMode(bufferLength);
							
							long bytesUploaded = 0;		//Keep a local counter of the bytes we have transferred, this is used after the stream writing is completed
							
							OutputStream outputStream = urlConnection.getOutputStream();		//Open an  output stream, this is the raw file data, uncompressed
							DeflaterOutputStream compressedOutputStream = new DeflaterOutputStream(outputStream);
							
							for (int i = 0; i < fileLength; i += bufferLength){			//Upload the data in a loop, split into sections of bufferLength length
								if (fileLength - i >= bufferLength){					//True if the end of the file will not be reached during this read
									fileInputStream.read(buffer, 0, bufferLength);		//Read into the buffer until the buffer is full
									compressedOutputStream.write(buffer, 0, bufferLength);		//Read from the contents of the buffer and write the bytes we read to a compressed output stream
									bytesUploaded+= bufferLength;
									if((System.currentTimeMillis() - lastFileProgressEventTimestamp) > MIN_FILE_UPLOAD_INTERVAL_MS){
										sendNewProgressEvent(requestCode, bytesUploaded, fileLength, extras);
									}
								}
								else{														//True if this will be the last read from this file. In this case, we do the same as above, but only read until the end fo the file, meaning the buffer may not be full after the read
									fileInputStream.read(buffer, 0, fileLength - i);
									compressedOutputStream.write(buffer, 0, fileLength - i);
									bytesUploaded+= fileLength - i;
									sendNewProgressEvent(requestCode, bytesUploaded, fileLength, extras);
								}
							}
							Log.d(TAG, "Upload complete, bytes: "+bytesUploaded);
							
							//Close the streams
							fileInputStream.close();				
							
							compressedOutputStream.flush();
							compressedOutputStream.close();
		
							outputStream.flush();
							outputStream.close();
					
							//Read the response from the server
							String responseString = readResponse(urlConnection);
							urlConnection.disconnect();	//Close the connection to free up resources
							
							if(responseString!=null){
								if(debugRequests){
									Log.d(TAG+" POST RESPONSE:", responseString);
								}
								sendRequestCompleteEvent(requestCode, responseString, extras);
							}
							else{
								sendErrorEvent(requestCode, ERROR_EMPTY_RESPONSE, extras);
							}
						}
						catch (Exception e){
							Log.d(TAG, "Exception while executing request, retrying, retries remaining: "+maximumRetries);
							//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
							if(maximumRetries>0){			//True if there is still at least one retry left
								Log.e(TAG, "Exception while executing request: "+e.toString());
								Log.d(TAG, "Retrying, retries remaining: "+maximumRetries);
								executePostRequest(requestCode, address, fileToUpload, maximumRetries-1, allowCaching, extras);
							}
							else{
								sendErrorEvent(requestCode, ERROR_SERVER_ERROR, extras);
							}
						}
					}
					else{
						sendErrorEvent(requestCode, ERROR_URL_INVALID, extras);
					}
				}
			});
			getRequestThread.start();
		}
		else{
			sendErrorEvent(requestCode, ERROR_NO_CONNECTION, extras);
		}
	}
	
	/**TODO still fails multiple times with STE*/
	public void executeRequest(final RequestMethod requestMethod, final Integer requestCode, final String address, final HashMap<String, String> headers, final String contentType, final String body, final int maximumRetries, final boolean allowCaching, final Bundle extras) {
		if(isConnected()){
			Thread postRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					if(debugRequests){
						Log.d(TAG+" "+requestMethod.stringValue+" REQUEST:", address);
						if(body!=null){
							Log.d(TAG+" "+requestMethod.stringValue+" BODY:", body);
						}
					}
					String responseString = null;
	
					URL url = null;
					HttpURLConnection urlConnection = null;
	
					try {
						url = new URL(address);
					}
					catch (MalformedURLException e) {
					}
					if(url!=null){
						try {
							urlConnection = (HttpURLConnection) url.openConnection();
							urlConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
							urlConnection.setReadTimeout(READ_TIMEOUT_MS);
							
							urlConnection.setRequestMethod(requestMethod.stringValue);
							
							if(headers!=null){
								addRequestHeadersToConnection(urlConnection, headers);
							}
							
							if(!allowCaching){
								urlConnection.addRequestProperty("Cache-Control", "no-cache");
							}
							
							if(contentType!=null){
								urlConnection.setRequestProperty("Content-Type", contentType);
							}
							
							if(body!=null){
								urlConnection.setFixedLengthStreamingMode(body.getBytes().length);
			
								PrintWriter out = new PrintWriter(urlConnection.getOutputStream());
								out.print(body);
								out.close();
							}
							
							int responseCode = urlConnection.getResponseCode();
							if(debugRequests){
								Log.d(TAG, "Response code: "+responseCode);
							}
							if(!responseCodeOk(responseCode)){		//Response code was not ok, output a log message
								Log.e(TAG, "Error executing "+requestMethod.stringValue+" request, response code was: "+responseCode);
							}
							responseString = readResponse(urlConnection);
							
							if(responseString!=null){
								if(debugRequests){
									Log.d(TAG+" "+requestMethod.stringValue+" RESPONSE:", responseString);
								}
								sendRequestCompleteEvent(requestCode, responseString, extras);
							}
							else{
								sendErrorEvent(requestCode, ERROR_EMPTY_RESPONSE, extras);
							}
						}
						catch (Exception e) {		//Reach here if the server didn't give us a socket or some other exception occurred
							//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
							if(maximumRetries>0){			//True if there is still at least one retry left
								Log.e(TAG, "Exception while executing request: "+e.toString());
								Log.d(TAG, "Retrying, retries remaining: "+maximumRetries);
								executePostRequest(requestCode, address, headers, body, maximumRetries-1, allowCaching, extras);
							}
						}
						finally {
							urlConnection.disconnect();
						}
					}
					else{
						sendErrorEvent(requestCode, ERROR_URL_INVALID, extras);
					}
				}
			});
			postRequestThread.start();
		}
		else{
			sendErrorEvent(requestCode, ERROR_NO_CONNECTION, extras);
		}
	}
	
	public void downloadFile(final Integer requestCode, final String address, final HashMap<String, String> headers, final String destinationPath, final String desiredFileName, final int maximumRetries, final int timeoutSeconds, final Bundle extras){
		Log.d(TAG, "Downloading from: "+address+" to: "+destinationPath);
		Thread downloadFileThread = new Thread(new Runnable() {
			public void run(){
				try {
					URL url = new URL(address);
					HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
					httpConn.setReadTimeout(timeoutSeconds * 1000);
					
					if(headers!=null){
						addRequestHeadersToConnection(httpConn, headers);
					}
					
					int responseCode = httpConn.getResponseCode();

					// always check HTTP response code first
					Log.d(TAG, "Response code: "+responseCode);
					if(responseCode == HttpURLConnection.HTTP_OK) {
						String fileName = "";
						String disposition = httpConn.getHeaderField("Content-Disposition");

						if(desiredFileName!=null){
							fileName = desiredFileName;
						}
						else if(disposition!=null){
							// extracts file name from header field
							int index = disposition.indexOf("filename=");
							if (index > 0) {
								fileName = disposition.substring(index + 10,
										disposition.length() - 1);
							}
						}
						else{
							// extracts file name from URL
							fileName = address.substring(address.lastIndexOf("/") + 1, address.length());
						}

						//Open input stream from the HTTP connection
						InputStream inputStream = httpConn.getInputStream();
						String saveFilePath = destinationPath + File.separator + fileName;

						// opens an output stream to save into file
						FileOutputStream outputStream = new FileOutputStream(saveFilePath);

						int totalBytes = 0;
						int bytesRead = -1;
						byte[] buffer = new byte[4096];
						while ((bytesRead = inputStream.read(buffer)) != -1) {
							outputStream.write(buffer, 0, bytesRead);
							totalBytes+= bytesRead;
						}
						Log.d(TAG, "BYTES READ: "+totalBytes);
						outputStream.close();
						inputStream.close();
						File downloadedFile = new File(saveFilePath);
						if(downloadedFile.exists()){
							sendDownloadCompleteEvent(requestCode, downloadedFile, extras);
						}
						else{
							sendErrorEvent(requestCode, ERROR_LOCAL_FILE_INVALID, extras);
						}
					}
					else {
						sendErrorEvent(requestCode, ERROR_URL_INVALID, extras);
					}
					httpConn.disconnect();
				}
				catch (IOException e) {
					//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
					if(maximumRetries>0){			//True if there is still at least one retry left
						Log.e(TAG, "Exception while executing request: "+e.toString());
						Log.d(TAG, "Retrying, retries remaining: "+maximumRetries);
						downloadFile(requestCode, address, headers, destinationPath, desiredFileName, maximumRetries-1, timeoutSeconds, extras);
					}
					else{
						sendErrorEvent(requestCode, ERROR_SERVER_ERROR, extras);
					}
				}
			}
		});
		downloadFileThread.start();
	}

	private String readResponse(HttpURLConnection urlConnection) {
		try {
			InputStream in = new BufferedInputStream(urlConnection.getInputStream());
			StringBuilder sb = new StringBuilder();
			BufferedReader r = new BufferedReader(new InputStreamReader(in), 1000);

			for (String line = r.readLine(); line != null; line = r.readLine()) {
				sb.append(line).append("\n");
			}

			in.close();

			return sb.toString();
		}
		catch (IOException e) {
			if(debugRequests){
				Log.e(TAG, "Error reading response: "+e.toString());
			}
			return null;
		}
	}

	private void sendNewProgressEvent(final Integer requestCode, final long bytesUploaded, final long fileSize, final Bundle extras) {
		Log.d(TAG, "TIME: "+System.currentTimeMillis());
		if(parentInterface!=null && requestCode!=null){
			Runnable uiThreadTask = new Runnable() {
				@Override
				public void run() {
					parentInterface.newProgress(requestCode, fileSize, bytesUploaded, extras);
				}
			};
			handler.post(uiThreadTask);
		}
		lastFileProgressEventTimestamp = System.currentTimeMillis();
	}
	
	private void sendDownloadCompleteEvent(final int requestCode, final File downloadedFile, final Bundle extras) {
		if(parentInterface!=null){
			Runnable uiThreadTask = new Runnable() {
				@Override
				public void run() {
					parentInterface.fileDownloaded(requestCode, downloadedFile, extras);
				}
			};
			handler.post(uiThreadTask);
		}
	}
	
	private void sendRequestCompleteEvent(final Integer requestCode, final String responseString, final Bundle extras) {
		if(parentInterface!=null && requestCode!=null){
			Runnable uiThreadTask = new Runnable() {
				@Override
				public void run() {
					parentInterface.httpRequestComplete(requestCode, responseString, extras);
				}
			};
			handler.post(uiThreadTask);
		}
	}
	
	private void sendErrorEvent(final Integer requestCode, final int errorCode, final Bundle extras) {
		if(parentInterface!=null && requestCode!=null){
			Runnable uiThreadTask = new Runnable() {
				@Override
				public void run() {
					parentInterface.httpError(requestCode, errorCode, extras);
				}
			};
			handler.post(uiThreadTask);
		}
	}
}