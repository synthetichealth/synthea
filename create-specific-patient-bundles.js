const fs = require('fs');
const path = require('path');
const readline = require('readline');

// Directory containing the NDJSON files
const inputDir = './output/fhir';
const outputDir = './output/specific_patients';

// Create output directory if it doesn't exist
if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true });
}

// Specific patient IDs to process
const targetPatientIds = [
  'c3c9077d-00d0-ea63-9193-8f634b813fe3',
  '43ba0765-eb10-12af-068d-b167230fb4eb',
  '75ecfdf3-8a27-5a77-1f2c-8fb092c93c1b',
  '1fbce2bd-a438-0ba4-8e49-7cb193fd58e5',
  '52294bd7-f902-c78f-c874-9a4ccaa3b048',
  '425a071a-86fe-c6c0-e40e-c9a7bdfbad9a',
  '8f9da769-5c7a-8497-20af-ab5d838add26',
  '64d71cae-83b8-e00e-f8f6-30e7b5e2774a',
  '3d4d0d95-1764-babf-3b45-9b49b2df450e',
  'cd15b4c9-7665-1f98-3c29-6570505d69b2',
  '31df6c73-61af-5e31-4b3a-6bf9072e6372',
  'c1be7227-e1c2-e8f4-caa4-d378eaefb1c4',
  'cd9b1b77-bb08-281d-6dee-8877510adc12',
  '117beb89-b808-3365-8096-e6754b95f9e3',
  'f264fb67-97d1-2ade-74b7-eb947ec8cfc7',
  '297ef0c9-c3a4-069a-a4f4-fa3f687e9412',
  '2de282e4-b739-50b6-d504-514ffc9513da',
  'e7bf774b-9bed-9d14-849e-ffb3a6334922',
  '30c08c27-f6ab-144d-7466-6fc45f676099',
  '535b06c6-9a3e-7cfe-9f24-07bf81398a4f'
];

async function main() {
  console.log(`Processing ${targetPatientIds.length} specific patients...`);
  
  // First, collect all resources related to these patients
  const patientResources = {};
  const relatedResourceIds = new Set();
  
  // Load all resource types from the directory
  const resourceFiles = fs.readdirSync(inputDir)
    .filter(file => file.endsWith('.ndjson'));
  
  // First pass: collect all direct patient resources and identify related resource IDs
  for (const file of resourceFiles) {
    const resourceType = path.basename(file, '.ndjson');
    const filePath = path.join(inputDir, file);
    
    if (resourceType === 'Patient') {
      // Get the patient resources first
      await collectPatientResources(filePath, targetPatientIds, patientResources);
    } else {
      // For other resources, identify which ones reference our target patients
      await collectRelatedResourceIds(filePath, targetPatientIds, relatedResourceIds);
    }
  }
  
  console.log(`Found ${Object.keys(patientResources).length} patient resources and ${relatedResourceIds.size} related resource IDs`);
  
  // Second pass: collect all resources that were identified as related
  const allResources = { ...patientResources };
  
  for (const file of resourceFiles) {
    if (file === 'Patient.ndjson') continue; // Skip patients, we already have them
    
    const resourceType = path.basename(file, '.ndjson');
    const filePath = path.join(inputDir, file);
    
    await collectResourcesByIds(filePath, relatedResourceIds, allResources);
  }
  
  // Create a bundle for each patient
  for (const patientId of targetPatientIds) {
    if (patientResources[patientId]) {
      await createPatientBundle(patientId, patientResources[patientId], allResources, outputDir);
    } else {
      console.log(`Warning: Patient ${patientId} not found in the input data`);
    }
  }
  
  console.log('All patient bundles created successfully!');
}

async function collectPatientResources(filePath, patientIds, resultMap) {
  const fileStream = fs.createReadStream(filePath);
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });
  
  for await (const line of rl) {
    try {
      const resource = JSON.parse(line);
      if (patientIds.includes(resource.id)) {
        resultMap[resource.id] = resource;
      }
    } catch (error) {
      console.error('Error parsing resource:', error);
    }
  }
}

async function collectRelatedResourceIds(filePath, patientIds, resultSet) {
  const fileStream = fs.createReadStream(filePath);
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });
  
  for await (const line of rl) {
    try {
      const resource = JSON.parse(line);
      
      // Check if this resource references any of our target patients
      if (isResourceRelatedToPatients(resource, patientIds)) {
        resultSet.add(resource.id);
        
        // Also add any resources this resource references
        collectReferencedResources(resource, resultSet);
      }
    } catch (error) {
      console.error(`Error processing resource in ${filePath}:`, error);
    }
  }
}

