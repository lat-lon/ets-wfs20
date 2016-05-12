package org.opengis.cite.iso19142.util;

import javax.xml.namespace.QName;

import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSTypeDefinition;
import org.opengis.cite.iso19136.util.XMLSchemaModelUtils;

/**
 * A property of a feature type. The property value may be simple (e.g. a
 * string) or complex (e.g. a geometry element).
 */
public class FeatureProperty {

	private QName name;
	private QName featureType;
	private QName valueType;
	private XSElementDeclaration declaration;

	public FeatureProperty() {
	}

	/**
	 * Constructor specifying the feature type and property declaration. The
	 * property name and value type are derived from the element declaration.
	 * 
	 * @param featureType
	 *            A QName specifying the feature type to which the property
	 *            belongs.
	 * @param declaration
	 *            A schema component representing an element declaration for the
	 *            property.
	 */
	public FeatureProperty(QName featureType, XSElementDeclaration declaration) {
		this.featureType = featureType;
		this.declaration = declaration;
		setName(new QName(declaration.getNamespace(), declaration.getName()));
		setValueType(getTypeName(declaration));
	}

	/**
	 * Gets the qualified name of the feature property.
	 * 
	 * @return A QName object.
	 */
	public QName getName() {
		return name;
	}

	public void setName(QName name) {
		this.name = name;
	}

	/**
	 * Gets the qualified name of the feature type to which this property
	 * belongs.
	 * 
	 * @return A QName object.
	 */
	public QName getFeatureType() {
		return featureType;
	}

	public void setFeatureType(QName featureType) {
		this.featureType = featureType;
	}

	/**
	 * Gets the qualified name of the property value type. This is either the
	 * name of a simple datatype (e.g. xsd:decimal) or the name of an acceptable
	 * child element (e.g. gml:Point).
	 * 
	 * @return A QName object.
	 */
	public QName getValueType() {
		return valueType;
	}

	public void setValueType(QName valueType) {
		this.valueType = valueType;
	}

	/**
	 * Gets the element declaration for this feature property.
	 * 
	 * @return A schema component representing an element declaration from an
	 *         XML Schema.
	 */
	public XSElementDeclaration getDeclaration() {
		return declaration;
	}

	public void setDeclaration(XSElementDeclaration declaration) {
		this.declaration = declaration;
		setName(new QName(declaration.getNamespace(), declaration.getName()));
		setValueType(getTypeName(declaration));
	}

	/**
	 * Returns the name of a property value type. If the property has a complex
	 * type, this is the type name of the expected value. Since the use of a
	 * choice compositor is very unconventional in this context, only one
	 * element is assumed to appear as an allowed value of a complex type.
	 * 
	 * @param decl
	 *            A schema component representing an element declaration.
	 * @return A QName denoting the name of a simple or complex type.
	 */
	QName getTypeName(XSElementDeclaration decl) {
		XSTypeDefinition typeDef = decl.getTypeDefinition();
		QName typeName = null;
		if (typeDef.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
			typeName = new QName(typeDef.getNamespace(), typeDef.getName());
		} else {
			XSComplexTypeDefinition complexTypeDef = (XSComplexTypeDefinition) typeDef;
			XSElementDeclaration elemDecl = XMLSchemaModelUtils
					.getAllElementsInParticle(complexTypeDef.getParticle())
					.get(0);
			typeName = new QName(elemDecl.getNamespace(), elemDecl.getName());
		}
		return typeName;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(getFeatureType()).append('/');
		str.append(getName()).append('/');
		str.append(getValueType());
		return str.toString();
	}

}