## WFS 2.0 (ISO 19142:2010) Conformance Test Suite

This executable test suite checks Web Feature Service (WFS) 2.0 implementations 
for conformance to ISO 19142:2010, _Geographic information -- Web Feature Service_ 
(also published as [OGC 09-025r1](http://portal.opengeospatial.org/files/?artifact_id=39967)).
Four fundamental conformance levels are covered by the test suite:</p>

1. **Simple WFS**: Implements the following operations: `GetCapabilities`, `DescribeFeatureType`, 
`ListStoredQueries`, `DescribeStoredQueries`, and the `GetFeature` operation with at least the 
StoredQuery action (GetFeatureById).
2. **Basic WFS**: As for **Simple WFS**, plus the `GetFeature` operation with the Query action 
and the `GetPropertyValue` operation.
3. **Transactional WFS**: As for **Basic WFS**, plus the `Transaction` operation.
4. **Locking WFS**: As for **Transactional WFS**, plus at least one of the `GetFeatureWithLock` 
or `LockFeature` operations.

The test suite is schema-aware in the sense that the WFS under test does not need 
to support any particular application schema or to be loaded with specialized test 
data. However, the following preconditions must be satisfied:

* The GML application schema meets the requirements of the GML conformance class 
"GML application schemas defining features and feature collections" (ISO 19136, A.1.4).
* Data are available for at least one feature type advertised in the capabilities 
document.
* The service capabilities description contains all required elements in accord 
with the "Simple WFS" conformance class.

Visit the [project documentation website](http://opengeospatial.github.io/ets-wfs20/) 
for more information, including the API documentation.
