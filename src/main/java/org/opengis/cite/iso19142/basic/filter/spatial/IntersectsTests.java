package org.opengis.cite.iso19142.basic.filter.spatial;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;

import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSTypeDefinition;
import org.opengis.cite.geomatics.Extents;
import org.opengis.cite.iso19142.ETSAssert;
import org.opengis.cite.iso19142.ErrorMessage;
import org.opengis.cite.iso19142.ErrorMessageKeys;
import org.opengis.cite.iso19142.Namespaces;
import org.opengis.cite.iso19142.ProtocolBinding;
import org.opengis.cite.iso19142.SuiteAttribute;
import org.opengis.cite.iso19142.WFS2;
import org.opengis.cite.iso19142.basic.filter.QueryFilterFixture;
import org.opengis.cite.iso19142.util.AppSchemaUtils;
import org.opengis.cite.iso19142.util.ServiceMetadataUtils;
import org.opengis.cite.iso19142.util.WFSRequest;
import org.opengis.cite.iso19142.util.XMLUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sun.jersey.api.client.ClientResponse;

/**
 * Tests the response to a GetFeature request that includes the spatial
 * predicate <em>Intersects</em>. This predicate is the logical complement of
 * the <em>Disjoint</em> predicate; that is:
 * 
 * <pre>
 * a.Intersects(b) &#8660; ! a.Disjoint(b)
 * </pre>
 */
public class IntersectsTests extends QueryFilterFixture {

	public final static String IMPL_SPATIAL_FILTER = "ImplementsSpatialFilter";
	private static final String INTERSECTS_OP = "Intersects";
	private XSTypeDefinition gmlGeomBaseType;
	private static final String XSLT_ENV2POLYGON = "/org/opengis/cite/iso19142/util/bbox2polygon.xsl";

	/**
	 * Checks the value of the filter constraint {@value #IMPL_SPATIAL_FILTER}
	 * in the capabilities document. All tests are skipped if this is not
	 * "TRUE".
	 * 
	 * @param testContext
	 *            Information about the test run environment.
	 */
	@BeforeTest
	public void implementsSpatialFilter(ITestContext testContext) {
		this.wfsMetadata = (Document) testContext.getSuite().getAttribute(
				SuiteAttribute.TEST_SUBJECT.getName());
		String xpath = String.format(
				"//fes:Constraint[@name='%s' and (ows:DefaultValue = 'TRUE')]",
				IMPL_SPATIAL_FILTER);
		NodeList result;
		try {
			result = XMLUtils.evaluateXPath(this.wfsMetadata, xpath, null);
		} catch (XPathExpressionException e) {
			throw new AssertionError(e.getMessage());
		}
		if (result.getLength() == 0) {
			throw new SkipException(ErrorMessage.format(
					ErrorMessageKeys.NOT_IMPLEMENTED, IMPL_SPATIAL_FILTER));
		}
	}

	/**
	 * Creates an XSTypeDefinition object representing the
	 * gml:AbstractGeometryType definition.
	 */
	@BeforeClass
	public void createGeometryBaseType() {
		this.gmlGeomBaseType = model.getTypeDefinition("AbstractGeometryType",
				Namespaces.GML);
	}

	/**
	 * Checks if the spatial operator "Intersects" is supported. If not, all
	 * tests for this operator are skipped.
	 */
	@BeforeClass
	public void implementsIntersectsOp() {
		if (!ServiceMetadataUtils.implementsSpatialOperator(this.wfsMetadata,
				"Intersects")) {
			throw new SkipException(ErrorMessage.format(
					ErrorMessageKeys.NOT_IMPLEMENTED, "Intersects operator"));
		}
	}