function isResourceRelatedToPatients(resource, patientIds) {
  const resourceStr = JSON.stringify(resource);
  
  // Check if this resource has any references to our target patients
  for (const patientId of patientIds) {
    if (resourceStr.includes(`Patient/${patientId}`)) {
      return true;
    }
  }
  
  return false;
}

function collectReferencedResources(resource, resultSet) {
  const visited = new Set();
  
  function traverse(obj) {
    if (!obj || visited.has(obj)) return;
    
    if (typeof obj === 'object') {
      visited.add(obj);
      
      // Check if this is a reference
      if (obj.reference && typeof obj.reference === 'string') {
        const parts = obj.reference.split('/');
        if (parts.length === 2 && parts[1]) {
          resultSet.add(parts[1]);
        }
      }
      
      // Traverse all properties
      for (const key in obj) {
        traverse(obj[key]);
      }
    }
  }
  
  traverse(resource);
}

async function collectResourcesByIds(filePath, resourceIds, resultMap) {
  const fileStream = fs.createReadStream(filePath);
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });
  
  for await (const line of rl) {
    try {
      const resource = JSON.parse(line);
      if (resourceIds.has(resource.id)) {
        resultMap[resource.id] = resource;
      }
    } catch (error) {
      console.error(`Error processing resource in ${filePath}:`, error);
    }
  }
}

async function createPatientBundle(patientId, patientResource, allResources, outputDir) {
  console.log(`Creating bundle for patient ${patientId}...`);
  
  // Build a set of all resources that should be in this patient's bundle
  const bundleResourceIds = new Set([patientId]);
  const relatedResources = findAllRelatedResources(patientId, allResources, bundleResourceIds);
  
  // Initialize the bundle
  const bundle = {
    resourceType: "Bundle",
    type: "transaction",
    entry: []
  };
  
  // First add the patient resource
  bundle.entry.push({
    fullUrl: `urn:uuid:${patientId}`,
    resource: patientResource,
    request: {
      method: "POST",
      url: "Patient"
    }
  });
  
  // Add all related resources
  for (const resourceId of relatedResources) {
    if (resourceId !== patientId && allResources[resourceId]) {
      const resource = allResources[resourceId];
      bundle.entry.push({
        fullUrl: `urn:uuid:${resourceId}`,
        resource: resource,
        request: {
          method: "POST",
          url: resource.resourceType
        }
      });
    }
  }
  
  // Write the bundle to a file
  const patientName = getPatientName(patientResource);
  const outputFile = path.join(outputDir, `${patientName}_bundle.json`);
  fs.writeFileSync(outputFile, JSON.stringify(bundle, null, 2));
  console.log(`  Bundle created at ${outputFile} with ${bundle.entry.length} resources`);
}

function findAllRelatedResources(patientId, allResources, visitedSet = new Set()) {
  // This is a recursive function that finds all resources directly or indirectly
  // related to the patient
  
  // Start with resources that directly reference the patient
  for (const [resourceId, resource] of Object.entries(allResources)) {
    if (visitedSet.has(resourceId)) continue;
    
    const resourceStr = JSON.stringify(resource);
    if (resourceStr.includes(`Patient/${patientId}`)) {
      visitedSet.add(resourceId);
      
      // Now recursively find resources that reference this resource
      findResourceReferences(resourceId, allResources, visitedSet);
    }
  }
  
  return visitedSet;
}

function findResourceReferences(resourceId, allResources, visitedSet) {
  // Find any other resources that reference this resource
  for (const [otherResourceId, otherResource] of Object.entries(allResources)) {
    if (visitedSet.has(otherResourceId)) continue;
    
    const resourceStr = JSON.stringify(otherResource);
    if (resourceStr.includes(`/${resourceId}`)) {
      visitedSet.add(otherResourceId);
      
      // Continue the recursion
      findResourceReferences(otherResourceId, allResources, visitedSet);
    }
  }
}

function getPatientName(patientResource) {
  if (patientResource.name && patientResource.name.length > 0) {
    const name = patientResource.name[0];
    
    let formattedName = '';
    if (name.given && name.given.length > 0) {
      formattedName += name.given[0];
    }
    
    if (name.family) {
      if (formattedName) formattedName += '_';
      formattedName += name.family;
    }
    
    if (formattedName) {
      return formattedName.replace(/\s+/g, '_');
    }
  }
  
  // Fallback to ID if no name is present
  return patientResource.id;
}

main().catch(console.error);