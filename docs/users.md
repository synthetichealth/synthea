# Creating a patient dataset
This is mostly taken from the original Synthea documentationt that can be found [here](https://github.com/synthetichealth/synthea/wiki/Basic-Setup-and-Running).

# Setup Synthea<sup>TM</sup>

These instructions are intended for those just wishing to run Synthea and generate data. 

## Prerequisites
 - Java 11 or newer (select JDK, not JRE install)

## Installation

Download the binary distribution to a local directory:

- [https://github.com/synthetichealth/synthea/releases/download/master-branch-latest/synthea-with-dependencies.jar](https://github.com/synthetichealth/synthea/releases/download/master-branch-latest/synthea-with-dependencies.jar)


# Running Synthea<sup>TM</sup>

```
cd /directory/you/downloaded/synthea/to
java -jar synthea-with-dependencies.jar
```

When you run this command, you should see output similar to the following:

```
Scanned 60 modules and 36 submodules.
Loading submodule modules/breast_cancer/tnm_diagnosis.json
Loading submodule modules/allergies/allergy_incidence.json
Loading submodule modules/dermatitis/moderate_cd_obs.json
...
Loading module modules/opioid_addiction.json
Loading module modules/dialysis.json
...
Loading module modules/hypertension.json
Running with options:
Population: 1
Seed: 1570658792125
Provider Seed:1570658792125
Location: Massachusetts
Min Age: 0
Max Age: 140
1 -- Arthur650 Carroll471 (39 y/o M) Southwick, Massachusetts 
{alive=1, dead=0}
```
This command takes additional parameters to specify different regions or common run options. Any options not specified are left at the default value.

```
java -jar synthea-with-dependencies.jar [-h]
                                        [-s seed] 
                                        [-r referenceDate as YYYYMMDD]
                                        [-cs clinician seed]
                                        [-p populationSize]
                                        [-g gender]
                                        [-a minAge-maxAge]
                                        [-c localConfigFilePath]
                                        [-d localModulesDirPath]
                                        [state [city]]
```

Some examples:

 -  `java -jar synthea-with-dependencies.jar -h` -- output help messages and command line options and quit without further processing
 -  `java -jar synthea-with-dependencies.jar Massachusetts` -- to generate a population in all cities and towns in Massachusetts
 -  `java -jar synthea-with-dependencies.jar Alaska Juneau` -- to generate a population in only Juneau, Alaska
 -  `java -jar synthea-with-dependencies.jar -s 12345` -- to generate a population using seed 12345. Populations generated with the same seed and the same version of Synthea should be identical
 -  `java -jar synthea-with-dependencies.jar -p 1000` -- to generate a population of 1000 patients
 -  `java -jar synthea-with-dependencies.jar -a 30-40` -- to generate a population of 30 to 40 year olds
 -  `java -jar synthea-with-dependencies.jar -g F` -- to generate only female patients
 -  `java -jar synthea-with-dependencies.jar -s 987 Washington Seattle` -- to generate a population in only Seattle, Washington, using seed 987
 -  `java -jar synthea-with-dependencies.jar -s 21 -p 100 Utah "Salt Lake City"` -- to generate a population of 100 patients in Salt Lake City, Utah, using seed 21
 -   `java -jar synthea-with-dependencies.jar --exporter.fhir.use_us_core_ig true` -- generate a population that exports FHIR according to the US Core R4 Implementation Guide profiles.

When running this the outputs will be found in the output folder. Currently the data generated will only work for the South West of England, thus the region settings won't work. 