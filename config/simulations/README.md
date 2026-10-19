# Synthea Physiology Simulations

These configuration files run a single execution of a Synthea physiology
(BioModel) simulation from the command line. Each config selects a model, an ODE
solver, step size, and duration, and can render output charts. Output lands in
`output/physiology/{name}/` as a CSV, one or more PNG charts, and (optionally) a
FHIR resource.

## What this is

The BioModels here are **dual-use physiology primitives**. The same ODE model can
drive:

- a **wearables simulator** — device-style signals (an ECG trace, a nocturnal
  melatonin curve, a breathing waveform), and
- a **digital twin / digital avatar** — the underlying physiological state of a
  simulated person.

The collection started at n=1 (a single smartwatch heart rate) and grew into a
library of primitives spanning multiple **device types**, **physiology systems**,
and **time scales** (~1/second to ~1/month). The goal is breadth: a well-rounded
set of building blocks that downstream modules and applications can draw on.

## Two entry paths

1. **Standalone simulation** (this directory). Runs one model in isolation to
   prove the solver converges and to generate isolated signals + charts (and,
   optionally, FHIR). This is the hardware-simulation / validation path.
2. **In-framework module state** (`src/main/resources/physiology/...` +
   `Physiology` states inside a module). Incorporates a model's output into a
   simulated person during a normal Synthea run, exported as observations
   (FHIR `SampledData`).

Both are **opt-in** and off by default. Enable the in-framework paths with:

```
--physiology.generators.enabled=true    # PhysiologyValueGenerator -> VitalSigns
--physiology.state.enabled=true          # Physiology module states -> attributes/observations
```

The standalone path (below) does not require either flag.

## Catalog

Grouped by physiology system; time scale is the characteristic dynamics, "wired"
means an in-framework module already consumes it.

| System | Model / config | Time scale | Example device | Status |
|---|---|---|---|---|
| Cardiac | `ecg` (McSharry2003) | ~1/second | smartwatch / Holter | wired → `wearables_cardiac_monitoring` |
| Cardiac | `Smith2004_CVS`, `Smith2004_CVS_arrhythmia` | ~1/second | — | standalone example |
| Pulmonary | `pulmonary_oxygen_intake` (Guyton1972) | ~1/second | pulse-ox / CPAP | wired → `wearables_sleepapnea` |
| Pulmonary | `o2_transport_metabolism` (Lai2007) | ~1/minute | — | standalone |
| Pulmonary | `pulmonary_fluid_dynamics` (Guyton1972) | ~1/minute | — | standalone — resting equilibrium (needs input perturbation to show edema) |
| Endocrine / circadian | `circadian_clock` (Hong2009) | ~1/day | sleep tracker | wired → `wearables_sleepapnea` |
| Endocrine / circadian | `mammalian_circadian_rhythm_non_24hr` (Leloup2004) | ~1/day | — | standalone |
| Endocrine / circadian | `plasma_melatonin` (Brown1997) | ~1/day | light-exposure / sleep | standalone |
| Endocrine / circadian | `cortisol_depression` (HPA axis) | ~1/day | stress / fatigue monitor | standalone — chronic-stressor scenario |
| Endocrine / circadian | `kyrylov_hpa_axis` (Kyrylov2005) | ~1–2h ultradian | stress / fatigue monitor | standalone — pulsatile cortisol rhythm |
| Endocrine | `menstrual_cycle` (Roblitz2013) | ~1/month | cycle tracker | wired → `endometriosis` |
| Metabolic | `insulin_signalling_normal` (Brännmark2013) | ~1/minute | CGM | standalone |
| Metabolic | `insulin_signalling_diabetic` (Brännmark2013) | ~1/minute | CGM | standalone |
| Metabolic | `weight_change` (ChowHall2008) | ~1/day | smart scale | standalone |
| Cellular / aging | `telomere_associated_dna_damage` (Talemi2015) | long-term | — | standalone |

## Usage

`./gradlew physiology --args="config/simulations/[config name].yml"`

## Examples

```
  ./gradlew physiology --args="config/simulations/circadian_clock.yml"
  ./gradlew physiology --args="config/simulations/cortisol_depression.yml"
  ./gradlew physiology --args="config/simulations/ecg.yml"
  ./gradlew physiology --args="config/simulations/insulin_signalling_diabetic.yml"
  ./gradlew physiology --args="config/simulations/insulin_signalling_normal.yml"
  ./gradlew physiology --args="config/simulations/kyrylov_hpa_axis.yml"
  ./gradlew physiology --args="config/simulations/mammalian_circadian_rhythm_non_24hr.yml"
  ./gradlew physiology --args="config/simulations/menstrual_cycle.yml"
  ./gradlew physiology --args="config/simulations/o2_transport_metabolism.yml"
  ./gradlew physiology --args="config/simulations/plasma_melatonin.yml"
  ./gradlew physiology --args="config/simulations/pulmonary_fluid_dynamics.yml"
  ./gradlew physiology --args="config/simulations/pulmonary_oxygen_intake.yml"
  ./gradlew physiology --args="config/simulations/telomere_associated_dna_damage.yml"
  ./gradlew physiology --args="config/simulations/weight_change.yml"
```

## Synthea Usage

```
./run_synthea -s 21 -p 1000 Indiana "Evansville" --physiology.generators.enabled="true" --physiology.state.enabled="true"
```

## Output

Charts (PNG) and raw data (CSV) are written to `output/physiology/{name}/`.

You may also wish to create a large population of 10,000 or more individuals, and
search for gallbladder patients (currently the only patients that have ECG
physiology data attached to them):

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
