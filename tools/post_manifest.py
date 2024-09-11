import requests
import json

# Configuration
fhir_base_url = "http://localhost:8080/fhir"
manifest_file = "manifest.json"

# Read the manifest file
with open(manifest_file, "r") as f:
    manifest = json.load(f)

# Prepare the request URL and headers
request_url = f"{fhir_base_url}/$import"
headers = {
    "Content-Type": "application/json",
    "Accept": "application/fhir+json",
    "Prefer": "respond-async"
}

# Post the manifest to the FHIR server
response = requests.post(request_url, headers=headers, json=manifest)

# Check the response
if response.status_code == 202:
    print("Import request accepted.")
    content_location = response.headers.get("Content-Location")
    if content_location:
        print(f"Check status at: {content_location}")
    else:
        print("No Content-Location header found in the response.")
else:
    print(f"Failed to post manifest. Status code: {response.status_code}")
    try:
        error_message = response.json()
        print("Error details:", json.dumps(error_message, indent=2))
    except json.JSONDecodeError:
        print("Failed to decode error response.")

