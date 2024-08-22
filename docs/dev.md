# Dev setup
Dev setup can be found in the [README](https://github.com/nhsengland/swpc_synthea).

# Development
There are two types of development for the code:

- To develop the underlying engine see java.md.

- To develop the underlying information that the simulation works on read below about the resources and data sources

## Resource in swpc_synthea

```
├── src/main/resources
|   ├── export
|   ├── geography
|   ├── keep_modules
|   ├── modules
|   ├── physiology
|   ├── providers
|   ├── templates
|   biometrics.yml
|   birthweights.csv
|   bmi_correlations.json
|   cdc_growth_charts.json
|   cdc_wtleninf.csv
|   gdb_disability_weights.csv
|   growth_data_error_rates.json
|   htn_drugs.csv
|   immunization_schedule.json
|   language_lookup.json
|   names.yml
|   nhanes_two_year_olds_bmi.csv
|   race_ethnicity_codes.json
|   shr_mapping.csv
|   telemedicine_config.json
|   us_core_mapping.csv
```

We'll now highlight a few key files for the simulation inputs

### geography/demographics.csv
This file sets the general demographics for the population created during this simulation, such as age distributions and town populations. The meanings of the different columns in this file can be found [here](https://github.com/synthetichealth/synthea/wiki/Demographics-for-Other-Areas). 

The demographics breakdowns are done by the full region rather than per town as information per town was not available. 

| Column Name | Contains | Data Sources for UK Version |
| ----------- | ---------|---------------------------- |
| ID | Town ID number | monotonically increasing from 1 |
| COUNTY | County Code where town is located | TOWN_211CODE column from [ONS data table](https://www.ons.gov.uk/peoplepopulationandcommunity/populationandmigration/populationestimates/datasets/understandingtownsinenglandandwalespopulationanddemography) |
| NAME | Town Name | TOWN_211NAME column from above ONS table |
| STNAME | Region Name | REGION/COUNTRY from above ONS table |
| POPESTIMATE2015 | Population of that town | TOTAL population rows for 2019 from above ONS data |
| CTYNAME | County Name where town is located | range of wikipedia sites for the towns |
| TOT_POP |  total county population | [ONS Region data](https://explore-local-statistics.beta.ons.gov.uk/areas/E12000009-south-west) |
| Ethnicity (includes ASIAN, BLACK, MIXED, WHITE, OTHER columns) | Percentage of the population that is of a certain ethnicity[^1] | [NHS Health Survey England](https://digital.nhs.uk/data-and-information/publications/statistical/health-survey-england-additional-analyses/ethnicity-and-health-2011-2019-experimental-statistics/data-tables) |
| Ages (includes all age breakdown columns) | percentage of population in different age groups | Another [NHS Health Survey England](https://digital.nhs.uk/data-and-information/publications/statistical/health-survey-for-england/2021-part-2)[^2] |
| Income (includes all income breakdown columns) | percentage of population in different income brackets | [ONS Employment data](https://www.ons.gov.uk/datasets/ashe-tables-25/editions/time-series/versions/7)[^3] |
| LESS_THAN_HS | fraction of people with no qualifications, or level 1 or 2 of education (as classified by ONS) | [ONS Education data](https://www.ons.gov.uk/peoplepopulationandcommunity/educationandchildcare/bulletins/educationenglandandwales/census2021#how-highest-level-of-qualification-varied-across-england-and-wales) |
| HS_DEGREE | fraction of people with level 3 education | Same as above |
| SOME_COLLEGE | fraction of people with apprenticeships | Same as above |
| BS_DEGREE | fraction of people with level 4 education | Same as above |

Many of the above sources are complimented by data from the [2021 census](https://www.nomisweb.co.uk/sources/census_2021/report?compare=E12000009)

## geography/sdoh.csv
This file contains information on social determinants of health for the different regions.

| Column Name | Contains | Data Sources for UK Version |
| ----------- | ---------|---------------------------- |
| FOOD_INSECURITY | percentage of people with food insecurity | Sheffield University [study](https://shefuni.maps.arcgis.com/apps/instant/interactivelegend/index.html?appid=8be0cd9e18904c258afd3c959d6fc4d7) | 
| SEVERE_HOUSING_COST_BURDEN | percentage of people with severe housing cost burden [^4] | Government [English housing survey](https://www.gov.uk/government/statistics/annex-tables-for-english-housing-survey-headline-report-2022-to-2023) |
| UNEMPLOYMENT | percentage of unemployment | ONS local labour market [data](https://www.ons.gov.uk/visualisations/labourmarketlocal/E06000066/) |
| NO_VEHICLE_ACCESS | percentage of the population with no access to a vehicle | ONS census [data](https://www.ons.gov.uk/census/maps/choropleth/housing/number-of-cars-or-vans/number-of-cars-3a/no-cars-or-vans-in-household?geoLock=lad&lad=E07000042) |

## geography/postcodes.csv
Originally called zipcodes.csv but changed to use the English word. Postcode information found [here](https://www.freemaptools.com/download-uk-postcode-lat-lng.htm). 

## modules/ 
Store for the clinical modules saved as jsons.  Most of these are currently based on the original US Synthea<sup>TM</sup> version.  See index.md for a list of changed modules.

## providers/
These file sets different medical facilities for patients to attend in the simulation. 

GP practices in the South West were found in the [NHS digital GP Practice Data](https://digital.nhs.uk/services/organisation-data-service/export-data-files/csv-downloads/gp-and-gp-practice-related-data), and the conversion from postcode to latitude and longitude was done using the [grid reference finder](https://gridreferencefinder.com/postcodeBatchConverter/). 

[^1]: Ethnicity categories were changed from the American version to align better with UK ethnicity breakdowns 
[^2]: Under 18 ages set to 0 as we are only interested in an adult population currently. 
[^3]: Income brackets don't match exactly, and so estimations of the breakdowns within the brackets used in Synthea had to be done. 
[^4]: We used data on mortgagors who found affording their mortgage very or fairly difficult (table AT2_8) plus renters who found affording their rent very or fairly difficult over the total number of people surveyed in the study used. This data was only available for the whole country. 
