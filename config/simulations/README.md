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

## References  

- [Smith2004_CVS_human](https://www.ebi.ac.uk/biomodels/MODEL1006230000) 
- [Guyton1972_PulmonaryOxygenIntake](https://www.ebi.ac.uk/biomodels/MODEL0911047946) 
- [Guyton1972_PulmonaryFluidDynamics](https://www.ebi.ac.uk/biomodels/MODEL0911091440) 
- [Lai2007_O2_Transport_Metabolism](https://www.ebi.ac.uk/biomodels/BIOMD0000000248) 
- [Brännmark2013 - Insulin signalling in human adipocytes (normal condition)](https://www.ebi.ac.uk/biomodels/BIOMD0000000448) 
- [Brännmark2013 - Insulin signalling in human adipocytes (diabetic condition)](https://www.ebi.ac.uk/biomodels/BIOMD0000000449) 
- [Talemi2015 - Persistent telomere-associated DNA damage foci (TAF)](https://www.ebi.ac.uk/biomodels/MODEL1412200000) 
- [ChowHall2008 Dynamics of Human Weight Change_ODE_1](https://www.ebi.ac.uk/biomodels/BIOMD0000000901) 
- [Hong2009_CircadianClock](https://www.ebi.ac.uk/biomodels/BIOMD0000000216) 
- [Brown1997 - Plasma Melatonin Levels](https://www.ebi.ac.uk/biomodels/BIOMD0000000672) 
- [Leloup2004 - Mammalian Circadian Rhythm models for 23.8 and 24.2 hours](https://www.ebi.ac.uk/biomodels/BIOMD0000000975)  
- [Roblitz2013 - Menstrual Cycle following GnRH analogue administration](https://www.ebi.ac.uk/biomodels/BIOMD0000000494) 
- [Kyrylov2005_HPAaxis](https://www.ebi.ac.uk/biomodels/MODEL0478740924) 
- [Jerby2010_Liver_Metabolism](https://www.ebi.ac.uk/biomodels/MODEL1009150002)


