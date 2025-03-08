import os
import json
import pandas as pd
from datetime import datetime, timedelta

# Path to your Synthea FHIR output
fhir_folder = r"Z:\synthea\output\fhir"

# List all JSON files
json_files = [f for f in os.listdir(fhir_folder) if f.endswith(".json")]

# Function to parse FHIR dates safely
def parse_fhir_date(date_str):
    if date_str:
        return pd.to_datetime(date_str[:10])  # Convert string to datetime
    return pd.NaT  # Return Not-a-Time for missing values

# Extract encounters
encounters = []

for file in json_files:
    file_path = os.path.join(fhir_folder, file)
    with open(file_path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
            if "entry" in data:
                for entry in data["entry"]:
                    resource = entry.get("resource", {})
                    if resource.get("resourceType") == "Encounter":
                        patient_id = resource["subject"]["reference"]
                        encounter_id = resource["id"]
                        start_date = parse_fhir_date(resource.get("period", {}).get("start"))
                        end_date = parse_fhir_date(resource.get("period", {}).get("end"))
                        encounter_class = resource.get("class", {}).get("code")

                        # ✅ Corrected diagnosis extraction
                        if "reasonCode" in resource and "coding" in resource["reasonCode"][0]:
                            diagnosis = resource["reasonCode"][0]["coding"][0].get("display", "Unknown")
                        else:
                            diagnosis = "Unknown"

                        encounters.append([patient_id, encounter_id, start_date, end_date, encounter_class, diagnosis])
        except json.JSONDecodeError:
            print(f"Skipping file due to JSON error: {file}")

# Create DataFrame
df = pd.DataFrame(encounters, columns=["Patient_ID", "Encounter_ID", "Start_Date", "End_Date", "Class", "Diagnosis"])

# Ensure dataframe is not empty
if df.empty:
    print("No encounters found in the dataset.")
else:
    # Ensure datetime format
    df["Start_Date"] = pd.to_datetime(df["Start_Date"])
    df["End_Date"] = pd.to_datetime(df["End_Date"])

    # Sort by Patient and Admission Date
    df = df.sort_values(by=["Patient_ID", "Start_Date"])

    # Identify Readmissions (within 30 days)
    df["Prev_Admission"] = df.groupby("Patient_ID")["Start_Date"].shift(1)
    df["Prev_Admission"] = pd.to_datetime(df["Prev_Admission"])  # Ensure datetime
    df["Days_Between"] = (df["Start_Date"] - df["Prev_Admission"]).dt.days

    # Define readmission: occurs within 30 days
    df["Readmission"] = df["Days_Between"].apply(lambda x: 1 if pd.notna(x) and 0 < x <= 30 else 0)

    # Show readmission statistics
    total_encounters = len(df)
    readmission_count = df["Readmission"].sum()
    
    readmission_rate = (readmission_count / total_encounters) * 100 if total_encounters > 0 else 0
    print(f"Readmission Rate: {readmission_rate:.2f}%")

    # Save results to a CSV file
    df.to_csv("synthea_readmissions_fixed.csv", index=False)
    print("Results saved to synthea_readmissions_fixed.csv")
