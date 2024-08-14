# Generating Primary Care Data Using UK Version of [Synthea](https://synthetichealth.github.io/synthea/)

Primary care records are crucial for understanding healthcare interactions at both the population and individual levels. However, these records are difficult to obtain and integrate with other services, hindering innovation due to data unavailability and privacy concerns.

Our project aims to address this by developing a code base to generate primary care electronic health records. We start by creating a synthetic population that mirrors a region in England, and then adapt the US-based tool [Synthea](https://synthetichealth.github.io/synthea/) for the English NHS context (particularly focusing on the South West of England). We chose Synthea as it is already a highly developed, accuracy tested synthetic generator, and adapting it is relatively simple with clinical imput. It is also highly efficient and quick to generate large amounts of data. 

See the [data science website](https://nhsengland.github.io/datascience/our_work/swpclab/) for more details on the aims of the project. 

## Current UK Adaptations 
Currently the demographics for this generator are UK based, as is the Hypertension module and the Hypertension medication module. These two modules have been made based on the [NICE guidelines](https://www.nice.org.uk/guidance/ng136) for hypertension diagnosis, management and medication, together with some clinical input. However, they are still waiting on further clinical input, in particular for the choice of specific medications prescribed. Find documentation on the differences between NICE and the original version of these modules at: ```/docs/compare_hypertension_to_nice.pdf```