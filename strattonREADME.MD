# Synthea Data Generation

This README provides instructions on how to run Synthea to generate synthetic patient data for Arkansas.

## Prerequisites

- Java installed on your system
- Synthea JAR file located at `Z:\synthea\build\libs\synthea-with-dependencies.jar`

## Running Synthea

To generate synthetic patient data for Arkansas with specific parameters, use the following command:


- run java -jar Z:\synthea\build\libs\synthea-with-dependencies.jar Arkansas -a 18-64 -s 22 -cs 22


### Command Breakdown:

- `Arkansas`: Specifies the state for which to generate data
- `-a 18-64`: Sets the age range for generated patients (18 to 64 years old)
- `-s 22`: Sets the seed for random number generation
- `-cs 22`: Sets the seed for the city selection

## Additional Information

- Adjust the parameters as needed for your specific use case
- For more options and detailed documentation, refer to the [Synthea GitHub repository](https://github.com/synthetichealth/synthea)

## Output

The generated data will be saved in the default output directory specified by Synthea. Check the Synthea documentation for information on customizing the output location and format.

## Troubleshooting

If you encounter any issues while running Synthea, ensure that:
1. Java is properly installed and accessible from the command line
2. The path to the Synthea JAR file is correct
3. You have the necessary permissions to read the JAR file and write to the output directory

For further assistance, consult the Synthea documentation or reach out to the Synthea community.
