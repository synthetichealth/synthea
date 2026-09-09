# Synthea Python Wrapper

A Python wrapper for the Synthea synthetic patient data generator that simplifies generating and working with FHIR patient data.

## Prerequisites

- Python 3.7 or higher
- Java 11 or higher (for running Synthea)
- Synthea project built with `./gradlew build`

## Installation

```bash
cd scripts/python_wrapper
# Optional: Create virtual environment
python3 -m venv synthea_env
source synthea_env/bin/activate  # On macOS/Linux
# synthea_env\Scripts\activate   # On Windows

# Install dependencies if needed in the future
# pip install -r requirements.txt
```

## Usage

### Basic Example

```python
from wrapper import SyntheaGenerator

gen = SyntheaGenerator(
    state="California",
    gender="F",
    age="30-40",
    patients=2,
    module="diabetes"
)

patients = gen.generate()
gen.save("my_output/diabetes_test")

print(f"Generated {len(patients)} patients")
```

### Constructor Parameters

- `state` (str): State to generate patients in (default: "Massachusetts")
- `gender` (str): Patient gender - "M" or "F" (default: None = both)
- `age` (str): Age range like "30-40" or single age "25" (default: None = all ages)
- `patients` (int): Number of patients to generate (default: 1)
- `module` (str): Specific module to run (default: None = all modules)
- `synth_path` (str): Path to Synthea directory (default: auto-detected)

### Methods

#### `generate()`
Runs Synthea and returns a list of FHIR Bundle dictionaries containing patient data.

#### `save(output_dir)`
Saves the generated FHIR files to the specified directory.

## Running the Example

```bash
# From the synthea root directory
python3 scripts/python_wrapper/example.py

# Or from the python_wrapper directory
cd scripts/python_wrapper
python3 example.py

# With virtual environment
source synthea_env/bin/activate
python example.py
```

## Error Handling

The wrapper includes comprehensive error handling:

- Validates Synthea CLI execution with `subprocess.CalledProcessError`
- Checks for output directory existence
- Handles JSON parsing errors gracefully
- Provides meaningful error messages with `RuntimeError`

## Output

The wrapper returns a list of FHIR Bundle dictionaries. Each bundle contains:
- Patient demographic information
- Medical conditions
- Medications
- Encounters
- Observations
- And other FHIR resources

## Advanced Usage

### Custom Synthea Path

```python
gen = SyntheaGenerator(
    state="Texas",
    patients=5,
    synth_path="/path/to/your/synthea/installation"
)
```

### Multiple Generations

```python
# Generate different patient populations
diabetes_gen = SyntheaGenerator(state="California", module="diabetes", patients=10)
diabetes_patients = diabetes_gen.generate()
diabetes_gen.save("output/diabetes")

heart_gen = SyntheaGenerator(state="New York", module="heart_disease", patients=5) 
heart_patients = heart_gen.generate()
heart_gen.save("output/heart_disease")
```

### Working with Generated Data

```python
patients = gen.generate()

for patient_bundle in patients:
    entries = patient_bundle.get("entry", [])
    
    # Find the patient resource
    patient_resource = None
    for entry in entries:
        if entry.get("resource", {}).get("resourceType") == "Patient":
            patient_resource = entry["resource"]
            break
    
    if patient_resource:
        name = patient_resource.get("name", [{}])[0]
        given = name.get("given", [])
        family = name.get("family", "")
        print(f"Patient: {' '.join(given)} {family}")
```

## Troubleshooting

### Common Issues

1. **"No patients generated"**: Check that the specified module exists in `src/main/resources/modules/`

2. **"CLI failed" error**: Ensure Synthea is built (`./gradlew build`) and the run script is executable (`chmod +x ./run_synthea`)

3. **Path errors**: Specify an explicit `synth_path` if auto-detection fails:
   ```python
   gen = SyntheaGenerator(synth_path="/absolute/path/to/synthea")
   ```

4. **Permission errors**: Make sure the wrapper has write permissions to the output directory

### Available Modules

Common modules include:
- `diabetes`
- `heart_disease`
- `lung_cancer`
- `breast_cancer`
- `stroke`
- `asthma`

See `src/main/resources/modules/` for the complete list.

## Integration

This wrapper is designed to be easily integrated into larger Python applications, data pipelines, and research workflows. It can be packaged as a standalone Python package when needed.