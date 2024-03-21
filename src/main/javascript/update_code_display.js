// This is a stand alone node script to iterate through the modules
// and update all code displays that do not match an allowed value.
// (i.e., code displays that would not pass FHIR validation)
// The script also attempts to standardize when the same code is used in
// multiple modules, and to minimize the number of updates.
// Any invalid/unknown codes will be printed to console.
//
// Note this does require the `sync-fetch` library to be installed first.
// 
// Example:
//   npm install sync-fetch
//   node update_code_display.js /path/to/synthea/src/main/resources/modules/
//
// Tested on node v20.3.1

const fetch = require('sync-fetch')
const process = require('process');
const fs = require('fs');
const path = require('path');

const moduleDirPath = process.argv[2];

let moduleDir = fs.opendirSync(moduleDirPath);

const codeInventory = {};
// codeInventory[system][code] = set("display1", "display2", ...)

const allCodes = [];

const codeDictionary = {};
// codeDictionary[system][code] = "display to use"

// systems to skip because tx.fhir.org doesn't know about them
const SYSTEMS_TO_SKIP = ['NUBC', 'DICOM-DCM', 'DICOM-SOP'];

// codes used in Synthea as placeholders that we know aren't real
const PLACEHOLDER_CODES = ['999999',
  "99999-0", "99999-1", "99999-2", "99999-3", "99999-4",
  "99999-5", "99999-6", "99999-7", "99999-8", "99999-9",
  "99999-10", "99999-11",
  'X9999-0', 'X9999-1', 'X9999-2'];

const CODE_SYSTEM_URIS = {
 'SNOMED-CT': 'http://snomed.info/sct',
 'LOINC': 'http://loinc.org',
 'RxNorm': 'http://www.nlm.nih.gov/research/umls/rxnorm',
 'CVX': 'http://hl7.org/fhir/sid/cvx'
};

if (fs.existsSync('./code_dictionary.json')) {
  const rawJSON = fs.readFileSync('./code_dictionary.json');
  const loadedDictionary = JSON.parse(rawJSON);
  Object.assign(codeDictionary, loadedDictionary);
  console.log("Using previously saved ./code_dictionary.json")
} else {
  processFiles(moduleDir, moduleDirPath, inventoryAllCodes);
  // the Sets used in the inventory don't stringify properly,
  // so this replacer function turns them into arrays
  // https://stackoverflow.com/a/46491780
  const jsonifySets = (_key, value) => (value instanceof Set ? [...value] : value);
  fs.writeFileSync('./code_inventory.json', JSON.stringify(codeInventory, jsonifySets, 2));
  console.log("Saved ./code_inventory.json")

  buildDictionary();
  fs.writeFileSync('./code_dictionary.json', JSON.stringify(codeDictionary, null, 2));
  console.log("Saved ./code_dictionary.json")

  // re-open the module dir to re-iterate through it
  moduleDir = fs.opendirSync(moduleDirPath);
}

processFiles(moduleDir, moduleDirPath, checkAndUpdateAllCodes);

/**
 * Process all the module files in the given directory
 * by applying the given function to each path.
 */
function processFiles(dirEntry, parentPath, fileFunction) {
  let fileInFolder = dirEntry.readSync();
  while (fileInFolder != null) {
    if(fileInFolder.isDirectory()) {
      const newPath = path.join(parentPath, fileInFolder.name);
      const subDir = fs.opendirSync(newPath);
      processFiles(subDir, newPath, fileFunction);
    } else if (fileInFolder.isFile() && fileInFolder.name.endsWith('.json')) {
      const moduleJSONPath = path.join(parentPath, fileInFolder.name);
      fileFunction(moduleJSONPath);
    }
    fileInFolder = dirEntry.readSync();
  }
}

function inventoryAllCodes(moduleJSONPath) {
  console.log(`Inventorying ${moduleJSONPath}`);
  const rawJSON = fs.readFileSync(moduleJSONPath);
  let module = JSON.parse(rawJSON);
  
  walkObject(module, inventoryCode);
}

function checkAndUpdateAllCodes(moduleJSONPath) {
  console.log(`Checking and updating ${moduleJSONPath}`);
  const rawJSON = fs.readFileSync(moduleJSONPath);
  let module = JSON.parse(rawJSON);
  
  walkObject(module, checkAndUpdateCode);

  const updatedModuleJSON = JSON.stringify(module, null, 2);
  fs.writeFileSync(moduleJSONPath, updatedModuleJSON);
}

/**
 * Recursively iterate through the given object and
 * apply the given function to any "code" objects.
 * A "code" object is one that has fields "system", "code", and "display".
 */
function walkObject(object, codeFunction) {
  if (Array.isArray(object)) {
    object.forEach(o => walkObject(o, codeFunction));
  } else if (typeof object === 'object' && object !== null) {
    if (object.system && object.code && object.display) {
      if (SYSTEMS_TO_SKIP.includes(object.system) || PLACEHOLDER_CODES.includes(object.code.toString())) {
        return;
      }
      codeFunction(object);
    } else {
      for (const [key, value] of Object.entries(object)) {
        walkObject(value, codeFunction);
      }
    }
  }
  // else, it's a null or primitive, nothing to do
}

