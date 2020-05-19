package org.mitre.synthea.helpers;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * HAPI annotation client for invoking operations on a FHIR terminology service.
 */
public interface TerminologyClient extends IRestfulClient {

  @Operation(type = ValueSet.class, name = "$expand")
  ValueSet expand(@OperationParam(name = "url") UriType url, 
      @OperationParam(name = "count") IntegerType count, 
      @OperationParam(name = "offset") IntegerType offset);

}
