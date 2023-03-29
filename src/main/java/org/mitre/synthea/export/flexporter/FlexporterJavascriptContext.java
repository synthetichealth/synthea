package org.mitre.synthea.export.flexporter;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class FlexporterJavascriptContext {
  private final Context jsContext;

  private Value workingBundleAsJSObject;

  /**
   * Default constructor for a Javascript Context.
   */
  public FlexporterJavascriptContext() {
    jsContext = Context.create("js");
    // TODO: if we want to add custom libraries like fhirpath or fhir-mapper, do it here
    // try {

    //   for (String filename : Arrays.asList("fhirpath.js", "fhir-mapper.js") ) {
    //     String fileText = Files.readString(Path.of());
    //     loadFunction(fileText);


    //     loadFile(filename);
    //   }
    //   System.out.println("All functions available from Java (as loaded into Bindings) "
    //              + jsContext.getBindings("js").getMemberKeys());
    // } catch (Exception e) {
    //      e.printStackTrace();
    // }
  }

  /**
   * Load the JS file referenced by the given file path into the JS context.
   * Globals registered in the given file will be accessible to other loaded functions.
   * @param filename Path to a JS file
   * @throws IOException if the file doesn't exist, can't be read, or can't be parsed as JS
   */
  public void loadFile(String filename) throws IOException {
    URL url = FlexporterJavascriptContext.class.getClassLoader().getResource("./lib/" + filename);

    jsContext.eval(Source.newBuilder("js", url).build());
  }

  /**
   * Load a JS function from string.
   * @param functionDef JavaScript code
   */
  public void loadFunction(String functionDef) {
    jsContext.eval("js", functionDef);
  }

  /**
   * Load a Bundle as the JS context's working bundle.
   * All executed functions will use the bundle as an argument.
   *
   * @param bundleAsString Bundle as JSON string
   */
  public void loadBundle(String bundleAsString) {
    // workingBundleAsJSObject = JSON.parse(bundleAsString)

    Value parseFn = jsContext.eval("js","(bundleString) => JSON.parse(bundleString)");

    workingBundleAsJSObject = parseFn.execute(bundleAsString);
  }

  /**
   * Get the current working Bundle as a JSON string.
   */
  public String getBundle() {
    // return JSON.stringify(workingBundleAsJSObject)

    Value stringifyFn = jsContext.eval("js","(bundle) => JSON.stringify(bundle)");

    String bundleString = stringifyFn.execute(workingBundleAsJSObject).asString();

    return bundleString;
  }

  /**
   * Applies a function to the working Bundle.
   * Invoked as `fnName(bundle)`
   * The function must have already been loaded by loadFile or loadFunction.
   *
   * @param fnName Function name to invoke
   */
  public void applyFunctionToBundle(String fnName) {
    // assumption is the fn has already been loaded by loadFunction.
    // good news -- based on testing, if the bundle is modified in-place
    //   in the JS context then our variable "sees" those updates.
    // (the variable maintains a reference to the object within the JS VM)

    Value applyFn = jsContext.getBindings("js").getMember(fnName);
    applyFn.execute(workingBundleAsJSObject);
  }

  /**
   * Applies a function to each resource within the Bundle.
   * Invoked as `fnName(resource, bundle)`.
   * (i.e., the bundle itself is also passed to the function as context)
   * The function must have already been loaded by loadFile or loadFunction.
   *
   * @param fnName Function name to invoke
   * @param resourceType Resource Type to apply the function to, other resources will be ignored
   */
  public void applyFunctionToResources(String fnName, String resourceType) {
    // assumption is the fn has already been loaded by loadFunction

    Value applyFn = jsContext.getBindings("js").getMember(fnName);

    Value entries = workingBundleAsJSObject.getMember("entry");

    for (int i = 0; i < entries.getArraySize(); i++) {
      Value entry = entries.getArrayElement(i);
      Value resource = entry.getMember("resource");

      if (StringUtils.isNotBlank(resourceType)) {
        String type = resource.getMember("resourceType").asString();
        if (!resourceType.equalsIgnoreCase(type)) {
          continue;
        }
      }

      // provide both the resource and the full bundle for context
      // (eg, so we can create references to other resources)
      applyFn.execute(resource, workingBundleAsJSObject);
    }
  }
}
