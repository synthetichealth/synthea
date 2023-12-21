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

if (fs.existsSync('./code_dictionary.json')) {
  const rawJSON = fs.readFileSync('./code_dictionary.json');
  const loadedDictionary = JSON.parse(rawJSON);
  Object.assign(codeDictionary, loadedDictionary);
  console.log("Using previously saved ./code_dictionary.json")
} else {
  processFiles(moduleDir, moduleDirPath, inventoryAllCodes);
  buildDictionary();
  fs.writeFileSync('./code_dictionary.json', JSON.stringify(codeDictionary, null, 2));
  console.log("Saved ./code_dictionary.json")
  // re-open to re-iterate through
  moduleDir = fs.opendirSync(moduleDirPath);
}

processFiles(moduleDir, moduleDirPath, checkAndUpdateAllCodes);

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
  console.log(moduleJSONPath);
  const rawJSON = fs.readFileSync(moduleJSONPath);
  let module = JSON.parse(rawJSON);
  
  walkObject(module, inventoryCode);
}

function checkAndUpdateAllCodes(moduleJSONPath) {
  console.log(moduleJSONPath);
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
    let { system, code, display } = codeObj;
    code = code.toString();
    if (!codeDictionary[system]) {
      codeDictionary[system] = {};
    }

    const body = requestBody(codeObj);
    const VALIDATE_CODE_HEADERS = {
      "Content-Type": "application/fhir+json",
      "Accept": "application/fhir+json"
    };
    const res = fetch(
        'http://tx.fhir.org/r4/CodeSystem/$validate-code?',
        { 
          method: 'POST', 
          body: JSON.stringify(body, null, 2), 
          headers: VALIDATE_CODE_HEADERS 
        }).json();

    const displayParam = res.parameter.find(p => p.name === 'display');
    const success = res.parameter.find(p => p.name === 'result').valueBoolean;
    if (success) {
      // the current display may or may not be the singular best,
      // but it is one of the allowed values.
      // the "display" param contains the official best value
      const bestDisplay = displayParam.valueString;

      if (codeInventory[system][code].has(bestDisplay)) {
        // we use the best display somewhere already, stick to it
        codeDictionary[system][code] = bestDisplay;
      } else {
        // standardize to the current display
        codeDictionary[system][code] = display;
      }
    } else {
      if (displayParam) {
        // the code is valid but the display is not allowed
        // update the code to use the given display and
        // store the given display going forward
        codeDictionary[system][code] = displayParam.valueString;
      } else {
        handleErrorOrExit(body, res);
      }
    }
  }
}

/**
 * Construct the request body to validate the given code.
 */
function requestBody(codeObj) {
  const coding = JSON.parse(JSON.stringify(codeObj)); // simple copy
  coding.code = coding.code.toString();
  switch (coding.system) {
  case 'SNOMED-CT':
    coding.system = 'http://snomed.info/sct';
    break;
  case 'LOINC':
    coding.system = 'http://loinc.org';
    break;
  case 'RxNorm':
    coding.system = 'http://www.nlm.nih.gov/research/umls/rxnorm';
    break;
  case 'CVX':
    coding.system = 'http://hl7.org/fhir/sid/cvx';
    break;

  default:
    console.log(`Unexpected code system ${coding.system} may not be supported by tx.fhir.org`);
  }
  return {
    "resourceType": "Parameters",
    "parameter": [
      {
        "name": "coding",
        "valueCoding": coding
      },
      {
        "name": "default-to-latest-version",
        "valueBoolean": true
      }
    ]
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

  // capitalization doesn't appear to matter, so only update a code
  // if it differs in more than just caps
  if (codeDictionary[system][code].toLowerCase() != display.toLowerCase()) {
    console.log(`Updating "${display}" -> "${codeDictionary[system][code]}"`)
    codeObj.display = codeDictionary[system][code];
  }
}

/**
 * Handle errors from the terminology service.
 * "Unknown code" errors are expected so just print those out to the log.
 * Anything else, print the request and response then exit.
 */
function handleErrorOrExit(body, res) {
  const messageParam = res.parameter.find(p => p.name === 'message');
  if (messageParam) {
    const message = messageParam.valueString;

    if (message.startsWith("Unable to find code ") 
      || message.startsWith("Unknown Code '")) {
      console.log(message);
      return;
    }
  }
  // something else went wrong. print message and stop
  console.log("Error occurred.")
  console.log("Request body:")
  console.log(JSON.stringify(body, null, 2));
  console.log("\nResponse:")
  console.log(JSON.stringify(res, null, 2));
  process.exit(1);
}
