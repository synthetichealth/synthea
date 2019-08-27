package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.hapi.ctx.IValidationSupport;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;

/**
 * ValidationSupport provides implementation guide profiles (i.e. StructureDefinitions)
 * to the FHIR validation process. This class does not provide ValueSet expansion.
 */
public class ValidationSupportR4 implements IValidationSupport {
  private static String profileDir = "structureDefinitions/r4";

  private List<IBaseResource> resources;
  private Map<String, IBaseResource> resourcesMap;
  private List<StructureDefinition> definitions;
  private Map<String, StructureDefinition> definitionsMap;
  private Map<String, CodeSystem> codeSystemMap;

  /**
   * Defines the custom validation support for various implementation guides.
   */
  public ValidationSupportR4() {
    resources = new ArrayList<IBaseResource>();
    resourcesMap = new HashMap<String, IBaseResource>();
    definitions = new ArrayList<StructureDefinition>();
    definitionsMap = new HashMap<String, StructureDefinition>();
    codeSystemMap = new HashMap<String, CodeSystem>();

    try {
      loadFromDirectory(profileDir);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * Loads the structure definitions from the given directory.
   * @param rootDir the directory to load structure definitions from
   * @return a list of structure definitions
   * @throws Throwable when there is an error reading the structure definitions.
   */
  private void loadFromDirectory(String rootDir) throws Throwable {

    IParser jsonParser = FhirContext.forR4().newJsonParser();
    jsonParser.setParserErrorHandler(new StrictErrorHandler());

    URL profilesFolder = ClassLoader.getSystemClassLoader().getResource(rootDir);
    Path path = Paths.get(profilesFolder.toURI());
    Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".json")).forEach(f -> {
          try {
            IBaseResource resource = jsonParser.parseResource(new FileReader(f.toFile()));
            resources.add(resource);
            if (resource instanceof CodeSystem) {
              CodeSystem cs = (CodeSystem) resource;
              resourcesMap.put(cs.getUrl(), cs);
              codeSystemMap.put(cs.getUrl(), cs);
            } else if (resource instanceof ValueSet) {
              ValueSet vs = (ValueSet) resource;
              resourcesMap.put(vs.getUrl(), vs);
            } else if (resource instanceof StructureDefinition) {
              StructureDefinition sd = (StructureDefinition) resource;
              resourcesMap.put(sd.getUrl(), sd);
              definitions.add(sd);
              definitionsMap.put(sd.getUrl(), sd);
            }
          } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Override
  public ValueSetExpansionOutcome expandValueSet(FhirContext theContext,
      ConceptSetComponent theInclude) {
    return null;
  }

  @Override
  public List<IBaseResource> fetchAllConformanceResources(FhirContext theContext) {
    return resources;
  }

  @Override
  public List<StructureDefinition> fetchAllStructureDefinitions(FhirContext theContext) {
    return definitions;
  }

  @Override
  public CodeSystem fetchCodeSystem(FhirContext theContext, String theSystem) {
    return codeSystemMap.get(theSystem);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends IBaseResource> T fetchResource(FhirContext theContext,
      Class<T> theClass, String theUri) {
    return (T) resourcesMap.get(theUri);
  }

  @Override
  public StructureDefinition fetchStructureDefinition(FhirContext theCtx, String theUrl) {
    return definitionsMap.get(theUrl);
  }

  @Override
  public boolean isCodeSystemSupported(FhirContext theContext, String theSystem) {
    return codeSystemMap.containsKey(theSystem);
  }

  @Override
  public CodeValidationResult validateCode(FhirContext theContext, String theCodeSystem,
      String theCode, String theDisplay) {
    IssueSeverity severity = IssueSeverity.WARNING;
    String message = "Unsupported CodeSystem";

    if (isCodeSystemSupported(theContext, theCodeSystem)) {
      severity = IssueSeverity.ERROR;
      message = "Code not found";

      CodeSystem cs = codeSystemMap.get(theCodeSystem);
      for (ConceptDefinitionComponent def : cs.getConcept()) {
        if (def.getCode().equals(theCode)) {
          if (def.getDisplay() != null && theDisplay != null) {
            if (def.getDisplay().equals(theDisplay)) {
              severity = IssueSeverity.INFORMATION;
              message = "Validated Successfully";
            } else {
              severity = IssueSeverity.WARNING;
              message = "Validated Code; Display mismatch";
            }
          } else {
            severity = IssueSeverity.WARNING;
            message = "Validated Code; No display";
          }
        }
      }
    }

    return new CodeValidationResult(severity, message);
  }

  @Override
  public LookupCodeResult lookupCode(FhirContext theContext, String theSystem, String theCode) {
    if (isCodeSystemSupported(theContext, theSystem)) {
      LookupCodeResult result = new LookupCodeResult();
      result.setSearchedForSystem(theSystem);
      result.setSearchedForCode(theCode);
      result.setFound(false);

      CodeSystem cs = codeSystemMap.get(theSystem);
      for (ConceptDefinitionComponent def : cs.getConcept()) {
        if (def.getCode().equals(theCode)) {
          result.setCodeDisplay(def.getDisplay());
          result.setFound(true);
          return result;
        }
      }
    }
    return LookupCodeResult.notFound(theSystem, theCode);
  }

  @Override
  public ValueSet fetchValueSet(FhirContext theContext, String uri) {
    return (ValueSet) resourcesMap.get(uri);
  }

  @Override
  public StructureDefinition generateSnapshot(
      StructureDefinition theInput, String theUrl, String theWebUrl, String theProfileName) {
    return null;
  }
}