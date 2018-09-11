package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.dstu3.hapi.validation.DefaultProfileValidationSupport;
import org.hl7.fhir.dstu3.hapi.validation.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.dstu3.hapi.ctx.IValidationSupport;
import org.hl7.fhir.dstu3.hapi.validation.FhirInstanceValidator;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ValidationResources {
  private FhirContext ctx;
  private FhirValidator validator;
  private FhirInstanceValidator instanceValidator;
  static final Logger logger = LoggerFactory.getLogger(ValidationResources.class);


  /**
   * Constructor for the class that creates the context and validator for usage by the
   * rest of the program.
   */
  public ValidationResources() {

    //Only support for dstu3 for now
    ctx = FhirContext.forDstu3();
    validator = ctx.newValidator();
    instanceValidator = new FhirInstanceValidator();
    IValidationSupport valSupport = new ValidationSupport();
    ValidationSupportChain support = new ValidationSupportChain(valSupport,
        new DefaultProfileValidationSupport());
    instanceValidator.setValidationSupport(support);
    validator.registerValidatorModule(instanceValidator);

  }

  /**
   * Loads the structure definitions from the given directory.
   * @param rootDir the directory to load structure definitions from
   * @return a list of structure definitions
   */
  public static List<StructureDefinition> loadFromDirectory(String rootDir) {

    IParser xmlParser = FhirContext.forDstu3().newXmlParser();
    xmlParser.setParserErrorHandler(new StrictErrorHandler());
    List<StructureDefinition> definitions = new ArrayList<>();

    File[] profiles =
        new File(ValidationResources.class.getClassLoader()
            .getResource(rootDir)
            .getFile())
            .listFiles();

    Arrays.asList(profiles).forEach(f -> {
      try {
        StructureDefinition sd = xmlParser.parseResource(StructureDefinition.class,
            new FileReader(f));
        definitions.add(sd);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    });

    return definitions;
  }

  /**
   * Runs the validator on a given resource and deals with response.
   * @param theResource the resource to be validated
   * @return whether the validation was successful or unsuccessful
   */
  public ValidationResult validate(IBaseResource theResource) {
    ValidationResult result = validator.validateWithResult(theResource);


    // Do we have any errors or fatal errors?

    // Show the issues
    for (SingleValidationMessage next : result.getMessages()) {
      switch (next.getSeverity()) {
        case ERROR:
          logger.error(next.getLocationString() + " - " + next.getMessage());
          break;
        case INFORMATION:
          logger.info(next.getLocationString() + " - " + next.getMessage());
          break;
        case WARNING:
          logger.warn(next.getLocationString() + " - " + next.getMessage());
          break;
        case FATAL:
          logger.error(next.getLocationString() + " - " + next.getMessage());
          break;
        default:
          logger.debug(next.getLocationString() + " - " + next.getMessage());
      }
    }

    return result;

  }


}