# R4 validation resources

The `CodeSystem-cdcrec.json` files in `uscore5`, `uscore6`, and `uscore7` are
unchanged copies of the existing `uscore4` test resource, published in
[US Core 4.0.0](https://hl7.org/fhir/us/core/STU4/CodeSystem-cdcrec.json).
They retain the source metadata and are not separate US Core 5, 6, or 7 releases
of the code system.

The US Core 5.0.1, 6.1.0, and 7.0.0 packages reference external race and ethnicity
terminology rather than bundling this code system. Synthea's test loader reads
local JSON files without resolving package dependencies. The HAPI FHIR 8.12.1
update requires this code system locally for the existing race and ethnicity
validation tests.
