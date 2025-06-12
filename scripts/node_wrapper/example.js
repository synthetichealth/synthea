// scripts/node_wrapper/example.js

const SyntheaGenerator = require('./wrapper');
const fs = require('fs').promises;

async function main() {
  try {
    const gen = new SyntheaGenerator({
      state: "California",
      gender: "F",
      age: "30-40",
      patients: 2,
      module: "diabetes"
    });

    console.log('Generating patients...');
    const patients = await gen.generate();

    if (!patients || patients.length === 0) {
      console.log("No patients generated.");
    } else {
      console.log(`Generated ${patients.length} patients`);
      console.log("Sample patient resourceType:", patients[0]?.resourceType);
    }

    console.log('Saving files...');
    await gen.save("my_output/diabetes_test");

    const savedFiles = await fs.readdir("my_output/diabetes_test");
    console.log("Saved files:", savedFiles);
    
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

main();