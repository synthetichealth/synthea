import pandas as pd
import numpy as np
import datetime
import random

def main():
    # =======================
    # 1. LOAD THE CSV FILES
    # =======================
    print("Loading CSVs...")
    patients_df       = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/patients.csv")
    encounters_df     = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/encounters.csv")
    observations_df   = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/observations.csv")
    allergies_df      = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/allergies.csv")
    conditions_df     = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/conditions.csv")
    imaging_df        = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/imaging_studies.csv")
    medications_df    = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/medications.csv")
    procedures_df     = pd.read_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/procedures.csv")

    # -------------------------------------------------------------------------
    # For date/time columns, convert them to datetime objects for easier math.
    # Some columns might be blank (NaN), so use 'errors="coerce"'.
    # Adjust column names if necessary.
    # -------------------------------------------------------------------------
    date_cols_encounters = ["START", "STOP"]
    for c in date_cols_encounters:
        encounters_df[c] = (
            pd.to_datetime(encounters_df[c], errors="coerce")
            .dt.tz_localize(None)
        )

    date_cols_obs = ["DATE"]
    observations_df["DATE"] = (
        pd.to_datetime(observations_df["DATE"], errors="coerce")
        .dt.tz_localize(None)
    )

    # Similarly for other tables if needed...
    # allergies_df might have START/STOP as well, etc.
    allergies_df["START"] = (
        pd.to_datetime(allergies_df["START"], errors="coerce")
        .dt.tz_localize(None)
    )
    allergies_df["STOP"] = (
        pd.to_datetime(allergies_df["STOP"], errors="coerce")
        .dt.tz_localize(None)
    )

    conditions_df["START"] = (
        pd.to_datetime(conditions_df["START"], errors="coerce")
        .dt.tz_localize(None)
    )
    conditions_df["STOP"] = (
        pd.to_datetime(conditions_df["STOP"], errors="coerce")
        .dt.tz_localize(None)
    )

    imaging_df["DATE"] = (
        pd.to_datetime(imaging_df["DATE"], errors="coerce")
        .dt.tz_localize(None)
    )

    medications_df["START"] = (
        pd.to_datetime(medications_df["START"], errors="coerce")
        .dt.tz_localize(None)
    )
    medications_df["STOP"] = (
        pd.to_datetime(medications_df["STOP"], errors="coerce")
        .dt.tz_localize(None)
    )

    procedures_df["START"] = (
        pd.to_datetime(procedures_df["START"], errors="coerce")
        .dt.tz_localize(None)
    )
    procedures_df["STOP"] = (
        pd.to_datetime(procedures_df["STOP"], errors="coerce")
        .dt.tz_localize(None)
    )

    # We’ll also parse patients' BIRTHDATE, DEATHDATE
    patients_df["BIRTHDATE"] = (
        pd.to_datetime(patients_df["BIRTHDATE"], errors="coerce")
        .dt.tz_localize(None)
    )
    patients_df["DEATHDATE"] = (
        pd.to_datetime(patients_df["DEATHDATE"], errors="coerce")
        .dt.tz_localize(None)
    )

    # ==============
    # 2. FILTER FOR PATIENTS WITH AT LEAST ONE EMERGENCY VISIT
    # ==============
    # We'll consider "emergency" if ENCOUNTERCLASS == 'emergency'
    #   OR if DESCRIPTION == 'Emergency room admission (procedure)' 
    #   (Synthea sometimes uses either/both to indicate ED.)
    # You can refine this logic if your data does so differently.
    print("Filtering for patients with emergency visits...")
    emergency_mask = (
        (encounters_df["ENCOUNTERCLASS"].str.lower() == "emergency") |
        (encounters_df["DESCRIPTION"].str.lower().str.contains("emergency room admission"))
    )
    ed_encounters = encounters_df[emergency_mask].copy()
    
    # Now we have all emergency visits. We only want patients who have at least 1 ED visit
    ed_patient_ids = ed_encounters["PATIENT"].unique().tolist()
    print(f"Found {len(ed_patient_ids)} patients with emergency visits.")

    # Subset your main patients_df to only those who appear in ed_patient_ids
    patients_df = patients_df[patients_df["Id"].isin(ed_patient_ids)].copy()

    # ==============
    # 3. RANDOMLY SELECT ONE ED VISIT PER PATIENT
    # ==============
    # For each patient, pick a random ED encounter row
    selected_ed_rows = []
    for pid in ed_patient_ids:
        # All ED encounters for this patient
        sub = ed_encounters[ed_encounters["PATIENT"] == pid]
        if len(sub) == 0:
            continue
        random_choice = sub.sample(1)  # pick 1 row at random
        selected_ed_rows.append(random_choice)

    final_ed_encounters = pd.concat(selected_ed_rows).copy()
    final_ed_encounters.reset_index(drop=True, inplace=True)

    print(f"Randomly selected 1 ED encounter for each of {len(final_ed_encounters)} patients.")

    # We'll rename for convenience
    final_ed_encounters.rename(columns={"STOP":"ED_STOP"}, inplace=True)

    # ==============
    # 4. FOR EACH PATIENT, TRUNCATE ALL DATA AFTER THE SELECTED ED VISIT STOP TIME
    #    AND SHIFT TIME SO THE ED VISIT ENDS ON 2025-02-15.
    # ==============
    # Let's define a reference date for the final ED visit
    final_ed_date = pd.to_datetime("2025-02-15 12:00:00")

    # Build a dictionary: patient_id -> (chosen_ed_stop_time, time_offset)
    # So for each patient, we know how to shift other events
    pid_to_ed_stop = {}
    for idx, row in final_ed_encounters.iterrows():
        pid = row["PATIENT"]
        stop_time = row["ED_STOP"]
        if pd.isnull(stop_time):
            # If the STOP is missing, assume it's the same as START or skip
            stop_time = row["START"]
        if pd.isnull(stop_time):
            # If we can't figure out the stop time, skip or default 
            stop_time = pd.to_datetime("2020-01-01")

        # We'll compute how many days (or seconds) to shift
        # We want: (stop_time + offset) = final_ed_date
        # => offset = final_ed_date - stop_time
        offset = final_ed_date - stop_time
        pid_to_ed_stop[pid] = (stop_time, offset)

    # HELPER: clamp a date to this patient's final ED stop
    def clamp_and_shift_date(dt, pid):
        """
        If dt is after the patient's chosen ED stop_time, 
        discard that record by returning None.
        Otherwise shift dt by offset so final ED is at 2025-02-15.
        """
        if pd.isnull(dt):
            return dt
        stop_time, offset = pid_to_ed_stop[pid]
        if dt > stop_time:
            return None
        return dt + offset

    # We’ll do these steps for each data table, removing rows after the ED stop time, 
    # and time‐shifting the remaining rows.

    # ENCOUNTERS:
    def process_encounters(enc):
        # Keep only patients in our final list
        enc = enc[enc["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        # SHIFT START and STOP
        new_start = []
        new_stop  = []
        for i, r in enc.iterrows():
            pid = r["PATIENT"]
            start_t = r["START"]
            stop_t  = r["STOP"]
            shifted_start = clamp_and_shift_date(start_t, pid)
            shifted_stop  = clamp_and_shift_date(stop_t,  pid)
            # If both are None, or if start is None (meaning it's after ED stop time) => drop
            if (shifted_start is None):
                new_start.append(None)
                new_stop.append(None)
            else:
                new_start.append(shifted_start)
                # For STOP, it's okay if it's None, but we’ll keep the row if start exists
                if shifted_stop is None:
                    # If the event was ongoing at final ED time, we can clamp it 
                    # or just store final ED date
                    shifted_stop = final_ed_date
                new_stop.append(shifted_stop)
        
        enc["START"] = new_start
        enc["STOP"]  = new_stop
        # Drop rows where START is None => means it’s after ED or invalid
        enc = enc.dropna(subset=["START"])
        return enc

    encounters_cleaned = process_encounters(encounters_df)

    # OBSERVATIONS:
    def process_observations(obs):
        obs = obs[obs["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_dates = []
        for i, r in obs.iterrows():
            pid = r["PATIENT"]
            dt = r["DATE"]
            shifted = clamp_and_shift_date(dt, pid)
            new_dates.append(shifted)
        obs["DATE"] = new_dates
        obs = obs.dropna(subset=["DATE"])
        return obs

    observations_cleaned = process_observations(observations_df)

    # ALLERGIES:
    def process_allergies(allg):
        allg = allg[allg["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_start = []
        new_stop  = []
        for i, r in allg.iterrows():
            pid = r["PATIENT"]
            s = clamp_and_shift_date(r["START"], pid)
            e = clamp_and_shift_date(r["STOP"], pid)
            new_start.append(s)
            new_stop.append(e if s is not None else None)
        allg["START"] = new_start
        allg["STOP"]  = new_stop
        allg = allg.dropna(subset=["START"])  # remove if start is after ED
        return allg

    allergies_cleaned = process_allergies(allergies_df)

    # CONDITIONS:
    def process_conditions(cond):
        cond = cond[cond["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_start = []
        new_stop  = []
        for i, r in cond.iterrows():
            pid = r["PATIENT"]
            s = clamp_and_shift_date(r["START"], pid)
            e = clamp_and_shift_date(r["STOP"], pid)
            new_start.append(s)
            new_stop.append(e if s is not None else None)
        cond["START"] = new_start
        cond["STOP"]  = new_stop
        cond = cond.dropna(subset=["START"])
        return cond

    conditions_cleaned = process_conditions(conditions_df)

    # IMAGING:
    def process_imaging(img):
        img = img[img["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_dates = []
        for i, r in img.iterrows():
            pid = r["PATIENT"]
            dt = clamp_and_shift_date(r["DATE"], pid)
            new_dates.append(dt)
        img["DATE"] = new_dates
        img = img.dropna(subset=["DATE"])
        return img

    imaging_cleaned = process_imaging(imaging_df)

    # MEDICATIONS:
    def process_meds(med):
        med = med[med["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_start = []
        new_stop  = []
        for i, r in med.iterrows():
            pid = r["PATIENT"]
            s = clamp_and_shift_date(r["START"], pid)
            e = clamp_and_shift_date(r["STOP"], pid)
            new_start.append(s)
            new_stop.append(e if s is not None else None)
        med["START"] = new_start
        med["STOP"]  = new_stop
        med = med.dropna(subset=["START"])
        return med

    medications_cleaned = process_meds(medications_df)

    # PROCEDURES:
    def process_procedures(proc):
        proc = proc[proc["PATIENT"].isin(pid_to_ed_stop.keys())].copy()
        new_start = []
        new_stop  = []
        for i, r in proc.iterrows():
            pid = r["PATIENT"]
            s = clamp_and_shift_date(r["START"], pid)
            e = clamp_and_shift_date(r["STOP"], pid)
            new_start.append(s)
            new_stop.append(e if s is not None else None)
        proc["START"] = new_start
        proc["STOP"]  = new_stop
        proc = proc.dropna(subset=["START"])
        return proc

    procedures_cleaned = process_procedures(procedures_df)

    # =======================
    # 5. CREATE THE "BIG TABLE" FOR THE FINAL ED VISIT
    # =======================
    # You want a table with columns like:
    # [ PATIENT, FIRST_NAME, MIDDLE_NAME, LAST_NAME, DATE_OF_BIRTH, ... 
    #   plus ENCOUNTER details, plus REASON_CODE, plus aggregated "previous medical history",
    #   plus columns from Observations, etc. ]
    #
    # We'll do this for the *selected* ED encounters only. For each row, we gather info from:
    # - patients_df
    # - the chosen ED encounter
    # - any "previous medical history" from conditions
    # - allergies, imaging, meds, etc.
    #
    # Note: This can get complex quickly (e.g. merging multiple conditions). 
    # We'll do a simplified approach: 
    #   * We create a single row per patient, with strings listing all conditions/allergies/etc. 
    #   * We also pivot key vital signs from the Observations table if found on that ED encounter. 
    #   * You can adapt as needed.

    # 5A. Prepare the selected ED table to join
    # Remember we renamed STOP -> ED_STOP
    # We'll keep e.g. REASONCODE, REASONDESCRIPTION, plus columns you asked for
    # You can rename them to match your final columns if you like.
    final_ed_encounters.rename(columns={
        "Id": "ENCOUNTER_ID",
        "CODE":"ENCOUNTER_CODE",
        "DESCRIPTION":"ENCOUNTER_DESCRIPTION"
    }, inplace=True)

    # 5B. Join in patient demographic columns
    # We'll rename some columns from patients to match your final schema
    patients_df.rename(columns={
        "Id": "PATIENT",
        "FIRST": "FIRST_NAME",
        "MIDDLE": "MIDDLE_NAME",
        "LAST": "LAST_NAME",
        "BIRTHDATE": "DATE_OF_BIRTH",
        "MARITAL": "MARITAL_STATUS"
    }, inplace=True)

    big_merged = pd.merge(
        final_ed_encounters,
        patients_df,
        left_on="PATIENT", 
        right_on="PATIENT",
        how="left"
    )

    # 5C. Summarize prior conditions (PREVIOUS_MEDICAL_HISTORY) for that patient
    # We'll define "prior" as any condition START < ED_STOP
    # Then combine them into a string
    def build_conditions_summary(pid, ed_stop):
        sub = conditions_cleaned[(conditions_cleaned["PATIENT"] == pid)]
        sub = sub[sub["START"] <= ed_stop]  # only those started before or at ED stop
        # we can combine the 'DESCRIPTION' into a semicolon separated list 
        cond_list = sub["DESCRIPTION"].dropna().unique().tolist()
        return "; ".join(cond_list)

    # We'll do the same for allergies, imaging, meds, procedures, etc.
    def build_allergies_summary(pid, ed_stop):
        sub = allergies_cleaned[(allergies_cleaned["PATIENT"] == pid)]
        sub = sub[sub["START"] <= ed_stop]
        # combine e.g. 'DESCRIPTION1' or just the 'DESCRIPTION' column
        # but in Synthea's allergies table, we have a "DESCRIPTION" or "TYPE"?
        # We'll assume "DESCRIPTION" is the top-level. If that’s missing, adjust as needed.
        allergy_list = sub["DESCRIPTION"].dropna().unique().tolist()
        return "; ".join(allergy_list)

    def build_imaging_summary(pid, ed_stop):
        sub = imaging_cleaned[(imaging_cleaned["PATIENT"] == pid)]
        sub = sub[sub["DATE"] <= ed_stop]
        # We might combine the 'BODYSITE_DESCRIPTION' or 'MODALITY_DESCRIPTION'
        imag_list = sub["MODALITY_DESCRIPTION"].dropna().unique().tolist()
        return "; ".join(imag_list)

    def build_meds_summary(pid, ed_stop):
        sub = medications_cleaned[(medications_cleaned["PATIENT"] == pid)]
        sub = sub[sub["START"] <= ed_stop]
        # Potentially separate "current meds" vs "previous meds"? 
        # e.g. if STOP < ed_stop => it’s a previous med, else ongoing
        current_meds = sub[(sub["STOP"].isnull()) | (sub["STOP"] >= ed_stop)]
        prev_meds    = sub[(sub["STOP"].notnull()) & (sub["STOP"] < ed_stop)]
        curr_list = current_meds["DESCRIPTION"].dropna().unique().tolist()
        prev_list = prev_meds["DESCRIPTION"].dropna().unique().tolist()
        return (
            "; ".join(curr_list),
            "; ".join(prev_list)
        )

    def build_procedures_summary(pid, ed_stop):
        sub = procedures_cleaned[(procedures_cleaned["PATIENT"] == pid)]
        sub = sub[sub["START"] <= ed_stop]
        proc_list = sub["DESCRIPTION"].dropna().unique().tolist()
        return "; ".join(proc_list)

    # 5D. For Observations, we might pivot certain codes to columns
    # For instance, 
    #   * Body Height (CODE=8302-2)
    #   * Pain severity (CODE=72514-3)
    #   * Body Weight (CODE=29463-7)
    #   * BMI (CODE=39156-5)
    #   * etc.
    # We can do a small function to grab the last known value before ED_STOP for each code.
    # Or we can grab the value specifically from the final ED encounter if you prefer.

    # Map LOINC codes or descriptions to the final column name you want:
    obs_map = {
        "8302-2":  "Body Height",
        "72514-3": "Pain severity - 0-10 verbal numeric rating [Score] - Reported",
        "29463-7": "Body Weight",
        "39156-5": "Body mass index (BMI) [Ratio]",
        "59576-9": "Body mass index (BMI) [Percentile] Per age and sex",
        "8462-4":  "Diastolic Blood Pressure",
        "8480-6":  "Systolic Blood Pressure",
        "8867-4":  "Heart rate",
        "9279-1":  "Respiratory rate",
        "2708-6":  "Oxygen saturation in Arterial blood",   # Or 2710-2, depending on your data
        "8310-5":  "Body temperature"                       # If present
        # etc...
    }

    # Let's define a function that returns a dict {colname: last_value} for each code of interest
    def get_observations_for_pid(pid, ed_stop):
        sub = observations_cleaned[(observations_cleaned["PATIENT"] == pid)]
        sub = sub[sub["DATE"] <= ed_stop]
        # We'll keep only rows where CODE in obs_map
        sub = sub[sub["CODE"].isin(obs_map.keys())]
        # For each code, get the last measurement by time
        result = {}
        for code, label in obs_map.items():
            # filter
            sub2 = sub[sub["CODE"] == code]
            if len(sub2) == 0:
                result[label] = np.nan
            else:
                # sort by DATE
                sub2 = sub2.sort_values("DATE")
                last_val = sub2.iloc[-1]["VALUE"]  # textual or numeric
                result[label] = last_val
        return result

    # 5E. Now we loop through final_ed_encounters (or big_merged) 
    #     and build out the columns:
    big_merged["PREVIOUS_MEDICAL_HISTORY"] = ""
    big_merged["KNOWN_ALLERGIES"] = ""
    big_merged["PREVIOUS_IMAGING_STUDIES"] = ""
    big_merged["PREVIOUS_MEDICAL_PROCEDURES"] = ""
    big_merged["CURRENT_MEDICATIONS"] = ""
    big_merged["PREVIOUS_MEDICATIONS"] = ""

    # We'll also hold placeholders for each of the vital sign columns we want
    for label in obs_map.values():
        big_merged[label] = np.nan

    # Fill in the data
    print("Building final aggregated columns...")
    for i, r in big_merged.iterrows():
        pid = r["PATIENT"]
        ed_stop = r["ED_STOP"]

        # Conditions
        big_merged.at[i, "PREVIOUS_MEDICAL_HISTORY"] = build_conditions_summary(pid, ed_stop)

        # Allergies
        big_merged.at[i, "KNOWN_ALLERGIES"] = build_allergies_summary(pid, ed_stop)

        # Imaging
        big_merged.at[i, "PREVIOUS_IMAGING_STUDIES"] = build_imaging_summary(pid, ed_stop)

        # Procedures
        big_merged.at[i, "PREVIOUS_MEDICAL_PROCEDURES"] = build_procedures_summary(pid, ed_stop)

        # Medications (current, previous)
        curr_meds, prev_meds = build_meds_summary(pid, ed_stop)
        big_merged.at[i, "CURRENT_MEDICATIONS"] = curr_meds
        big_merged.at[i, "PREVIOUS_MEDICATIONS"] = prev_meds

        # Observations pivot
        obs_dict = get_observations_for_pid(pid, ed_stop)
        for label, val in obs_dict.items():
            big_merged.at[i, label] = val

    # We now have a giant table with columns from:
    #   - patient demographics
    #   - final ED encounter data
    #   - aggregated fields (conditions, allergies, imaging, meds)
    #   - pivoted observation columns

    # Rename columns as you see fit to match your final specification
    # e.g. you might rename "ENCOUNTERCLASS" -> "ENCOUNTER_CLASS"

    # Clean up or reorder columns to match your desired final:
    final_columns = [
        "PATIENT", 
        "FIRST_NAME",
        "MIDDLE_NAME",
        "LAST_NAME",
        "DATE_OF_BIRTH",
        "MARITAL_STATUS",
        "RACE", 
        "ETHNICITY",
        "GENDER",
        "ORGANIZATION",  
        "ENCOUNTERCLASS",   # from final_ed_encounters
        "ENCOUNTER_CODE",   
        "ENCOUNTER_DESCRIPTION", 
        "BASE_ENCOUNTER_COST",
        "TOTAL_CLAIM_COST",
        "PAYER_COVERAGE",
        "REASONCODE",
        "REASONDESCRIPTION",
        "PREVIOUS_MEDICAL_HISTORY",
        "PREVIOUS_MEDICAL_PROCEDURES",
        "KNOWN_ALLERGIES",
        "PREVIOUS_IMAGING_STUDIES",
        "CURRENT_MEDICATIONS",
        "PREVIOUS_MEDICATIONS",
        "Body Height",
        "Pain severity - 0-10 verbal numeric rating [Score] - Reported",
        "Body Weight",
        "Body mass index (BMI) [Ratio]",
        "Body mass index (BMI) [Percentile] Per age and sex",
        "Diastolic Blood Pressure",
        "Systolic Blood Pressure",
        "Heart rate",
        "Respiratory rate",
        "Oxygen saturation in Arterial blood",
        "Body temperature"
    ]

    # Some columns might not exist or might be missing in the big_merged, so we’ll filter:
    final_columns = [c for c in final_columns if c in big_merged.columns]

    final_df = big_merged[final_columns].copy()

    # ================================
    # 6. (Optional) SAVE RESULTS
    # ================================
    # 6A. Save cleaned versions of each table (encounters_cleaned, etc.)
    encounters_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/allergies.csv", index=False)
    observations_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/observations_cleaned.csv", index=False)
    allergies_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/allergies_cleaned.csv", index=False)
    conditions_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/conditions_cleaned.csv", index=False)
    imaging_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/imaging_cleaned.csv", index=False)
    medications_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/medications_cleaned.csv", index=False)
    procedures_cleaned.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/procedures_cleaned.csv", index=False)
    patients_df.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/patients_cleaned.csv", index=False)

    # 6B. Save the final big table
    final_df.to_csv("/Users/samdonegan/Documents/GitHub/synthea/src/main/data/final_ed_patients.csv", index=False)

    print("Done! Final ED patient data saved to 'final_ed_patients.csv'.")

if __name__ == "__main__":
    main()
