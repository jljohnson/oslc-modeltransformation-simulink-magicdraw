package edu.gatech.mbse.oslc.sync.clients;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.Resource;

import edu.gatech.mbse.oslc.wink.clients.OslcRestClient;

import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;



public class OSLCGetSimulinkRDFVocabulary {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	public static void main(String[] args) { 

		String baseHTTPURI = "http://localhost:8181/oslc4jsimulink";		
		String simulinkRDFVocabularyURI = baseHTTPURI + "/services/" + "test";

		
		URL url;
		HttpURLConnection urlConnection = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		try {
			
			
			
			

			
				// read this response into InputStream
				url = new URL(simulinkRDFVocabularyURI);		  
				urlConnection = (HttpURLConnection) url.openConnection();   		         
				inputStream = new BufferedInputStream(urlConnection.getInputStream()); 
				
				// write the inputStream to a FileOutputStream
				outputStream = 
		                    new FileOutputStream(new File("downloadedRDFVocabulary.rdf"));

				int read = 0;
				byte[] bytes = new byte[1024];

				while ((read = inputStream.read(bytes)) != -1) {
					outputStream.write(bytes, 0, read);
				}

				System.out.println("Done!");

			} catch (IOException e) {
				e.printStackTrace();
			} 
		    
			
			
			
			
			
			
    
//	        BufferedReader reader = new BufferedReader(in);
//	        StringBuilder result = new StringBuilder();
//	        String line;
//	        while((line = reader.readLine()) != null) {
//	            result.append(line);
//	        }
//	        System.out.println(result.toString());
//	    } catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	    finally 
	    {     
	    	if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (outputStream != null) {
				try {
					// outputStream.flush();
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
	    	
	    	urlConnection.disconnect();   
	    } 
		
		
		
		
		// get the parameter value / perform a GET
//		System.out.println("Performing HTTP GET on resource "
//				+ simulinkRDFVocabularyURI);
//		Resource clientGETResponse = getOslcRestClient(
//				simulinkRDFVocabularyURI).getClientResource();
//		ClientResponse clientResponse = clientGETResponse.get();
////		HttpInputStream message = clientResponse.getEntity(HttpInputStream.class);
		System.out.println("ok");

		
	}

	public static OslcRestClient getOslcRestClient(String simulinkResourceURI) {
		return new OslcRestClient(PROVIDERS, simulinkResourceURI,
				"application/rdf+xml", 240000);
	}

	
}
