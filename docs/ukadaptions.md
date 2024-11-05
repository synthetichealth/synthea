# UK Adaptations to Synthea<sup>TM</sup>

**Stage 1: Removing non-English NHS functions and simplifying the Java to an MVP**

Functions relating to:

* Flexporter (functionality which could be brought back later)
* Payers and related managers
* Insurance plans
* Claims (mostly for medications)
* Income, healthcare expenses and coverage
* Cost
* Exporting as DSTU2 or STU3
* Cardiovascular disease module (as this is a US-based calculator)
* ASCVD, Framingam and C19 Immunizations (as these are all US-based and not applicable)
* CMSStateCodeMapper

These functions have all been commented using a `UKAdp` tag to keep an audit trail. These adaptions result in 113 sections of code commented out across 16 files (all within the `src/main/java/org/mitre/synthea/`).

**Stage 2: Adapting Resource files for UK South West Region context**

* Replace demographics.csv with South West towns and cities
* Replace fipscodes.csv with County GSS codes
* Update social determinants of health (sdoh.csv) file with food insecurity, severe housing cost burdens, unemployment, and vehicle access values correct for the UK regions.
* Replace timezones.csv with GMT
* Replace zipcodes.csv with uk based postcodes
* Keep birthweights.csv as US version (for the moment)
* Keep bmi_correlations.json as US version (for the moment)
* Keep cdc_growth_charts.json as US version (for the moment)
* Keep gbd_disability_weights.csv as US version (for the moment)
* Update immunization_schedule.json to vaccine schedules used in the UK
* Update synthea.properties to remove unused exporter and payer functionality and amend inputs for South West Region.
* Reduce the care settings down to hospitals, primary care and urgent care, and update these to have South West facilities.

There are still many US-based nuances that need to be dealt with such as payer columns still appearing in the outputs.

**Stage 3: Module Update**

The Hypertension module and the Hypertension medication module have been made based on the [NICE guidelines](https://www.nice.org.uk/guidance/ng136) for hypertension diagnosis, management and medication, together with some clinical input. However, they are still waiting on further clinical input, in particular for the choice of specific medications prescribed. 

Find documentation on the differences between NICE and the original US version of these modules at: ```/docs/compare_hypertension_to_nice.pdf```.
