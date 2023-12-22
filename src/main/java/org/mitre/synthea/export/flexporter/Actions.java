package org.mitre.synthea.export.flexporter;

import ca.uhn.fhir.parser.IParser;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.export.flexporter.FieldWrapper.DateFieldWrapper;
import org.mitre.synthea.export.flexporter.FieldWrapper.ReferenceFieldWrapper;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/**
 * The Actions class represents the set of all actions the Flexporter can perform.
 * The entry point is the applyMapping function which invokes the appropriate method.
 * Unlike the State class where each state type is a subclass, this uses a method
 * to represent each action. At some point it may be refactored to be more like State.
 */
@SuppressWarnings("unchecked") // For now
public abstract class Actions {

  /**
   * Apply the given Mapping to the provided Bundle.
   * @param bundle FHIR bundle
   * @param mapping Flexporter mapping
   * @param person Synthea Person object that was used to create the Bundle.
   *     This will be null if running the flexporter standalone from the run_flexporter task.
   * @param fjContext Flexporter Javascript Context associated with this run
   * @return the Bundle after all transformations have been applied.
   *      Important: in many cases it will be the same Bundle object as passed in, but not always!
   */
  public static Bundle applyMapping(Bundle bundle, Mapping mapping, Person person,
      FlexporterJavascriptContext fjContext) {

    for (Map<String, Object> action : mapping.actions) {
      bundle = applyAction(bundle, action, person, fjContext);
    }

    return bundle;
  }

  /**
   * Apply the given single Action to the provided Bundle.
   * @param bundle FHIR bundle
   * @param action Flexporter action
   * @param person Synthea Person object that was used to create the Bundle.
   *     This will be null if running the flexporter standalone from the run_flexporter task.
   * @param fjContext Flexporter Javascript Context associated with this run
   * @return the Bundle after the transformation has been applied.
   *      Important: in many cases it will be the same Bundle object as passed in, but not always!
   */
  public static Bundle applyAction(Bundle bundle, Map<String, Object> action, Person person,
      FlexporterJavascriptContext fjContext) {
    // TODO: this could be handled better but for now just key off a specific field in the action

    Bundle returnBundle = bundle;
    // most actions modify the bundle in-place, but some might return a whole new one

    if (action.containsKey("profiles")) {
      applyProfiles(bundle, (List<Map<String, String>>) action.get("profiles"));

    } else if (action.containsKey("set_values")) {
      setValues(bundle, (List<Map<String, Object>>) action.get("set_values"), person, fjContext);

    } else if (action.containsKey("keep_resources")) {
      keepResources(bundle, (List<String>) action.get("keep_resources"));

    } else if (action.containsKey("delete_resources")) {
      deleteResources(bundle, (List<String>) action.get("delete_resources"));

    } else if (action.containsKey("create_resource")) {
      createResource(bundle, (List<Map<String, Object>>) action.get("create_resource"), person,
          null);

    } else if (action.containsKey("min_date") || action.containsKey("max_date")) {
      dateFilter(bundle, (String)action.get("min_date"), (String)action.get("max_date"));

    } else if (action.containsKey("shift_dates")) {
      shiftDates(bundle, (String)action.get("shift_dates"));

    } else if (action.containsKey("execute_script")) {
      returnBundle = executeScript((List<Map<String, String>>) action.get("execute_script"), bundle,
          fjContext);
    }

    return returnBundle;
  }


  /**
   * Apply a profile to resources matching certain rules. Note this only adds the profile URL to
   * resource.meta.profile, it does not apply any other transformations or checking.
   * Resources to apply the profile to are selected by FHIRPath. (Or simply resource type)
   *
   * @param bundle Bundle to apply profiles to
   * @param items List of rule definitions for applying profiles. Each item should have a "profile"
   *     for the URL and "applicability" for the FHIRPath to select resources.
   */
  public static void applyProfiles(Bundle bundle, List<Map<String, String>> items) {
    // TODO: might it be faster to loop over all the resources
    // and check applicability for each item only once?
    for (Map<String, String> item : items) {
      String applicability = item.get("applicability");
      String profile = item.get("profile");

      List<Base> matchingResources = FhirPathUtils.evaluateBundle(bundle, applicability, true);

      for (Base match : matchingResources) {
        if (match instanceof Resource) {
          applyProfile((Resource) match, profile);
        }
      }
    }
  }

