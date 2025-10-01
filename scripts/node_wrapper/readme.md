# Synthea Node.js Wrapper

A Node.js wrapper for the Synthea synthetic patient data generator that simplifies generating and working with FHIR patient data.

## Prerequisites

- Node.js 14 or higher
- Java 11 or higher (for running Synthea)
- Synthea project built with `./gradlew build`

## Installation

```bash
cd scripts/node_wrapper
npm install  # If you add dependencies later
```

## Usage

### Basic Example

```javascript
const SyntheaGenerator = require('./wrapper');

async function generatePatients() {
  const gen = new SyntheaGenerator({
    state: "California",
    gender: "F",
    age: "30-40", 
    patients: 2,
    module: "diabetes"
  });

  const patients = await gen.generate();
  await gen.save("my_output/diabetes_test");
  
  console.log(`Generated ${patients.length} patients`);
}

generatePatients().catch(console.error);
```

### Constructor Options

- `state` (string): State to generate patients in (default: "Massachusetts")
- `gender` (string): Patient gender - "M" or "F" (default: null = both)
- `age` (string): Age range like "30-40" or single age "25" (default: null = all ages)
- `patients` (number): Number of patients to generate (default: 1)
- `module` (string): Specific module to run (default: null = all modules)
- `synthPath` (string): Path to Synthea directory (default: auto-detected)

### Methods

#### `generate()`
Runs Synthea and returns an array of FHIR Bundle objects containing patient data.

#### `save(outputDir)`
Saves the generated FHIR files to the specified directory.

## Running the Example

```bash
# From the synthea root directory
node scripts/node_wrapper/example.js

# Or from the node_wrapper directory
cd scripts/node_wrapper
node example.js

# Or using npm script
npm run example
```

## Error Handling

The wrapper includes comprehensive error handling:

- Validates Synthea execution
- Checks for output directory existence
- Handles JSON parsing errors gracefully
- Provides meaningful error messages

## Output

The wrapper returns an array of FHIR Bundle objects. Each bundle contains:
- Patient demographic information
- Medical conditions
- Medications
- Encounters
- Observations
- And other FHIR resources

## Integration

This wrapper is designed to be easily integrated into larger Node.js applications and can be refactored into an npm package when needed.