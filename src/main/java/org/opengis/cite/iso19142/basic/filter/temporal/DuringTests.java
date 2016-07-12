package org.opengis.cite.iso19142.basic.filter.temporal;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathExpressionException;

import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSTypeDefinition;
import org.opengis.cite.iso19142.ErrorMessage;
import org.opengis.cite.iso19142.ErrorMessageKeys;
import org.opengis.cite.iso19142.Namespaces;
import org.opengis.cite.iso19142.ProtocolBinding;
import org.opengis.cite.iso19142.SuiteAttribute;
import org.opengis.cite.iso19142.WFS2;
import org.opengis.cite.iso19142.basic.filter.QueryFilterFixture;
import org.opengis.cite.iso19142.util.AppSchemaUtils;
import org.opengis.cite.iso19142.util.ServiceMetadataUtils;
import org.opengis.cite.iso19142.util.TestSuiteLogger;
import org.opengis.cite.iso19142.util.TimeUtils;
import org.opengis.cite.iso19142.util.WFSMessage;
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
 * Tests the response to a GetFeature request that includes the temporal
 * predicate <em>During</em>. The relation can be expressed as follows when
 * comparing a temporal instant to a temporal period:
 * 
 * <pre>
 * self.position &gt; other.begin.position AND self.position &lt; other.end.position
 * </pre>
 * 
 * <p>
 * If both operands are periods then the following must hold:
 * </p>
 * 
 * <pre>
 * self.begin.position &gt; other.begin.position AND self.end.position &lt; other.end.position
 * </pre>
 * 
 * <p>
 * The following figure illustrates the relationship. A solid line denotes a
 * temporal property; a dashed line denotes a literal time value that specifies
 * the temporal extent of interest.
 * </p>
 *
 * <img src="doc-files/during.png" alt="During relationship">
 *
 * <p style="margin-bottom: 0.5em">
 * <strong>Sources</strong>
 * </p>
 * <ul>
 * <li>ISO 19108, 5.2.3.5: TM_RelativePosition</li>
 * </ul>
 */
public class DuringTests extends QueryFilterFixture {

    private static final String DURING_OP = "During";
    public final static String IMPL_TEMPORAL_FILTER = "ImplementsTemporalFilter";
    private XSTypeDefinition gmlTimeBaseType;

