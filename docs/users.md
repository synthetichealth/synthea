# Setup swpc_Synthea
See [Readme](https://github.com/nhsengland/swpc_synthea/blob/master/README.md)

# Creating a patient dataset
See the original Synthea documentationt that can be found [here](https://github.com/synthetichealth/synthea/wiki/Basic-Setup-and-Running).

# Interegating a patient dataset
PLACEHOLDER


# Configuration

## Synthea Properties
The `src/main/resources/synthea.properties` file controls the main configuration of the simulation

Here is a reduced view of the file for the main configuration options used in swpc_synthea and their default values

```
exporter.baseDirectory = ./output/
exporter.use_uuid_filenames = false
exporter.subfolders_by_id_substring = false
exporter.years_of_history = 10
exporter.metadata.export = true
exporter.encoding = UTF-8
exporter.csv.export = true

# the number of patients to generate, by default
# this can be overridden by passing a different value to the Generator constructor
generate.default_population = 5

# the number of threads to use for the generator, set the value to -1 to match the number of
# available processors (as per Runtime.getRuntime().availableProcessors())
# defaults to -1 if not specified
generate.thread_pool_size = -1

generate.log_patients.detail = simple
# options are "none", "simple", or "detailed" (without quotes). defaults to simple if another value is used
# none = print nothing to the console during generation
# simple = print patient names once they are generated.
# detailed = print patient names, atributes, vital signs, etc..  May slow down processing

generate.timestep = 604800000
# time is in ms
# 1000 * 60 * 60 * 24 * 7 = 604800000

generate.demographics.default_file = geography/demographics.csv
generate.geography.postcodes.default_file = geography/postcodes.csv
generate.geography.country_code = England
generate.geography.timezones.default_file = geography/timezones.csv
generate.geography.foreign.birthplace.default_file = geography/foreign_birthplace.json
generate.geography.sdoh.default_file = geography/sdoh.csv

# Lookup Table Folder location
generate.lookup_tables = modules/lookup_tables/

# if criteria are provided, (for example, only_dead_patients, only_alive_patients, or a "patient keep module" with -k flag)
# this is the maximum number of times synthea will loop over a single slot attempting to produce a matching patient.
# after this many failed attempts, it will throw an exception.
# set this to 0 to allow for unlimited attempts (but be aware of the possibility that it will never complete!)
generate.max_attempts_to_keep_patient = 1000

# Probability of each person having a middle name. 0 is zero, 1.0 is 100% chance.
generate.middle_names = 0.80

# if true, the entire population will use veteran prevalence data
generate.veteran_population_override = false

# these should add up to 1.0
# weighting and categories are inspired by the following but there are no specific hard numbers to point to
# http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1694190/pdf/amjph00543-0042.pdf
# http://www.ncbi.nlm.nih.gov/pubmed/8122813
generate.demographics.socioeconomic.weights.income = 0.2
generate.demographics.socioeconomic.weights.education = 0.7
generate.demographics.socioeconomic.weights.occupation = 0.1

generate.demographics.socioeconomic.score.low = 0.0
generate.demographics.socioeconomic.score.middle = 0.25
generate.demographics.socioeconomic.score.high = 0.66

generate.demographics.socioeconomic.education.less_than_hs.min = 0.0
generate.demographics.socioeconomic.education.less_than_hs.max = 0.5
generate.demographics.socioeconomic.education.hs_degree.min = 0.1
generate.demographics.socioeconomic.education.hs_degree.max = 0.75
generate.demographics.socioeconomic.education.some_college.min = 0.3
generate.demographics.socioeconomic.education.some_college.max = 0.85
generate.demographics.socioeconomic.education.bs_degree.min = 0.5
generate.demographics.socioeconomic.education.bs_degree.max = 1.0

# The average family size in the US is 3.13. The 2010 FPL for a 3-person household is $18310. Tuned it to $17550 for realistic medicaid/ACA enrollments.
generate.demographics.socioeconomic.income.poverty = 17550
generate.demographics.socioeconomic.income.high = 75000

generate.birthweights.default_file = birthweights.csv
generate.birthweights.logging = false

# Providers
generate.providers.hospitals.default_file = providers/hospitals.csv
# --generate.providers.longterm.default_file = providers/longterm.csv
# --generate.providers.nursing.default_file = providers/nursing.csv
# --generate.providers.rehab.default_file = providers/rehab.csv
# --generate.providers.hospice.default_file = providers/hospice.csv
# --generate.providers.dialysis.default_file = providers/dialysis.csv
# --generate.providers.homehealth.default_file = providers/home_health_agencies.csv
# --generate.providers.veterans.default_file = providers/va_facilities.csv
generate.providers.urgentcare.default_file = providers/urgent_care_facilities.csv
generate.providers.primarycare.default_file = providers/primary_care_facilities.csv
# --generate.providers.ihs.hospitals.default_file = providers/ihs_facilities.csv
# --generate.providers.ihs.primarycare.default_file = providers/ihs_centers.csv

# Provider selection behavior
# How patients select a provider organization:
#  nearest - select the closest provider. See generate.providers.maximum_search_distance
#  random  - select randomly.
#  network - select a random provider in your insurance network. same as random except it changes every time the patient switches insurance provider.
#  medicare - select the nearest provider that can bill Medicare. If no Medicare provider is found, it defaults back to "nearest".
generate.providers.selection_behavior = nearest

# if a provider cannot be found for a certain type of service,
# this will default to the nearest hospital.
generate.providers.default_to_hospital_on_failure = true

# Quit Smoking
lifecycle.quit_smoking.baseline = 0.01
lifecycle.quit_smoking.timestep_delta = -0.01
lifecycle.quit_smoking.smoking_duration_factor_per_year = 1.0

# Quit Alcoholism
lifecycle.quit_alcoholism.baseline = 0.001
lifecycle.quit_alcoholism.timestep_delta = -0.001
lifecycle.quit_alcoholism.alcoholism_duration_factor_per_year = 1.0

# Adherence
lifecycle.adherence.baseline = 0.05

# set this to true to enable randomized "death by natural causes"
# highly recommended if "only_dead_patients" is true
lifecycle.death_by_natural_causes = false

# set this to enable "death by loss of care" or missed care,
# e.g. not covered by insurance or otherwise unaffordable.
# only functional if "generate.payers.loss_of_care" is also true.
lifecycle.death_by_loss_of_care = false

# Use physiology simulations to generate some VitalSigns
physiology.generators.enabled = false

# Allow physiology module states to be executed
# If false, all Physiology state objects will immediately redirect to the state defined in
# the alt_direct_transition field
physiology.state.enabled = false

# set to true to introduce errors in height, weight and BMI observations for people
# under 20 years old
growtherrors = false
```


