# Artifical Primary Care Data Using UK Version of [Synthea](https://synthetichealth.github.io/synthea/)

Primary care records are crucial for understanding healthcare interactions at both the population and individual levels. However, these records are difficult to obtain and integrate with other services, hindering innovation due to data unavailability and privacy concerns.

Our project aims to address this by developing a code base to generate primary care electronic health records. We start by creating a synthetic population that mirrors a region in England, and then adapt the US-based tool [Synthea](https://synthetichealth.github.io/synthea/) for the English NHS context (particularly focusing on the South West of England).

See the [data science website](https://nhsengland.github.io/datascience/our_work/swpclab/) for more details on the aims of the project. 

## Current UK Adaptations 
Currently the demographics for this generator are UK based, as is the Hypertension module and the Hypertension medication module. These two modules have been made based on the [NICE guidelines](https://www.nice.org.uk/guidance/ng136) for hypertension diagnosis, management and medication, together with some clinical input. However, they are still waiting on further clinical input, in particular for the choice of specific medications prescribed. Find documentation on the differences between NICE and the original version of these modules at: ```/docs/compare_hypertension_to_nice.pdf```

## Data Sources for demographics
### demographics.csv
This file sets the general demographics for the population created during this simulation, such as age distributions and town populations. The meanings of the different columns in this file can be found [here](https://github.com/synthetichealth/synthea/wiki/Demographics-for-Other-Areas). It can be found at: ```src/main/resources/geography/demographics.csv```.

The demographics breakdowns are done by the full region rather than per town as information per town was not available. 

| Column Name | Contains | Data Sources for UK Version |
| ----------- | ---------|---------------------------- |
| ID | Town ID number | monotonically increasing from 1 |
| COUNTY | code for the county that town is found in | TOWN_211CODE column from [ONS data table](https://www.ons.gov.uk/peoplepopulationandcommunity/populationandmigration/populationestimates/datasets/understandingtownsinenglandandwalespopulationanddemography) |
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

### Providers
These file sets different medical facilities for patients to attend in the simulation. It can be found at: ```src/main/resources/providers/..```.

GP practices in the South West were found in the [NHS digital GP Practice Data](https://digital.nhs.uk/services/organisation-data-service/export-data-files/csv-downloads/gp-and-gp-practice-related-data), and the conversion from postcode to latitude and longitude was done using the [grid reference finder](https://gridreferencefinder.com/postcodeBatchConverter/). 

### sdoh.csv
This file contains information on social determinants of health for the different regions.

| Column Name | Contains | Data Sources for UK Version |
| ----------- | ---------|---------------------------- |
| FOOD_INSECURITY | percentage of people with food insecurity | Sheffield University [study](https://shefuni.maps.arcgis.com/apps/instant/interactivelegend/index.html?appid=8be0cd9e18904c258afd3c959d6fc4d7) | 
| SEVERE_HOUSING_COST_BURDEN | percentage of people with severe housing cost burden [^4] | Government [English housing survey](https://www.gov.uk/government/statistics/annex-tables-for-english-housing-survey-headline-report-2022-to-2023) |
| UNEMPLOYMENT | percentage of unemployment | ONS local labour market [data](https://www.ons.gov.uk/visualisations/labourmarketlocal/E06000066/) |
| NO_VEHICLE_ACCESS | percentage of the population with no access to a vehicle | ONS census [data](https://www.ons.gov.uk/census/maps/choropleth/housing/number-of-cars-or-vans/number-of-cars-3a/no-cars-or-vans-in-household?geoLock=lad&lad=E07000042) |

### postcodes.csv
Originally called zipcodes.csv but changed to use the English word. Postcode information found [here](https://www.freemaptools.com/download-uk-postcode-lat-lng.htm). 

[^1]: Ethnicity categories were changed from the American version to align better with UK ethnicity breakdowns 
[^2]: Under 18 ages set to 0 as we are only interested in an adult population currently. 
[^3]: Income brackets don't match exactly, and so maths was done to estimate the brackets used in Synthea. 
[^4]: We used data on mortgagors who found affording their mortgage very or fairly difficult (table AT2_8) plus renters who found affording their rent very or fairly difficult over the total number of people surveyed in the study used. This data was only available for the whole country. 