  /**
   * Helper function to add a single profile URL to a resource,
   * and set the Meta if not already set.
   *
   * @param resource FHIR resource
   * @param profileURL Profile URL to add, if not already present
   */
  public static void applyProfile(Resource resource, String profileURL) {
    Meta meta = resource.getMeta();
    if (meta == null) {
      meta = new Meta();
      resource.setMeta(meta);
    }
    if (!meta.hasProfile(profileURL)) {
      meta.addProfile(profileURL);
    }
  }

  /**
   * Set values on existing resources within the Bundle, based on rules.
   *
   * @param bundle Bundle to apply rules to
   * @param items List of rules. Rules include "applicability" to select which resources to apply
   *     values to, and "fields" which are "location" and "value" pairs defining which field to set
   *     and what value to put there.
   * @param person Synthea person object to fetch values from (e.g, attributes). May be null
   * @param fjContext Javascript context for this run
   */
  public static void setValues(Bundle bundle, List<Map<String, Object>> items, Person person,
      FlexporterJavascriptContext fjContext) {
    for (Map<String, Object> entry : items) {
      String applicability = (String) entry.get("applicability");
      List<Map<String, Object>> fields = (List<Map<String, Object>>) entry.get("fields");

      List<Base> matchingResources = FhirPathUtils.evaluateBundle(bundle, applicability, true);

      for (Base match : matchingResources) {
        if (match instanceof Resource) {
          Map<String, Object> fhirPathMapping =
              createFhirPathMapping(fields, bundle, (Resource) match, person, fjContext);

          CustomFHIRPathResourceGeneratorR4<Resource> fhirPathgenerator =
              new CustomFHIRPathResourceGeneratorR4<>();
          fhirPathgenerator.setMapping(fhirPathMapping);
          fhirPathgenerator.setResource((Resource) match);

          fhirPathgenerator.generateResource((Class<Resource>) match.getClass());
        }
      }
    }
  }

