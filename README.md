# synthea_java [![Build Status](https://travis-ci.org/synthetichealth/synthea_java.svg?branch=master)](https://travis-ci.org/synthetichealth/synthea_java)
Java implementation of the Synthea engine.

Partially implemented.
- Partial FHIR STU3 Export.
- Transitions are complete.
- Not all logic or states are finished.
- Demographics to not vary.
- System output and error messages slows everything down.

### Build and Test
```
./gradlew build test
```

### Run
```
./gradlew run
```

# License

Copyright 2017 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