/**
 * Add the given code to the inventory.
 */
function inventoryCode(codeObj) {
  let { system, code, display } = codeObj;
  code = code.toString();

  if (!codeInventory[system]) {
    codeInventory[system] = {};
  }
  if (!codeInventory[system][code]) {
    codeInventory[system][code] = new Set();
    allCodes.push(codeObj);
  }

  codeInventory[system][code].add(display);
}

function buildDictionary() {
  for (const codeObj of allCodes) {
    let { system, code, display, version } = codeObj;
    code = code.toString();
    if (!codeDictionary[system]) {
      codeDictionary[system] = {};
    }
    let systemUri = CODE_SYSTEM_URIS[system];
    if (!systemUri) {
      systemUri = system;
      console.log(`Unexpected code system ${system} may not be supported by tx.fhir.org`);
    }
    // http://hl7.org/fhir/R4/terminology-service.html#validation
    let requestUrl = `http://tx.fhir.org/r4/CodeSystem/$validate-code?system=${systemUri}&code=${code}&display=${display}`
    if (version) {
      requestUrl += `&version=${version}`
    }
    const res = fetch(requestUrl, { headers: { "Accept": "application/fhir+json" } }).json();

    const displayParam = res.parameter.find(p => p.name === 'display');
    const success = res.parameter.find(p => p.name === 'result').valueBoolean;

    if (displayParam) {
      // the "display" param of the response contains a preferred code.
      // NOTE: in the case of SNOMED, the returned code is preferred but
      // not necessarily the "fully specified name".
      // eg, it may return "Aspirin" instead of "Aspirin (substance)"
      const aPreferredDisplay = displayParam.valueString;

      // success indicates whether the provided code was allowed
      const currentDisplay = success ? display : null;

      const bestDisplay = selectBestCode(system, codeInventory[system][code], aPreferredDisplay, currentDisplay);
      codeDictionary[system][code] = bestDisplay;
    } else {
      handleErrorOrExit(requestUrl, res);
    }
  }
}

function selectBestCode(system, inventory, aPreferredDisplay, currentDisplay) {
  if (system === 'SNOMED-CT' && !aPreferredDisplay.endsWith(")")) {
    // if the preferred code that the server returned doesn't have the semantic tag
    // then do some special logic
    for (const option of inventory) {
      // if we already use the preferred display with a snomed semantic tag, use that.
      // some codes aren't regex safe
      const regexSafeDisplay = aPreferredDisplay.replaceAll('+', '\\+').replaceAll('(', '\\(').replaceAll(')', '\\)');
      if (option.match(new RegExp(`^${regexSafeDisplay} \\([a-z\\+/ ]+\\)$`, 'i'))) {
        return option;
      }
    }
  }

  if (inventory.has(aPreferredDisplay)) {
    // we use the preferred display somewhere already, stick to it
    return aPreferredDisplay;
  } else if (currentDisplay) {
    // standardize to this current display to reduce changes
    return currentDisplay;
  } else {
    return aPreferredDisplay;
  }
}

/**
 * Check whether the display on the given code is appropriate,
 * and if not, update it.
 */
function checkAndUpdateCode(codeObj) {
  let { system, code, display } = codeObj;
  code = code.toString();
  if (!codeDictionary[system][code]) {
    // something went wrong in validating the code earlier,
    // the log will show what it was
    return;
  }

  const bestDisplay = codeDictionary[system][code];

  if (system === 'SNOMED-CT') {
    const regexSafeDisplay = display.replaceAll('+', '\\+').replaceAll('(', '\\(').replaceAll(')', '\\)');
    if (bestDisplay.match(new RegExp(`^${regexSafeDisplay} \\([a-z\\+/ ]+\\)$`, 'i'))) {
      // don't update just to add the snomed semantic tag 
      return;
    }
  }

  // capitalization doesn't appear to matter, so only update a code
  // if it differs in more than just caps
  if (bestDisplay.toLowerCase() != display.toLowerCase()) {
    console.log(`Updating "${display}" -> "${bestDisplay}"`)
    codeObj.display = bestDisplay;
  }
}

/**
 * Handle errors from the terminology service.
 * "Unknown code" errors are expected so just print those out to the log.
 * Anything else, print the request and response then exit.
 */
function handleErrorOrExit(requestUrl, res) {
  const messageParam = res.parameter.find(p => p.name === 'message');
  if (messageParam) {
    const message = messageParam.valueString;

    if (message.startsWith("Unable to find code ") 
      || message.startsWith("Unknown code '")) {
      console.log(message);
      return;
    }
  }
  // something else went wrong. print message and stop
  console.log("Error occurred.")
  console.log("Request URL:")
  console.log(requestUrl);
  console.log("\nResponse:")
  console.log(JSON.stringify(res, null, 2));
  process.exit(1);
}
