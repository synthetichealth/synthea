package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.SchemaBaseValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import ca.uhn.fhir.validation.schematron.SchematronBaseValidator;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ValidationResources loads validation resources (e.g. StructureDefinitions, ValueSets,
 * etc) and validates Resources conforming to those profiles using the validate method.
 */
public class ValidationResources {
  private FhirValidator validatorSTU3;
  private FhirValidator validatorR4;
  static final Logger logger = LoggerFactory.getLogger(ValidationResources.class);

  /**
   * Create FHIR context, validator, and validation support.
   */
  public ValidationResources() {
    initializeSTU3();
    initializeR4();
  }

  private void initializeSTU3() {
    FhirContext ctx = FhirContext.forDstu3();
    validatorSTU3 = ctx.newValidator();
    org.hl7.fhir.dstu3.hapi.validation.FhirInstanceValidator instanceValidator =
        new org.hl7.fhir.dstu3.hapi.validation.FhirInstanceValidator();
    ValidationSupportSTU3 validationSupport = new ValidationSupportSTU3();
    org.hl7.fhir.dstu3.hapi.validation.ValidationSupportChain support =
        new org.hl7.fhir.dstu3.hapi.validation.ValidationSupportChain(
            new org.hl7.fhir.dstu3.hapi.ctx.DefaultProfileValidationSupport(), validationSupport);
    instanceValidator.setValidationSupport(support);

    IValidatorModule schemaValidator = new SchemaBaseValidator(ctx);
    IValidatorModule schematronValidator = new SchematronBaseValidator(ctx);

    validatorSTU3.registerValidatorModule(schemaValidator);
    validatorSTU3.registerValidatorModule(schematronValidator);
    validatorSTU3.registerValidatorModule(instanceValidator);
  }

  private void initializeR4() {
    FhirContext ctx = FhirContext.forR4();
    validatorR4 = ctx.newValidator();
    org.hl7.fhir.r4.hapi.validation.FhirInstanceValidator instanceValidator =
        new org.hl7.fhir.r4.hapi.validation.FhirInstanceValidator();
    ValidationSupportR4 validationSupport = new ValidationSupportR4();
    org.hl7.fhir.r4.hapi.validation.ValidationSupportChain support =
        new org.hl7.fhir.r4.hapi.validation.ValidationSupportChain(
            new org.hl7.fhir.r4.hapi.ctx.DefaultProfileValidationSupport(), validationSupport);
    instanceValidator.setValidationSupport(support);
    instanceValidator.setNoTerminologyChecks(true);

    IValidatorModule schemaValidator = new SchemaBaseValidator(ctx);
    IValidatorModule schematronValidator = new SchematronBaseValidator(ctx);

    validatorR4.registerValidatorModule(schemaValidator);
    validatorR4.registerValidatorModule(schematronValidator);
    validatorR4.registerValidatorModule(instanceValidator);
  }

  /**
   * Runs validation on a given resource, logs the results, and returns the response.
   * @param theResource the resource to be validated
   * @return ValidationResult
   */
  public ValidationResult validateSTU3(IBaseResource theResource) {
    ValidationResult result = validatorSTU3.validateWithResult(theResource);
    logIssues(result);
    return result;
  }

  /**
   * Runs validation on a given resource, logs the results, and returns the response.
   * @param theResource the resource to be validated
   * @return ValidationResult
   */
  public ValidationResult validateR4(IBaseResource theResource) {
    ValidationResult result = validatorR4.validateWithResult(theResource);
    logIssues(result);
    return result;
  }

  /**
   * Do we have any errors or fatal errors? If so, show the issues.
   * @param result Log the validation result.
   */
  private void logIssues(ValidationResult result) {
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
  }
}