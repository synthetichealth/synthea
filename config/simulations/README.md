# Synthea Physiology Simulations

The simulation configuration files in this directory can be used to execute
a single run of a Synthea physiology model via the gradle command line interface.
The configuration allows manipulation of the differential equation solver, step
size, sim duration, and configuration for output charts if desired. Output
files will be in the `output/physiology` directory.

Note that these are only utilized for the physiology gradle command and are not
part of the normal Synthea execution procedure.

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