  /**
   * Create new resources to add to the Bundle, either a single resource or a resource based on
   * other instances of existing resources. Fields on the resource as well as the "based on"
   * resource will be set based on rules.
   *
   * @param bundle Bundle to add resources to
   * @param resourcesToCreate List of rules. Rules include a "resourceType", optionally a "based_on"
   *     FHIRPath to select resources to base the new one off of, and "fields" which are "location"
   *     and "value" pairs defining which field to set and what value.
   * @param person Synthea person object to fetch values from (e.g, attributes). May be null
   * @param fjContext Javascript context for this run
   */
  public static void createResource(Bundle bundle, List<Map<String, Object>> resourcesToCreate,
      Person person, FlexporterJavascriptContext fjContext) {
    // TODO: this is fundamentally similar to setValues, so extract common logic

    for (Map<String, Object> newResourceDef : resourcesToCreate) {

      String resourceType = (String) newResourceDef.get("resourceType");

      String basedOnPath = null;
      String basedOnState = null;
      String basedOnModule = null;
      Map<String, Object> basedOn = (Map<String, Object>) newResourceDef.get("based_on");
      if (basedOn != null) {
        basedOnPath = (String) basedOn.get("resource");
        basedOnState = (String) basedOn.get("state");
        basedOnModule = (String) basedOn.get("module");
      }

      List<String> profiles = (List<String>) newResourceDef.get("profiles");
      List<Map<String, Object>> fields = (List<Map<String, Object>>) newResourceDef.get("fields");

      List<Base> basedOnResources;
      List<Map<String, Object>> writeback;

      if (basedOnPath != null) {
        basedOnResources = FhirPathUtils.evaluateBundle(bundle, basedOnPath, true);
        // this may return empty list, in which no new resources will be created

        writeback = (List<Map<String, Object>>) newResourceDef.get("writeback");

      } else if (basedOnState != null && basedOnModule != null) {
        String moduleKey = String.format("%s Module", basedOnModule);
        List<State> moduleHistory = (List<State>)person.attributes.get(moduleKey);

        if (moduleHistory == null) {

          // TODO - maybe throw an exception?
          return;
        }
        final String basedOnStateName = basedOnState; // java weirdness
        List<State> instances = moduleHistory.stream()
            .filter(s -> s.name.equals(basedOnStateName))
            .collect(Collectors.toList());

        basedOnResources = new ArrayList<>();

        for (State instance : instances) {
          Long entered = instance.entered;
          Long exited = instance.exited;

          // map these to a FHIR type so that we can re-use the existing concepts

          // TODO: not sure Encounter is the best but it gives us a Period
          //  and a reference to another encounter.
          // Parameters could also work if we want more generic things,
          //  but the FHIRPath to retrieve from it is ugly
          Encounter dummyEncounter = new Encounter();
          Period period = new Period();
          if (entered != null) {
            period.setStartElement(new DateTimeType(new Date(entered)));
          }
          if (exited != null) {
            period.setEndElement(new DateTimeType(new Date(exited)));
          }
          dummyEncounter.setPeriod(period);

          Encounter encounterAtThatState = findEncounterAtState(instance, bundle);
          if (encounterAtThatState != null) {
            dummyEncounter.setPartOf(new Reference("urn:uuid:" + encounterAtThatState.getId()));
            dummyEncounter.setParticipant(encounterAtThatState.getParticipant());
            // TODO: copy any other fields over?
          }

          basedOnResources.add(dummyEncounter);
        }

        if (!basedOnResources.isEmpty()) {
          // rewrite "State.entered" to "Encounter.period.start", etc
          // so that the end user doesn't have to know we used an Encounter

          for (Map<String, Object> field : fields) {
            Object valueDef = field.get("value");
            if (valueDef instanceof String && ((String) valueDef).startsWith("$")) {
              String value = (String) valueDef;
              if (value.contains("State.")) {
                value = value
                    .replace("State.entered", "Encounter.period.start")
                    .replace("State.exited", "Encounter.period.end");

                field.put("value", value);
              }
            }
          }

        }

        writeback = null;
      } else {
        basedOnResources = Collections.singletonList(null);
        writeback = null;
      }


      for (Base basedOnItem : basedOnResources) {
        // IMPORTANT: basedOnItem may be null

        Map<String, Object> fhirPathMapping =
            createFhirPathMapping(fields, bundle, (Resource) basedOnItem, person, fjContext);

        CustomFHIRPathResourceGeneratorR4<Resource> fhirPathgenerator =
            new CustomFHIRPathResourceGeneratorR4<>();
        fhirPathgenerator.setMapping(fhirPathMapping);

        Resource createdResource = fhirPathgenerator.generateResource(resourceType);

        // ensure the new resource has an ID
        // seems like this should work as part of the fhirpathgenerator, but it didn't
        // this might be easier anyway
        createdResource.setId(UUID.randomUUID().toString());
        // TODO: consistent UUIDs
        if (profiles != null) {
          profiles.forEach(p -> applyProfile(createdResource, p));
        }

        // TODO: see if there's a good way to add the resource after the based-on resource
        BundleEntryComponent newEntry = bundle.addEntry();

        newEntry.setResource(createdResource);
        newEntry.setFullUrl("urn:uuid:" + createdResource.getId());

        if (bundle.getType().equals(BundleType.TRANSACTION)) {
          BundleEntryRequestComponent request = newEntry.getRequest();
          // as of now everything in synthea is POST to resourceType.
          request.setMethod(HTTPVerb.POST);
          request.setUrl(resourceType);
        }

        if (writeback != null && !writeback.isEmpty()) {
          Map<String, Object> writebackMapping =
              createFhirPathMapping(writeback, bundle, createdResource, person, fjContext);

          CustomFHIRPathResourceGeneratorR4<Resource> writebackGenerator =
              new CustomFHIRPathResourceGeneratorR4<>();
          writebackGenerator.setMapping(writebackMapping);
          writebackGenerator.setResource((Resource) basedOnItem);

          writebackGenerator.generateResource((Class<? extends Resource>) basedOnItem.getClass());
        }
      }
    }
  }

