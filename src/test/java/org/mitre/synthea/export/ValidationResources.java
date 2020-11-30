package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;

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
   * Create FHIR context, validator, and validation chain.
   */
  public ValidationResources() {
    initializeSTU3();
    initializeR4();
  }

  private void initializeSTU3() {
    FhirContext ctx = FhirStu3.getContext();
    FhirInstanceValidator instanceValidator =
        new FhirInstanceValidator(ctx);
    ValidationSupportChain chain = new ValidationSupportChain(
            new ValidationSupportSTU3(ctx),
            new DefaultProfileValidationSupport(ctx),
            new InMemoryTerminologyServerValidationSupport(ctx),
            new CommonCodeSystemsTerminologyService(ctx)
    );
    instanceValidator.setValidationSupport(chain);
    instanceValidator.setAnyExtensionsAllowed(true);
    instanceValidator.setErrorForUnknownProfiles(false);
    validatorSTU3 = ctx.newValidator().registerValidatorModule(instanceValidator);
  }

  private void initializeR4() {
    FhirContext ctx = FhirR4.getContext();
    FhirInstanceValidator instanceValidator =
        new FhirInstanceValidator(ctx);
    ValidationSupportChain chain = new ValidationSupportChain(
            new ValidationSupportR4(ctx),
            new DefaultProfileValidationSupport(ctx),
            new InMemoryTerminologyServerValidationSupport(ctx),
            new CommonCodeSystemsTerminologyService(ctx)
    );
    instanceValidator.setValidationSupport(chain);
    instanceValidator.setAnyExtensionsAllowed(true);
    instanceValidator.setErrorForUnknownProfiles(false);
    validatorR4 = ctx.newValidator().registerValidatorModule(instanceValidator);
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