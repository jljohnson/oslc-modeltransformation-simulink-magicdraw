package edu.gatech.mbse.oslc.transformations;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;
import edu.gatech.mbse.oslc.wink.clients.OslcRestClient;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.QueryCapability;
import org.eclipse.lyo.oslc4j.core.model.Service;
import org.eclipse.lyo.oslc4j.core.model.ServiceProvider;
import org.eclipse.lyo.oslc4j.core.model.ServiceProviderCatalog;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFWriter;












import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnector;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnectorEnd;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFlowProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLItemFlow;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPackage;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPartProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkBlock;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkLine;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkModel;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;

public class Simulink2MagicDrawTransformation {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	static String simulinkModelToRetrieveID = "model11.slx---model11";
	static String magicDrawProjectToCreateID = "TestProject2";
//	static String magicDrawProjectToCreateID = "httpskoneksys118080svnmagicdrawrepository---TestProject2";

	static OslcRestClient oslcSimulinkServiceProviderCatalogRestClient = null;
	static OslcRestClient oslcSimulinkModelRestClient = null;
	static OslcRestClient oslcSimulinkBlocksRestClient = null;
	static OslcRestClient oslcSimulinkLinesRestClient = null;
	static OslcRestClient oslcSimulinkParametersRestClient = null;

	static OslcRestClient oslcSysMLBlockCreationRestClient = null;
	static OslcRestClient oslcSysMLPartCreationRestClient = null;
	static OslcRestClient oslcSysMLConnectorEndCreationRestClient = null;
	static OslcRestClient oslcSysMLConnectorCreationRestClient = null;
	static OslcRestClient oslcSysMLPortCreationRestClient = null;
	static OslcRestClient oslcSysMLValuePropertyCreationRestClient = null;
	static OslcRestClient oslcSysMLItemFlowCreationRestClient = null;
	static OslcRestClient oslcSysMLPackageCreationRestClient = null;
	static OslcRestClient oslcSysMLInterfaceBlockCreationRestClient = null;
	static OslcRestClient oslcSysMLFlowPropertyCreationRestClient = null;

	static URI sysMLInputInterfaceBlockURI = null;
	static URI sysMLOutputInterfaceBlockURI = null;

	static Map<String, String> simBlockQualifiedNameExtPortNumberMap = new HashMap<String, String>();
	static Map<String, String> simExtPortNumberBlockQualifiedNameMap = new HashMap<String, String>();

	static String simulinkBaseHTTPURI = "http://localhost:8181/oslc4jsimulink";
	static String magicDrawBaseHTTPURI = "http://localhost:8080/oslc4jmagicdraw";
	static String correspondenceModelFileName = "SimulinkMagicDrawCorrespondence";

	static SysMLPackage sysmlSimulinkLibraryPackage = null;
	static Model model = null;
	static String rdfNS = null;
	static String crspndncNS = null;
	static ArrayList<URI> noPartPropertiesList = null;

	public static void main(String[] args) {
		simBlockQualifiedNameExtPortNumberMap.clear();
		simExtPortNumberBlockQualifiedNameMap.clear();

		registerOSLCSimulinkRESTClients();
		registerOSLCMagicDrawSysMLRESTClients();

		setUpCorrespondenceModel();

		// retrieving and converting Simulink model
		final SimulinkModel simulinkModel = oslcSimulinkModelRestClient
				.getOslcResource(SimulinkModel.class);
		System.out.println(simulinkModel.getName());

		// assumption is that the MagicDraw project already
		// exists
		initializingSysMLModel();

		// mapping Simulink blocks into SysML blocks
		final SimulinkBlock[] simulinkBlocks = oslcSimulinkBlocksRestClient
				.getOslcResource(SimulinkBlock[].class);
		mappingSimulinkBlocksIntoSysMLBlocks(simulinkBlocks);

		// mapping Simulink blocks into SysML parts + retrieving and converting
		// Simulink block parameters
		final SimulinkParameter[] simulinkParameters = oslcSimulinkParametersRestClient
				.getOslcResource(SimulinkParameter[].class);
		mappingSimulinkBlocksIntoSysMLParts(simulinkBlocks, simulinkParameters,
				simulinkModel);

		// mapping Simulink lines into SysML connectors
		// retrieving and converting Simulink lines
		final SimulinkLine[] simulinkLines = oslcSimulinkLinesRestClient
				.getOslcResource(SimulinkLine[].class);
		mappingSimulinkLinesIntoSysMLConnectors(simulinkLines, simulinkBlocks,
				simulinkModel);

		// saving Apache Jena Model containing correspondences on disk
		saveCorrespondenceModel();
		
		System.out.println("Simulink to MagicDraw done.");
	}

