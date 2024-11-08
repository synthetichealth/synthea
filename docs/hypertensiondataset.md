# Sample Hypertension Dataset 

This dataset contains 1000 patients, run only through the hypertension module. This means the patients will have gone only through routine appointments, routine vaccinations, and hypertension diagnosis and treatment (if applicable to that specific patient). It can be found at `Sample_data/csv_hypertension_1000` or download it as a zip file [here](images/csv_hypertension_1000.zip){:download="csv_hypertension_1000.zip"}. 

## Data set tables descriptions 
Details on this dataset can be found in the attached technical output specification. 

[Synthea Hypertension TOS File Download](SyntheaUK_Hypertension_TOS.xlsx){:download="HypertensionTOS"}

## Limitations
* **South West only**: this proof-of-concept dataset is using demographics and healthcare providers exclusively from the South West of England. The processes for hypertension diagnosis and care are accurate to the UK NICE guidelines, but the  people demographics are from the South West and thus may not be accurate distributions for the rest of the UK. Sources for this data can be found [here](../devs/resources) and in the subpages attached to it. 
* **Hypertension only**: The only condition that has been adapted to be a UK process so far is hypertension, therefore, the pathway for hypertension is accurate, but no other disease pathways are included in the sample dataset. If you desire to have other diseases and don’t mind that they include US pathways instead of following the NICE guidelines, contact us to get a cut of data that includes other pathways. 
* **Prescription medication brands**: The types of medications prescribed when a patient in our dataset goes through the hypertension pathway are accurate to the NICE guidelines, however, the specific brands have been chosen randomly due to a lack of clinical input. If you have insight on which brands we should include, please raise an issue here with the details so that we can continue to improve this dataset. 

## Credits
We would like to credit Synthea for the original framework of this synthetic data generator.