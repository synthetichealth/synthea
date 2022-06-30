package org.mitre.synthea.export.flexporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;
import org.mitre.synthea.world.agents.Person;

// For now
@SuppressWarnings("unchecked")
public abstract class Actions {

  public static void applyMapping(Bundle bundle, Mapping mapping, Person person) {
    mapping.actions.forEach(action -> applyAction(bundle, action, person));
  }

  public static void applyAction(Bundle bundle, Map<String, Object> action, Person person) {
    // TODO: this could be handled better but for now just key off a specific field in the action

    if (action.containsKey("profiles")) {
      applyProfiles(bundle, (List<Map<String, String>>) action.get("profiles"));
    } else if (action.containsKey("set_values")) {
      setValues(bundle, (List<Map<String, Object>>) action.get("set_values"), person);
    } else if (action.containsKey("keep_resources")) {
      keepResources(bundle, (List<String>) action.get("keep_resources"));
    } else if (action.containsKey("delete_resources")) {
      deleteResources(bundle, (List<String>) action.get("delete_resources"));
    } else if (action.containsKey("create_resource")) {
      createResource(bundle, (List<Map<String, Object>>) action.get("create_resource"), person);
    }
  }


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

  private static void applyProfile(Resource resource, String profileURL) {
    Meta meta = resource.getMeta();
    if (meta == null) {
      meta = new Meta();
      resource.setMeta(meta);
    }
    if (!meta.hasProfile(profileURL)) {
      meta.addProfile(profileURL);
    }
  }

  public static void setValues(Bundle bundle, List<Map<String, Object>> items, Person person) {
    for (Map<String, Object> entry : items) {
      String applicability = (String) entry.get("applicability");
      List<Map<String, Object>> fields = (List<Map<String, Object>>) entry.get("fields");

      List<Base> matchingResources = FhirPathUtils.evaluateBundle(bundle, applicability, true);

      for (Base match : matchingResources) {
        if (match instanceof Resource) {
          Map<String, String> fhirPathMapping =
              createFhirPathMapping(fields, bundle, (Resource) match, person);

          CustomFHIRPathResourceGeneratorR4<Resource> fhirPathgenerator =
              new CustomFHIRPathResourceGeneratorR4<>(FhirPathUtils.FHIR_CTX);
          fhirPathgenerator.setMapping(fhirPathMapping);
          fhirPathgenerator.setResource((Resource) match);

          fhirPathgenerator.generateResource((Class<Resource>) match.getClass());
        }
      }
    }
  }


  public static void createResource(Bundle bundle, List<Map<String, Object>> resourcesToCreate,
      Person person) {
    // TODO: this is fundamentally similar to setValues, so extract common logic

    for (Map<String, Object> newResourceDef : resourcesToCreate) {

      String resourceType = (String) newResourceDef.get("resourceType");
      String basedOnPath = (String) newResourceDef.get("based_on");
      List<String> profiles = (List<String>) newResourceDef.get("profiles");

      List<Base> basedOnResources;
      List<Map<String, Object>> writeback;

      if (basedOnPath == null) {
        basedOnResources = Collections.singletonList(null);
        writeback = null;
      } else {
        basedOnResources = FhirPathUtils.evaluateBundle(bundle, basedOnPath, true);
        // this may return empty list, in which no new resources will be created

        writeback = (List<Map<String, Object>>) newResourceDef.get("writeback");
      }

      List<Map<String, Object>> fields = (List<Map<String, Object>>) newResourceDef.get("fields");

      for (Base basedOnItem : basedOnResources) {
        // IMPORTANT: basedOnItem may be null

        Map<String, String> fhirPathMapping =
            createFhirPathMapping(fields, bundle, (Resource) basedOnItem, person);

        CustomFHIRPathResourceGeneratorR4<Resource> fhirPathgenerator =
            new CustomFHIRPathResourceGeneratorR4<>(FhirPathUtils.FHIR_CTX);
        fhirPathgenerator.setMapping(fhirPathMapping);

        Resource createdResource = fhirPathgenerator.generateResource(resourceType);

        // ensure the new resource has an ID
        // seems like this should work as part of the fhirpathgenerator, but it didn't
        // this might be easier anyway
        createdResource.setId(UUID.randomUUID().toString());
        if (profiles != null) {
          profiles.forEach(p -> applyProfile(createdResource, p));
        }

        // TODO: see if there's a good way to add the resource after the based-on resource
        BundleEntryComponent newEntry = bundle.addEntry();

        newEntry.setResource(createdResource);

        if (bundle.getType().equals(BundleType.TRANSACTION)) {
          BundleEntryRequestComponent request = newEntry.getRequest();
          // as of now everything in synthea is POST to resourceType.
          request.setMethod(HTTPVerb.POST);
          request.setUrl(resourceType);
        }

        if (writeback != null && !writeback.isEmpty()) {
          Map<String, String> writebackMapping =
              createFhirPathMapping(writeback, bundle, createdResource, person);

          CustomFHIRPathResourceGeneratorR4<Resource> writebackGenerator =
              new CustomFHIRPathResourceGeneratorR4<>(FhirPathUtils.FHIR_CTX);
          writebackGenerator.setMapping(writebackMapping);
          writebackGenerator.setResource((Resource) basedOnItem);

          writebackGenerator.generateResource((Class<? extends Resource>) basedOnItem.getClass());
        }
      }
    }
  }


