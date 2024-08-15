# UK adaptation of the Synthea<sup>TM</sup> Patient Generator

This is an adaption from a fork of Synthea<sup>TM</sup> taken on the 15th May 2024. 

Synthea<sup>TM</sup> is a Synthetic Patient Population Simulator. The goal is to output synthetic, realistic (but not real), patient data and associated health records in a variety of formats.  Read their [wiki](https://github.com/synthetichealth/synthea/wiki) and [Frequently Asked Questions](https://github.com/synthetichealth/synthea/wiki/Frequently-Asked-Questions) for more information.

This adaptaion makes a series of incompatible changes to Synthea<sup>TM</sup> in order create a tool for NHS England called "swpc_synthea".  These adaptions are listed in the MkDoc documentation pages. 

## Prerequisites
 - Java JDK 11 or newer

## Installation

Clone the repo, then build and run the test suite:
```
git clone https://github.com/nhsengland/swpc_synthea.git
cd swpc_synthea
./gradlew build check test
```

# Running swpc_synthea

The default properties file values can be found at `src/main/resources/synthea.properties`.

## Generate Synthetic Patients
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
```

# License for Synthea<sup>TM</sup>

2017-2023 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# License for NHS England UK Adaptions
*relating to any commits in this fork since the 15th May 2024*

2024 NHS England

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