  /**
   * Helper method to find the Encounter that was active as of the given State.
   * If the state type is Encounter, returns the Encounter it started.
   * May return null if there was no Encounter active when the state was hit,
   * or may return surprising results if the encounter was active in a different module.
   */
  private static Encounter findEncounterAtState(State state, Bundle bundle) {
    Encounter encounterAtThatState = null;

    if (state instanceof State.Encounter) {
      String uuid = state.entry.uuid.toString();
      for (BundleEntryComponent entry : bundle.getEntry())  {
        Resource r = entry.getResource();
        if (r instanceof Encounter && r.getId().equals(uuid)) {
          encounterAtThatState = (Encounter) r;
          break;
        }
      }

    } else if (state.entered != null) {
      LocalDateTime stateEntered =
          Instant.ofEpochMilli(state.entered).atOffset(ZoneOffset.UTC).toLocalDateTime();

      for (BundleEntryComponent entry : bundle.getEntry())  {
        Resource r = entry.getResource();
        if (r instanceof Encounter) {
          Encounter currEnc = (Encounter)r;
          Period period = currEnc.getPeriod();
          Date startDt = period.getStart();
          LocalDateTime encStart = startDt.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();

          Date endDt = period.getEnd();

          if (endDt == null) {
            // the Encounter is still open
            if (stateEntered.isAfter(encStart) || stateEntered.isEqual(encStart)) {
              encounterAtThatState = (Encounter) r;
              break;
            }
          } else {
            LocalDateTime encEnd = endDt.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
            if ((stateEntered.isAfter(encStart) || stateEntered.isEqual(encStart))
                && (stateEntered.isBefore(encEnd) || stateEntered.isEqual(encEnd))) {
              encounterAtThatState = (Encounter) r;
              break;
            }
          }
        }
      }
    }
    return encounterAtThatState;
  }


  private static Map<String, Object> createFhirPathMapping(List<Map<String, Object>> fields,
      Bundle sourceBundle, Resource sourceResource, Person person,
      FlexporterJavascriptContext fjContext) {

    Map<String, Object> fhirPathMapping = new HashMap<>();

    for (Map<String, Object> field : fields) {
      String location = (String)field.get("location");
      Object valueDef = field.get("value");
      String transform = (String)field.get("transform");

      if (valueDef == null) {
        // do nothing, leave it null
      } else if (valueDef instanceof String) {
        String valueString = (String)valueDef;

        if (valueString.startsWith("$")) {
          valueDef = getValue(sourceBundle, valueString, sourceResource, person, fjContext);
        } // else - assume it's a raw value
      }

      // TODO: consider a "skip-resource-if-null" kind of thing
      // or "don't create this resource if the referenced field on the source resource is missing"

      // Things are starting to get a little wonky with types.
      // What else could we have here?
      if (valueDef instanceof Base && ((Base) valueDef).isPrimitive()) {
        valueDef = ((Base)valueDef).primitiveValue();
      }

      if (transform != null) {
        // TODO: valuetransforms should support objects
        valueDef = ValueTransforms.apply((String)valueDef, transform);
      }

      if (valueDef instanceof String || valueDef instanceof Base) {
        // String may be a raw value from the mapping, or a primitive from FHIR
        // Base means we plucked a full FHIR object from somewhere
        fhirPathMapping.put(location, valueDef);

      } else if (valueDef instanceof Map<?,?>) {
        Map<String,Object> valueMap = (Map<String, Object>) valueDef;

        populateFhirPathMapping(fhirPathMapping, location, valueMap);

      } else {
        // unexpected type here - is it even possible to get anything else?
        String type = valueDef == null ? "null" : valueDef.getClass().toGenericString();
        System.err.println("Unhandled type in createFhirPathMapping: " + type);
      }
    }

    return fhirPathMapping;
  }