	private static void saveCorrespondenceModel() {
		RDFWriter modelWriter = model.getWriter("RDF/XML-ABBREV");
		OutputStream out;
		try {
			out = new FileOutputStream(correspondenceModelFileName + ".rdf");			
			modelWriter.write(model, out, null);
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void mappingSimulinkLinesIntoSysMLConnectors(
			SimulinkLine[] simulinkLines, SimulinkBlock[] simulinkBlocks,
			SimulinkModel simulinkModel) {
		// make sure ports are not created twice
		ArrayList<URI> createdPortURIs = new ArrayList<URI>();

		// create the sysml connector and sysml item flow
		for (SimulinkLine simulinkLine : simulinkLines) {

			try {
				SysMLConnector sysmlConnector = new SysMLConnector();
				String sourcePortURI = simulinkLine.getSourcePort().toString();
				String sourcePortID = sourcePortURI.replace(simulinkBaseHTTPURI
						+ "/services/" + simulinkModelToRetrieveID
						+ "/outputports/", "");

				// simulink lines can have multiple branches (targets)
				for (Link targetPortLink : simulinkLine.getTargetPorts()) {
					String targetPortURI = targetPortLink.getValue().toString();
					String targetPortID = targetPortURI.replaceAll(
							simulinkBaseHTTPURI + "/services/"
									+ simulinkModelToRetrieveID
									+ "/inputports/", "");
					String lineID = sourcePortID + "---" + targetPortID;
					sysmlConnector.setName(lineID);
					URI sysmlConnectorURI = URI.create(magicDrawBaseHTTPURI
							+ "/services/" + magicDrawProjectToCreateID
							+ "/connectors/" + lineID);
					sysmlConnector.setAbout(sysmlConnectorURI);

					// *************** SOURCE PORT ***************

					// convert Simulink outputport into SysML source port
					// get port name
					String sourcePortQualifiedName = sourcePortID.split("/")[sourcePortID
							.split("/").length - 1];
					String sourcePortName = sourcePortQualifiedName
							.split("::outport::")[sourcePortQualifiedName
							.split("::outport::").length - 1];

					// this gives you the block owning the source port
					String sourceBlockQualifiedName = sourcePortQualifiedName
							.split("::outport::")[0];
					String sysmlSourcePortQualifiedName = sourceBlockQualifiedName
							.replaceAll("::", "_")
							+ "_SimBlock"
							+ "::"
							+ "out"
							+ sourcePortName;

					// source part owning port URI to know if port is on ibd
					// boundary or on a part
					URI sourcePartWithPortURI = getPartURI(
							sourceBlockQualifiedName, simulinkModel);

					URI sysmlSourcePortURI = null;
					URI sysMLSourcePortOwnerURI = null;
					// URI sysMLSourceConjugatePortURI = null;

					// source port on boundary URI
					boolean isInternalSourcePort = false;
					if (noPartPropertiesList.contains(sourcePartWithPortURI)) {
						// change sysmlSourcePortURI and
						// sysMLSourcePortOwnerURI:
						sysmlSourcePortURI = getPortOnBoundaryURI(
								sourceBlockQualifiedName, simulinkModel);
						sysMLSourcePortOwnerURI = getOwnerURI(
								sourceBlockQualifiedName, simulinkModel);

						// there is the risk that the internal port resets the
						// type of the (same) external port as Output although
						// the type should be Input
						isInternalSourcePort = true;

						// // no conjugate port URI
						// // get the conjugate Port uri
						// String externalPortNumber =
						// simBlockQualifiedNameExtPortNumberMap
						// .get(sourceBlockQualifiedName);
						// sysMLSourceConjugatePortURI =
						// getSourceConjugatePortOnBoundaryURI(
						// sourceBlockQualifiedName, simulinkModel,
						// externalPortNumber);
					}
					// source port on part URI
					else {

						sysMLSourcePortOwnerURI = getPortOnPartOwnerURI(
								sourceBlockQualifiedName, simulinkBlocks);

						// if port belongs to subsystem block, give it a
						// different uri
						String sourcePortOwnerName = sourceBlockQualifiedName
								.split("::outport::")[0];
						URI simulinkLSourcePortOwnerURI = URI
								.create(simulinkBaseHTTPURI + "/services/"
										+ simulinkModelToRetrieveID
										+ "/blocks/" + sourcePortOwnerName);
						OslcRestClient oslcSimulinkSourceBlockRestClient = new OslcRestClient(
								PROVIDERS, simulinkLSourcePortOwnerURI);
						SimulinkBlock sourceSimulinkBlock = oslcSimulinkSourceBlockRestClient
								.getOslcResource(SimulinkBlock.class);
						if (sourceSimulinkBlock.getType().equals("SubSystem")) {
							String sourceConjugateBlockQualifiedName = simExtPortNumberBlockQualifiedNameMap
									.get(sourcePortQualifiedName);
							sysmlSourcePortURI = getPortOnBoundaryURI(
									sourceConjugateBlockQualifiedName,
									simulinkModel);

						} else {
							sysmlSourcePortURI = getPortOnPartURI(sysmlSourcePortQualifiedName);
						}

						// // no conjugate port URI
						// // get the conjugate Port uri
						// String sourceConjugateBlockQualifiedName =
						// simExtPortNumberBlockQualifiedNameMap
						// .get(sourcePortQualifiedName);
						// if (sourceConjugateBlockQualifiedName != null) {
						// sysMLSourceConjugatePortURI = getPortOnBoundaryURI(
						// sourceConjugateBlockQualifiedName,
						// simulinkModel);
						// }
					}

					// owner of connector is owner of block owning the source
					// port
					// all SysML blocks (either SimModel or SimBlock) are under
					// the
					// top-level SysML model element
					URI connectorOwnerURI = getOwnerURI(
							sourceBlockQualifiedName, simulinkModel);
					sysmlConnector.setOwner(connectorOwnerURI);

					// if
					// (!createdPortURIs.contains(sysMLSourceConjugatePortURI))
					// {

					// change port uri to conjugate port uri for consistency
					// if (sysMLSourceConjugatePortURI != null) {
					// if (sysmlSourcePortURI.toString().contains("::out")) {
					// sysmlSourcePortURI = sysMLSourceConjugatePortURI;
					// }
					// }

					// create the source sysml port
					SysMLPort sysMLSourcePort = new SysMLPort();
					sysMLSourcePort.setName(sourcePortName);
					sysMLSourcePort.setAbout(sysmlSourcePortURI);
					if (isInternalSourcePort) {
						sysMLSourcePort.setType(sysMLInputInterfaceBlockURI);
					} else {
						sysMLSourcePort.setType(sysMLOutputInterfaceBlockURI);
					}
					sysMLSourcePort.setOwner(sysMLSourcePortOwnerURI);
					oslcSysMLPortCreationRestClient
							.addOslcResource(sysMLSourcePort);
					System.out.println(simulinkLine.getSourcePort());
					System.out.println(sysmlSourcePortURI);

					createdPortURIs.add(sysmlSourcePortURI);

					// create the source sysml connector end
					SysMLConnectorEnd sysmlSourceConnectorEnd = new SysMLConnectorEnd();
					sysmlSourceConnectorEnd.setRole(sysmlSourcePortURI);

					if (!noPartPropertiesList.contains(sourcePartWithPortURI)) {
						sysmlSourceConnectorEnd
								.setPartWithPort(sourcePartWithPortURI);
					}
					URI sysmlSourceConnectorEndURI = URI
							.create(magicDrawBaseHTTPURI + "/services/"
									+ magicDrawProjectToCreateID
									+ "/connectorends/" + lineID + "::"
									+ sourcePortID);
					sysmlSourceConnectorEnd.setOwner(sysmlConnectorURI);
					sysmlSourceConnectorEnd
							.setAbout(sysmlSourceConnectorEndURI);
					oslcSysMLConnectorEndCreationRestClient
							.addOslcResource(sysmlSourceConnectorEnd);
					System.out.println(sysmlSourceConnectorEndURI);

					// *************** TARGET PORT ***************

					// convert Simulink inputport into SysML target port
					// get port name
					String targetPortQualifiedName = targetPortID.split("/")[targetPortID
							.split("/").length - 1];
					String targetPortName = targetPortQualifiedName
							.split("::inport::")[targetPortQualifiedName
							.split("::inport::").length - 1];

					// get target port owner URI(type of Simulink block
					// instance/
					// type of
					// SysML part which owns the port)
					String targetBlockQualifiedName = targetPortQualifiedName
							.split("::inport::")[0];
					String sysmlTargetPortQualifiedName = targetBlockQualifiedName
							.replaceAll("::", "_")
							+ "_SimBlock"
							+ "::"
							+ "in"
							+ targetPortName;

					// target part owning port URI
					URI targetPartWithPortURI = getPartURI(
							targetBlockQualifiedName, simulinkModel);

					URI sysmlTargetPortURI = null;
					URI sysMLTargetPortOwnerURI = null;
					// URI sysMLTargetConjugatePortURI = null;

					// target port on boundary URI
					if (noPartPropertiesList.contains(targetPartWithPortURI)) {
						// change sysmlTargetPortURI and
						// sysMLTargetPortOwnerURI:
						sysmlTargetPortURI = getPortOnBoundaryURI(
								targetBlockQualifiedName, simulinkModel);
						sysMLTargetPortOwnerURI = getOwnerURI(
								targetBlockQualifiedName, simulinkModel);

						// // get the conjugate Port uri
						// String externalPortNumber =
						// simBlockQualifiedNameExtPortNumberMap
						// .get(targetBlockQualifiedName);
						// sysMLTargetConjugatePortURI =
						// getTargetConjugatePortOnBoundaryURI(
						// targetBlockQualifiedName, simulinkModel,
						// externalPortNumber);
					}
					// target port on part URI
					else {
						// sysmlTargetPortURI =
						// getPortOnPartURI(sysmlTargetPortQualifiedName);

						sysMLTargetPortOwnerURI = getPortOnPartOwnerURI(
								targetBlockQualifiedName, simulinkBlocks);

						// if port belongs to subsystem block, give it a
						// different uri
						String targetPortOwnerName = targetBlockQualifiedName
								.split("::inport::")[0];
						URI simulinkLTargetPortOwnerURI = URI
								.create(simulinkBaseHTTPURI + "/services/"
										+ simulinkModelToRetrieveID
										+ "/blocks/" + targetPortOwnerName);
						OslcRestClient oslcSimulinkTargetBlockRestClient = new OslcRestClient(
								PROVIDERS, simulinkLTargetPortOwnerURI);
						SimulinkBlock targetSimulinkBlock = oslcSimulinkTargetBlockRestClient
								.getOslcResource(SimulinkBlock.class);
						if (targetSimulinkBlock.getType().equals("SubSystem")) {
							String targetConjugateBlockQualifiedName = simExtPortNumberBlockQualifiedNameMap
									.get(targetPortQualifiedName);
							sysmlTargetPortURI = getPortOnBoundaryURI(
									targetConjugateBlockQualifiedName,
									simulinkModel);
						} else {
							sysmlTargetPortURI = getPortOnPartURI(sysmlTargetPortQualifiedName);
						}

						// // get the conjugate Port uri
						// String targetConjugateBlockQualifiedName =
						// simExtPortNumberBlockQualifiedNameMap
						// .get(targetPortQualifiedName);
						// if (targetConjugateBlockQualifiedName != null) {
						// sysMLTargetConjugatePortURI = getPortOnBoundaryURI(
						// targetConjugateBlockQualifiedName,
						// simulinkModel);
						// }
					}

					// if
					// (!createdPortURIs.contains(sysMLTargetConjugatePortURI))
					// {
					// create the target sysml port
					SysMLPort sysMLTargetPort = new SysMLPort();
					sysMLTargetPort.setName(targetPortName);
					sysMLTargetPort.setAbout(sysmlTargetPortURI);
					sysMLTargetPort.setType(sysMLInputInterfaceBlockURI);
					sysMLTargetPort.setOwner(sysMLTargetPortOwnerURI);
					oslcSysMLPortCreationRestClient
							.addOslcResource(sysMLTargetPort);
					System.out.println(simulinkLine.getTargetPorts());
					System.out.println(sysmlTargetPortURI);
					createdPortURIs.add(sysmlTargetPortURI);
					// }
					// else {
					// System.out.println("redundant port");
					// System.out.println("trying to create port: "
					// + sysmlTargetPortURI);
					// System.out.println("conjugate port already created: "
					// + sysMLTargetConjugatePortURI);
					// }

					// create the target sysml connector end
					SysMLConnectorEnd sysmlTargetConnectorEnd = new SysMLConnectorEnd();
					// if
					// (!createdPortURIs.contains(sysMLTargetConjugatePortURI))
					// {
					sysmlTargetConnectorEnd.setRole(sysmlTargetPortURI);
					// }
					// else {
					// sysmlTargetConnectorEnd
					// .setRole(sysMLTargetConjugatePortURI);
					// }

					// if port is on boundary of IBD, PartWithPort = null
					if (!noPartPropertiesList.contains(targetPartWithPortURI)) {
						sysmlTargetConnectorEnd
								.setPartWithPort(targetPartWithPortURI);
					}
					URI sysmlTargetConnectorEndURI = URI
							.create(magicDrawBaseHTTPURI + "/services/"
									+ magicDrawProjectToCreateID
									+ "/connectorends/" + lineID + "::"
									+ targetPortID);
					sysmlTargetConnectorEnd.setOwner(sysmlConnectorURI);
					sysmlTargetConnectorEnd
							.setAbout(sysmlTargetConnectorEndURI);
					oslcSysMLConnectorEndCreationRestClient
							.addOslcResource(sysmlTargetConnectorEnd);
					System.out.println(sysmlTargetConnectorEndURI);

					Link[] connectorEnds = new Link[2];
					connectorEnds[0] = new Link(sysmlSourceConnectorEndURI);
					connectorEnds[1] = new Link(sysmlTargetConnectorEndURI);
					sysmlConnector.setEnds(connectorEnds);
					oslcSysMLConnectorCreationRestClient
							.addOslcResource(sysmlConnector);
					System.out.println(simulinkLine.getAbout());
					System.out.println(sysmlConnector.getAbout());

					// create sysml itemflow
					SysMLItemFlow sysMLItemFlow = new SysMLItemFlow();
					URI itemFlowSourcePortURI = sysmlSourceConnectorEnd
							.getRole();
					// URI itemFlowSourcePortURI =
					// URI.create(magicDrawBaseHTTPURI + "/services/"
					// + magicDrawProjectToCreateID + "/ports/" +
					// sysmlSourcePortQualifiedName);
					sysMLItemFlow.setInformationSource(itemFlowSourcePortURI);
					URI itemFlowTargetPortURI = sysmlTargetConnectorEnd
							.getRole();
					// URI itemFlowTargetPortURI =
					// URI.create(magicDrawBaseHTTPURI + "/services/"
					// + magicDrawProjectToCreateID + "/ports/" +
					// sysmlTargetPortQualifiedName);
					sysMLItemFlow.setInformationTarget(itemFlowTargetPortURI);
					URI sysMLItemFlowURI = URI.create(magicDrawBaseHTTPURI
							+ "/services/" + magicDrawProjectToCreateID
							+ "/itemflows/" + lineID);
					sysMLItemFlow.setAbout(sysMLItemFlowURI);
					sysMLItemFlow.setRealizingConnector(sysmlConnector
							.getAbout());
					oslcSysMLItemFlowCreationRestClient
							.addOslcResource(sysMLItemFlow);

				}

			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	private static void setUpCorrespondenceModel() {
		// Apache Jena Model and prefixes to capture correspondences between
		// Simulink and MagicDraw elements
		model = ModelFactory.createDefaultModel();
		rdfNS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
		model.setNsPrefix("rdf", rdfNS);
		crspndncNS = "http://open-services.net/ns/crspndnc/";
		model.setNsPrefix("crspndnc", crspndncNS);

	}

	private static void mappingSimulinkBlocksIntoSysMLParts(
			SimulinkBlock[] simulinkBlocks,
			SimulinkParameter[] simulinkParameters, SimulinkModel simulinkModel) {
		// retrieving and converting Simulink blocks into SysML parts (SysML
		// blocks must have been created beforehand)
		noPartPropertiesList = new ArrayList<URI>();
		for (SimulinkBlock simulinkBlock : simulinkBlocks) {
			try {
				// create SysML part corresponding to the Simulink block
				// instance
				SysMLPartProperty sysMLPart = new SysMLPartProperty();
				sysMLPart.setName(simulinkBlock.getName());
				sysMLPart.setLower("1");
				sysMLPart.setUpper("1");
				URI simulinkBlockURI = simulinkBlock.getAbout();
				String partQualifiedName = simulinkBlockURI.toString().replace(
						simulinkBaseHTTPURI + "/services/"
								+ simulinkModelToRetrieveID + "/blocks/", "");
				URI sysmlPartURI = getPartURI(partQualifiedName, simulinkModel);
				sysMLPart.setAbout(sysmlPartURI);
				String partOwnerQualifiedName = simulinkBlockURI.toString()
						.replace(
								simulinkBaseHTTPURI + "/services/"
										+ simulinkModelToRetrieveID
										+ "/blocks/", "");
				URI sysMLPartOwnerURI = getOwnerURI(partOwnerQualifiedName,
						simulinkModel);
				sysMLPart.setOwner(sysMLPartOwnerURI);
				URI sysMLBlockOfInstanceURI = URI.create(magicDrawBaseHTTPURI
						+ "/services/" + magicDrawProjectToCreateID
						+ "/blocks/" + getSysMLBlockName(simulinkBlock));
				sysMLPart.setType(sysMLBlockOfInstanceURI);

				String simulinkBlockQualifiedName = simulinkBlock.getAbout()
						.toString();
				simulinkBlockQualifiedName = simulinkBlockQualifiedName
						.replaceAll(simulinkBaseHTTPURI + "/services/"
								+ simulinkModelToRetrieveID + "/blocks/", "");

				// map block parameters
				for (SimulinkParameter simulinkParameter : getSimulinkBlockParameters(simulinkBlockQualifiedName)) {
					String simulinkParameterName = simulinkParameter.getName();
					String simulinkParameterValue = simulinkParameter
							.getValue();

					// filter for only mapping specific parameters
					if (!(simulinkParameterName.equals("ModelName")
							| simulinkParameterName.equals("Inputs") | simulinkParameterName
								.equals("Value"))) {
						continue;
					}
					if (simulinkParameterName.equals("Inputs")
							& !simulinkBlock.getType().equals("Sum")) {
						continue;
					}
					if (simulinkParameterName.equals("Value")
							& !simulinkBlock.getType().equals("Constant")) {
						continue;
					}

					// add sysml value property to SysML block representing
					// Simulink library block
					SysMLValueProperty sysMLValuePropertyOfLibraryBlock = new SysMLValueProperty();
					sysMLValuePropertyOfLibraryBlock
							.setName(simulinkParameterName);
					sysMLValuePropertyOfLibraryBlock.setLower("1");
					sysMLValuePropertyOfLibraryBlock.setUpper("1");
					URI sysMLValuePropertyOfLibraryBlockURI = URI
							.create(magicDrawBaseHTTPURI
									+ "/services/"
									+ magicDrawProjectToCreateID
									+ "/valueproperties/"
									+ sysmlSimulinkLibraryPackage.getName()
											.replaceAll("\\n", "-")
											.replaceAll(" ", "_") + "::"
									+ simulinkBlock.getType() + "::"
									+ simulinkParameterName);
					sysMLValuePropertyOfLibraryBlock
							.setAbout(sysMLValuePropertyOfLibraryBlockURI);
					sysMLValuePropertyOfLibraryBlock.setOwner(URI
							.create(magicDrawBaseHTTPURI
									+ "/services/"
									+ magicDrawProjectToCreateID
									+ "/blocks/"
									+ sysmlSimulinkLibraryPackage.getName()
											.replaceAll("\\n", "-")
											.replaceAll(" ", "_") + "::"
									+ simulinkBlock.getType()));
					oslcSysMLValuePropertyCreationRestClient
							.addOslcResource(sysMLValuePropertyOfLibraryBlock);
					System.out.println(sysMLValuePropertyOfLibraryBlockURI);

					// add sysml value property to SysML block representing
					// Simulink block instance
					SysMLValueProperty sysMLValueProperty = new SysMLValueProperty();
					sysMLValueProperty.setName(simulinkParameterName);
					sysMLValueProperty.setLower("1");
					sysMLValueProperty.setUpper("1");
					sysMLValueProperty.setDefaultValue(simulinkParameterValue);
					URI sysMLValuePropertyURI = URI.create(magicDrawBaseHTTPURI
							+ "/services/" + magicDrawProjectToCreateID
							+ "/valueproperties/"
							+ getSysMLBlockName(simulinkBlock) + "::"
							+ simulinkParameterName);
					sysMLValueProperty.setAbout(sysMLValuePropertyURI);
					sysMLValueProperty.setOwner(sysMLBlockOfInstanceURI);
					oslcSysMLValuePropertyCreationRestClient
							.addOslcResource(sysMLValueProperty);
					System.out.println(sysMLValuePropertyURI);

					// save correspondence between Simulink parameter and SysML
					// value property
					// create the correspondence resource
					String correspondenceID = sysMLValueProperty.getAbout()
							.toString();
					com.hp.hpl.jena.rdf.model.Resource crspndncRS = model
							.createResource("http://testProvider.com/correspondences/"
									+ correspondenceID);
					// reference to resource type
					Property typeProperty = model
							.createProperty(rdfNS + "type");
					crspndncRS
							.addProperty(typeProperty,
									"http://open-services.net/ns/crspndnc/ResourcePair");
					// references to corresponding resource URIs
					Property resource1URIProperty = model
							.createProperty(crspndncNS + "resource1URI");
					crspndncRS.addProperty(resource1URIProperty,
							simulinkParameter.getAbout().toString());
					Property resource2URIProperty = model
							.createProperty(crspndncNS + "resource2URI");
					crspndncRS.addProperty(resource2URIProperty,
							sysMLValueProperty.getAbout().toString());
					// reference to corresponding resource ETags
					ClientResponse clientHEADResponse1 = getOslcRestClient(
							simulinkParameter.getAbout().toString())
							.headOslcResource(null);
					String resource1ETag = getETagFromResponse(clientHEADResponse1);
					ClientResponse clientHEADResponse2 = getOslcRestClient(
							sysMLValueProperty.getAbout().toString())
							.headOslcResource(null);
					String resource2ETag = getETagFromResponse(clientHEADResponse2);
					Property resource1ETagProperty = model
							.createProperty(crspndncNS + "resource1ETag");
					crspndncRS
							.addProperty(resource1ETagProperty, resource1ETag);
					Property resource2ETagProperty = model
							.createProperty(crspndncNS + "resource2ETag");
					crspndncRS
							.addProperty(resource2ETagProperty, resource2ETag);
					// reference to the pair of properties
					// create blank node containing property pair
					com.hp.hpl.jena.rdf.model.Resource nestedPropertyPairResource = model
							.createResource();
					Property propertyPairProperty = model
							.createProperty(crspndncNS + "PropertyPair");
					crspndncRS.addProperty(propertyPairProperty,
							nestedPropertyPairResource);
					// populate the blank node
					// note: properties have a multiplicity of one!
					Property resource1PropertyURIProperty = model
							.createProperty(crspndncNS + "resource1PropertyURI");
					nestedPropertyPairResource.addProperty(
							resource1PropertyURIProperty,
							"http://mathworks.com/simulink/rdf#Parameter/name");
					Property resource2PropertyURIProperty = model
							.createProperty(crspndncNS + "resource2PropertyURI");
					nestedPropertyPairResource.addProperty(
							resource2PropertyURIProperty,
							"http://omg.org/sysml/rdf#NamedElement/name");
					com.hp.hpl.jena.rdf.model.Resource nestedPropertyPairResource2 = model
							.createResource();
					crspndncRS.addProperty(propertyPairProperty,
							nestedPropertyPairResource2);
					nestedPropertyPairResource2
							.addProperty(resource1PropertyURIProperty,
									"http://mathworks.com/simulink/rdf#Parameter/value");
					nestedPropertyPairResource2
							.addProperty(resource2PropertyURIProperty,
									"http://omg.org/sysml/rdf#ValueProperty/defaultValue");
				}

				// Simulink block ports are represented as blocks
				// in the internal Simulink model of the block (e.g. subsystem
				// block)
				// SysML ports on parts are represented as ports in
				// the IBD model of the block (eg. subsystem block)
				// All Simulink lines connecting Simulink block ports are mapped
				// into SysML connectors connecting SysML ports.
				if (simulinkBlock.getType().equals("Inport")
						| simulinkBlock.getType().equals("Outport")) {
					// only create part if block is top-level block and not an
					// internal block
					if (!partOwnerQualifiedName.contains("::")) {
						oslcSysMLPartCreationRestClient
								.addOslcResource(sysMLPart);
						System.out.println(sysmlPartURI);
					} else {
						// create list of URI partproperties which should not
						// exist
						// when ports are created, if partwithportURI of a port
						// is on the list, then handle it differently!!
						noPartPropertiesList.add(sysmlPartURI);
						System.out
								.println("inport/outport block which should not be a SysML part: "
										+ sysmlPartURI);
					}

					// get the port number of the subsystem port
					for (SimulinkParameter simulinkParameter : simulinkParameters) {
						if (simulinkParameter
								.getAbout()
								.toString()
								.startsWith(
										simulinkBaseHTTPURI + "/services/"
												+ simulinkModelToRetrieveID
												+ "/parameters/"
												+ simulinkBlockQualifiedName)) {
							if (simulinkParameter.getName().equals("Port")) {
								String externalPortNumber = simulinkParameter
										.getValue();
								simBlockQualifiedNameExtPortNumberMap.put(
										simulinkBlockQualifiedName,
										externalPortNumber);

								// get modified simulinkBlockQualifiedName (with
								// one less part)
								String[] elementQualifiedNameSegments = simulinkBlockQualifiedName
										.split("::");
								String topLevelSimulinkBlockQualifiedName = null;
								// get the owner qualified name
								// distinguish between block describing the main
								// model and regular
								// blocks
								if (elementQualifiedNameSegments.length > 1) {
									for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
										if (i == 0) {
											topLevelSimulinkBlockQualifiedName = elementQualifiedNameSegments[i];
										} else {
											topLevelSimulinkBlockQualifiedName = topLevelSimulinkBlockQualifiedName
													+ "::"
													+ elementQualifiedNameSegments[i];
										}
									}
								}

								if (simulinkBlock.getType().equals("Inport")) {
									simExtPortNumberBlockQualifiedNameMap.put(
											topLevelSimulinkBlockQualifiedName
													+ "::inport::"
													+ externalPortNumber,
											simulinkBlockQualifiedName);
								} else if (simulinkBlock.getType().equals(
										"Outport")) {
									simExtPortNumberBlockQualifiedNameMap.put(
											topLevelSimulinkBlockQualifiedName
													+ "::outport::"
													+ externalPortNumber,
											simulinkBlockQualifiedName);
								}
							}
						}
					}
				} else {
					oslcSysMLPartCreationRestClient.addOslcResource(sysMLPart);
					System.out.println(sysmlPartURI);
				}

			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private static void mappingSimulinkBlocksIntoSysMLBlocks(
			SimulinkBlock[] simulinkBlocks) {
		// retrieving and converting Simulink blocks (block types and block
		// instances)
		for (SimulinkBlock simulinkBlock : simulinkBlocks) {
			try {

				// create SysML block corresponding to the Simulink block type
				SysMLBlock sysMLBlock = new SysMLBlock();
				sysMLBlock.setName(simulinkBlock.getType());
				URI sysMLBlockURI = URI.create(magicDrawBaseHTTPURI
						+ "/services/"
						+ magicDrawProjectToCreateID
						+ "/blocks/"
						+ sysmlSimulinkLibraryPackage.getName()
								.replaceAll("\\n", "-").replaceAll(" ", "_")
						+ "::" + simulinkBlock.getType());
				sysMLBlock.setAbout(sysMLBlockURI);
				sysMLBlock.setOwner(sysmlSimulinkLibraryPackage.getAbout());
				oslcSysMLBlockCreationRestClient.addOslcResource(sysMLBlock);
				System.out.println(simulinkBlock.getAbout());
				System.out.println(sysMLBlockURI);

				// create SysML block corresponding to the Simulink block
				// instance
				// in Simulink, block instance and block type can have the same
				// name
				// since both will be mapped to SysML blocks, the
				// instance-specific block needs a special name
				SysMLBlock sysMLBlockOfInstance;
				sysMLBlockOfInstance = new SysMLBlock();
				sysMLBlockOfInstance.setName(getSysMLBlockName(simulinkBlock));
				URI sysMLBlockOfInstanceURI = URI.create(magicDrawBaseHTTPURI
						+ "/services/" + magicDrawProjectToCreateID
						+ "/blocks/" + getSysMLBlockName(simulinkBlock));
				sysMLBlockOfInstance.setAbout(sysMLBlockOfInstanceURI);
				sysMLBlockOfInstance.setOwner(URI.create(magicDrawBaseHTTPURI
						+ "/services/" + magicDrawProjectToCreateID + "/model/"
						+ "Data"));
				Link inheritedBlockLink = new Link(sysMLBlockURI);
				// inheritance between instance-specific block and block type
				Link[] inheritedBlocksLinkArray = new Link[1];
				inheritedBlocksLinkArray[0] = inheritedBlockLink;
				sysMLBlockOfInstance
						.setInheritedBlocks(inheritedBlocksLinkArray);
				oslcSysMLBlockCreationRestClient
						.addOslcResource(sysMLBlockOfInstance);
				System.out.println(sysMLBlockOfInstanceURI);

			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private static void initializingSysMLModel() {
		// create SysML block whose IBD corresponds to Simulink model
		// A block needs to be created to own the parts corresponding to
		// Simulink block instances
		URI sysMLModelBlockURI = null;
		try {
			SysMLBlock sysMLModelBlock = new SysMLBlock();
			sysMLModelBlock.setName(magicDrawProjectToCreateID + "_SimModel");
			sysMLModelBlockURI = URI.create(magicDrawBaseHTTPURI + "/services/"
					+ magicDrawProjectToCreateID + "/blocks/"
					+ magicDrawProjectToCreateID + "_SimModel");
			sysMLModelBlock.setAbout(sysMLModelBlockURI);
			sysMLModelBlock.setOwner(URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID + "/model/"
					+ "Data"));
			oslcSysMLBlockCreationRestClient.addOslcResource(sysMLModelBlock);

			// create SysML package for Simulink block types
			sysmlSimulinkLibraryPackage = new SysMLPackage();
			sysmlSimulinkLibraryPackage.setName("Simulink Library Blocks");

			URI sysmlPackageURI = URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID + "/packages/"
					+ "Simulink_Library_Blocks");
			sysmlSimulinkLibraryPackage.setAbout(sysmlPackageURI);
			URI modelURI = URI.create(magicDrawBaseHTTPURI + "/services/"
					+ magicDrawProjectToCreateID + "/model");
			sysmlSimulinkLibraryPackage.setOwner(modelURI);

			oslcSysMLPackageCreationRestClient
					.addOslcResource(sysmlSimulinkLibraryPackage);
			System.out.println(sysmlSimulinkLibraryPackage.getAbout());

			// creating Input and Output SysML interface blocks
			SysMLInterfaceBlock sysMLInputInterfaceBlock = new SysMLInterfaceBlock();
			sysMLInputInterfaceBlock.setName("Input");
			sysMLInputInterfaceBlockURI = URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID
					+ "/interfaceblocks/Simulink_Library_Blocks::" + "Input");
			sysMLInputInterfaceBlock.setAbout(sysMLInputInterfaceBlockURI);
			sysMLInputInterfaceBlock.setOwner(URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID + "/packages/"
					+ "Simulink_Library_Blocks"));
			oslcSysMLInterfaceBlockCreationRestClient
					.addOslcResource(sysMLInputInterfaceBlock);

			SysMLFlowProperty sysMLFlowProperty = new SysMLFlowProperty();
			sysMLFlowProperty.setName("in");
			sysMLFlowProperty.setDirection("in");
			URI sysMLInFlowPropertyURI = URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID
					+ "/flowproperty/Simulink_Library_Blocks::" + "Input::in");
			sysMLFlowProperty.setAbout(sysMLInFlowPropertyURI);
			sysMLFlowProperty.setOwner(URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID
					+ "/interfaceblocks/" + "Simulink_Library_Blocks::Input"));
			oslcSysMLFlowPropertyCreationRestClient
					.addOslcResource(sysMLFlowProperty);

			SysMLInterfaceBlock sysMLOutputInterfaceBlock = new SysMLInterfaceBlock();
			sysMLOutputInterfaceBlock.setName("Output");
			sysMLOutputInterfaceBlockURI = URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID
					+ "/interfaceblocks/Simulink_Library_Blocks::" + "Output");
			sysMLOutputInterfaceBlock.setAbout(sysMLOutputInterfaceBlockURI);
			sysMLOutputInterfaceBlock.setOwner(URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID + "/packages/"
					+ "Simulink_Library_Blocks"));
			oslcSysMLInterfaceBlockCreationRestClient
					.addOslcResource(sysMLOutputInterfaceBlock);

			SysMLFlowProperty sysMLOutFlowProperty = new SysMLFlowProperty();
			sysMLOutFlowProperty.setName("out");
			sysMLOutFlowProperty.setDirection("out");
			URI sysMLOutFlowPropertyURI = URI
					.create(magicDrawBaseHTTPURI + "/services/"
							+ magicDrawProjectToCreateID
							+ "/flowproperty/Simulink_Library_Blocks::"
							+ "Output::out");
			sysMLOutFlowProperty.setAbout(sysMLOutFlowPropertyURI);
			sysMLOutFlowProperty.setOwner(URI.create(magicDrawBaseHTTPURI
					+ "/services/" + magicDrawProjectToCreateID
					+ "/interfaceblocks/" + "Simulink_Library_Blocks::Output"));
			oslcSysMLFlowPropertyCreationRestClient
					.addOslcResource(sysMLOutFlowProperty);

		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	static String getSysMLBlockName(SimulinkBlock simulinkBlock) {
		String blockID = simulinkBlock
				.getAbout()
				.toString()
				.replace(
						simulinkBaseHTTPURI + "/services/"
								+ simulinkModelToRetrieveID + "/blocks/", "");
		String blockQualifiedName = blockID.split("/")[blockID.split("/").length - 1];
		blockQualifiedName = blockQualifiedName.replaceAll("::", "_");
		String sysmlBlockName = blockQualifiedName + "_SimBlock";
		return sysmlBlockName;
	}

	static void registerOSLCMagicDrawSysMLRESTClients() {
		// URI of the HTTP request
		String sysmlBlockCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/blocks";
		String sysmlPartCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/partproperties";
		String sysmlConnectorEndCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/connectorends";
		String sysmlConnectorCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/connectors";
		String sysmlPortCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/ports";
		String sysmlValuePropertyCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID
				+ "/valueproperties";
		String sysmlItemFlowCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/itemflows";
		String sysmlPackageCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/packages";
		String sysmlInterfaceBlockCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID
				+ "/interfaceblocks";
		String sysmlFlowPropertyCreationFactoryURI = magicDrawBaseHTTPURI
				+ "/services/" + magicDrawProjectToCreateID + "/flowproperties";

		// expected mediatype
		String mediaType = "application/rdf+xml";

		// readTimeout specifies how long the RestClient object waits (in
		// milliseconds) for a response before timing out
		int readTimeout = 2400000;

		// set up the credentials for the basic authentication
		BasicAuthSecurityHandler basicAuthHandler = new BasicAuthSecurityHandler();
		basicAuthHandler.setUserName("foo");
		basicAuthHandler.setPassword("bar");

		// creating the OSLC REST clients
		oslcSysMLBlockCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlBlockCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLPartCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlPartCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLConnectorEndCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlConnectorEndCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLConnectorCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlConnectorCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLPortCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlPortCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLValuePropertyCreationRestClient = new OslcRestClient(
				PROVIDERS, sysmlValuePropertyCreationFactoryURI, mediaType,
				readTimeout);

		oslcSysMLItemFlowCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlItemFlowCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLPackageCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlPackageCreationFactoryURI, mediaType, readTimeout);

		oslcSysMLInterfaceBlockCreationRestClient = new OslcRestClient(
				PROVIDERS, sysmlInterfaceBlockCreationFactoryURI, mediaType,
				readTimeout);

		oslcSysMLFlowPropertyCreationRestClient = new OslcRestClient(PROVIDERS,
				sysmlFlowPropertyCreationFactoryURI, mediaType, readTimeout);
	}

	static void registerOSLCSimulinkRESTClients() {
		// URI of the HTTP request
		String serviceProviderCatalogURI = simulinkBaseHTTPURI
				+ "/services/catalog/singleton";

		// expected mediatype
		String mediaType = "application/rdf+xml";

		// readTimeout specifies how long the RestClient object waits (in
		// milliseconds) for a response before timing out
		int readTimeout = 2400000;

		// set up the HTTP connection
		oslcSimulinkServiceProviderCatalogRestClient = new OslcRestClient(
				PROVIDERS, serviceProviderCatalogURI, mediaType, readTimeout);
		// oslcSimulinkServiceProviderCatalogRestClient = new OslcRestClient(
		// PROVIDERS, serviceProviderCatalogURI, mediaType);

		// retrieve the serviceProviderCatalog as POJO
		final ServiceProviderCatalog serviceProviderCatalog = oslcSimulinkServiceProviderCatalogRestClient
				.getOslcResource(ServiceProviderCatalog.class);
		System.out.println(serviceProviderCatalog.getTitle());

		// retrieve Simulink model
		// retrieve queryCapabilityURI
		String simulinkModelQueryCapabilityURI = null;
		String simulinkBlocksQueryCapabilityURI = null;
		String simulinkLinesQueryCapabilityURI = null;
		String simulinkParametersQueryCapabilityURI = null;
		for (ServiceProvider serviceProvider : serviceProviderCatalog
				.getServiceProviders()) {
			if (serviceProvider.getTitle().equals(simulinkModelToRetrieveID)) {
				for (Service service : serviceProvider.getServices()) {
					for (QueryCapability queryCapability : service
							.getQueryCapabilities()) {
						System.out.println(queryCapability.getQueryBase());
						String resourceShapeURI = queryCapability
								.getResourceShape().toString();
						if (resourceShapeURI.endsWith("model")) {
							simulinkModelQueryCapabilityURI = queryCapability
									.getQueryBase().toString();
						} else if (resourceShapeURI.endsWith("block")) {
							simulinkBlocksQueryCapabilityURI = queryCapability
									.getQueryBase().toString();
						} else if (resourceShapeURI.endsWith("line")) {
							simulinkLinesQueryCapabilityURI = queryCapability
									.getQueryBase().toString();
						} else if (resourceShapeURI.endsWith("parameter")) {
							simulinkParametersQueryCapabilityURI = queryCapability
									.getQueryBase().toString();
						}
					}
				}
			}
		}

		// retrieving and converting Simulink model
		oslcSimulinkModelRestClient = new OslcRestClient(PROVIDERS,
				simulinkModelQueryCapabilityURI, mediaType, readTimeout);

		oslcSimulinkBlocksRestClient = new OslcRestClient(PROVIDERS,
				simulinkBlocksQueryCapabilityURI, mediaType, readTimeout);

		oslcSimulinkLinesRestClient = new OslcRestClient(PROVIDERS,
				simulinkLinesQueryCapabilityURI, mediaType, readTimeout);

		oslcSimulinkParametersRestClient = new OslcRestClient(PROVIDERS,
				simulinkParametersQueryCapabilityURI, mediaType, readTimeout);
	}

	static URI getOwnerURI(String elementQualifiedName,
			SimulinkModel simulinkModel) {
		String[] elementQualifiedNameSegments = elementQualifiedName
				.split("::");
		String elementOwnerQualifiedName = null;
		// get the owner qualified name
		// distinguish between block describing the main model and regular
		// blocks
		if (elementQualifiedNameSegments.length > 1) {
			for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
				if (i == 0) {
					elementOwnerQualifiedName = elementQualifiedNameSegments[i];
				} else {
					elementOwnerQualifiedName = elementOwnerQualifiedName + "_"
							+ elementQualifiedNameSegments[i];
				}
			}
			elementOwnerQualifiedName = elementOwnerQualifiedName + "_SimBlock";
		} else {
			elementOwnerQualifiedName = magicDrawProjectToCreateID
					+ "_SimModel";
		}

		URI sysMLOwnerURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/blocks/"
				+ elementOwnerQualifiedName);
		return sysMLOwnerURI;
	}

	static URI getPartURI(String partQualifiedName, SimulinkModel simulinkModel) {
		String partName = partQualifiedName.split("::")[partQualifiedName
				.split("::").length - 1];
		String[] elementQualifiedNameSegments = partQualifiedName.split("::");
		String elementOwnerQualifiedName = null;
		// get the owner qualified name
		// distinguish between block describing the main model and regular
		// blocks
		if (elementQualifiedNameSegments.length > 1) {
			for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
				if (i == 0) {
					elementOwnerQualifiedName = elementQualifiedNameSegments[i];
				} else {
					elementOwnerQualifiedName = elementOwnerQualifiedName + "_"
							+ elementQualifiedNameSegments[i];
				}
			}
			elementOwnerQualifiedName = elementOwnerQualifiedName + "_SimBlock";
		} else {
			elementOwnerQualifiedName = magicDrawProjectToCreateID
					+ "_SimModel";
		}

		URI sysMLOwnerURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/partproperties/"
				+ elementOwnerQualifiedName + "::" + partName);
		return sysMLOwnerURI;
	}
	
	
	static URI getPortOnBoundaryURI(String elementQualifiedName,
			SimulinkModel simulinkModel) {
		String portName = elementQualifiedName.split("::")[elementQualifiedName
				.split("::").length - 1];
		String[] elementQualifiedNameSegments = elementQualifiedName
				.split("::");
		String elementOwnerQualifiedName = null;
		// get the owner qualified name
		// distinguish between block describing the main model and regular
		// blocks
		if (elementQualifiedNameSegments.length > 1) {
			for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
				if (i == 0) {
					elementOwnerQualifiedName = elementQualifiedNameSegments[i];
				} else {
					elementOwnerQualifiedName = elementOwnerQualifiedName + "_"
							+ elementQualifiedNameSegments[i];
				}
			}
			elementOwnerQualifiedName = elementOwnerQualifiedName + "_SimBlock";
		} else {
			elementOwnerQualifiedName = magicDrawProjectToCreateID
					+ "_SimModel";
		}

		URI sysMLOwnerURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/ports/"
				+ elementOwnerQualifiedName + "::" + portName);
		return sysMLOwnerURI;
	}

	static URI getPortOnPartURI(String sysmlPortQualifiedName) {
		// determine URI as if port was on a part and make sure port was not yet
		// created
		// source port on part URI
		URI sysmlSourcePortURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/ports/"
				+ sysmlPortQualifiedName);
		return sysmlSourcePortURI;
	}

	static URI getPortOnPartOwnerURI(String elementQualifiedName,
			SimulinkBlock[] simulinkBlocks) {
		// port owner (block) URI (type of Simulink block instance/ type of
		// SysML part which owns the port)
		String sourcePortOwnerName = elementQualifiedName.split("::outport::")[0];
		URI sysMLSourcePortOwnerURI = null;
		for (SimulinkBlock simulinkBlock : simulinkBlocks) {
			if (simulinkBlock
					.getAbout()
					.toString()
					.equals(simulinkBaseHTTPURI + "/services/"
							+ simulinkModelToRetrieveID + "/blocks/"
							+ sourcePortOwnerName)) {
				// ports will be added to the instance-specific block
				// type in SysML
				sysMLSourcePortOwnerURI = URI.create(magicDrawBaseHTTPURI
						+ "/services/" + magicDrawProjectToCreateID
						+ "/blocks/" + getSysMLBlockName(simulinkBlock));

				break;
			}
		}
		return sysMLSourcePortOwnerURI;
	}

	private static URI getSourceConjugatePortOnBoundaryURI(
			String elementQualifiedName, SimulinkModel simulinkModel,
			String externalPortNumber) {
		// String portName =
		// elementQualifiedName.split("::")[elementQualifiedName
		// .split("::").length - 1];
		String[] elementQualifiedNameSegments = elementQualifiedName
				.split("::");
		String elementOwnerQualifiedName = null;
		// get the owner qualified name
		// distinguish between block describing the main model and regular
		// blocks
		if (elementQualifiedNameSegments.length > 1) {
			for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
				if (i == 0) {
					elementOwnerQualifiedName = elementQualifiedNameSegments[i];
				} else {
					elementOwnerQualifiedName = elementOwnerQualifiedName + "_"
							+ elementQualifiedNameSegments[i];
				}
			}
			elementOwnerQualifiedName = elementOwnerQualifiedName + "_SimBlock";
		} else {
			elementOwnerQualifiedName = magicDrawProjectToCreateID
					+ "_SimModel";
		}

		URI sysMLOwnerURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/ports/"
				+ elementOwnerQualifiedName + "::in" + externalPortNumber);
		return sysMLOwnerURI;
	}

	private static URI getTargetConjugatePortOnBoundaryURI(
			String elementQualifiedName, SimulinkModel simulinkModel,
			String externalPortNumber) {
		// String portName =
		// elementQualifiedName.split("::")[elementQualifiedName
		// .split("::").length - 1];
		String[] elementQualifiedNameSegments = elementQualifiedName
				.split("::");
		String elementOwnerQualifiedName = null;
		// get the owner qualified name
		// distinguish between block describing the main model and regular
		// blocks
		if (elementQualifiedNameSegments.length > 1) {
			for (int i = 0; i < elementQualifiedNameSegments.length - 1; i++) {
				if (i == 0) {
					elementOwnerQualifiedName = elementQualifiedNameSegments[i];
				} else {
					elementOwnerQualifiedName = elementOwnerQualifiedName + "_"
							+ elementQualifiedNameSegments[i];
				}
			}
			elementOwnerQualifiedName = elementOwnerQualifiedName + "_SimBlock";
		} else {
			elementOwnerQualifiedName = magicDrawProjectToCreateID
					+ "_SimModel";
		}

		URI sysMLOwnerURI = URI.create(magicDrawBaseHTTPURI + "/services/"
				+ magicDrawProjectToCreateID + "/ports/"
				+ elementOwnerQualifiedName + "::out" + externalPortNumber);
		return sysMLOwnerURI;
	}

	private static boolean isTargetPortAlreadyCreated(URI sysmlTargetPortURI) {
		// String sysmlTargetPortURI.toString();
		return true;
	}

	static List<SimulinkParameter> getSimulinkBlockParameters(
			String blockQualifiedName) {
		List<SimulinkParameter> blockParameters = new ArrayList<SimulinkParameter>();
		SimulinkParameter[] simulinkParameters = oslcSimulinkParametersRestClient
				.getOslcResource(SimulinkParameter[].class);
		for (SimulinkParameter simulinkParameter : simulinkParameters) {
			if (simulinkParameter
					.getAbout()
					.toString()
					.startsWith(
							simulinkBaseHTTPURI + "/services/"
									+ simulinkModelToRetrieveID
									+ "/parameters/" + blockQualifiedName)) {
				blockParameters.add(simulinkParameter);
			}
		}
		return blockParameters;
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
