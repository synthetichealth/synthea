# Synthea Physiology Simulations

The simulation configuration files in this directory can be used to execute
a single run of a Synthea physiology model via the gradle command line interface.
The configuration allows manipulation of the differential equation solver, step
size, sim duration, and configuration for output charts if desired. Output
files will be in the `output/physiology` directory.

Note that these are only utilized for the physiology gradle command and are not
part of the normal Synthea execution procedure.


## Configuration  

You will need to set the following two fields to `true` in the `src/main/resources/synthea.properties` file.  

```
# Use physiology simulations to generate some VitalSigns
physiology.generators.enabled = true

# Allow physiology module states to be executed
# If false, all Physiology state objects will immediately redirect to the state defined in
# the alt_direct_transition field
physiology.state.enabled = true
```

## Usage

`./gradlew physiology --args="config/simulations/[config name].yml"`


## Examples  

```
  ./gradlew physiology --args="config/simulations/circadian_clock.yml"
  ./gradlew physiology --args="config/simulations/ecg.yml"
  ./gradlew physiology --args="config/simulations/insulin_signalling_diabetic.yml"
  ./gradlew physiology --args="config/simulations/insulin_signalling_normal.yml"
  ./gradlew physiology --args="config/simulations/liver_metabolism.yml"
  ./gradlew physiology --args="config/simulations/mammalian_circadian_rhythm_non_24hr.yml"
  ./gradlew physiology --args="config/simulations/menstrual_cycle.yml"
  ./gradlew physiology --args="config/simulations/o2_transport_metabolism.yml"
  ./gradlew physiology --args="config/simulations/plasma_melatonin.yml"  
  ./gradlew physiology --args="config/simulations/pulmonary_fluid_dynamics.yml"
  ./gradlew physiology --args="config/simulations/pulmonary_oxygen_intake.yml"
  ./gradlew physiology --args="config/simulations/telomere_associated_dna_damage.yml"
  ./gradlew physiology --args="config/simulations/weight_change.yml"
```

## Output 

Graphs and raw data in CVS files will be found in `output/physiology` folder.  

You may also wish to create a large population of 10,000 or more individuals, and search for gallblader patients (which are currently the only patients that have ECG physiology data attached to them.)

```
# generate the sample patients
run_synthea -p 10000

# then search for gallbladder conditions with any of the following terms:
  - Media
  - 29303009 
  - Electrocardiogram
  - valueSampledData
```


