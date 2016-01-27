package edu.gatech.mbse.oslc.syncmanager;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.http.HttpServletResponse;
import javax.xml.datatype.DatatypeConfigurationException;

import org.apache.wink.client.ClientResponse;

import edu.gatech.mbse.oslc.sync.util.OSLCCrpdngPropertyPair;
import edu.gatech.mbse.oslc.wink.clients.OslcRestClient;

import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.provider.jena.JenaModelHelper;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.RDFReader;
import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;




import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;

public class OSLCLinkManager extends TimerTask {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	static Model readModel;
	static Map<String,String> uriETagMap = new HashMap<String,String>();;

	public static void main(String[] args) {

		uriETagMap.clear();
		
		// path to correspondence file
		String correspondenceFileName = "file:C:/Users/CParedis/git/"
				+ "edu.gatech.mbse.oslc.sync.simulink-magicdraw/"
				+ "edu.gatech.mbse.oslc.sync.simulink-magicdraw/"
				+ "correspondence files/Correspondence - ImportFromSimulink2-model11.rdf";
		InputStream in = FileManager.get().open(correspondenceFileName);
		if (in == null) {
			throw new IllegalArgumentException("File: " + correspondenceFileName
					+ " not found");
		}

		// read the RDF/XML file
		readModel = ModelFactory.createDefaultModel();
		readModel.read(in, "RDF/XML");
		loadURIETagMap();
		
		Timer timer = new Timer();
		timer.schedule(new OSLCLinkManager(), 5000, 5000);
	}

