import os
import json
import re

# Configuration
base_url = "http://fileserver:8000"
input_format = "application/fhir+ndjson"
output_directory = "../output/fhir/"

# Function to generate manifest
def generate_manifest(base_url, input_format, output_directory):
    parameters = {
        "resourceType": "Parameters",
        "parameter": [
            {
                "name": "inputFormat",
                "valueCode": input_format
            },
            {
                "name": "inputSource",
                "valueUrl": base_url
            },
            {
                "name": "storageDetail",
                "part": [
                    {
                        "name": "type",
                        "valueCode": "http"
                    }
                ]
            },
            {
                "name": "input",
                "part": []
            }
        ]
    }

    # Iterate through files in output_directory
    for filename in os.listdir(output_directory):
        if filename.endswith(".ndjson"):
            resource_type = os.path.splitext(filename)[0]
            resource_type = re.match(r'^[a-zA-Z]+', resource_type).group(0)
            file_url = f"{base_url}/{filename}"
            
            parameters["parameter"][3]["part"].append({
                "name": "type",
                "valueCode": resource_type
            })
            parameters["parameter"][3]["part"].append({
                "name": "url",
                "valueUrl": file_url
            })
    
    return parameters

# Generate manifest
parameters = generate_manifest(base_url, input_format, output_directory)

# Write Parameters resource to file
with open("manifest.json", "w") as f:
    json.dump(parameters, f, indent=2)

print("FHIR Parameters resource generated and saved as manifest.json")
