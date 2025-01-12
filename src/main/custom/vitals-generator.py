import pandas as pd
from datetime import datetime, timedelta
import numpy as np
import os
import json
import time
import logging
from dotenv import load_dotenv
import gspread
from oauth2client.service_account import ServiceAccountCredentials

# ---------------------------
# 0. ENV + OPENAI (Optional)
# ---------------------------
load_dotenv()

OPENAI_API_KEY = os.getenv('OPENAI_API_KEY')

# ------------------------------------------
# 1. GOOGLE SHEETS FUNCTIONS
# ------------------------------------------
def setup_google_sheets():
    """Set up connection to Google Sheets with exponential backoff."""
    max_retries = 3
    retry_delay = 5
    
    for attempt in range(max_retries):
        try:
            service_account_info = json.loads(os.getenv("GSPREAD_SERVICE_ACCOUNT"))
            scope = [
                'https://spreadsheets.google.com/feeds',
                'https://www.googleapis.com/auth/drive'
            ]
            credentials = ServiceAccountCredentials.from_json_keyfile_dict(service_account_info, scope)
            return gspread.authorize(credentials)
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            logging.warning(f"Connection attempt {attempt+1} failed, retrying in {retry_delay} seconds...")
            time.sleep(retry_delay)
            retry_delay *= 2

def get_worksheets(gc):
    """Get source (Encounters) and target (time-series) worksheets with retry logic."""
    max_retries = 3
    retry_delay = 5
    
    for attempt in range(max_retries):
        try:
            spreadsheet = gc.open('Medhack')
            source_worksheet = spreadsheet.worksheet('[Train] Full')
            try:
                target_worksheet = spreadsheet.worksheet('[Train] Vitals')
            except gspread.WorksheetNotFound:
                target_worksheet = spreadsheet.add_worksheet(title='[Train] Vitals', rows='1000', cols='10')
            
            return source_worksheet, target_worksheet
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            logging.warning(f"Worksheet access attempt {attempt+1} failed, retrying in {retry_delay} seconds...")
            time.sleep(retry_delay)
            retry_delay *= 2

# ------------------------------------------
# 2. MODIFIER FUNCTIONS
# ------------------------------------------
def reason_modifiers(reason_description):
    """Returns a dict of baseline offsets for each vital sign based on admission reason."""
    reason = reason_description.lower()
    offsets = {
        'heart_rate': 0.0,
        'systolic_bp': 0.0,
        'diastolic_bp': 0.0,
        'respiratory_rate': 0.0,
        'oxygen_saturation': 0.0
    }
    
    if "laceration" in reason or "sprain" in reason or "burn injury" in reason:
        offsets['heart_rate'] += 5
        offsets['systolic_bp'] += 3
        offsets['diastolic_bp'] += 2
    
    elif "myocardial infarction" in reason:
        offsets['heart_rate'] += 10
        offsets['systolic_bp'] -= 5
        offsets['diastolic_bp'] -= 3
        offsets['oxygen_saturation'] -= 2
    
    elif "asthma" in reason:
        offsets['respiratory_rate'] += 4
        offsets['oxygen_saturation'] -= 3
    
    elif "seizure" in reason:
        offsets['heart_rate'] += 6
    
    elif "drug overdose" in reason:
        offsets['respiratory_rate'] -= 3
        offsets['oxygen_saturation'] -= 5
        offsets['heart_rate'] -= 5
    
    elif "chronic congestive heart failure" in reason:
        offsets['heart_rate'] += 5
        offsets['systolic_bp'] -= 5
        offsets['diastolic_bp'] -= 3
        offsets['oxygen_saturation'] -= 2
    
    return offsets

def pain_modifiers(pain_score):
    """Return offsets based on pain 0-10 scale."""
    try:
        p = float(pain_score)
    except:
        p = 0
    
    offsets = {
        'heart_rate': p * 1.0,
        'systolic_bp': p * 0.5,
        'diastolic_bp': p * 0.3,
        'respiratory_rate': p * 0.2,
        'oxygen_saturation': 0.0
    }
    
    return offsets

