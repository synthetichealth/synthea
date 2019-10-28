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

