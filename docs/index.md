# Generating Primary Care Data Using UK Adaptation of [Synthea](https://synthetichealth.github.io/synthea/)

Primary care records are crucial for understanding healthcare interactions at both the population and individual levels. However, these records are difficult to obtain and integrate with other services, hindering innovation due to data unavailability and privacy concerns.

Our project aims to address this by developing a code base to generate primary care electronic health records. We start by creating a synthetic population that mirrors a region in England (currently focusing on the South West of England), and then adapt the US-based tool [Synthea](https://synthetichealth.github.io/synthea/) for the English NHS context. We chose Synthea as it is already a highly developed, accuracy tested synthetic generator, and adapting it is relatively simple with clinical input. It is also highly efficient and quick to generate large amounts of data. 

See the [data science website](https://nhsengland.github.io/datascience/our_work/swpclab/) for more details on the aims of the project. 

## Usage and Limitations

The aim of Synthea is to produce data that is "Realistic but not real." This means that data produced by Synthea can be used to trial systems, and to test software and procedures, but it should not be used to draw statistical insight or do analysis. 

The UK adaptation of Synthea has removed several features which were US specific, and integrates UK statistics. Details of the UK adaptations implemented can be found [here](https://github.com/nhsengland/swpc_synthea/blob/Adds_acceptible_usage/docs/ukadaptions.md).

### Evaluation

The functionality and stability of the release has been "smoke-tested" by assuring valid outputs can be obtained when using various option flags described in the documentation. 

In the current release of the UK Adaptation of Synthea there have not been any statistical validations of outputs. 
