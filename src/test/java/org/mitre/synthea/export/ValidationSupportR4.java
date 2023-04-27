package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;

import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;

/**
 * ValidationSupport provides implementation guide profiles (i.e. StructureDefinitions)
 * to the FHIR validation process. This class does not provide ValueSet expansion.
 */
public class ValidationSupportR4 extends PrePopulatedValidationSupport {
  private static final String PROFILE_DIR = "structureDefinitions/r4";

  /**
   * Defines the custom validation support for various implementation guides.
   * @param ctx the FHIR context
   * @param useUSCore4 Whether or not to load US Core 4 artifacts
   * @param useUSCore5 Whether or not to load US Core 5 artifacts
   */
  public ValidationSupportR4(FhirContext ctx, boolean useUSCore4, boolean useUSCore5) {
    super(ctx);

    try {
      loadFromDirectory(PROFILE_DIR, useUSCore4, useUSCore5);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * Loads the structure definitions from the given directory.
   * @param rootDir the directory to load structure definitions from
   * @param useUSCore4 Whether or not to load US Core 4 artifacts
   * @param useUSCore5 Whether or not to load US Core 5 artifacts
   * @throws Throwable when there is an error reading the structure definitions.
   */
  private void loadFromDirectory(String rootDir, boolean useUSCore4, boolean useUSCore5)
      throws Throwable {
    IParser jsonParser = FhirR4.getContext().newJsonParser();
    jsonParser.setParserErrorHandler(new StrictErrorHandler());

    URL profilesFolder = ClassLoader.getSystemClassLoader().getResource(rootDir);
    Path path = Paths.get(profilesFolder.toURI());
    Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".json")).forEach(f -> {
          try {
            if (!useUSCore4 && f.toString().contains("uscore4")) {
              return;
            }
            if (!useUSCore5 && f.toString().contains("uscore5")) {
              return;
            }

            IBaseResource resource = jsonParser.parseResource(new FileReader(f.toFile()));
            handleResource(resource);
          } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void handleResource(IBaseResource resource) {
    if (resource instanceof Bundle) {
      Bundle bundle = (Bundle) resource;
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.hasResource()) {
          handleResource(entry.getResource());
        }
      }
    } else {
      if (resource instanceof CodeSystem) {
        this.addCodeSystem(resource);
      } else if (resource instanceof ValueSet) {
        ValueSet vs = (ValueSet) resource;
        this.addValueSet(vs);
      } else if (resource instanceof StructureDefinition) {
        this.addStructureDefinition(resource);
      }
    }
  }

  @Override
  public CodeValidationResult validateCodeInValueSet(
      ValidationSupportContext theValidationSupportContext,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      String theCode,
      String theDisplay,
      @Nonnull IBaseResource theValueSet) {
    return validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode,
        theDisplay, theValueSet.getIdElement().getValue());
  }

  @Override
  public CodeValidationResult validateCode(
      ValidationSupportContext theValidationSupportContext,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      String theCode,
      String theDisplay,
      String theValueSetUrl) {
    CodeValidationResult result = null;
    if (theValueSetUrl != null) {
      result = validateCodeUsingValueSet(theCodeSystem, theCode, theDisplay, theValueSetUrl);
    } else {
      LookupCodeResult codeSystemContainsCode =
          lookupCode(theValidationSupportContext, theCodeSystem, theCode, null);
      if (codeSystemContainsCode.isFound()) {
        result = new CodeValidationResult();
        result.setCode(theCode);
        result.setDisplay(theDisplay);
        result.setMessage("Included");
        result.setSeverity(IssueSeverity.INFORMATION);
      }
    }
    return result;
  }

  private CodeValidationResult validateCodeUsingValueSet(
      String theCodeSystem,
      String theCode,
      String theDisplay,
      String theValueSetUrl) {
    CodeValidationResult result = null;
    if (theValueSetUrl == null || theValueSetUrl.isEmpty()) {
      result = new CodeValidationResult();
      result.setCode(theCode);
      result.setDisplay(theDisplay);
      result.setMessage("No ValueSet!");
      result.setSeverity(IssueSeverity.FATAL);
    } else {
      ValueSet vs = (ValueSet) this.fetchValueSet(theValueSetUrl);
      if (vs.hasCompose()) {
        ValueSetComposeComponent vscc = vs.getCompose();
        if (vscc.hasInclude()) {
          for (ConceptSetComponent csc : vscc.getInclude()) {
            if ((theCodeSystem == null
                || (theCodeSystem != null && theCodeSystem.equals(csc.getSystem()))))  {
              for (ConceptReferenceComponent crc : csc.getConcept())  {
                if (crc.hasCode() && crc.getCode().equals(theCode)) {
                  result = new CodeValidationResult();
                  result.setCode(theCode);
                  result.setDisplay(theDisplay);
                  result.setMessage("Included");
                  result.setSeverity(IssueSeverity.INFORMATION);
                }
              }
            }
          }
        }
        if (result == null && vscc.hasExclude()) {
          for (ConceptSetComponent csc : vscc.getExclude()) {
            if ((theCodeSystem == null
                || (theCodeSystem != null && theCodeSystem.equals(csc.getSystem()))))  {
              for (ConceptReferenceComponent crc : csc.getConcept())  {
                if (crc.hasCode() && crc.getCode().equals(theCode)) {
                  result = new CodeValidationResult();
                  result.setCode(theCode);
                  result.setDisplay(theDisplay);
                  result.setMessage("Excluded");
                  result.setSeverity(IssueSeverity.ERROR);
                }
              }
            }
          }
        }
      }
      if (result == null && vs.hasExpansion()) {
        ValueSetExpansionComponent vsec = vs.getExpansion();
        if (vsec.hasContains()) {
          for (ValueSetExpansionContainsComponent vsecc : vsec.getContains()) {
            if (theCodeSystem == null
                || (theCodeSystem != null && theCodeSystem.equals(vsecc.getSystem()))) {
              if (vsecc.getCode().equals(theCode)) {
                result = new CodeValidationResult();
                result.setCode(theCode);
                result.setDisplay(theDisplay);
                result.setMessage("Included");
                result.setSeverity(IssueSeverity.INFORMATION);
              }
            }
          }
        }
      }
    }

    return result;
  }

  private boolean conceptContainsCode(ConceptDefinitionComponent cdc, String code) {
    if (cdc.hasCode() && cdc.getCode().equals(code)) {
      return true;
    } else if (cdc.hasConcept()) {
      for (ConceptDefinitionComponent child : cdc.getConcept()) {
        if (conceptContainsCode(child, code)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public LookupCodeResult lookupCode(
      ValidationSupportContext theValidationSupportContext,
      String theSystem,
      String theCode,
      String theDisplayLanguage) {
    LookupCodeResult result = new LookupCodeResult();
    result.setSearchedForCode(theCode);
    result.setSearchedForSystem(theSystem);
    result.setFound(false);

    CodeSystem cs = (CodeSystem) this.fetchCodeSystem(theSystem);
    if (cs != null) {
      for (ConceptDefinitionComponent cdc : cs.getConcept()) {
        if (conceptContainsCode(cdc, theCode)) {
          result.setFound(true);
        }
      }
    }
    return result;
  }
}