  private static Map<String, String> createFhirPathMapping(List<Map<String, Object>> fields,
      Bundle sourceBundle, Resource sourceResource, Person person) {
    Map<String, String> fhirPathMapping = new HashMap<>();

    for (Map<String, Object> field : fields) {
      String location = (String)field.get("location");
      Object valueDef = field.get("value");
      String transform = (String)field.get("transform");

      if (valueDef == null) {
        // do nothing, leave it null
      } else if (valueDef instanceof String) {
        String valueString = (String)valueDef;
        
        if (valueString.startsWith("$")) {
          valueDef = getValue(sourceBundle, valueString, sourceResource, person);
        } // else - assume it's a raw value
        
      } else if (valueDef instanceof Map<?,?>) {
        System.out.println("breakpoint me");
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

      // TODO: the $getField option allows copying a single primitive value
      // do we want to allow copying an entire object somehow?


      
      if (valueDef instanceof String) {
        String valueString = (String)valueDef;
        
        fhirPathMapping.put(location, valueString);
        
      } else if (valueDef instanceof Map<?,?>) {
        // TODO: objects should be nestable to >1 level
        Map<String,String> valueMap = (Map<String, String>) valueDef;
        for(Map.Entry<String,String> entry : valueMap.entrySet()) {
          String key = entry.getKey();
          String value = entry.getValue();
          
          fhirPathMapping.put(location + "." + key, value);
        }
      } else if (valueDef instanceof Base) {
        // we plucked a full FHIR object from somewhere.
        // the ugly/slow way to handle it is to convert it to a Map<String,?>
        // and plug it into the fhirpath mapping as above
        
        // note that hapi has strange internal state,
        // ex a CodeableConcept.coding.system is a string,
        // but internally represented as an object with multiple fields
        // "myStringValue", "myCoercedValue", "disallowExtensions"
        // so a trivial Gson conversion is out
        
        // ideally we'd be able to find the right setter function
        // and set it on the target directly
        // TODO 
        
        System.err.println("FHIR Types not yet handled in createFhirPathMapping: " + valueDef.getClass());
        
        
      } else {
        // 
        System.err.println("Unhandled type in createFhirPathMapping: " + valueDef.getClass());
      }
    }

    return fhirPathMapping;
  }

  public static void keepResources(Bundle bundle, List<String> list) {
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

    // TODO: additional passes for deleted resource IDs
  }


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

    // TODO: additional passes for deleted resource IDs
  }


  private static Object getValue(Bundle bundle, String valueDef, Resource currentResource,
      Person person) {
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
      return getField(currentResource, flagValues);
    } else if (flag.equals("findRef")) {
      return findReference(bundle, flagValues);
    } else if (flag.equals("findValue")) {
      return findValues(bundle, flagValues);
    } else if (flag.equals("getAttribute")) {
      return getAttribute(person, flagValues);
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

    if (id.startsWith("urn:uuid:"))
      return id;

    return resource.getResourceType().toString() + "/" + id;
  }

  private static Base getField(Resource currentResource, String... args) {
    // args[0] = FHIRPath, from this resource
    // args[1] = how to disambiguate if there are multiple? TODO

    List<Base> fieldValues = FhirPathUtils.evaluateResource(currentResource, args[0]);

    if (fieldValues.isEmpty())
      return null;

    return fieldValues.get(0);
  }


  private static String findReference(Bundle bundle, String... flagValues) {
    // args[0] = FHIRPath, find a resource in the bundle
    // args[1] = how to disambiguate. ex "same-encounter" TODO
    // note the challenge will be how to pick the "encounter" field on arbitrary resources
    // when in doubt, use more fhirpath?

    List<Base> matchingResources = FhirPathUtils.evaluateBundle(bundle, flagValues[0], true);

    if (matchingResources.isEmpty())
      return null;

    return createReference((Resource) matchingResources.get(0));
  }

  private static String findValues(Bundle bundle, String... args) {
    // args[0] = FHIRPath, from this resource
    // args[1] = how to disambiguate if there are multiple? TODO
    List<Base> fieldValues = FhirPathUtils.evaluateBundle(bundle, args[0], false);

    if (fieldValues.isEmpty())
      return null;

    return fieldValues.get(0).primitiveValue();
  }
}