def patient_characteristics_modifiers(height_cm, weight_kg, body_temp_c):
    """Adjust baseline vitals based on body metrics."""
    offsets = {
        'heart_rate': 0.0,
        'systolic_bp': 0.0,
        'diastolic_bp': 0.0,
        'respiratory_rate': 0.0,
        'oxygen_saturation': 0.0
    }
    
    try:
        h_m = float(height_cm) / 100.0
        w_kg = float(weight_kg)
        bmi = w_kg / (h_m * h_m)
        
        if bmi >= 30:
            offsets['heart_rate'] += 5
            offsets['systolic_bp'] += 5
            offsets['diastolic_bp'] += 3
        elif bmi <= 18.5:
            offsets['heart_rate'] -= 2
            offsets['systolic_bp'] -= 3
            offsets['diastolic_bp'] -= 2
    except:
        pass
    
    try:
        t = float(body_temp_c)
        if t >= 38.0:
            offsets['heart_rate'] += 10
            offsets['respiratory_rate'] += 2
        elif t <= 35.0:
            offsets['heart_rate'] -= 5
            offsets['respiratory_rate'] -= 1
    except:
        pass
    
    return offsets

def apply_modifiers(baseline_dict, reason_description, pain_score, height, weight, temperature):
    """Apply all modifiers to baseline vitals."""
    reason_offs = reason_modifiers(reason_description)
    pain_offs = pain_modifiers(pain_score)
    char_offs = patient_characteristics_modifiers(height, weight, temperature)
    
    final_offsets = {
        'heart_rate': (reason_offs['heart_rate'] + pain_offs['heart_rate'] + char_offs['heart_rate']),
        'systolic_bp': (reason_offs['systolic_bp'] + pain_offs['systolic_bp'] + char_offs['systolic_bp']),
        'diastolic_bp': (reason_offs['diastolic_bp'] + pain_offs['diastolic_bp'] + char_offs['diastolic_bp']),
        'respiratory_rate': (reason_offs['respiratory_rate'] + pain_offs['respiratory_rate'] + char_offs['respiratory_rate']),
        'oxygen_saturation': (reason_offs['oxygen_saturation'] + pain_offs['oxygen_saturation'] + char_offs['oxygen_saturation']),
    }
    
    modified_baseline = {}
    for key in baseline_dict.keys():
        base_val = baseline_dict[key]
        offset_val = final_offsets.get(key, 0.0)
        # If base_val is NaN, we'll just treat it as 0 or normal. Let's do 0 for now:
        if np.isnan(base_val):
            base_val = 0.0
        modified_baseline[key] = base_val + offset_val
    
    return modified_baseline

# -------------------------------------------------------------
# 2a. Parse medical history / procedures -> condition flags
# -------------------------------------------------------------
def parse_conditions_from_history(history_str, procedures_str):
    """
    Look for keywords in PREVIOUS_MEDICAL_HISTORY / PREVIOUS_MEDICAL_PROCEDURES 
    to decide if a patient has certain conditions.
    Adjust / expand as needed to capture more conditions.
    """
    text_combined = (history_str + " " + procedures_str).lower()

    conditions = []

    if "atrial fibrillation" in text_combined or "af" in text_combined:
        conditions.append("atrial_fibrillation")
    
    if "copd" in text_combined:
        conditions.append("copd")
    
    if "hypertension" in text_combined or "high blood pressure" in text_combined:
        conditions.append("chronic_hypertension")
    
    if "anxiety" in text_combined:
        conditions.append("severe_anxiety")
    
    # If it explicitly says arrhythmia
    if "arrhythmia" in text_combined:
        conditions.append("arrhythmia")
    
    if "sleep apnea" in text_combined:
        conditions.append("sleep_apnea")

    # Add more if you want to detect other conditions 
    # e.g. "heart failure," "diabetes," etc.

    return conditions

