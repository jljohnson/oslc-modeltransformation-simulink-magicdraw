package edu.gatech.mbse.oslc.sync.clients;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.wink.client.ClientResponse;

import edu.gatech.mbse.oslc.wink.clients.OslcRestClient;

import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;



public class OSLCUpdateSimulinkParameter {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	public static void main(String[] args) {

		String baseHTTPURI = "http://localhost:8181/oslc4jsimulink";
		String projectId = "model11";

		String simulinkConstBlockValueParameterURI = baseHTTPURI + "/services/"
				+ projectId + "/parameters/Constant::Value";

		// get the parameter value / perform a GET
		System.out.println("Performing HTTP GET on resource "
				+ simulinkConstBlockValueParameterURI);
		ClientResponse clientGETResponse = getOslcRestClient(
				simulinkConstBlockValueParameterURI).getOslcResource();
		SimulinkParameter simulinkParameter = clientGETResponse
				.getEntity(SimulinkParameter.class);
		System.out.println("Value of parameter resource "
				+ simulinkConstBlockValueParameterURI + ": "
				+ simulinkParameter.getValue());
		String resource1ETagFromGET = getETagFromResponse(clientGETResponse);
		System.out.println("ETag of resource "
				+ simulinkConstBlockValueParameterURI + ": " + resource1ETagFromGET);

		// update the parameter value
		// create a new parameter resource
		SimulinkParameter constantValueParameter;
		try {
			constantValueParameter = new SimulinkParameter();

			constantValueParameter.setName("Value");
			constantValueParameter.setValue("20");
			URI constantValueParameterURI = URI.create(baseHTTPURI
					+ "/services/" + projectId + "/parameters/"
					+ "Constant::Value");
			constantValueParameter.setAbout(constantValueParameterURI);
			// perform a PUT with If-Match header
			System.out.println("\r\nPerforming HTTP PUT on resource "
					+ simulinkConstBlockValueParameterURI);
			ClientResponse clientPUTResponse = getOslcRestClient(
					simulinkConstBlockValueParameterURI)
					.updateOslcResourceReturnClientResponse(
							constantValueParameter, resource1ETagFromGET);
			int putStatusCode = clientPUTResponse.getStatusCode();
			if (putStatusCode == HttpServletResponse.SC_OK) {
				System.out.println("Update of resource "
						+ simulinkConstBlockValueParameterURI
						+ " was successful ");
			} else if (putStatusCode == HttpServletResponse.SC_PRECONDITION_FAILED) {
				System.out.println("Update of resource "
						+ simulinkConstBlockValueParameterURI
						+ " was NOT successful ");
			} else {
				System.out.println("putStatusCode " + putStatusCode);
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static OslcRestClient getOslcRestClient(String simulinkResourceURI) {
		return new OslcRestClient(PROVIDERS, simulinkResourceURI,
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
