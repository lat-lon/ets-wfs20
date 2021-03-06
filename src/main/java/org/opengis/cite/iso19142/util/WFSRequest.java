package org.opengis.cite.iso19142.util;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xerces.xs.XSElementDeclaration;
import org.opengis.cite.iso19142.Namespaces;
import org.opengis.cite.iso19142.WFS2;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Provides various utility methods for constructing and manipulating the
 * content of WFS request messages.
 */
public class WFSRequest {

	private static final String TNS_PREFIX = "tns";
	private static final DocumentBuilder BUILDER = initDocBuilder();

	private static DocumentBuilder initDocBuilder() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = null;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			TestSuiteLogger.log(Level.WARNING, "Failed to create parser", e);
		}
		return builder;
	}

	/**
	 * Transforms the XML representation of a WFS request entity to its
	 * corresponding KVP serialization format.
	 * 
	 * @param xmlSource
	 *            A Source representing the XML request entity.
	 * @return A String containing the resulting query component.
	 */
	public static String transformEntityToKVP(Source xmlSource) {
		Source xsltSource = new StreamSource(
				WFSRequest.class.getResourceAsStream("xml2kvp.xsl"));
		TransformerFactory factory = TransformerFactory.newInstance();
		StringWriter writer = new StringWriter();
		try {
			Transformer transformer = factory.newTransformer(xsltSource);
			transformer.transform(xmlSource, new StreamResult(writer));
		} catch (Exception e) {
			TestSuiteLogger.log(
					Level.WARNING,
					"Failed to generate KVP result from Source "
							+ xmlSource.getSystemId(), e);
		}
		return writer.toString();
	}

	/**
	 * Wraps the given XML request entity in the body of a SOAP envelope.
	 * 
	 * @param xmlSource
	 *            The Source providing the XML request entity.
	 * @param version
	 *            The version of the SOAP protocol (either "1.1" or "1.2"); if
	 *            not specified the latest version is assumed.
	 * @return A DOM Document node representing a SOAP request message.
	 */
	public static Document wrapEntityInSOAPEnvelope(Source xmlSource,
			String version) {
		String soapNS;
		if (null != version && version.equals("1.1")) {
			soapNS = Namespaces.SOAP11;
		} else {
			soapNS = Namespaces.SOAP_ENV;
		}
		Document soapDoc = BUILDER.newDocument();
		Element soapEnv = soapDoc.createElementNS(soapNS, "soap:Envelope");
		soapDoc.appendChild(soapEnv);
		Element soapBody = soapDoc.createElementNS(soapNS, "soap:Body");
		soapEnv.appendChild(soapBody);
		try {
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer idTransformer = tFactory.newTransformer();
			Document wfsReq = BUILDER.newDocument();
			idTransformer.transform(xmlSource, new DOMResult(wfsReq));
			soapBody.appendChild(soapDoc.importNode(
					wfsReq.getDocumentElement(), true));
		} catch (Exception e) {
			TestSuiteLogger.log(
					Level.WARNING,
					"Failed to create SOAP envelope from Source "
							+ xmlSource.getSystemId(), e);
		}
		return soapDoc;
	}

	/**
	 * Adds a simple wfs:Query element (without a filter) to the given request
	 * entity. The typeNames attribute value is set using the supplied QName
	 * objects. Namespace bindings are added if necessary.
	 * 
	 * @param doc
	 *            A Document representing a WFS request entity that accepts
	 *            wfs:Query elements as children of the document element
	 *            (GetFeature, GetPropertyValue, GetFeatureWithLock,
	 *            LockFeature).
	 * @param qNames
	 *            A sequence of QName objects representing (qualified) feature
	 *            type names recognized by the IUT.
	 */
	public static void appendSimpleQuery(Document doc, QName... qNames) {
		Element docElement = doc.getDocumentElement();
		Element newQuery = doc.createElementNS(Namespaces.WFS, "wfs:Query");
		StringBuilder typeNames = new StringBuilder();
		for (QName qName : qNames) {
			// look for prefix already bound to this namespace URI
			String nsPrefix = docElement.lookupPrefix(qName.getNamespaceURI());
			if (null == nsPrefix) {
				nsPrefix = "ns" + Integer.toString((int) (Math.random() * 100));
				newQuery.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":"
						+ nsPrefix, qName.getNamespaceURI());
			}
			typeNames.append(nsPrefix).append(':').append(qName.getLocalPart())
					.append(' ');
		}
		newQuery.setAttribute("typeNames", typeNames.toString().trim());
		docElement.appendChild(newQuery);
	}

	/**
	 * Adds a wfs:StoredQuery element to the given request entity.
	 * 
	 * @param doc
	 *            A Document representing a WFS request entity that accepts
	 *            wfs:StoredQuery elements as children of the document element
	 *            (GetFeature, GetPropertyValue, GetFeatureWithLock,
	 *            LockFeature).
	 * @param queryId
	 *            A URI that identifies the stored query to invoke.
	 * @param params
	 *            A Map containing query parameters (may be empty, e.g.
	 *            {@literal Collections.<String, Object>.emptyMap()}). A
	 *            parameter name is associated with an Object (String or QName)
	 *            representing its value.
	 */
	public static void appendStoredQuery(Document doc, String queryId,
			Map<String, Object> params) {
		Element docElement = doc.getDocumentElement();
		Element newQuery = doc.createElementNS(Namespaces.WFS,
				"wfs:StoredQuery");
		newQuery.setAttribute("id", queryId);
		docElement.appendChild(newQuery);
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			Element param = doc
					.createElementNS(Namespaces.WFS, WFS2.PARAM_ELEM);
			param.setPrefix("wfs");
			param.setAttribute("name", entry.getKey());
			newQuery.appendChild(param);
			Object value = entry.getValue();
			if (QName.class.isInstance(value)) {
				QName qName = QName.class.cast(value);
				String prefix = (qName.getPrefix().isEmpty()) ? "tns" : qName
						.getPrefix();
				param.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix,
						qName.getNamespaceURI());
				param.setTextContent(prefix + ":" + qName.getLocalPart());
			} else {
				param.setTextContent(value.toString());
			}
		}
	}

	/**
	 * Creates an XML request entity of the specified request type.
	 * 
	 * @param requestType
	 *            The local name of the document element.
	 * @return A Document representing a WFS request entity.
	 */
	public static Document createRequestEntity(String requestType) {
		String resourceName = requestType + ".xml";
		Document doc = null;
		try {
			doc = BUILDER.parse(WFSRequest.class
					.getResourceAsStream(resourceName));
		} catch (Exception e) {
			TestSuiteLogger.log(Level.WARNING,
					"Failed to parse request entity from classpath: "
							+ resourceName, e);
		}
		return doc;
	}

	/**
	 * Sets the value of the typeName attribute on an action element
	 * (wfs:Update, wfs:Delete) contained in a Transaction request entity.
	 * 
	 * @param elem
	 *            An action element in a transaction request.
	 * @param qName
	 *            The qualified name of a feature type.
	 */
	public static void setTypeName(Element elem, QName qName) {
		List<String> actions = Arrays.asList(WFS2.UPDATE, WFS2.DELETE);
		if (!actions.contains(elem.getLocalName())) {
			return;
		}
		StringBuilder typeNames = new StringBuilder();
		// look for prefix already bound to this namespace URI
		String nsPrefix = elem.lookupPrefix(qName.getNamespaceURI());
		if (null == nsPrefix) { // check document element
			nsPrefix = elem.getOwnerDocument().lookupPrefix(
					qName.getNamespaceURI());
		}
		if (null == nsPrefix) {
			nsPrefix = "ns" + Integer.toString((int) (Math.random() * 100));
			elem.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + nsPrefix,
					qName.getNamespaceURI());
		}
		typeNames.append(nsPrefix).append(':').append(qName.getLocalPart());
		elem.setAttribute("typeName", typeNames.toString());
	}

	/**
	 * Builds a filter predicate containing a fes:ResourceId element that
	 * identifies the feature instance to be modified.
	 * 
	 * @param id
	 *            A String denoting a GML object identifier (gml:id).
	 * @return An Element node (fes:Filter).
	 */
	public static Element newResourceIdFilter(String id) {
		Element filter = XMLUtils.createElement(new QName(Namespaces.FES,
				"Filter", "fes"));
		Element resourceId = XMLUtils.createElement(new QName(Namespaces.FES,
				"ResourceId", "fes"));
		resourceId.setAttribute("rid", id);
		filter.appendChild(filter.getOwnerDocument().adoptNode(resourceId));
		return filter;
	}

	/**
	 * Inserts a standard GML property into a given feature instance. If the
	 * property node already exists it is replaced.
	 * 
	 * @param feature
	 *            An Element node representing a GML feature
	 * @param gmlProperty
	 *            An Element node representing a standard (non-deprecated) GML
	 *            feature property.
	 */
	public static void insertGMLProperty(Element feature, Element gmlProperty) {
		Document doc = feature.getOwnerDocument();
		QName gmlPropName = new QName(gmlProperty.getNamespaceURI(),
				gmlProperty.getLocalName());
		NodeList existing = feature.getElementsByTagNameNS(
				gmlPropName.getNamespaceURI(), gmlPropName.getLocalPart());
		if (existing.getLength() > 0) {
			Node oldProp = existing.item(0);
			gmlProperty.setPrefix(oldProp.getPrefix());
			feature.replaceChild(doc.adoptNode(gmlProperty), oldProp);
			return;
		}
		// Create map associating GML property with collection of following
		// siblings to help determine insertion point (before nextSibling).
		Map<QName, List<String>> followingSiblingsMap = new HashMap<QName, List<String>>();
		followingSiblingsMap.put(new QName(Namespaces.GML, "description"),
				Arrays.asList("descriptionReference", "identifier", "name",
						"boundedBy", "location"));
		followingSiblingsMap.put(new QName(Namespaces.GML, "identifier"),
				Arrays.asList("name", "boundedBy", "location"));
		followingSiblingsMap.put(new QName(Namespaces.GML, "name"),
				Arrays.asList("boundedBy", "location"));
		if (!followingSiblingsMap.containsKey(gmlPropName))
			return; // ignore deprecated properties
		List<String> followingSibs = followingSiblingsMap.get(gmlPropName);
		NodeList properties = feature.getChildNodes();
		Node nextSibling = null;
		for (int i = 0; i < properties.getLength(); i++) {
			Node property = properties.item(i);
			if (property.getNodeType() != Node.ELEMENT_NODE)
				continue;
			String nsURI = property.getNamespaceURI();
			String propName = property.getLocalName();
			// check if application-defined prop or a following GML prop
			if (!nsURI.equals(Namespaces.GML)
					|| (followingSibs.contains(propName) && nsURI
							.equals(Namespaces.GML))) {
				nextSibling = property;
				break;
			}
		}
		if (nextSibling.getNamespaceURI().equals(Namespaces.GML)) {
			gmlProperty.setPrefix(nextSibling.getPrefix());
		} else {
			gmlProperty.setPrefix("gml");
		}
		feature.insertBefore(doc.adoptNode(gmlProperty), nextSibling);
	}

	/**
	 * Creates an Element node (fes:ValueReference) containing an XPath
	 * expression derived from a property element declaration.
	 * 
	 * @param propertyElem
	 *            An element declaration that defines some feature property.
	 * @return An Element containing an XPath expression and an appropriate
	 *         namespace binding.
	 */
	public static Element createValueReference(XSElementDeclaration propertyElem) {
		Element valueRef = XMLUtils.createElement(new QName(Namespaces.FES,
				"ValueReference", "fes"));
		valueRef.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + TNS_PREFIX,
				propertyElem.getNamespace());
		valueRef.setTextContent(TNS_PREFIX + ":" + propertyElem.getName());
		return valueRef;
	}

	/**
	 * Creates a GML envelope covering the area of use for the "WGS 84" CRS
	 * (srsName="urn:ogc:def:crs:EPSG::4326").
	 * 
	 * @return A Document containing gml:Envelope as the document element.
	 */
	public static Document createGMLEnvelope() {
		Document doc;
		try {
			doc = BUILDER.parse(WFSRequest.class
					.getResourceAsStream("Envelope.xml"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return doc;
	}

	/**
	 * Adds a namespace binding to the document element.
	 * 
	 * @param doc
	 *            A Document representing a request entity.
	 * @param qName
	 *            A QName containing a namespace URI and prefix; the local part
	 *            is ignored.
	 */
	public static void addNamespaceBinding(Document doc, QName qName) {
		Element docElem = doc.getDocumentElement();
		docElem.setAttribute(
				XMLConstants.XMLNS_ATTRIBUTE + ":" + qName.getPrefix(),
				qName.getNamespaceURI());
	}

	/**
	 * Adds a sequence of wfs:Replace statements to the given transaction
	 * request entity.
	 * 
	 * @param trxRequest
	 *            A Document node representing a wfs:Transaction request entity.
	 * @param replacements
	 *            A List containing replacement feature representations (as
	 *            GML).
	 */
	public static void addReplaceStatements(Document trxRequest,
			List<Element> replacements) {
		Element docElem = trxRequest.getDocumentElement();
		if (!docElem.getLocalName().equals(WFS2.TRANSACTION)) {
			throw new IllegalArgumentException(
					"Document node is not a Transaction request: "
							+ docElem.getNodeName());
		}
		for (Element feature : replacements) {
			Element replace = trxRequest.createElementNS(Namespaces.WFS,
					"Replace");
			replace.setPrefix("wfs");
			replace.appendChild(trxRequest.importNode(feature, true));
			Element filter = WFSRequest.newResourceIdFilter(feature
					.getAttributeNS(Namespaces.GML, "id"));
			replace.appendChild(trxRequest.adoptNode(filter));
			docElem.appendChild(replace);
		}
		if (TestSuiteLogger.isLoggable(Level.FINE)) {
			TestSuiteLogger.log(Level.FINE,
					XMLUtils.writeNodeToString(trxRequest));
		}
	}

	/**
	 * Appends a wfs:Insert element to the document element in the given request
	 * entity. The wfs:Insert element contains the supplied feature instance.
	 * 
	 * @param request
	 *            A Document node representing a wfs:Transaction request entity.
	 * @param feature
	 *            A Node representing a GML feature instance.
	 */
	public static void addInsertStatement(Document request, Node feature) {
		Element docElem = request.getDocumentElement();
		if (!docElem.getLocalName().equals(WFS2.TRANSACTION)) {
			throw new IllegalArgumentException(
					"Document node is not a Transaction request: "
							+ docElem.getNodeName());
		}
		Element insert = request.createElementNS(Namespaces.WFS, "Insert");
		docElem.appendChild(insert);
		insert.appendChild(request.importNode(feature, true));
	}

	/**
	 * Adds a ResourceId predicate to a GetFeature (or GetFeatureWithLock)
	 * request entity that contains a simple query expression without a filter.
	 * The identifiers should match features of the indicated type.
	 * 
	 * @param request
	 *            The request entity (/wfs:GetFeature/[wfs:Query]).
	 * @param idSet
	 *            A {@literal Set<String>} of feature identifiers that conform
	 *            to the xsd:ID datatype.
	 */
	public static void addResourceIdPredicate(Document request,
			Set<String> idSet) {
		if (idSet.isEmpty())
			return;
		if (!request.getDocumentElement().getLocalName()
				.startsWith(WFS2.GET_FEATURE)) {
			throw new IllegalArgumentException(
					"Expected a GetFeature(WithLock) request: "
							+ request.getDocumentElement().getNodeName());
		}
		NodeList queryList = request.getElementsByTagNameNS(Namespaces.WFS,
				WFS2.QUERY_ELEM);
		if (queryList.getLength() == 0) {
			throw new IllegalArgumentException(
					"No wfs:Query element found in request: "
							+ request.getDocumentElement().getNodeName());
		}
		Element filter = request.createElementNS(Namespaces.FES, "Filter");
		queryList.item(0).appendChild(filter);
		for (String id : idSet) {
			Element resourceId = request.createElementNS(Namespaces.FES,
					"ResourceId");
			resourceId.setAttribute("rid", id);
			filter.appendChild(resourceId);
		}
	}

}