	static String getResourceType(Resource resource) {
		String type = null;
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				Property predicate = statement.getPredicate();
				if (predicate.getURI().equals(
						"http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
					RDFNode object = statement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						return literal.getString();
					}
				}
			}
		}
		return type;
	}

	static String getResource1ETag(Resource resource) {
		String stringLiteral = null;
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				Property predicate = statement.getPredicate();
				if (predicate.getURI().equals(
						"http://open-services.net/ns/crspndnc/"
								+ "resource1ETag")) {
					RDFNode object = statement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						return literal.getString();
					}
				}
			}
		}
		return stringLiteral;
	}

	static String getResource2ETag(Resource resource) {
		String stringLiteral = null;
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				Property predicate = statement.getPredicate();
				if (predicate.getURI().equals(
						"http://open-services.net/ns/crspndnc/"
								+ "resource2ETag")) {
					RDFNode object = statement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						return literal.getString();
					}
				}
			}
		}
		return stringLiteral;
	}

	static String getResource1URI(Resource resource) {
		String stringLiteral = null;
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				Property predicate = statement.getPredicate();
				if (predicate.getURI().equals(
						"http://open-services.net/ns/crspndnc/"
								+ "resource1URI")) {
					RDFNode object = statement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						return literal.getString();
					}
				}
			}
		}
		return stringLiteral;
	}

	static String getResource2URI(Resource resource) {
		String stringLiteral = null;
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				Property predicate = statement.getPredicate();
				if (predicate.getURI().equals(
						"http://open-services.net/ns/crspndnc/"
								+ "resource2URI")) {
					RDFNode object = statement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						return literal.getString();
					}
				}
			}
		}
		return stringLiteral;
	}

	static ArrayList<OSLCCrpdngPropertyPair> getListOfPropertyPairs(
			Resource resource) {
		ArrayList<OSLCCrpdngPropertyPair> propertyPairList = new ArrayList<OSLCCrpdngPropertyPair>();
		StmtIterator statementIT = resource.listProperties();
		if (statementIT.hasNext()) {
			while (statementIT.hasNext()) {
				Statement statement = statementIT.next();
				RDFNode object = statement.getObject();
				if (object.isAnon()) {
					ArrayList<String> propertyList = new ArrayList<String>();
					if (object instanceof Resource) {
						Resource nestedLinkedResource = (Resource) object;
						StmtIterator statementIT2 = nestedLinkedResource
								.listProperties();
						if (statementIT2.hasNext()) {
							while (statementIT2.hasNext()) {
								Statement nestedStatement = statementIT2.next();
								RDFNode nestedObject = nestedStatement
										.getObject();
								if (nestedObject instanceof Literal) {
									Literal literal = (Literal) nestedObject;
									propertyList.add(literal.getString());
								}
							}
						}
						if (propertyList.size() == 2) {
							String property1URI = propertyList.get(0);
							String property2URI = propertyList.get(1);
							OSLCCrpdngPropertyPair propertyPair = new OSLCCrpdngPropertyPair(
									property1URI, property2URI);
							propertyPairList.add(propertyPair);
						}
					}
				}
			}
		}
		return propertyPairList;
	}

	@Override
	public void run() {
		ResIterator subjectIT = readModel.listSubjects();
		if (subjectIT.hasNext()) {
			while (subjectIT.hasNext()) {
				Resource resource = subjectIT.nextResource();
				String resourceType = getResourceType(resource);
				if (resourceType != null) {
					if (!resourceType
							.equals("http://open-services.net/ns/crspndnc/ResourcePair")) {
						continue;
					}
				} else {
					continue;
				}
				String resource1URI = getResource1URI(resource);
				String resource2URI = getResource2URI(resource);

				ArrayList<OSLCCrpdngPropertyPair> propertyPairList = getListOfPropertyPairs(resource);
				checkAndUpdate(resource1URI, resource2URI, propertyPairList);
				checkAndUpdate(resource2URI, resource1URI, propertyPairList);
				
			}
		}

	}

	

	

	public static OslcRestClient getOslcRestClient(String simulinkResourceURI) {
		return new OslcRestClient(PROVIDERS, simulinkResourceURI,
				"application/rdf+xml", 240000);
	}

	public static String getETagFromResponse(ClientResponse clientResponse) {
		List<String> eTagValueList = clientResponse.getHeaders().get("ETag");
		String eTag = null;
		if (eTagValueList.size() > 0) {
			eTag = eTagValueList.get(0);
		}
		return eTag;
	}

	public String getPropertyValue(Class oslcResourceClass,
			AbstractResource oslcResource, String propertyName) {
		for (Method method : oslcResourceClass.getDeclaredMethods()) {
			if (method.getName().startsWith("get" + propertyName)) {
				String fieldName = method.getName().replace("get", "")
						.toLowerCase();
				Object fieldValue;
				try {
					fieldValue = method.invoke(oslcResource, null);
					if (fieldValue instanceof String) {
						String fieldValueString = (String) fieldValue;
//						System.out.println(fieldName + ": " + fieldValueString);
						return fieldValueString;
					}
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
		return null;
	}
	
	private AbstractResource setPropertyValue(Class oslcResourceClass,
			AbstractResource oslcResource, String propertyName,
			String propertyvalue) {		
		for (Method method : oslcResourceClass.getDeclaredMethods()) {
			if (method.getName().startsWith("set" + propertyName)) {				
				try {
					method.invoke(oslcResource, propertyvalue);	
					break;
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
		return oslcResource;
	}
	
	private static void loadURIETagMap(){		 
		ResIterator subjectIT = readModel.listSubjects();
		if (subjectIT.hasNext()) {
			while (subjectIT.hasNext()) {
				Resource resource = subjectIT.nextResource();
				String resourceType = getResourceType(resource);
				if (resourceType != null) {
					if (!resourceType
							.equals("http://open-services.net/ns/crspndnc/ResourcePair")) {
						continue;
					}
				} else {
					continue;
				}
				String resource1URI = getResource1URI(resource);				
				String resource2URI = getResource2URI(resource);
				
				// initializing eTags based on HTTP requests
				ClientResponse clientHEAD1Response = getOslcRestClient(
						resource1URI).headOslcResource(null);
				String resource1ETag = getETagFromResponse(clientHEAD1Response);
				ClientResponse clientHEAD2Response = getOslcRestClient(
						resource2URI).headOslcResource(null);
				String resource2ETag = getETagFromResponse(clientHEAD2Response);
												
				// initializing eTags saved in correspondence file
//				String resource1ETag = getResource1ETag(resource);				
//				String resource2ETag = getResource2ETag(resource);				
				
				
				uriETagMap.put(resource1URI, resource1ETag);
				uriETagMap.put(resource2URI, resource2ETag);
			}
		}
	}
	
	private void checkAndUpdate(String resource1URI, String resource2URI, ArrayList<OSLCCrpdngPropertyPair> propertyPairList) {
		String resource1ETag = uriETagMap.get(resource1URI);
		String resource2ETag = uriETagMap.get(resource2URI);
		// check if Resource1ETag from server has changed
		ClientResponse clientHEAD1Response = getOslcRestClient(
				resource1URI).headOslcResource(null);

		// if it has changed, then retrieve values from resource1
		// properties from server
		String resource1ETagFromServer = getETagFromResponse(clientHEAD1Response);
		if (!resource1ETag.equals(resource1ETagFromServer)) {
			// perform a GET
			ClientResponse clientGET1Response = getOslcRestClient(
					resource1URI).getOslcResource();
			// retrieve resource1 entity from response
			Class<? extends AbstractResource> oslcResourceClass1 = null;
			if (resource1URI.contains("/parameters/")
					& resource1URI
							.contains("http://localhost:8181/oslc4jsimulink/services/")) {
				oslcResourceClass1 = SimulinkParameter.class;
			} else if (resource1URI.contains("/valueproperties/")
					& resource1URI
							.contains("http://localhost:8080/oslc4jmagicdraw/services/")) {
				oslcResourceClass1 = SysMLValueProperty.class;
			}
			AbstractResource oslcResource1 = clientGET1Response
					.getEntity(oslcResourceClass1);
			
			// get resource2 entity
			ClientResponse clientGET2Response = getOslcRestClient(
					resource2URI).getOslcResource();
			Class<? extends AbstractResource> oslcResourceClass2 = null;
			if (resource2URI.contains("/parameters/")
					& resource2URI
							.contains("http://localhost:8181/oslc4jsimulink/services/")) {
				oslcResourceClass2 = SimulinkParameter.class;
			} else if (resource2URI.contains("/valueproperties/")
					& resource2URI
							.contains("http://localhost:8080/oslc4jmagicdraw/services/")) {
				oslcResourceClass2 = SysMLValueProperty.class;
			}
			AbstractResource oslcResource2 = clientGET2Response
					.getEntity(oslcResourceClass2);
			AbstractResource updatedOSLCResource2 = null;
			
			// get property names whose values to retrieve					
			for (OSLCCrpdngPropertyPair oslcCrpdngPropertyPair : propertyPairList) {
//				System.out.println("Property Pair");
				
				// switch property order if property1 does not belong to resource1
				String property1URI = oslcCrpdngPropertyPair
						.getProperty1URI();
				String property2URI = oslcCrpdngPropertyPair
						.getProperty2URI();
				if(resource1URI.contains("http://localhost:8181/oslc4jsimulink/services/") & property1URI.contains("http://mathworks.com/simulink/rdf#Parameter/")){
					// order is ok
				}
				else if(resource1URI.contains("http://localhost:8080/oslc4jmagicdraw/services/") & property1URI.contains("http://omg.org/sysml/rdf#ValueProperty/")){
					// order is ok
				}
				else{
					// property order needs to be switched
					String tempURI = property1URI;
					property1URI = property2URI;
					property2URI = tempURI;
				}
				
				// get property1 name
//				System.out.println("Property1: " + property1URI);
				String property1Name = property1URI.split("/")[property1URI
						.split("/").length - 1];
				property1Name = property1Name.substring(0, 1)
						.toUpperCase()
						+ property1Name.substring(1,
								property1Name.length());						
				
				// get property2 name				
//				System.out.println("Property2: " + property2URI);
				String property2Name = property2URI.split("/")[property2URI
						.split("/").length - 1];
				property2Name = property2Name.substring(0, 1)
						.toUpperCase()
						+ property2Name.substring(1,
								property2Name.length());
				
				// property 2 value needs to be updated				
				// get new property value 
				String property1value = getPropertyValue(
						oslcResourceClass1, oslcResource1, property1Name);
				
				updatedOSLCResource2 = setPropertyValue(
						oslcResourceClass2, oslcResource2, property2Name, property1value);
				
			}
			
			
			
			// update resource2 on server, perform a PUT with ETag
			ClientResponse clientPUTResponse = getOslcRestClient(resource2URI)
					.updateOslcResourceReturnClientResponse(updatedOSLCResource2, resource2ETag);
			// check that update was sucessful and that resource2ETag did not change in the mean time
			int putStatusCode = clientPUTResponse.getStatusCode();
			if(putStatusCode == HttpServletResponse.SC_OK){
//				System.out.println("Update of property " + property2Name + " of resource " + resource2URI + " was successful ");
				System.out.println("Update of resource " + resource2URI + " was successful ");
				// save the new resource2ETag
				String resource2ETagFromServer = getETagFromResponse(clientPUTResponse);
				uriETagMap.put(resource2URI, resource2ETagFromServer);
			}
			else if(putStatusCode == HttpServletResponse.SC_PRECONDITION_FAILED){
//				System.out.println("Update of property " + property2Name + " of resource " + resource2URI + " was NOT successful ");
				System.out.println("Update of resource " + resource2URI + " was NOT successful ");
			}
			else{
				System.out.println("putStatusCode " + putStatusCode);
			}
			
			// save the new resource1ETag
			uriETagMap.put(resource1URI, resource1ETagFromServer);
			
			
		}
		else{
			System.out.println(resource1URI + " -> Still up to date!");
		}
		
	}
}
