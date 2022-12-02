package org.mitre.synthea.export.flexporter;

import java.io.IOException;
import java.net.URL;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class FlexporterJavascriptContext {
  private final Context jsContext;

  private Value workingBundleAsJSObject;

  public FlexporterJavascriptContext() {
      jsContext = Context.create("js");
      // TODO: if we want to add custom libraries like fhirpath or fhir-mapper, do it here
//      try {
//
//          for (String filename : Arrays.asList("fhirpath.js", "fhir-mapper.js") ) {
//            String fileText = Files.readString(Path.of());
//            loadFunction(fileText);
//
//
//            loadFile(filename);
//          }
//          System.out.println("All functions available from Java (as loaded into Bindings) "
//                  + jsContext.getBindings("js").getMemberKeys());
//      } catch (Exception e) {
//          e.printStackTrace();
//      }
  }

  public void loadFile(String filename) throws IOException {
    URL url = FlexporterJavascriptContext.class.getClassLoader().getResource("./lib/" + filename);

    jsContext.eval(Source.newBuilder("js", url).build());
  }

  public void loadFunction(String functionDef) {
    jsContext.eval("js", functionDef);
  }

  public void loadBundle(String bundleAsString) {
    // workingBundleAsJSObject = JSON.parse(bundleAsString)

    Value parseFn = jsContext.eval("js","(bundleString) => JSON.parse(bundleString)");

    workingBundleAsJSObject = parseFn.execute(bundleAsString);
  }

  public String getBundle() {
    // return JSON.stringify(workingBundleAsJSObject)

    Value stringifyFn = jsContext.eval("js","(bundle) => JSON.stringify(bundle)");

    String bundleString = stringifyFn.execute(workingBundleAsJSObject).asString();

    return bundleString;
  }

  public void applyFunctionToBundle(String fnName) {
    // assumption is the fn has already been loaded by loadFunction
    // good news -- based on testing, if the bundle is modified in-place in the JS context then our variable "sees" those updates
    // (the variable maintains a reference to the object within the JS VM)

    Value applyFn = jsContext.getBindings("js").getMember(fnName);
    applyFn.execute(workingBundleAsJSObject);
  }

  public void applyFunctionToResources(String fnName) {
    // assumption is the fn has already been loaded by loadFunction

    Value applyFn = jsContext.getBindings("js").getMember(fnName);

    Value entries = workingBundleAsJSObject.getMember("entry");

    for (int i = 0; i < entries.getArraySize(); i++) {
      Value entry = entries.getArrayElement(i);

      Value resource = entry.getMember("resource");

      // provide both the resource and the full bundle for context
      // (eg, so we can create references to other resources)
      applyFn.execute(resource, workingBundleAsJSObject);
    }
  }

  public String exec(String fnName, String... args) {
    Value applyFn = jsContext.getBindings("js").getMember(fnName);
    Value processedJson = applyFn.execute((Object[])args);


    Value applyFn2 = jsContext.getBindings("js").getMember("getField2");

    Value result2 = applyFn2.execute(processedJson);

    return result2.asString();
  }

  public String applyTransforms(String fhirJson) {
    Value applyFn = jsContext.getBindings("js").getMember("apply");
    Value processedJson = applyFn.execute(fhirJson);

    String result = processedJson.asString();

    return result;
  }
}