# ------------------------------------------------
# 2b. Additional condition-based baseline changes
# ------------------------------------------------
def apply_condition_baseline(df, condition_list=None):
    """
    Applies baseline changes for each condition to the time-series
    *before* any state offsets (warning or crisis) are applied.
    """
    if condition_list is None:
        condition_list = []
    
    # For convenience, let's create some "baseline" columns so we can
    # reference original values later. We'll do it only once if they
    # don't exist yet:
    if 'heart_rate_baseline' not in df.columns:
        for col in ['heart_rate','systolic_bp','diastolic_bp','respiratory_rate','oxygen_saturation']:
            df[f"{col}_baseline"] = df[col].copy()

    # Atrial Fibrillation: erratic HR
    if 'atrial_fibrillation' in condition_list:
        n = len(df)
        random_walk = np.cumsum(np.random.normal(0, 2, n))  # small random changes
        large_spikes = np.random.choice([0, 1], size=n, p=[0.95, 0.05]) * np.random.randint(20, 50, size=n)
        df['heart_rate_baseline'] += (random_walk + large_spikes)
    
    # COPD: higher baseline RR, occasional spikes
    if 'copd' in condition_list:
        n = len(df)
        df['respiratory_rate_baseline'] += 5
        # random spikes
        spikes = np.random.choice([0, 1], size=n, p=[0.98, 0.02]) * 5
        df['respiratory_rate_baseline'] += spikes
    
    # Chronic Hypertension: raise systolic and diastolic by ~+20, plus bigger swings
    if 'chronic_hypertension' in condition_list:
        n = len(df)
        df['systolic_bp_baseline'] += 20
        df['diastolic_bp_baseline'] += 20
        # Possibly also allow bigger random variation
        bigger_noise_s = np.random.normal(0, 3, n)
        bigger_noise_d = np.random.normal(0, 2, n)
        df['systolic_bp_baseline'] += bigger_noise_s
        df['diastolic_bp_baseline'] += bigger_noise_d

    # Severe Anxiety: sporadic HR, RR bumps that might look like mini-crises
    if 'severe_anxiety' in condition_list:
        n = len(df)
        # random short bursts in heart_rate & respiratory_rate
        bursts = np.random.choice([0, 1], size=n, p=[0.95, 0.05])  # 5% chance each row starts a burst
        for i in range(n):
            if bursts[i] == 1:
                burst_len = min(10, n - i)
                for j in range(burst_len):
                    factor = 1.0 - (j / burst_len)
                    df.at[i + j, 'heart_rate_baseline'] += 10 * factor  # up to +10 BPM at start
                    df.at[i + j, 'respiratory_rate_baseline'] += 2 * factor

    # Arrhythmia: short sudden HR drops or spikes
    if 'arrhythmia' in condition_list:
        n = len(df)
        # occasional random "drops" or "spikes"
        arr_events = np.random.choice([0, 1], size=n, p=[0.98, 0.02])
        for i in range(n):
            if arr_events[i] == 1:
                # random direction: + or -
                change = np.random.randint(-30, 31)  # -30..+30
                df.at[i, 'heart_rate_baseline'] += change

    # Sleep Apnea: periodic O2 dips, irregular RR spikes
    if 'sleep_apnea' in condition_list:
        n = len(df)
        i = 0
        while i < n:
            if np.random.rand() < 0.02:  # 2% chance to start an apnea event
                apnea_len = min(20, n - i)
                for j in range(apnea_len):
                    df.at[i + j, 'oxygen_saturation_baseline'] -= 5  # dip by ~5
                    df.at[i + j, 'respiratory_rate_baseline'] += 2   # RR spike
                i += apnea_len
            else:
                i += 1

    # Finally, overwrite the actual columns with the new baseline
    for vital in ['heart_rate','systolic_bp','diastolic_bp','respiratory_rate','oxygen_saturation']:
        df[vital] = df[f"{vital}_baseline"]
    
    return df

