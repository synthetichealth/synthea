package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.dstu3.hapi.ctx.IValidationSupport;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;

import java.util.HashMap;
import java.util.List;


public class ValidationSupport implements IValidationSupport {
  private static String profileDir = "structureDefinitions";

  private final HashMap<String, StructureDefinition> definitionsMap;
  private final List<StructureDefinition> definitions;


  /**
   * Defines the custom validation support for DaVinci work.  Passes the structure definitions
   * to the validator to provide custom validations.
   */
  public ValidationSupport() {

    definitions = ValidationResources.loadFromDirectory(profileDir);
    definitionsMap = new HashMap<>();
    definitions.forEach(def -> definitionsMap.put(def.getUrl(), def));
  }

  @Override
  public ValueSet.ValueSetExpansionComponent expandValueSet(FhirContext theContext,
                                                            ConceptSetComponent theInclude) {
    return null;
  }

  @Override
  public List<IBaseResource> fetchAllConformanceResources(FhirContext theContext) {

    return null;
  }

  @Override
  public List<StructureDefinition> fetchAllStructureDefinitions(FhirContext theContext) {

    return definitions;
  }

  @Override
  public CodeSystem fetchCodeSystem(FhirContext theContext, String theSystem) {
    return null;
  }

  @Override
  public <T extends IBaseResource> T fetchResource(FhirContext theContext,
                                                   Class<T> theClass, String theUri) {
    return (T) definitionsMap.get(theUri);
  }

  @Override
  public StructureDefinition fetchStructureDefinition(FhirContext theCtx, String theUrl) {
    return definitionsMap.get(theUrl);
  }

  @Override
  public boolean isCodeSystemSupported(FhirContext theContext, String theSystem) {
    return false;
  }

  @Override
  public CodeValidationResult validateCode(FhirContext theContext, String theCodeSystem,
                                           String theCode, String theDisplay) {
    return null;
  }
}


//Definitions list gotten from code above