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
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;

import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * ValidationSupport provides implementation guide profiles (i.e. StructureDefinitions)
 * to the FHIR validation process. This class does not provide ValueSet expansion.
 */
public class ValidationSupportSTU3 extends PrePopulatedValidationSupport  {
  private static final String PROFILE_DIR = "structureDefinitions/stu3";

  /**
   * Defines the custom validation support for various implementation guides.
   * @param ctx the FHIR context
   */
  public ValidationSupportSTU3(FhirContext ctx) {
    super(ctx);
    
    try {
      loadFromDirectory(PROFILE_DIR);
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

    IParser jsonParser = FhirStu3.getContext().newJsonParser();
    jsonParser.setParserErrorHandler(new StrictErrorHandler());

    URL profilesFolder = ClassLoader.getSystemClassLoader().getResource(rootDir);
    Path path = Paths.get(profilesFolder.toURI());
    Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".json")).forEach(f -> {
          try {
            IBaseResource resource = jsonParser.parseResource(new FileReader(f.toFile()));
            if (resource instanceof CodeSystem) {
              this.addCodeSystem(resource);
            } else if (resource instanceof ValueSet) {
                // The PrePopulatedValidationSupport base class only supports
                // R4 ValueSets so we don't validate these here
                // ValueSet vs = (ValueSet) resource;
                // this.addValueSet(vs);
            } else if (resource instanceof StructureDefinition) {
              this.addStructureDefinition(resource);
            }
          } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
          }
        });
  }
}