# ------------------------------------------------
# 3. VITAL SIGNS GENERATOR CLASS
# ------------------------------------------------
class VitalSignsGenerator:
    def __init__(self, seed=None):
        if seed is not None:
            np.random.seed(seed)
        
        self.normal_ranges = {
            'heart_rate': (60, 100),
            'systolic_bp': (90, 120),
            'diastolic_bp': (60, 80),
            'respiratory_rate': (12, 20),
            'oxygen_saturation': (95, 100)
        }

    def add_natural_variation(self, baseline, time_points, vital_sign):
        """Add natural variation to the baseline value."""
        if vital_sign == 'heart_rate':
            variation = np.sin(np.linspace(0, 2*np.pi, len(time_points))) * 2
            noise = np.random.normal(0, 1, len(time_points))
            return baseline + variation + noise
        
        elif vital_sign in ['systolic_bp', 'diastolic_bp']:
            variation = np.sin(np.linspace(0, np.pi, len(time_points))) * 3
            noise = np.random.normal(0, 0.5, len(time_points))
            return baseline + variation + noise
        
        elif vital_sign == 'respiratory_rate':
            noise = np.random.normal(0, 0.5, len(time_points))
            return baseline + noise
        
        elif vital_sign == 'oxygen_saturation':
            noise = np.random.normal(0, 0.2, len(time_points))
            return np.minimum(100, baseline + noise)
        
        else:
            return np.array([baseline]*len(time_points))

    def generate_patient_series(
        self, 
        patient_baseline, 
        duration_minutes=60, 
        interval_seconds=5,
        start_time=None
    ):
        if start_time is None:
            start_time = datetime.now()
        
        n_points = int((duration_minutes * 60) / interval_seconds)
        time_points = np.arange(n_points)

        timestamps = [
            start_time + timedelta(seconds=int(i * interval_seconds)) 
            for i in time_points
        ]
        
        data = {'timestamp': timestamps}
        
        for vital_sign, baseline_val in patient_baseline.items():
            # If we have no baseline (NaN or 0), pick a midpoint of normal range
            if baseline_val == 0 or np.isnan(baseline_val):
                low, high = self.normal_ranges.get(vital_sign, (60, 100))
                baseline_val = (low + high) / 2.0
            
            values = self.add_natural_variation(baseline_val, time_points, vital_sign)
            data[vital_sign] = values
        
        df = pd.DataFrame(data)
        return df

# ---------------------------------------------
# 4. STATE MACHINE: baseline -> warning -> crisis
# ---------------------------------------------
def inject_states_and_crisis(df,
                             p_warning=0.3,
                             p_crisis=0.5,
                             warning_duration_minutes=5,
                             crisis_duration_minutes=10):
    """
    We define states = {0: baseline, 1: warning, 2: crisis}.
    The patient has a p_warning chance to move from baseline to warning,
    then a p_crisis chance to escalate from warning to crisis.
    """
    n = len(df)
    rows_per_minute = 60 // 5  # 12 rows per minute
    warning_len = warning_duration_minutes * rows_per_minute
    crisis_len = crisis_duration_minutes * rows_per_minute

    df['state_label'] = 0

    # Create "baseline" columns if they don't exist
    for col in ['heart_rate','systolic_bp','diastolic_bp','respiratory_rate','oxygen_saturation']:
        base_col = f"{col}_baseline"
        if base_col not in df.columns:
            df[base_col] = df[col].copy()

    # Decide if we get a warning
    do_warning = (np.random.rand() < p_warning)
    start_warning, end_warning = None, None
    start_crisis, end_crisis = None, None

    if do_warning:
        # Ensure we have enough room for both warning + crisis
        max_start_warning = n - (warning_len + crisis_len)
        if max_start_warning < 0:
            # no room, skip
            do_warning = False
        else:
            start_warning = np.random.randint(0, max_start_warning)
            end_warning = start_warning + warning_len - 1
            df.loc[start_warning:end_warning, 'state_label'] = 1

            # decide if we escalate to crisis
            if np.random.rand() < p_crisis:
                start_crisis = end_warning + 1
                end_crisis = start_crisis + crisis_len - 1
                if end_crisis >= n:
                    end_crisis = n - 1
                df.loc[start_crisis:end_crisis, 'state_label'] = 2

    # Now apply the actual offsets
    df = apply_state_offsets(df, 
                             start_warning, end_warning,
                             start_crisis, end_crisis)
    return df

