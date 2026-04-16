# Synthea<sup>TM</sup> Patient Generator ![Build Status](https://github.com/synthetichealth/synthea/workflows/.github/workflows/ci-build-test.yml/badge.svg?branch=master) [![codecov](https://codecov.io/gh/synthetichealth/synthea/branch/master/graph/badge.svg)](https://codecov.io/gh/synthetichealth/synthea)

Synthea<sup>TM</sup> is a Synthetic Patient Population Simulator. The goal is to output synthetic, realistic (but not real), patient data and associated health records in a variety of formats.

Read our [wiki](https://github.com/synthetichealth/synthea/wiki) and [Frequently Asked Questions](https://github.com/synthetichealth/synthea/wiki/Frequently-Asked-Questions) for more information.

Currently, Synthea<sup>TM</sup> features include:
- Birth to Death Lifecycle
- Configuration-based statistics and demographics (defaults with Massachusetts Census data)
- Modular Rule System
  - Drop in [Generic Modules](https://github.com/synthetichealth/synthea/wiki/Generic-Module-Framework)
  - Custom Java rules modules for additional capabilities
- Primary Care Encounters, Emergency Room Encounters, and Symptom-Driven Encounters
- Conditions, Allergies, Medications, Vaccinations, Observations/Vitals, Labs, Procedures, CarePlans
- Formats
  - HL7 FHIR (R4, STU3 v3.0.1, and DSTU2 v1.0.2)
  - Bulk FHIR in ndjson format (set `exporter.fhir.bulk_data = true` to activate)
  - C-CDA (set `exporter.ccda.export = true` to activate)
  - CSV (set `exporter.csv.export = true` to activate)
  - CPCDS (set `exporter.cpcds.export = true` to activate)
- Rendering Rules and Disease Modules with Graphviz

## Developer Quick Start

These instructions are intended for those wishing to examine the Synthea source code, extend it or build the code locally. Those just wishing to run Synthea should follow the [Basic Setup and Running](https://github.com/synthetichealth/synthea/wiki/Basic-Setup-and-Running) instructions instead.

### Installation

**System Requirements:**
Synthea<sup>TM</sup> requires Java JDK 17 or newer. We strongly recommend using a Long-Term Support (LTS) release of Java, 17 or 25, as issues may occur with more recent non-LTS versions.

To clone the Synthea<sup>TM</sup> repo, then build and run the test suite:
```
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build check test
```

### Changing the default properties


The default properties file values can be found at `src/main/resources/synthea.properties`.
By default, synthea does not generate CCDA, CPCDA, CSV, or Bulk FHIR (ndjson). You'll need to
adjust this file to activate these features.  See the [wiki](https://github.com/synthetichealth/synthea/wiki)
for more details, or use our [guided customizer tool](https://synthetichealth.github.io/spt/#/customizer).



### Generate Synthetic Patients
Generating the population one at a time...
```
./run_synthea
```

Command-line arguments may be provided to specify a state, city, population size, or seed for randomization.
```
run_synthea [-s seed] [-p populationSize] [state [city]]
```

Full usage info can be printed by passing the `-h` option.
```
$ ./run_synthea -h

> Task :run
Usage: run_synthea [options] [state [city]]
Options: [-s seed]
         [-cs clinicianSeed]
         [-p populationSize]
         [-r referenceDate as YYYYMMDD]
         [-g gender]
         [-a minAge-maxAge]
         [-o overflowPopulation]
         [-c localConfigFilePath]
         [-d localModulesDirPath]
         [-i initialPopulationSnapshotPath]
         [-u updatedPopulationSnapshotPath]
         [-t updateTimePeriodInDays]
         [-f fixedRecordPath]
         [-k keepMatchingPatientsPath]
         [--config*=value]
          * any setting from src/main/resources/synthea.properties

Examples:
run_synthea Massachusetts
run_synthea Alaska Juneau
run_synthea -s 12345
run_synthea -p 1000
run_synthea -s 987 Washington Seattle
run_synthea -s 21 -p 100 Utah "Salt Lake City"
run_synthea -g M -a 60-65
run_synthea -p 10 --exporter.fhir.export=true
run_synthea --exporter.baseDirectory="./output_tx/" Texas
```

Some settings can be changed in `./src/main/resources/synthea.properties`.

Synthea<sup>TM</sup> will output patient records in C-CDA and FHIR formats in `./output`.

### Run with Docker

Build the image from the repository root:
```
docker build -t synthea .
```

To stamp the image with a specific Synthea version string, pass a build argument:
```
docker build -t synthea --build-arg SYNTHEA_VERSION=$(git describe --tags --always) .
```

The container writes generated artifacts to `/synthea-output` by default. Bind-mount a local directory there to make outputs available on the host:
```
mkdir -p ./output
docker run --rm \
  -v "$(pwd)/output:/synthea-output" \
  synthea -p 10 Massachusetts
```

Any Synthea CLI arguments can be passed after the image name and will be forwarded to the generator.
If no CLI arguments are provided, the container can build the run configuration from environment variables such as `SYNTHEA_POPULATION`, `SYNTHEA_STATE`, and `SYNTHEA_CITY`.

Set `SYNTHEA_OUTPUT_FORMAT` to choose the exported format without passing individual `--exporter.*` flags. Supported values are `fhir`, `bulk_fhir`, `fhir_stu3`, `fhir_dstu2`, `ccda`, `json`, `csv`, `cpcds`, `bfd`, `cdw`, `text`, and `clinical_note`. You can also provide a comma-separated list such as `fhir,csv`.

Example:
```
mkdir -p ./output
docker run --rm \
  -e SYNTHEA_OUTPUT_FORMAT=csv \
  -e SYNTHEA_POPULATION=10 \
  -e SYNTHEA_STATE=Massachusetts \
  -v "$(pwd)/output:/synthea-output" \
  synthea
```

Supported runtime env vars are also listed in `.env.example`.

To use a different in-container mount point, set `SYNTHEA_OUTPUT_DIR` and mount the same path:
```
mkdir -p ./synthea-data
docker run --rm \
  -e SYNTHEA_OUTPUT_DIR=/data \
  -v "$(pwd)/synthea-data:/data" \
  synthea -p 100 --exporter.csv.export=true Texas
```

Generated files will be written into the mounted host directory instead of the repository-local `./output` folder.

### Run with Docker Compose

For a repeatable local setup, use the included Compose file. It builds the image, mounts `./output` into the container, and runs Synthea with a default command:
```
docker compose up synthea
```

The default Compose runtime settings are currently:
```
SYNTHEA_POPULATION=10
SYNTHEA_STATE=Massachusetts
SYNTHEA_OUTPUT_FORMAT=fhir
```

The Compose setup also defaults `SYNTHEA_OUTPUT_FORMAT` to `fhir`. Override it per run or in your shell environment:
```
SYNTHEA_OUTPUT_FORMAT=csv docker compose run --rm synthea
```

You can set other generation defaults the same way:
```
SYNTHEA_POPULATION=100 SYNTHEA_STATE=Texas docker compose up synthea
```

If you pass explicit CLI arguments to `docker compose run` or `docker run`, those positional arguments are used instead of the env-based defaults:
```
docker compose run --rm synthea -p 100 Texas
```

If you want the image metadata to match a Git tag or commit, export `SYNTHEA_VERSION` before building:
```
export SYNTHEA_VERSION=$(git describe --tags --always)
docker compose build synthea
```

Compose automatically reads a project-local `.env` file for the `${...}` substitutions used in `compose.yaml`. The included `.env.example` shows all supported values for this container setup.

### Synthea<sup>TM</sup> GraphViz
Generate graphical visualizations of Synthea<sup>TM</sup> rules and modules.
```
./gradlew graphviz
```

### Concepts and Attributes
Generate a list of concepts (used in the records) or attributes (variables on each patient).
```
./gradlew concepts
./gradlew attributes
```

# License

Copyright 2017-2025 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
