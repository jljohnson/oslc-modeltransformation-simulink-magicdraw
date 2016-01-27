package edu.gatech.mbse.oslc.transformations;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wink.client.handlers.BasicAuthSecurityHandler;
import org.eclipse.lyo.oslc4j.client.OslcRestClient;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.QueryCapability;
import org.eclipse.lyo.oslc4j.core.model.Service;
import org.eclipse.lyo.oslc4j.core.model.ServiceProvider;
import org.eclipse.lyo.oslc4j.core.model.ServiceProviderCatalog;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;

import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnector;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnectorEnd;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLItemFlow;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPartProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkBlock;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkElementsToCreate;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkInputPort;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkLine;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkModel;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkOutputPort;
import edu.gatech.mbsec.adapter.simulink.resources.SimulinkParameter;


public class MagicDraw2SimulinkTransformation {

	private static final Set<Class<?>> PROVIDERS = new HashSet<Class<?>>();

	static {
		PROVIDERS.addAll(JenaProvidersRegistry.getProviders());
		// PROVIDERS.addAll(Json4JProvidersRegistry.getProviders());
	}

	static String magicDrawProjectToRetrieveID = "TestProject2";
	static String simulinkModelToCreateID = "model11AfterRT4.slx---model11AfterRT4";

	static OslcRestClient oslcSysMLSimulationBlockRestClient = null;
	static OslcRestClient oslcSysMLBlocksRestClient = null;
	static OslcRestClient oslcSysMLPartsRestClient = null;
	static OslcRestClient oslcSysMLConnectorEndsRestClient = null;
	static OslcRestClient oslcSysMLConnectorsRestClient = null;
	static OslcRestClient oslcSysMLPortsRestClient = null;
	static OslcRestClient oslcSysMLValuePropertiesRestClient = null;
	static OslcRestClient oslcSysMLItemFlowsRestClient = null;

	static Map<String, String> simBlockQualifiedNameExtPortNumberMap = new HashMap<String, String>();
	static Map<String, String> simExtPortNumberBlockQualifiedNameMap = new HashMap<String, String>();

	static String simulinkBaseHTTPURI = "http://localhost:8181/oslc4jsimulink";
	static String magicDrawBaseHTTPURI = "http://localhost:8080/oslc4jmagicdraw";

	static List<SimulinkBlock> blocksToCreate = new ArrayList<SimulinkBlock>();
	static List<SimulinkLine> linesToCreate = new ArrayList<SimulinkLine>();
	static List<SimulinkParameter> parametersToCreate = new ArrayList<SimulinkParameter>();

	public static void main(String[] args) {

		simBlockQualifiedNameExtPortNumberMap.clear();
		simExtPortNumberBlockQualifiedNameMap.clear();
		
		String sysmlSimulationBlockURI = magicDrawProjectToRetrieveID
				+ "_SimModel";
		registerOSLCMagicDrawSysMLRESTClients(sysmlSimulationBlockURI);

		// retrieving and converting SysML Simulation Block
		final SysMLBlock sysMLBlock = oslcSysMLSimulationBlockRestClient
				.getOslcResource(SysMLBlock.class);
		System.out.println(sysMLBlock.getName());

		// assumption is that the Simulink model already exists
		mapSysMLPartProperties(null, sysMLBlock.getPartProperties());		
		mapSysMLConnectors();

		// URI of the Simulink model resource
		String simulinkElementsToCreateURI = "http://localhost:8181/oslc4jsimulink/services/"
				+ simulinkModelToCreateID + "/model";
		// expected mediatype
		String mediaType = "application/rdf+xml";
		// readTimeout specifies how long the RestClient object waits (in
		// milliseconds) for a response before timing out
		int readTimeout = 2400000;
		// set up the credentials for the basic authentication
		BasicAuthSecurityHandler basicAuthHandler = new BasicAuthSecurityHandler();
		final OslcRestClient oslcSimulinkElementsToCreateRestClient = new OslcRestClient(
				PROVIDERS, simulinkElementsToCreateURI, mediaType, readTimeout,
				basicAuthHandler);
		SimulinkElementsToCreate newElements = new SimulinkElementsToCreate(
				blocksToCreate, linesToCreate, parametersToCreate);
		newElements
				.setAbout(URI
						.create("http://localhost:8181/oslc4jsimulink/services/" + simulinkModelToCreateID + "/model/elementstocreate"));
		oslcSimulinkElementsToCreateRestClient.addOslcResource(newElements);
		System.out.println("MagicDraw to Simulink done.");
	}