def apply_state_offsets(df, start_w, end_w, start_c, end_c):
    """
    For each row, if state_label=1 (warning), apply mild ramp from baseline.
    If state_label=2 (crisis), continue from the warning offset 
    without dropping to baseline, then escalate further.
    """
    # WARNING amplitude
    warn_heart_amp = 10
    warn_sbp_amp   = 5
    warn_dbp_amp   = 5
    warn_rr_amp    = 3
    warn_o2_amp    = -2

    # CRISIS amplitude
    crisis_heart_amp = 30
    crisis_sbp_amp   = 10
    crisis_dbp_amp   = 10
    crisis_rr_amp    = 5
    crisis_o2_amp    = -5

    vitals = ['heart_rate','systolic_bp','diastolic_bp','respiratory_rate','oxygen_saturation']

    # 1) Apply the WARNING offsets
    if start_w is not None and end_w is not None:
        length_w = end_w - start_w + 1
        for i, idx in enumerate(range(start_w, end_w + 1)):
            fraction = i / (length_w - 1) if length_w > 1 else 1.0
            # linear ramp from 0 to 1
            for vital in vitals:
                base_col = f"{vital}_baseline"
                val_base = df.at[idx, base_col]
                if vital == 'heart_rate':
                    df.at[idx, vital] = val_base + warn_heart_amp * fraction
                elif vital == 'systolic_bp':
                    df.at[idx, vital] = val_base + warn_sbp_amp * fraction
                elif vital == 'diastolic_bp':
                    df.at[idx, vital] = val_base + warn_dbp_amp * fraction
                elif vital == 'respiratory_rate':
                    df.at[idx, vital] = val_base + warn_rr_amp * fraction
                elif vital == 'oxygen_saturation':
                    df.at[idx, vital] = val_base + warn_o2_amp * fraction

    # 2) CRISIS offsets
    if start_c is not None and end_c is not None:
        length_c = end_c - start_c + 1
        
        # If no warning preceded it, we assume offset starts at 0
        warn_offset = {v: 0.0 for v in vitals}  
        
        if start_w is not None and end_w is not None:
            # Look at the last row of the warning to see the final offset
            for vital in vitals:
                base_col = f"{vital}_baseline"
                val_final = df.at[end_w, vital]
                val_base  = df.at[end_w, base_col]
                warn_offset[vital] = val_final - val_base
        
        for i, idx in enumerate(range(start_c, end_c + 1)):
            fraction = i / (length_c - 1) if length_c > 1 else 1.0
            # smooth sinusoidal from 0..1 => the crisis wave
            ramp = np.sin(np.pi * fraction)
            
            for vital in vitals:
                base_col = f"{vital}_baseline"
                val_base = df.at[idx, base_col]
                
                if vital == 'heart_rate':
                    df.at[idx, vital] = (
                        val_base 
                        + warn_offset[vital]
                        + crisis_heart_amp * ramp
                    )
                elif vital == 'systolic_bp':
                    df.at[idx, vital] = (
                        val_base 
                        + warn_offset[vital] 
                        + crisis_sbp_amp * ramp
                    )
                elif vital == 'diastolic_bp':
                    df.at[idx, vital] = (
                        val_base 
                        + warn_offset[vital] 
                        + crisis_dbp_amp * ramp
                    )
                elif vital == 'respiratory_rate':
                    df.at[idx, vital] = (
                        val_base 
                        + warn_offset[vital] 
                        + crisis_rr_amp * ramp
                    )
                elif vital == 'oxygen_saturation':
                    df.at[idx, vital] = (
                        val_base 
                        + warn_offset[vital] 
                        + crisis_o2_amp * ramp
                    )

    return df

