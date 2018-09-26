package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.dstu3.hapi.validation.DefaultProfileValidationSupport;
import org.hl7.fhir.dstu3.hapi.validation.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.dstu3.hapi.ctx.IValidationSupport;
import org.hl7.fhir.dstu3.hapi.validation.FhirInstanceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ValidationResources loads validation resources (e.g. StructureDefinitions, ValueSets,
 * etc) and validates Resources conforming to those profiles using the validate method.
 */
public class ValidationResources {
  private FhirContext ctx;
  private FhirValidator validator;
  private FhirInstanceValidator instanceValidator;
  static final Logger logger = LoggerFactory.getLogger(ValidationResources.class);

  /**
   * Create FHIR context, validator, and validation support.
   */
  public ValidationResources() {
    // Only support for dstu3 for now
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
   * Runs validation on a given resource, logs the results, and returns the response.
   * @param theResource the resource to be validated
   * @return ValidationResult
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