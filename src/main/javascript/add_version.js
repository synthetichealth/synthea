// This is a stand alone script to add a gmfVersion property to existing
// GMF JSON. This script takes a directory name and version as arguments.
//
// Example:
//   node add_version.js /path/to/synthea/src/main/resources/modules/ 1.0
//
// The script will add the property gmfVersion. It will not overwrite it
// if it already exists.
//
// The script runs on node.js with no other dependencies.
// Tested on node v14.8.0

const process = require('process');
const fs = require('fs');
const path = require('path');

const moduleDirPath = process.argv[2];
const gmfVersion = process.argv[3];

const moduleDir = fs.opendirSync(moduleDirPath);

processEntry(moduleDir, moduleDirPath);

function processEntry(dirEntry, parentPath) {
  let fileInFolder = dirEntry.readSync();
  while (fileInFolder != null) {
    if(fileInFolder.isDirectory()) {
      const newPath = path.join(parentPath, fileInFolder.name);
      const subDir = fs.opendirSync(newPath);
      processEntry(subDir, newPath);
    } else if (fileInFolder.isFile() && fileInFolder.name.endsWith('.json')) {
      const moduleJSONPath = path.join(parentPath, fileInFolder.name);
      addVersion(moduleJSONPath);
    }
    fileInFolder = dirEntry.readSync();
  }
}

function addVersion(moduleJSONPath) {
  console.log(moduleJSONPath);
  const rawJSON = fs.readFileSync(moduleJSONPath);
  let module = JSON.parse(rawJSON);
  if(module['gmf_version'] === undefined) {
    module['gmf_version'] = Number(gmfVersion);
  }
  const updatedModuleJSON = JSON.stringify(module, null, 2);
  fs.writeFileSync(moduleJSONPath, updatedModuleJSON);
}