# ---------------------------------------------
# 5. MAIN LOGIC (With Batching for Sheets)
# ---------------------------------------------
def main():
    gc = setup_google_sheets()
    source_ws, time_series_ws = get_worksheets(gc)
    
    # Pull data from 'Encounters' sheet.
    # Make sure the Google Sheet columns match your new CSV columns (PATIENT, REASONDESCRIPTION, etc.)
    encounters_data = source_ws.get_all_records()
    encounters_df = pd.DataFrame(encounters_data)
    
    generator = VitalSignsGenerator(seed=42)
    
    # Clear out existing data in 'train' sheet, then set up column headers
    time_series_ws.clear()
    headers = [
        "timestamp", "patient_id", "diastolic_bp", "systolic_bp",
        "heart_rate", "respiratory_rate", "oxygen_saturation", "state_label"
    ]
    time_series_ws.append_row(headers)
    
    base_time = datetime(2025, 1, 1, 19, 0, 0)

    all_rows_to_write = []

    for i, row in encounters_df.iterrows():
        patient_id = row.get("PATIENT", f"Unknown_{i}")
        reason_desc = row.get("REASONDESCRIPTION", "")  # e.g. "Injury of knee (disorder)"
        pain_score = row.get("Pain severity - 0-10 verbal numeric rating [Score] - Reported", "0")
        body_height = row.get("Body Height", "")
        body_weight = row.get("Body Weight", "")
        body_temp = row.get("Body temperature", "")

        # Grab the big text fields so we can parse them:
        pmh = row.get("PREVIOUS_MEDICAL_HISTORY", "")        # e.g. "Risk activity involvement..."
        pmp = row.get("PREVIOUS_MEDICAL_PROCEDURES", "")     # e.g. "Medication reconciliation..."

        def safe_float(x):
            try:
                return float(x)
            except:
                return np.nan
        
        # Grab baseline vitals
        pbaseline = {
            'diastolic_bp':      safe_float(row.get("Diastolic Blood Pressure", np.nan)),
            'systolic_bp':       safe_float(row.get("Systolic Blood Pressure", np.nan)),
            'heart_rate':        safe_float(row.get("Heart rate", np.nan)),
            'respiratory_rate':  safe_float(row.get("Respiratory rate", np.nan)),
            'oxygen_saturation': safe_float(row.get("Oxygen saturation in Arterial blood", np.nan)),
        }
        
        # Apply the static modifiers (reason, pain, BMI, fever, etc.)
        pbaseline_mod = apply_modifiers(
            baseline_dict=pbaseline,
            reason_description=reason_desc,
            pain_score=pain_score,
            height=body_height,
            weight=body_weight,
            temperature=body_temp
        )
        
        # Build a start time offset for each patient (so each patient starts 1 hour apart)
        patient_start_time = base_time + timedelta(hours=i)
        
        # 1-hour time-series at 5-second intervals
        ts_data = generator.generate_patient_series(
            patient_baseline=pbaseline_mod,
            duration_minutes=60,
            interval_seconds=5,
            start_time=patient_start_time
        )
        
        # Parse actual conditions from PMH + Procedures
        history_conditions = parse_conditions_from_history(pmh, pmp)
        
        # If you still want to randomize some, union them with random picks
        # so there's a chance of adding more conditions beyond what's in the data:
        random_conditions = []
        if np.random.rand() < 0.2:
            random_conditions.append('atrial_fibrillation')
        if np.random.rand() < 0.1:
            random_conditions.append('copd')
        if np.random.rand() < 0.15:
            random_conditions.append('chronic_hypertension')
        if np.random.rand() < 0.15:
            random_conditions.append('severe_anxiety')
        if np.random.rand() < 0.1:
            random_conditions.append('arrhythmia')
        if np.random.rand() < 0.08:
            random_conditions.append('sleep_apnea')
        
        # Combine the two lists (set() to avoid duplicates)
        conditions = list(set(history_conditions + random_conditions))
        
        # 1) Apply condition-based baseline changes
        ts_data = apply_condition_baseline(ts_data, condition_list=conditions)
        
        # 2) Inject the multi-state logic (baseline->warning->crisis)
        ts_data = inject_states_and_crisis(ts_data, 
                                           p_warning=0.3,
                                           p_crisis=0.5,
                                           warning_duration_minutes=5,
                                           crisis_duration_minutes=10)
        
        # Add patient_id, round numeric columns
        ts_data['patient_id'] = patient_id
        
        numeric_cols = ['diastolic_bp','systolic_bp','heart_rate','respiratory_rate','oxygen_saturation']
        for c in numeric_cols:
            ts_data[c] = ts_data[c].round(1)
        
        # Reorder columns
        ts_data = ts_data[['timestamp', 'patient_id', 
                           'diastolic_bp', 'systolic_bp', 'heart_rate',
                           'respiratory_rate', 'oxygen_saturation', 'state_label']]
        
        # Convert timestamps to ISO string
        ts_data['timestamp'] = ts_data['timestamp'].dt.strftime('%Y-%m-%dT%H:%M:%S')
        
        rows = ts_data.values.tolist()
        all_rows_to_write.extend(rows)

    # BATCH WRITE to Google Sheets
    BATCH_SIZE = 500
    for start_idx in range(0, len(all_rows_to_write), BATCH_SIZE):
        end_idx = start_idx + BATCH_SIZE
        batch = all_rows_to_write[start_idx:end_idx]
        time_series_ws.append_rows(batch, value_input_option='RAW')
        
        # Sleep briefly between batches to reduce API call rate
        time.sleep(1)

    print("All data with baseline->warning->crisis transitions (smooth) generated successfully.")

if __name__ == "__main__":
    main()