  private static void populateFhirPathMapping(Map<String, Object> fhirPathMapping, String basePath,
      Map<String, Object> valueMap) {
    for (Map.Entry<String,Object> entry : valueMap.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      String path = basePath + "." + key;

      if (value instanceof String) {
        fhirPathMapping.put(path, value);
      } else if (value instanceof Map<?,?>) {
        populateFhirPathMapping(fhirPathMapping, path, (Map<String, Object>) value);
      } else if (value != null) {
        System.err
            .println("Unexpected class found in populateFhirPathMapping -- " + value.getClass());
      }
    }
  }

  /**
   * Filter the Bundle by only keeping selected resources.
   *
   * @param bundle FHIR Bundle to filter
   * @param list List of resource types to retain, all other types not listed will be removed
   */
  private static void keepResources(Bundle bundle, List<String> list) {
    // TODO: make this FHIRPath instead of just straight resource types

    Set<String> resourceTypesToKeep = new HashSet<>(list);

    Set<String> deletedResourceIDs = new HashSet<>();

    Iterator<BundleEntryComponent> itr = bundle.getEntry().iterator();

    while (itr.hasNext()) {
      BundleEntryComponent entry = itr.next();

      Resource resource = entry.getResource();
      String resourceType = resource.getResourceType().toString();
      if (!resourceTypesToKeep.contains(resourceType)) {
        deletedResourceIDs.add(resource.getId());
        itr.remove();
      }
    }

    if (!deletedResourceIDs.isEmpty()) {
      pruneDeletedResources(bundle, deletedResourceIDs);
    }
  }

  /**
   * Shift all dates on all resources in the Bundle. Note this only modifies date/time-type fields
   * on resources and cannot modify dates embedded within strings (for example, clinical notes).
   * @param bundle FHIR Bundle to shift dates for
   * @param amountString Amount to shift in ISO-8601 format, either Period or Duration
   */
  private static void shiftDates(Bundle bundle, String amountString) {
    if (amountString == null) {
      return;
    }

    // note: amount can either be <= 1 day with second precision, or > 1 day with day precision
    // not both
    // (for example you can't shift by a year and 3 hours)
    TemporalAmount amount = null;

    if (amountString.contains("Y") || (amountString.indexOf('M') < amountString.indexOf('T'))) {
      // ISO-8601 period formats {@code PnYnMnD}
      // if we see Y, or M before T, it's a Period
      amount = java.time.Period.parse(amountString);
    } else {
      // ISO-8601 duration format {@code PnDTnHnMn.nS}
      amount = Duration.parse(amountString);
    }

    for (BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      List<DateFieldWrapper> dateFieldsOnResource =
          FieldWrapper.DATE_FIELDS.get(resource.getResourceType());
      if (dateFieldsOnResource == null) {
        continue;
      }

      for (DateFieldWrapper dateField : dateFieldsOnResource) {
        dateField.shift(resource, amount);
      }
    }
  }

  /**
   * Filter the Bundle by date. If any resource has a date outside the provided range
   * it will be filtered out.
   * TODO: align this with export.years_of_history filter. "active" items outside range?
   * @param bundle The Bundle to filter
   * @param minDateStr Earliest/start value of the time range, in ISO-8601 format
   * @param maxDateStr Latest/stop/end value of the time range, in ISO-8601 format
   */
  private static void dateFilter(Bundle bundle, String minDateStr, String maxDateStr) {
    if (minDateStr == null && maxDateStr == null) {
      return;
    }

    // TODO: support dates with or without time
    LocalDateTime minDate = null;
    LocalDateTime maxDate = null;

    if (minDateStr != null) {
      minDate = LocalDateTime.parse(minDateStr);
    }

    if (maxDateStr != null) {
      maxDate = LocalDateTime.parse(maxDateStr);
    }

    Set<String> deletedResourceIDs = new HashSet<>();

    Iterator<BundleEntryComponent> itr = bundle.getEntry().iterator();

    while (itr.hasNext()) {
      BundleEntryComponent entry = itr.next();

      Resource resource = entry.getResource();
      List<DateFieldWrapper> dateFieldsOnResource =
          FieldWrapper.DATE_FIELDS.get(resource.getResourceType());

      if (dateFieldsOnResource == null) {
        continue;
      }

      for (DateFieldWrapper dateField : dateFieldsOnResource) {
        if (!dateField.valueInRange(resource, minDate, maxDate)) {
          deletedResourceIDs.add(resource.getId());
          itr.remove();
          break;
        }
      }
    }

    if (!deletedResourceIDs.isEmpty()) {
      pruneDeletedResources(bundle, deletedResourceIDs);
    }
  }