	/**
	 * [{@code Test}] Submits a GetFeature request containing an Intersects
	 * predicate with a gml:Polygon operand. The response entity must be
	 * schema-valid and contain only instances of the requested type that
	 * intersect the given polygon.
	 * 
	 * @param binding
	 *            The ProtocolBinding to use for this request.
	 * @param featureType
	 *            A QName representing the qualified name of some feature type.
	 * 
	 * @see "ISO 19143:2010, 7.8.3.2: BBOX operator"
	 */
	@Test(description = "See ISO 19143: 7.8.3.2", dataProvider = "protocol-featureType")
	public void intersectsPolygon(ProtocolBinding binding, QName featureType) {
		List<XSElementDeclaration> geomProps = AppSchemaUtils
				.getFeaturePropertiesByType(model, featureType, gmlGeomBaseType);
		if (geomProps.isEmpty()) {
			throw new SkipException("Feature type has no geometry properties: "
					+ featureType);
		}
		WFSRequest.appendSimpleQuery(this.reqEntity, featureType);
		Document gmlEnv = Extents.envelopeAsGML(featureInfo.get(featureType)
				.getGeoExtent());
		Document gmlPolygon = XMLUtils.transform(new StreamSource(getClass()
				.getResourceAsStream(XSLT_ENV2POLYGON)), gmlEnv);
		Element valueRef = WFSRequest.createValueReference(geomProps.get(0));
		addSpatialPredicate(this.reqEntity, INTERSECTS_OP,
				gmlPolygon.getDocumentElement(), valueRef);
		URI endpoint = ServiceMetadataUtils.getOperationEndpoint(
				this.wfsMetadata, WFS2.GET_FEATURE, binding);
		ClientResponse rsp = wfsClient.submitRequest(new DOMSource(reqEntity),
				binding, endpoint);
		this.rspEntity = extractBodyAsDocument(rsp);
		Assert.assertEquals(rsp.getStatus(),
				ClientResponse.Status.OK.getStatusCode(),
				ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
		Map<String, String> nsBindings = new HashMap<String, String>();
		nsBindings.put(Namespaces.WFS, "wfs");
		NodeList members;
		String xpath = "//wfs:member/*";
		try {
			members = XMLUtils.evaluateXPath(this.rspEntity, xpath, nsBindings);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
		Assert.assertTrue(members.getLength() > 0, ErrorMessage.format(
				ErrorMessageKeys.XPATH_RESULT, this.rspEntity
						.getDocumentElement().getNodeName(), xpath));
		for (int i = 0; i < members.getLength(); i++) {
			ETSAssert.assertQualifiedName(members.item(i), featureType);
		}
	}

	/**
	 * Adds a spatial predicate to a GetFeature request entity. If the given
	 * geometry element has no spatial reference (srsName) it is assumed to use
	 * the default CRS specified in the capabilities document.
	 * 
	 * @param request
	 *            The request entity (wfs:GetFeature).
	 * @param spatialOp
	 *            The name of a spatial operator.
	 * @param gmlGeom
	 *            A DOM Element representing a GML geometry.
	 * @param valueRef
	 *            An Element (fes:ValueReference) that specifies the spatial
	 *            property to check. If it is {@code null}, the predicate
	 *            applies to all spatial properties.
	 */
	void addSpatialPredicate(Document request, String spatialOp,
			Element gmlGeom, Element valueRef) {
		if (!request.getDocumentElement().getLocalName()
				.equals(WFS2.GET_FEATURE)) {
			throw new IllegalArgumentException("Not a GetFeature request: "
					+ request.getDocumentElement().getNodeName());
		}
		Element queryElem = (Element) request.getElementsByTagNameNS(
				Namespaces.WFS, WFS2.QUERY_ELEM).item(0);
		Element filter = request.createElementNS(Namespaces.FES, "fes:Filter");
		queryElem.appendChild(filter);
		Element predicate = request.createElementNS(Namespaces.FES, "fes:"
				+ spatialOp);
		filter.appendChild(predicate);
		if (null != valueRef) {
			predicate.appendChild(request.importNode(valueRef, true));
		}
		// import geometry element to avoid WRONG_DOCUMENT_ERR
		predicate.appendChild(request.importNode(gmlGeom, true));
	}
}