	private static void mapSysMLConnectors() {
		// retrieving and converting SysML connectors
				final SysMLConnector[] sysMLConnectors = oslcSysMLConnectorsRestClient
						.getOslcResource(SysMLConnector[].class);
				final SysMLItemFlow[] sysMLItemFlows = oslcSysMLItemFlowsRestClient
						.getOslcResource(SysMLItemFlow[].class);
				Collection<SimulinkLine> simulinkLines = new ArrayList<SimulinkLine>();

				// create the Simulink lines
				for (SysMLConnector sysMLConnector : sysMLConnectors) {
					SysMLItemFlow itemFlowOfConnector = null;
					// get corresponding itemflow
					for (SysMLItemFlow sysMLItemFlow : sysMLItemFlows) {
						if(sysMLItemFlow.getRealizingConnector() != null){
							if (sysMLItemFlow.getRealizingConnector().equals(
									sysMLConnector.getAbout())) {
								itemFlowOfConnector = sysMLItemFlow;
								break;
							}
						}
						
						
					}
					
					if(itemFlowOfConnector == null){
						// no itemFlow linked to connector
						// do not map this connector, skip it
						continue;
					}

					URI itemFlowSource = itemFlowOfConnector.getInformationSource();
					URI itemFlowTarget = itemFlowOfConnector.getInformationTarget();

					Pattern pattern = Pattern.compile("\\d+");

					// check if connector has simulation block as owner
					try {
						SimulinkLine simulinkLine = new SimulinkLine();
						Link[] sysMLConnectorEnds = sysMLConnector.getEnds();

						boolean isInternalSubsystemConnection = false;
						Link sysMLConnectorEndLink1 = sysMLConnectorEnds[0];
						URI sysMLConnectorEndURI1 = sysMLConnectorEndLink1.getValue();
						OslcRestClient oslcSysMLConnectorEndRestClient1 = new OslcRestClient(
								PROVIDERS, sysMLConnectorEndURI1);
						SysMLConnectorEnd sysMLConnectorEnd1 = oslcSysMLConnectorEndRestClient1
								.getOslcResource(SysMLConnectorEnd.class);
						Link sysMLConnectorEndLink2 = sysMLConnectorEnds[1];
						URI sysMLConnectorEndURI2 = sysMLConnectorEndLink2.getValue();
						OslcRestClient oslcSysMLConnectorEndRestClient2 = new OslcRestClient(
								PROVIDERS, sysMLConnectorEndURI2);
						SysMLConnectorEnd sysMLConnectorEnd2 = oslcSysMLConnectorEndRestClient2
								.getOslcResource(SysMLConnectorEnd.class);

						URI simulinkOutputPortURI = null;
						URI simulinkInputPortURI = null;

						for (Link sysMLConnectorEndLink : sysMLConnectorEnds) {
							// create source port
							URI sysMLConnectorEndURI = sysMLConnectorEndLink.getValue();
							OslcRestClient oslcSysMLConnectorEndRestClient = new OslcRestClient(
									PROVIDERS, sysMLConnectorEndURI);
							SysMLConnectorEnd sysMLConnectorEnd = oslcSysMLConnectorEndRestClient
									.getOslcResource(SysMLConnectorEnd.class);
							URI connectorEndPortURI = sysMLConnectorEnd.getRole();
							String connectorEndPortName = connectorEndPortURI
									.toString().replace(
											"http://localhost:8080/oslc4jmagicdraw/services/"
													+ magicDrawProjectToRetrieveID
													+ "/ports/", "");
							connectorEndPortName = connectorEndPortName.split("::")[connectorEndPortName
									.split("::").length - 1];
							OslcRestClient oslcSysMLPortRestClient = new OslcRestClient(
									PROVIDERS, sysMLConnectorEnd.getRole(),
									"application/rdf+xml");
							SysMLPort sysmlPort = oslcSysMLPortRestClient
									.getOslcResource(SysMLPort.class);
							String portURI = sysmlPort.getAbout().toString();
							String portQualifiedName = portURI.split("/")[portURI.split("/").length - 1];
							String portName = portQualifiedName.split("::")[portQualifiedName.split("::").length - 1];
							OslcRestClient oslcSysMLPortOwnerRestClient = new OslcRestClient(
									PROVIDERS, sysmlPort.getOwner(),
									"application/rdf+xml");
							SysMLBlock sysmlPortOwner = oslcSysMLPortOwnerRestClient
									.getOslcResource(SysMLBlock.class);
							String blockType = getBlockType(sysmlPortOwner);

							// if port owner and connector owner are identical, then
							// port is a subsystem port
							if (sysMLConnector.getOwner().equals(sysmlPort.getOwner())) {
								// port is subsystem port
								// check the other connector port to check if connector
								// is within subsystem block or outside
								// get the partwith port of other connector port, check
								// if it has the same owner, if yes, then connector is
								// inside subsystem
								if (sysMLConnectorEndURI.equals(sysMLConnectorEndURI1)) {
									if (sysMLConnectorEnd2.getPartWithPort() != null) {
										OslcRestClient oslcSysMLPartWithPortRestClient2 = new OslcRestClient(
												PROVIDERS,
												sysMLConnectorEnd2.getPartWithPort(),
												"application/rdf+xml");
										SysMLPartProperty sysMLPartProperty2 = oslcSysMLPartWithPortRestClient2
												.getOslcResource(SysMLPartProperty.class);
										if (sysMLPartProperty2.getOwner().equals(
												sysmlPort.getOwner())) {
											isInternalSubsystemConnection = true;
										}
									}
								} else if (sysMLConnectorEndURI
										.equals(sysMLConnectorEndURI2)) {
									if (sysMLConnectorEnd1.getPartWithPort() != null) {
										OslcRestClient oslcSysMLPartWithPortRestClient1 = new OslcRestClient(
												PROVIDERS,
												sysMLConnectorEnd1.getPartWithPort(),
												"application/rdf+xml");
										SysMLPartProperty sysMLPartProperty1 = oslcSysMLPartWithPortRestClient1
												.getOslcResource(SysMLPartProperty.class);
										if (sysMLPartProperty1.getOwner().equals(
												sysmlPort.getOwner())) {
											isInternalSubsystemConnection = true;
										}
									}
								}
							}

							if (connectorEndPortURI.equals(itemFlowSource)) {
								String outIndex = null;
								Matcher outIndexMatcher = pattern
										.matcher(connectorEndPortName);
								while (outIndexMatcher.find()) {
									outIndex = outIndexMatcher.group();
									break;
								}
								if (outIndex == null) {
									System.err
											.println(connectorEndPortURI
													+ "Port Name does not contain any number information");
									continue;
								}
								if (blockType.equals("SubSystem")
										& isInternalSubsystemConnection) {
									outIndex = "1";
								}

								URI sourcePartWithPortURI = sysMLConnectorEnd
										.getPartWithPort();
								String sourcePartWithPort = null;
								// create source port
								if (sourcePartWithPortURI == null) {
									URI connectorOwner = sysMLConnector.getOwner();
									String connectorOwnerQualifiedName = connectorOwner
											.toString()
											.replace(
													"http://localhost:8080/oslc4jmagicdraw/services/"
															+ magicDrawProjectToRetrieveID
															+ "/blocks/", "");
									String connectorOwnerQualifiedName2 = getModifiedQualifiedName(connectorOwnerQualifiedName);
									simulinkOutputPortURI = URI
											.create(simulinkBaseHTTPURI + "/services/"
													+ simulinkModelToCreateID
													+ "/outputports/"
													+ connectorOwnerQualifiedName2
													+ "::" + portName
													+ "::outport::" + outIndex);
								} else {
									sourcePartWithPort = sourcePartWithPortURI
											.toString()
											.replace(
													"http://localhost:8080/oslc4jmagicdraw/services/"
															+ magicDrawProjectToRetrieveID
															+ "/partproperties/", "");
									// remove _SimBlock from qualifiedName
									String partQualifiedName = getModifiedQualifiedName(sourcePartWithPort);
									simulinkOutputPortURI = URI
											.create(simulinkBaseHTTPURI + "/services/"
													+ simulinkModelToCreateID
													+ "/outputports/"
													+ partQualifiedName + "::outport::"
													+ outIndex);
								}

								// create the Simulink Output Port
								SimulinkOutputPort simulinkOutputPort = new SimulinkOutputPort();
								simulinkOutputPort.setId(connectorEndPortName);
								simulinkOutputPort.setAbout(simulinkOutputPortURI);
								simulinkLine.setSourcePort(simulinkOutputPortURI);

							} else if (connectorEndPortURI.equals(itemFlowTarget)) {
								// create target port
								String inIndex = null;
								Matcher inIndexMatcher = pattern
										.matcher(connectorEndPortName);
								while (inIndexMatcher.find()) {
									inIndex = inIndexMatcher.group();
									break;
								}
								if (inIndex == null) {
									System.err
											.println(connectorEndPortURI
													+ "Port Name does not contain any number information");
									continue;
								}
								if (blockType.equals("SubSystem")
										& isInternalSubsystemConnection) {
									inIndex = "1";
								}

								String targetPortName = connectorEndPortName;
								SysMLConnectorEnd targetSysMLConnectorEnd = sysMLConnectorEnd;
								URI targetPartWithPortURI = targetSysMLConnectorEnd
										.getPartWithPort();
								String targetPartWithPort = null;

								// if owner of connector is equal to owner of
								// partwithport, port is a regular block port, else port
								// is an inport/outport block port
								if (targetPartWithPortURI == null) {
									URI connectorOwner = sysMLConnector.getOwner();
									String connectorOwnerQualifiedName = connectorOwner
											.toString()
											.replace(
													"http://localhost:8080/oslc4jmagicdraw/services/"
															+ magicDrawProjectToRetrieveID
															+ "/blocks/", "");
									String connectorOwnerQualifiedName2 = getModifiedQualifiedName(connectorOwnerQualifiedName);
									simulinkInputPortURI = URI
											.create(simulinkBaseHTTPURI + "/services/"
													+ simulinkModelToCreateID
													+ "/inputports/"
													+ connectorOwnerQualifiedName2
													+ "::" + portName
													+ "::inport::" + inIndex);
								} else {
									targetPartWithPort = targetPartWithPortURI
											.toString()
											.replace(
													"http://localhost:8080/oslc4jmagicdraw/services/"
															+ magicDrawProjectToRetrieveID
															+ "/partproperties/", "");
									String partQualifiedName = getModifiedQualifiedName(targetPartWithPort);

									// regular port
									simulinkInputPortURI = URI
											.create(simulinkBaseHTTPURI + "/services/"
													+ simulinkModelToCreateID
													+ "/inputports/"
													+ partQualifiedName + "::inport::"
													+ inIndex);
								}

								// create the Simulink Input Port
								SimulinkInputPort simulinkInputPort = new SimulinkInputPort();
								simulinkInputPort.setId(targetPortName);
								simulinkInputPort.setAbout(simulinkInputPortURI);
								Link[] simulinkLineTargetPortsLink = new Link[1];
								simulinkLineTargetPortsLink[0] = new Link(
										simulinkInputPortURI);
								simulinkLine
										.setTargetPorts(simulinkLineTargetPortsLink);
							} else {
								// item flow does not belong to connector
								System.out
										.println("Connector and ItemFlow not related!");
							}
						}
						String simulinkOutputPortURIRawPath = simulinkOutputPortURI
								.getRawPath();
						String simulinkOutputPortName = simulinkOutputPortURIRawPath
								.replace("/oslc4jsimulink/services/"
										+ simulinkModelToCreateID + "/outputports/", "");
						String simulinkInputPortURIRawPath = simulinkInputPortURI
								.getRawPath();
						String simulinkInputPortName = simulinkInputPortURIRawPath
								.replace("/oslc4jsimulink/services/"
										+ simulinkModelToCreateID + "/inputports/", "");
						simulinkLine.setAbout(URI.create(simulinkBaseHTTPURI
								+ "/services/" + simulinkModelToCreateID + "/lines/"
								+ simulinkOutputPortName + "---"
								+ simulinkInputPortName));

						simulinkLines.add(simulinkLine);

					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				// if Simulink lines have branches then
				// create modified line and delete other line
				Collection<SimulinkLine> simulinkLinesToDelete = new ArrayList<SimulinkLine>();
				Collection<SimulinkLine> simulinkLinesToNotDelete = new ArrayList<SimulinkLine>();

				// check if two simulink lines with the same source port exist (support
				// line branching)
				for (SimulinkLine simulinkLine : simulinkLines) {
					URI sourcePortURI = simulinkLine.getSourcePort();
					for (SimulinkLine simulinkLine2 : simulinkLines) {
						URI targetPortURI1 = simulinkLine.getTargetPorts()[0]
								.getValue();
						URI targetPortURI2 = simulinkLine2.getTargetPorts()[0]
								.getValue();
						if (simulinkLine2.getSourcePort().equals(sourcePortURI)
								& !targetPortURI1.equals(targetPortURI2)) {
							if (simulinkLinesToNotDelete.contains(simulinkLine2)) {
								continue;
							}
							// add new target to simulink line1
							Link[] newSimulinkLineTargetPortsLink = new Link[simulinkLine
									.getTargetPorts().length + 1];
							int i = 0;
							for (Link link : simulinkLine.getTargetPorts()) {
								newSimulinkLineTargetPortsLink[i] = new Link(
										link.getValue());
								i++;
							}
							newSimulinkLineTargetPortsLink[simulinkLine
									.getTargetPorts().length] = new Link(targetPortURI2);
							simulinkLine.setTargetPorts(newSimulinkLineTargetPortsLink);
							simulinkLinesToDelete.add(simulinkLine2);
							simulinkLinesToNotDelete.add(simulinkLine);
						}
					}
				}

				// delete simulink line2
				simulinkLines.removeAll(simulinkLinesToDelete);

				System.out.println("SECOND LOG **************************************");
				// logging Simulink lines
				for (SimulinkLine simulinkLine : simulinkLines) {
					System.out.println("");
					System.out.println("About: " + simulinkLine.getAbout());
					System.out.println("SourcePort: " + simulinkLine.getSourcePort());
					for (Link link : simulinkLine.getTargetPorts()) {
						System.out.println("TargetPort: " + link.getValue());
					}
				}
				// creating Simulink lines
				for (SimulinkLine simulinkLine : simulinkLines) {
//					oslcSimulinkLineCreationRestClient.addOslcResource(simulinkLine);
					linesToCreate.add(simulinkLine);
				}
		
	}

	private static void mapSysMLPartProperties(String ownerBlockName,
			Link[] sysMLPartProperties) {
		// retrieving and converting SysML parts of Simulation Block
		for (Link partPropertyLink : sysMLPartProperties) {
			try {
				OslcRestClient oslcSysMLSimulationBlockPartRestClient = new OslcRestClient(
						PROVIDERS, partPropertyLink.getValue());
				SysMLPartProperty sysMLPartProperty = oslcSysMLSimulationBlockPartRestClient
						.getOslcResource(SysMLPartProperty.class);

				// create Simulink block corresponding to the SysML part
				SimulinkBlock simulinkBlock = new SimulinkBlock();
				simulinkBlock.setName(sysMLPartProperty.getName());
				// get inherited block
				OslcRestClient oslcSysMLBlocktypeRestClient = new OslcRestClient(
						PROVIDERS, sysMLPartProperty.getType());
				SysMLBlock sysMLBlockType = oslcSysMLBlocktypeRestClient
						.getOslcResource(SysMLBlock.class);
				String sysMLPartTypeName = getBlockType(sysMLBlockType);
				simulinkBlock.setType(sysMLPartTypeName);
				URI simulinkBlockURI = null;
				if (ownerBlockName == null) {
					simulinkBlockURI = URI.create(simulinkBaseHTTPURI
							+ "/services/" + simulinkModelToCreateID
							+ "/blocks/" + sysMLPartProperty.getName());
				} else {
					simulinkBlockURI = URI.create(simulinkBaseHTTPURI
							+ "/services/" + simulinkModelToCreateID
							+ "/blocks/" + ownerBlockName + "::"
							+ sysMLPartProperty.getName());
				}
				simulinkBlock.setAbout(simulinkBlockURI);
				blocksToCreate.add(simulinkBlock);
				System.out.println(simulinkBlockURI);

				if (sysMLPartTypeName.equals("SubSystem")) {
					// map nested SysML parts to nested Simulink blocks
					mapSysMLPartProperties(sysMLPartProperty.getName(),
							sysMLBlockType.getPartProperties());

					// map ports to nested Simulink blocks
					mapSysMLPorts(sysMLBlockType.getName(),
							sysMLBlockType.getPorts());
				}

				// mapping all SysML value properties
				mapSysMLValueProperties(sysMLBlockType, ownerBlockName, sysMLPartProperty);
				
//				// mapping only specific SysML value properties				
//				mapSpecificSysMLValueProperties("ModelReference", "ModelName", sysMLBlockType, ownerBlockName, sysMLPartProperty);
//				mapSpecificSysMLValueProperties("Sum", "Inputs", sysMLBlockType, ownerBlockName, sysMLPartProperty);
//				mapSpecificSysMLValueProperties("Constant", "Value", sysMLBlockType, ownerBlockName, sysMLPartProperty);				

			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private static void mapSpecificSysMLValueProperties(
			String blockType, String valuePropertyName, 
			SysMLBlock sysMLBlockType, String ownerBlockName,
			SysMLPartProperty sysMLPartProperty) {
		String sysMLPartTypeName = getBlockType(sysMLBlockType);
		if (sysMLPartTypeName.equals(blockType)) {
			// set model reference parameter
			for (Link link : sysMLBlockType.getValueProperties()) {
				String valuePropertyURI = link.getValue().toString();
				if (valuePropertyURI.contains(valuePropertyName)) {
					OslcRestClient oslcSysMLValuePropertyRestClient = new OslcRestClient(
							PROVIDERS, valuePropertyURI);
					SysMLValueProperty sysMLValueProperty = oslcSysMLValuePropertyRestClient
							.getOslcResource(SysMLValueProperty.class);
					String propertyValue = sysMLValueProperty
							.getDefaultValue();
					SimulinkParameter simulinkParameter;
					try {
						simulinkParameter = new SimulinkParameter();
						simulinkParameter.setName(valuePropertyName);
						simulinkParameter
								.setValue(propertyValue);
						URI simulinkParameterURI = null;
						if (ownerBlockName == null) {
							simulinkParameterURI = URI
									.create(simulinkBaseHTTPURI
											+ "/services/"
											+ simulinkModelToCreateID
											+ "/parameters/"
											+ sysMLPartProperty.getName()
											+ "::" + valuePropertyName);
						} else {
							simulinkParameterURI = URI
									.create(simulinkBaseHTTPURI
											+ "/services/"
											+ simulinkModelToCreateID
											+ "/parameters/"
											+ ownerBlockName + "::"
											+ sysMLPartProperty.getName()
											+ "::" + valuePropertyName);
						}
						simulinkParameter
								.setAbout(simulinkParameterURI);
						parametersToCreate.add(simulinkParameter);
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					break;
				}
			}
		}
		
	}

	private static void mapSysMLValueProperties(SysMLBlock sysMLBlockType, String ownerBlockName, SysMLPartProperty sysMLPartProperty) {
		for (Link link : sysMLBlockType.getValueProperties()) {
			String valuePropertyURI = link.getValue().toString();			
				OslcRestClient oslcSysMLValuePropertyRestClient = new OslcRestClient(
						PROVIDERS, valuePropertyURI);
				SysMLValueProperty sysMLValueProperty = oslcSysMLValuePropertyRestClient
						.getOslcResource(SysMLValueProperty.class);
				String propertyValue = sysMLValueProperty
						.getDefaultValue();
				String propertyName = sysMLValueProperty
						.getName();
				SimulinkParameter simulinkParameter;
				try {
					simulinkParameter = new SimulinkParameter();
					simulinkParameter.setName(propertyName);
					simulinkParameter
							.setValue(propertyValue);
					URI simulinkParameterURI = null;
					if (ownerBlockName == null) {
						simulinkParameterURI = URI
								.create(simulinkBaseHTTPURI
										+ "/services/"
										+ simulinkModelToCreateID
										+ "/parameters/"
										+ sysMLPartProperty.getName()
										+ "::ModelName");
					} else {
						simulinkParameterURI = URI
								.create(simulinkBaseHTTPURI
										+ "/services/"
										+ simulinkModelToCreateID
										+ "/parameters/"
										+ ownerBlockName + "::"
										+ sysMLPartProperty.getName()
										+ "::ModelName");
					}
					simulinkParameter
							.setAbout(simulinkParameterURI);
					parametersToCreate.add(simulinkParameter);
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		
	}

	private static void mapSysMLPorts(String typeOfPartWitPortQualifiedName,
			Link[] ports) {
		// retrieving and converting SysML ports of Subsystem Blocks
		for (Link portLink : ports) {
			try {
				OslcRestClient oslcSysMLSimulationBlockPartRestClient = new OslcRestClient(
						PROVIDERS, portLink.getValue());
				SysMLPort sysMLPort = oslcSysMLSimulationBlockPartRestClient
						.getOslcResource(SysMLPort.class);

				// create Simulink block corresponding to the SysML port
				SimulinkBlock simulinkBlock = new SimulinkBlock();
				simulinkBlock.setName(sysMLPort.getName());

				String portURI = sysMLPort.getAbout().toString();
				String portQualifiedName = portURI.split("/")[portURI.split("/").length - 1];
				String portName = portQualifiedName.split("::")[portQualifiedName.split("::").length - 1];
				
				if (portName.contains("Out")) { // improve regex
					simulinkBlock.setType("Outport");
				} else {
					simulinkBlock.setType("Inport");
				}
				typeOfPartWitPortQualifiedName = typeOfPartWitPortQualifiedName
						.replace("_SimBlock", "");
				URI simulinkBlockURI = URI.create(simulinkBaseHTTPURI
						+ "/services/" + simulinkModelToCreateID + "/blocks/"
						+ typeOfPartWitPortQualifiedName + "::"
						+ portName);
				simulinkBlock.setAbout(simulinkBlockURI);

//				oslcSimulinkBlockCreationRestClient
//						.addOslcResource(simulinkBlock);
				blocksToCreate.add(simulinkBlock);
				System.out.println(simulinkBlockURI);

				// set port number parameter
				Pattern pattern = Pattern.compile("\\d+");
				String portIndex = null;
				Matcher portIndexMatcher = pattern.matcher(portName);
				while (portIndexMatcher.find()) {
					portIndex = portIndexMatcher.group();
					break;
				}
				if (portIndex == null) {
					System.err
							.println(sysMLPort.getAbout()
									+ "Port Name does not contain any number information");
					continue;
				}

				SimulinkParameter portNumberParameter = new SimulinkParameter();
				portNumberParameter.setName("Port");
				portNumberParameter.setValue(portIndex);
				URI portNumberParameterURI = null;

				portNumberParameterURI = URI.create(simulinkBaseHTTPURI
						+ "/services/" + simulinkModelToCreateID
						+ "/parameters/" + typeOfPartWitPortQualifiedName
						+ "::" + portName + "::Port");

				portNumberParameter.setAbout(portNumberParameterURI);
//				oslcSimulinkParametersRestClient
//						.addOslcResource(portNumberParameter);
				parametersToCreate.add(portNumberParameter);

			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	static String getSysMLBlockName(SimulinkBlock simulinkBlock) {
		String blockID = simulinkBlock
				.getAbout()
				.toString()
				.replace(
						"http://localhost:8181/oslc4jsimulink/services/"
								+ simulinkModelToCreateID + "/blocks/", "");
		String blockQualifiedName = blockID.split("/")[blockID.split("/").length - 1];
		blockQualifiedName = blockQualifiedName.replaceAll("::", "_");
		String sysmlBlockName;
		sysmlBlockName = blockQualifiedName + "_SimBlock";
		return sysmlBlockName;
	}

	static void registerOSLCMagicDrawSysMLRESTClients(
			String simulationBlockQualifiedName) {
		// URI of the HTTP request
		String sysmlSimulationBlockURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID
				+ "/blocks/"
				+ simulationBlockQualifiedName;
		String sysmlBlocksURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/blocks";
		String sysmlPartsURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/partproperties";
		String sysmlConnectorEndsURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/connectorends";
		String sysmlConnectorsURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/connectors";
		String sysmlPortsURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/ports";
		String sysmlValuePropertiesURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/valueproperties";
		String sysmlItemFlowsURI = "http://localhost:8080/oslc4jmagicdraw/services/"
				+ magicDrawProjectToRetrieveID + "/itemflows";

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

		oslcSysMLSimulationBlockRestClient = new OslcRestClient(PROVIDERS,
				sysmlSimulationBlockURI, mediaType, readTimeout,
				basicAuthHandler);

		oslcSysMLBlocksRestClient = new OslcRestClient(PROVIDERS,
				sysmlBlocksURI, mediaType, readTimeout, basicAuthHandler);

		oslcSysMLPartsRestClient = new OslcRestClient(PROVIDERS, sysmlPartsURI,
				mediaType, readTimeout, basicAuthHandler);

		oslcSysMLConnectorEndsRestClient = new OslcRestClient(PROVIDERS,
				sysmlConnectorEndsURI, mediaType, readTimeout, basicAuthHandler);

		oslcSysMLConnectorsRestClient = new OslcRestClient(PROVIDERS,
				sysmlConnectorsURI, mediaType, readTimeout, basicAuthHandler);

		oslcSysMLPortsRestClient = new OslcRestClient(PROVIDERS, sysmlPortsURI,
				mediaType, readTimeout, basicAuthHandler);

		oslcSysMLValuePropertiesRestClient = new OslcRestClient(PROVIDERS,
				sysmlValuePropertiesURI, mediaType, readTimeout,
				basicAuthHandler);

		oslcSysMLItemFlowsRestClient = new OslcRestClient(PROVIDERS,
				sysmlItemFlowsURI, mediaType, readTimeout, basicAuthHandler);
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
			elementOwnerQualifiedName = simulinkModel.getName() + "_SimModel";
		}

		URI sysMLOwnerURI = URI
				.create("http://localhost:8080/oslc4jmagicdraw/services/"
						+ magicDrawProjectToRetrieveID + "/blocks/"
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
			elementOwnerQualifiedName = simulinkModel.getName() + "_SimModel";
		}

		URI sysMLOwnerURI = URI
				.create("http://localhost:8080/oslc4jmagicdraw/services/"
						+ magicDrawProjectToRetrieveID + "/partproperties/"
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
			elementOwnerQualifiedName = simulinkModel.getName() + "_SimModel";
		}

		URI sysMLOwnerURI = URI
				.create("http://localhost:8080/oslc4jmagicdraw/services/"
						+ magicDrawProjectToRetrieveID + "/ports/"
						+ elementOwnerQualifiedName + "::" + portName);
		return sysMLOwnerURI;
	}

	static URI getPortOnPartURI(String sysmlPortQualifiedName) {
		// determine URI as if port was on a part and make sure port was not yet
		// created
		// source port on part URI
		URI sysmlSourcePortURI = URI
				.create("http://localhost:8080/oslc4jmagicdraw/services/"
						+ magicDrawProjectToRetrieveID + "/ports/"
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
					.equals("http://localhost:8181/oslc4jsimulink/services/"
							+ simulinkModelToCreateID + "/blocks/"
							+ sourcePortOwnerName)) {
				// ports will be added to the instance-specific block
				// type in SysML
				sysMLSourcePortOwnerURI = URI
						.create("http://localhost:8080/oslc4jmagicdraw/services/"
								+ magicDrawProjectToRetrieveID
								+ "/blocks/"
								+ getSysMLBlockName(simulinkBlock));
				break;
			}
		}
		return sysMLSourcePortOwnerURI;
	}	

	public static String getModifiedQualifiedName(String originalQualifiedName) {
		String[] originalQualifiedNameSegments = originalQualifiedName
				.split("::");
		StringBuffer qualifiedName = new StringBuffer();
		int i = 0;
		for (String string : originalQualifiedNameSegments) {
			if (i > 0) {
				qualifiedName.append("::");
			}
			if (string.contains("_SimModel")) {
				continue;
			}
			string = string.replace("_SimBlock", "");
			qualifiedName.append(string);
			i++;
		}
		return qualifiedName.toString();
	}

	public static String getBlockType(SysMLBlock sysMLBlock) {
		String sysMLPartTypeURI = sysMLBlock.getInheritedBlocks()[0].getValue()
				.toString();
		sysMLPartTypeURI = sysMLPartTypeURI.replace(
				"http://localhost:8080/oslc4jmagicdraw/services/"
						+ magicDrawProjectToRetrieveID + "/blocks/", "");
		String sysMLPartTypeName = sysMLPartTypeURI.split("::")[sysMLPartTypeURI
				.split("::").length - 1];
		return sysMLPartTypeName;
	}
}