  /**
   * Filter the Bundle by removing selected resources.
   * Resources that reference deleted resources will also be deleted, and so on.
   * TODO: Support different methods for handling references:
   * Cascade (current), Delete reference field but leave object, Do nothing
   *
   * @param bundle FHIR Bundle to filter
   * @param list List of resource types to delete, other types not listed will be kept
   */
  public static void deleteResources(Bundle bundle, List<String> list) {
    // TODO: make this FHIRPath instead of just straight resource types

    Set<String> resourceTypesToDelete = new HashSet<>(list);

    Set<String> deletedResourceIDs = new HashSet<>();

    Iterator<BundleEntryComponent> itr = bundle.getEntry().iterator();

    while (itr.hasNext()) {
      BundleEntryComponent entry = itr.next();

      Resource resource = entry.getResource();
      String resourceType = resource.getResourceType().toString();
      if (resourceTypesToDelete.contains(resourceType)) {
        deletedResourceIDs.add(resource.getId());
        itr.remove();
      }
    }

    if (!deletedResourceIDs.isEmpty()) {
      pruneDeletedResources(bundle, deletedResourceIDs);
    }
  }

  // TODO: add modes -- cascade delete resources, clear out the one field, etc
  private static void pruneDeletedResources(Bundle bundle, Set<String> deletedResourceIDs) {
    Iterator<BundleEntryComponent> itr = bundle.getEntry().iterator();

    Set<String> deletedIDsThisRound = new HashSet<>();

    while (itr.hasNext()) {
      BundleEntryComponent entry = itr.next();

      Resource resource = entry.getResource();

      List<ReferenceFieldWrapper> references =
          FieldWrapper.REFERENCE_FIELDS.get(resource.getResourceType());

      if (references == null) {
        continue;
      }

      for (ReferenceFieldWrapper rf : references) {
        List<String> referencedIDs = rf.getReferences(resource);

        boolean deleted = false;
        for (String referencedID : referencedIDs) {
          if (deletedResourceIDs.contains(referencedID)) {
            deletedIDsThisRound.add(resource.getId());
            itr.remove();
            deleted = true;
            break;
          }
        }
        if (deleted) {
          break;
        }
      }
    }

    if (!deletedIDsThisRound.isEmpty()) {
      pruneDeletedResources(bundle, deletedIDsThisRound);
    }
  }

  /**
   * Execute scripts against the given Bundle.
   *
   * @param scripts Script definitions, containing a definition of one or more JS functions, the
   *     name of the function to invoke, and whether it applies to the bundle as a whole, or to the
   *     individual resources.
   * @param bundle FHIR bundle to apply scripts against
   * @param fjContext Javascript context for this run
   * @return The new Bundle. IMPORTANT - using the JS context will always result in a new bundle,
   *     not modify the existing one in-place.
   */
  public static Bundle executeScript(List<Map<String, String>> scripts, Bundle bundle,
      FlexporterJavascriptContext fjContext) {
    IParser parser = FhirR4.getContext().newJsonParser();

    String bundleJson = parser.encodeResourceToString(bundle);

    fjContext.loadBundle(bundleJson);

    for (Map<String, String> scriptDef : scripts) {

      String function = scriptDef.get("function");
      String functionName = scriptDef.get("function_name");
      String applyTo = scriptDef.get("apply_to");

      fjContext.loadFunction(function);

      if (applyTo.equalsIgnoreCase("bundle")) {
        fjContext.applyFunctionToBundle(functionName);
      } else if (applyTo.equalsIgnoreCase("resource") || applyTo.equalsIgnoreCase("resources")) {
        String resourceType = scriptDef.get("resource_type");
        fjContext.applyFunctionToResources(functionName, resourceType);
      } else {
        throw new IllegalArgumentException("Unknown option for execute_script.apply_to: '" + applyTo
            + "'. Valid options are 'bundle' and 'resources'");
      }
    }

    String outBundleJson = fjContext.getBundle();

    Bundle newBundle = parser.parseResource(Bundle.class, outBundleJson);

    return newBundle;
  }


