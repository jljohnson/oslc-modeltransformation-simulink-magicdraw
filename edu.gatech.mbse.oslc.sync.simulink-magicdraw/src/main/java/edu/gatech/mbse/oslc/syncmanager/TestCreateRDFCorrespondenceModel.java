package edu.gatech.mbse.oslc.syncmanager;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import javax.xml.datatype.DatatypeConfigurationException;

import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.provider.jena.JenaModelHelper;

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

import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;

public class TestCreateRDFCorrespondenceModel {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// create an empty Model
				Model model = ModelFactory.createDefaultModel();

				// model writer
				RDFWriter modelWriter = model.getWriter("RDF/XML-ABBREV");
				// modelWriter.setProperty("attribtueQuoteChar", "'");
				// modelWriter.setProperty("showXMLDeclaration", "true");
				// modelWriter.setProperty("tab", "1");
				// modelWriter
				// .setProperty(
				// "blockRules",
				// "parseTypeLiteralPropertyElt");
				// + "parseTypeResourcePropertyElt,parseTypeCollectionPropertyElt");

				// prefix
//				String dctermsNS = "http://purl.org/dc/terms/";
//				model.setNsPrefix("dcterms", dctermsNS);

//				String oslcNS = "http://open-services.net/ns/core#";
//				model.setNsPrefix("oslc", oslcNS);

//				String rdfsNS = "http://www.w3.org/2000/01/rdf-schema#";
//				model.setNsPrefix("rdfs", rdfsNS);
				
				String rdfNS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
				model.setNsPrefix("rdf", rdfNS);

				String crspndncNS = "http://open-services.net/ns/crspndnc/";
				model.setNsPrefix("crspndnc", crspndncNS);
				
				
				
				
				
				// create the correspondence resource
				com.hp.hpl.jena.rdf.model.Resource crspndncRS = model
						.createResource("http://testProvider.com/correspondences/1");
				// reference to resource type
				Property typeProperty = model.createProperty(rdfNS + "type");
				crspndncRS.addProperty(typeProperty,
						"http://open-services.net/ns/crspndnc/ResourcePair");
				// references to corresponding resource URIs
				Property resource1URIProperty = model.createProperty(crspndncNS + "resource1URI");
				crspndncRS.addProperty(resource1URIProperty,
						"http://localhost:8181/oslc4jsimulink/services/model11/blocks/Constant");
				Property resource2URIProperty = model.createProperty(crspndncNS + "resource2URI");
				crspndncRS.addProperty(resource2URIProperty,
						"http://localhost:8080/oslc4jmagicdraw/services/model11/blocks/Constant");
				// reference to corresponding resource ETags
				Property resource1ETagProperty = model.createProperty(crspndncNS + "resource1ETag");
				crspndncRS.addProperty(resource1ETagProperty,
						"1212121212121212121");
				Property resource2ETagProperty = model.createProperty(crspndncNS + "resource2ETag");
				crspndncRS.addProperty(resource2ETagProperty,
						"665654646464646464");
				// reference to the pair of properties
				// create blank node containing property pair
				com.hp.hpl.jena.rdf.model.Resource nestedPropertyPairResource = model
						.createResource();
				Property propertyPairProperty = model.createProperty(crspndncNS + "PropertyPair");
				crspndncRS.addProperty(propertyPairProperty, nestedPropertyPairResource);
				// populate the blank node
				// note: properties have a multiplicity of one! 
				Property resource1PropertyURIProperty = model.createProperty(crspndncNS + "resource1PropertyURI");
				nestedPropertyPairResource.addProperty(resource1PropertyURIProperty, "http://mathworks.com/simulink/rdf#Parameter/name");
				Property resource2PropertyURIProperty = model.createProperty(crspndncNS + "resource2PropertyURI");
				nestedPropertyPairResource.addProperty(resource2PropertyURIProperty, "http://omg.org/sysml/rdf#NamedElement/name");
				
				
				// reference to the pair of properties
				// create blank node containing property pair
				com.hp.hpl.jena.rdf.model.Resource nestedPropertyPairResource2 = model
						.createResource();				
				crspndncRS.addProperty(propertyPairProperty, nestedPropertyPairResource2);
				// populate the blank node
				// note: properties have a multiplicity of one! 				
				nestedPropertyPairResource2.addProperty(resource1PropertyURIProperty, "http://mathworks.com/simulink/rdf#Parameter/value");	
				nestedPropertyPairResource2.addProperty(resource2PropertyURIProperty, "http://omg.org/sysml/rdf#ValueProperty/defaultValue");
				
				System.out.println("************   RDF/XML-ABBREV *******************");
				model.write(System.out, "RDF/XML-ABBREV");
				
				OutputStream out;
				try {
					out = new FileOutputStream("correspondence" + ".rdf");
					modelWriter.write(model, out, "http://example.org/");
					out.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				
	}

}
