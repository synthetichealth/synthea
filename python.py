import json
import pandas as pd
import os
from pathlib import Path

# Load and inspect the first JSON file to determine the structure
file_path = "/workspaces/synthea/output/fhir"  # Directory where files are uploaded
json_files = list(Path(file_path).glob("*.json"))

# Check the structure of one file
if json_files:
    sample_file = json_files[0]
    with open(sample_file, "r") as f:
        data = json.load(f)

    # Extracting general structure
    resource_entries = data.get("entry", [])
    
    # Collecting resource types
    resource_types = set(entry["resource"]["resourceType"] for entry in resource_entries if "resource" in entry)

    resource_types
else:
    resource_types = "No JSON files found."

resource_types