  private static Object getValue(Bundle bundle, String valueDef, Resource currentResource,
      Person person, FlexporterJavascriptContext fjContext) {
    // The flag has the format of $flagName([flagValue1, flagValue2, ..., flagValueN])

    String flag = StringUtils.substringBetween(valueDef, "$", "(");
    String flagValue = StringUtils.substringBetween(valueDef, "([", "])");
    String[] flagValues = flagValue.split(",");


    // basic naming scheme here is "set" and "get" refer to a particular resource
    // (ie, the current one, or the resource a new one is being based on)
    // and "find" refers to searching the entire bundle
    if (flag.equals("setRef")) {
      return setReference(currentResource, flagValues);
    } else if (flag.equals("getField")) {
      return getField(currentResource, fjContext, flagValues);
    } else if (flag.equals("findRef")) {
      return findReference(bundle, flagValues);
    } else if (flag.equals("findValue")) {
      return findValue(bundle, flagValues);
    } else if (flag.equals("getAttribute")) {
      return getAttribute(person, flagValues);
    } else if (flag.equals("randomCode")) {
      return randomCode(flagValues[0]);
    }

    return null;
  }

  private static String getAttribute(Person person, String... flagValues) {
    // flagValues[0] = attribute name
    // TODO: how to handle types that aren't just strings?

    // TODO: helpful error message if person == null
    Object attribute = person.attributes.get(flagValues[0]);

    if (attribute == null) {
      return null;
    }

    return String.valueOf(attribute);
  }

  private static String setReference(Resource currentResource, String... args) {
    return createReference(currentResource);
  }

  private static String createReference(Resource resource) {
    // ids in FHIR are a little weird
    // from some testing, ids in HAPI also seem to be a little flaky
    String id = resource.getId();

    if (id.startsWith("urn:uuid:")) {
      return id;
    }

    return resource.getResourceType().toString() + "/" + id;
  }

  private static Object getField(Resource currentResource, FlexporterJavascriptContext fjContext,
      String... args) {
    // args[0] = FHIRPath, from this resource
    // args[1] = how to disambiguate if there are multiple? TODO

    List<Base> fieldValues = FhirPathUtils.evaluateResource(currentResource, args[0]);

    if (fieldValues.isEmpty()) {
      return null;
    }

    return fieldValues.get(0);
  }


  private static String findReference(Bundle bundle, String... flagValues) {
    // args[0] = FHIRPath, find a resource in the bundle
    // args[1] = how to disambiguate. ex "same-encounter" TODO
    // note the challenge will be how to pick the "encounter" field on arbitrary resources
    // when in doubt, use more fhirpath?

    List<Base> matchingResources = FhirPathUtils.evaluateBundle(bundle, flagValues[0], true);

    if (matchingResources.isEmpty()) {
      return null;
    }

    return createReference((Resource) matchingResources.get(0));
  }

  private static Base findValue(Bundle bundle, String... args) {
    // args[0] = FHIRPath, from this resource
    // args[1] = how to disambiguate if there are multiple? TODO
    List<Base> fieldValues = FhirPathUtils.evaluateBundle(bundle, args[0], false);

    if (fieldValues.isEmpty()) {
      return null;
    }

    return fieldValues.get(0);
  }

  private static Map<String, String> randomCode(String valueSetUrl) {
    Code code = RandomCodeGenerator.getCode(valueSetUrl,
        (int) (Math.random() * Integer.MAX_VALUE));
    Map<String, String> codeAsMap = Map.of(
         "system", code.system,
         "code", code.code,
         "display", code.display);
    return codeAsMap;
  }
}
