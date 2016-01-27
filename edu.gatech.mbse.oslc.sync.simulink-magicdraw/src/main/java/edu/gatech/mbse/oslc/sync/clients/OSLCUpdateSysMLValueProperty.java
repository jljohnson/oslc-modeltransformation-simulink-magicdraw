package edu.gatech.mbse.oslc.sync.clients;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.wink.client.ClientResponse;
import edu.gatech.mbse.oslc.wink.clients.OslcRestClient;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;

import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;




public class OSLCUpdateSysMLValueProperty {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	public static void main(String[] args) {

		String baseHTTPURI = "http://localhost:8080/oslc4jmagicdraw";
		String projectId = "ImportFromSimulink2";

		String sysmlConstBlockValuePropertyURI = baseHTTPURI + "/services/"
				+ projectId + "/valueproperties/Constant_SimBlock::Value";

		// get the parameter value / perform a GET
		System.out.println("Performing HTTP GET on resource "
				+ sysmlConstBlockValuePropertyURI);
		ClientResponse clientGETResponse = getOslcRestClient(
				sysmlConstBlockValuePropertyURI).getOslcResource();
		SysMLValueProperty sysmlValueProperty = clientGETResponse
				.getEntity(SysMLValueProperty.class);
		System.out.println("Value of value property resource "
				+ sysmlConstBlockValuePropertyURI + ": "
				+ sysmlValueProperty.getDefaultValue());
		String resource1ETagFromGET = getETagFromResponse(clientGETResponse);
		System.out.println("ETag of resource "
				+ sysmlConstBlockValuePropertyURI + ": " + resource1ETagFromGET);

		// update the parameter value
		// create a new parameter resource
		SysMLValueProperty constantValueProperty;
		try {
			constantValueProperty = new SysMLValueProperty();
			constantValueProperty.setName("Value");
			constantValueProperty.setDefaultValue("3");
			URI constantValueParameterURI = URI.create(baseHTTPURI
					+ "/services/" + projectId + "/valueproperties/"
					+ "Constant_SimBlock::Value");
			constantValueProperty.setAbout(constantValueParameterURI);
			// perform a PUT with If-Match header
			System.out.println("\r\nPerforming HTTP PUT on resource "
					+ sysmlConstBlockValuePropertyURI);
			ClientResponse clientPUTResponse = getOslcRestClient(
					sysmlConstBlockValuePropertyURI)
					.updateOslcResourceReturnClientResponse(
							constantValueProperty, resource1ETagFromGET);
			int putStatusCode = clientPUTResponse.getStatusCode();
			if (putStatusCode == HttpServletResponse.SC_OK) {
				System.out.println("Update of resource "
						+ sysmlConstBlockValuePropertyURI
						+ " was successful ");
			} else if (putStatusCode == HttpServletResponse.SC_PRECONDITION_FAILED) {
				System.out.println("Update of resource "
						+ sysmlConstBlockValuePropertyURI
						+ " was NOT successful ");
			} else {
				System.out.println("putStatusCode " + putStatusCode);
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static OslcRestClient getOslcRestClient(String resourceURI) {
		return new OslcRestClient(PROVIDERS, resourceURI,
				"application/rdf+xml", 240000);
	}

	public static String getETagFromResponse(ClientResponse clientResponse) {
		String eTag = null;
		List<String> eTagValueList = clientResponse.getHeaders().get("ETag");
		if (eTagValueList.size() > 0) {
			eTag = eTagValueList.get(0);
		}
		return eTag;
	}
}