    /**
     * Checks the value of the filter constraint {@value #IMPL_TEMPORAL_FILTER}
     * in the capabilities document. All tests are skipped if this is not
     * "TRUE".
     * 
     * @param testContext
     *            Information about the test run environment.
     */
    @BeforeTest
    public void implementsTemporalFilter(ITestContext testContext) {
        this.wfsMetadata = (Document) testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJECT.getName());
        String xpath = String.format("//fes:Constraint[@name='%s' and (ows:DefaultValue = 'TRUE')]",
                IMPL_TEMPORAL_FILTER);
        NodeList result;
        try {
            result = XMLUtils.evaluateXPath(this.wfsMetadata, xpath, null);
        } catch (XPathExpressionException e) {
            throw new AssertionError(e.getMessage());
        }
        if (result.getLength() == 0) {
            throw new SkipException(ErrorMessage.format(ErrorMessageKeys.NOT_IMPLEMENTED, IMPL_TEMPORAL_FILTER));
        }
    }

    /**
     * Creates an XSTypeDefinition object representing the
     * gml:AbstractTimeGeometricPrimitiveType definition. This is the base type
     * for <code>TimeInstantType</code> and <code>TimePeriodType</code>.
     */
    @BeforeClass
    public void createTemporalPrimitiveBaseType() {
        this.gmlTimeBaseType = this.model.getTypeDefinition("AbstractTimeGeometricPrimitiveType", Namespaces.GML);
    }

    /**
     * [{@code Test}] Submits a GetFeature request containing a During temporal
     * predicate with a gml:TimePeriod operand (spanning the previous 5 years).
     * The response entity must contain only instances of the requested type
     * that satisfy the temporal relation.
     * 
     * @param binding
     *            The ProtocolBinding to use for this request.
     * @param featureType
     *            A QName representing the qualified name of some feature type.
     */
    @Test(description = "See ISO 19143: 7.14.6, A.9", dataProvider = "protocol-featureType")
    public void duringFixedPeriod(ProtocolBinding binding, QName featureType) {
        List<XSElementDeclaration> timeProps = AppSchemaUtils.getFeaturePropertiesByType(this.model, featureType,
                this.gmlTimeBaseType);
        // also look for simple temporal types
        for (XSTypeDefinition dataType : AppSchemaUtils.getSimpleTemporalDataTypes(this.model)) {
            timeProps.addAll(AppSchemaUtils.getFeaturePropertiesByType(this.model, featureType, dataType));
        }
        TestSuiteLogger.log(Level.FINE,
                String.format("Temporal properties for feature type %s: %s", featureType, timeProps));
        if (timeProps.isEmpty()) {
            throw new SkipException("Feature type has no temporal properties: " + featureType);
        }
        WFSMessage.appendSimpleQuery(this.reqEntity, featureType);
        // previous 5 years
        ZonedDateTime endTime = ZonedDateTime.now(ZoneId.of("Z"));
        ZonedDateTime startTime = endTime.minusYears(5);
        Document gmlTimePeriod = TimeUtils.periodAsGML(startTime, endTime);
        XSElementDeclaration timeProperty = timeProps.get(0);
        Element valueRef = WFSMessage.createValueReference(timeProperty);
        addTemporalPredicate(this.reqEntity, DURING_OP, gmlTimePeriod, valueRef);
        URI endpoint = ServiceMetadataUtils.getOperationEndpoint(this.wfsMetadata, WFS2.GET_FEATURE, binding);
        ClientResponse rsp = wfsClient.submitRequest(new DOMSource(reqEntity), binding, endpoint);
        this.rspEntity = extractBodyAsDocument(rsp);
        Assert.assertEquals(rsp.getStatus(), ClientResponse.Status.OK.getStatusCode(),
                ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
        // TODO: verify temporal relation
    }

    /**
     * Adds a temporal predicate to a GetFeature request entity. If the given
     * temporal element has no temporal reference (frame) it is assumed to use
     * the default TRS (ISO 8601).
     * 
     * @param request
     *            The request entity (wfs:GetFeature).
     * @param temporalOp
     *            The name of a spatial operator.
     * @param gmlTime
     *            A Document containing a GML temporal primitive.
     * @param valueRef
     *            An Element (fes:ValueReference) that specifies the temporal
     *            property to check. If it is {@code null}, the predicate
     *            applies to all temporal properties.
     */
    void addTemporalPredicate(Document request, String temporalOp, Document gmlTime, Element valueRef) {
        if (!request.getDocumentElement().getLocalName().equals(WFS2.GET_FEATURE)) {
            throw new IllegalArgumentException(
                    "Not a GetFeature request: " + request.getDocumentElement().getNodeName());
        }
        Element queryElem = (Element) request.getElementsByTagNameNS(Namespaces.WFS, WFS2.QUERY_ELEM).item(0);
        if (null == queryElem) {
            throw new IllegalArgumentException("No Query element found in GetFeature request entity.");
        }
        Element filter = request.createElementNS(Namespaces.FES, "fes:Filter");
        queryElem.appendChild(filter);
        Element predicate = request.createElementNS(Namespaces.FES, "fes:" + temporalOp);
        filter.appendChild(predicate);
        if (null != valueRef) {
            predicate.appendChild(request.importNode(valueRef, true));
        }
        // import temporal element to avoid WRONG_DOCUMENT_ERR
        predicate.appendChild(request.importNode(gmlTime.getDocumentElement(), true));
    }
}
