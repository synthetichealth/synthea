# SNOMED-CT conversion to ICD-10-CM - Files and Resources
Due to the restrictions of licensing with ICD-10, the the files used by 
the `SnomedConversion.java` class to convert codes 
from SNOMED-CT to ICD-10-CM have not have been included in the 
repository.  This README is intended to guide a user in getting those
files for those translations to work.

Not including the necessary translating files and using the converter 
will not stop the program from running.  Translations simply won't be 
found, and the original code will be returned.
## SNOMED to ICD-10-CM Mapping
One resource for SNOMED-CT to ICD-10-CM  mapping files is from:

<https://www.nlm.nih.gov/healthit/snomedct/us_edition.html>

This was the source used in the original implementation.
The production full map file was solely used:
`der2_iisssccRefset_ExtendedMapFull_US1000124_20190901.txt`

The file uses tabs between columns, which lends to easy ingestion.
### Implementation
When the tab-delimited file is loaded by `SnomedConversion.java`, three 
specific columns will be used to find a match.  First, 
`referencedComponentId` represents the SNOMED-CT codes.  Second, 
`mapRule` is used to determine if a row contains the correct ICD-10-CM 
code.  Finally, `mapTarget` is the found ICD-10-CD equivalent to the 
SNOMED-CT code.
## ICD-10-CM Display Descriptions
Two simple government resources were used to map the equivalent 
ICD-10-CM codes to a description value:

1. <https://www.cms.gov/Medicare/Coding/ICD10/index>

2. <https://www.cdc.gov/nchs/icd/icd10cm.htm>

Both sources have downloadable text files.  The text files use spaces
to determine columns.  The spaces are not equal between columns, so a 
good way to export with a regular deliminator for ingestion is to use a 
Spreadsheet software that can autodetect columns, then export to CSV.  
Make sure to include column headers.

### Implementation
Upon loading of the CSV file, specific columns are looked for to load 
the ICD-10-CM codes with their descriptions into a map.  The String 
`code` needs to be bound to the ICD-10-CM code column, and the String 
`display` needs to be bound to the corresponding description column.

## Using the Translation Files
If using ICD-10-CM translations is desired and the necessary files 
(SNOMED-CT to ICD-10-CM mapping and ICD-10-CM descriptions) are have 
been acquired, the paths of the files must be supplied to the 
`SnomedConversion.loadSnomedMap()` method.  In addition, for the 
`SnomedConversionTest.java` unit tests to run and pass, the paths of 
test data files need to be updated.

More information on using the SNOMED-CT to ICD-10-CM translator can be 
found in the `SnomedConversion.java` JavaDocs and comments.