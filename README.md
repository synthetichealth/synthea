# Synthea Patient Generator [![Build Status](https://travis-ci.org/synthetichealth/synthea.svg?branch=master)](https://travis-ci.org/synthetichealth/synthea)

Synthea is a Synthetic Patient Population Simulator. The goal is to output synthetic, realistic but not real, patient data and associated health records in a variety of formats.

Read our [wiki](https://github.com/synthetichealth/synthea/wiki) for more information.

Currently, Synthea features:
- Birth to Death Lifecycle
- Modular Rule System
  - Drop in [Generic Modules](https://github.com/synthetichealth/synthea/wiki/Generic-Module-Framework)
  - Custom Ruby rules modules for additional capabilities
- Primary Care Encounters and Emergency Room Encounters
- Conditions, Allergies, Medications, Vaccinations, Observations/Vitals, Labs
- Formats
 - FHIR (STU3)
 - C-CDA

### Quick Start
The output of C-CDA documents requires Mongo DB.
```
brew install mongodb
bundle install
```

### Generate Synthetic Patients
Generating an entire population at once...
```
mongod &
bundle exec rake synthea:generate
```
Or generating the population one at a time...
```
mongod &
bundle exec rake synthea:sequential
```

Some settings can be changed in `/config/synthea.yml`.

Synthea will output patient records in C-CDA (requires running instance of Mongo DB) and FHIR STU3 formats in `/output`.

### Upload to FHIR Server
After generating data, upload it to a STU3 server
```
bundle exec rake synthea:fhirupload[http://server/fhir/baseDstu3]
```

### Synthea GraphViz
Generate a graphical visualization of Synthea rules. Requires GraphViz to be installed.

```
brew install graphviz
bundle exec rake synthea:graphviz
```

# License

Copyright 2016 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
