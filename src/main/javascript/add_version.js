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
  if(module['gmfVersion'] === undefined) {
    module['gmfVersion'] = Number(gmfVersion);
  }
  const updatedModuleJSON = JSON.stringify(module, null, 2);
  fs.writeFileSync(moduleJSONPath, updatedModuleJSON);
}