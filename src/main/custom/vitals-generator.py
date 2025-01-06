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
            source_worksheet = spreadsheet.worksheet('Encounters')
            try:
                target_worksheet = spreadsheet.worksheet('time-series')
            except gspread.WorksheetNotFound:
                target_worksheet = spreadsheet.add_worksheet(title='time-series', rows='1000', cols='10')
            
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
        modified_baseline[key] = base_val + offset_val
    
    return modified_baseline

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
            if np.isnan(baseline_val):
                low, high = self.normal_ranges.get(vital_sign, (60, 100))
                baseline_val = (low + high) / 2.0
            
            values = self.add_natural_variation(baseline_val, time_points, vital_sign)
            data[vital_sign] = values
        
        return pd.DataFrame(data)

# ---------------------------------------------
# 4. MAIN LOGIC
# ---------------------------------------------
def main():
    gc = setup_google_sheets()
    source_ws, time_series_ws = get_worksheets(gc)
    
    encounters_data = source_ws.get_all_records()
    encounters_df = pd.DataFrame(encounters_data)
    
    generator = VitalSignsGenerator(seed=42)
    
    time_series_ws.clear()
    headers = [
        "timestamp", "patient_id", "diastolic_bp", "systolic_bp",
        "heart_rate", "respiratory_rate", "oxygen_saturation"
    ]
    time_series_ws.append_row(headers)
    
    base_time = datetime(2025, 1, 1, 19, 0, 0)
    
    for i, row in encounters_df.iterrows():
        patient_id = row.get("PATIENT", f"Unknown_{i}")
        reason_desc = row.get("REASONDESCRIPTION", "")
        pain_score = row.get("Pain severity - 0-10 verbal numeric rating [Score] - Reported", "0")
        body_height = row.get("Body Height", "")
        body_weight = row.get("Body Weight", "")
        body_temp = row.get("Body temperature", "")
        
        def safe_float(x):
            try:
                return float(x)
            except:
                return np.nan
        
        pbaseline = {
            'diastolic_bp':      safe_float(row.get("Diastolic Blood Pressure", np.nan)),
            'systolic_bp':       safe_float(row.get("Systolic Blood Pressure", np.nan)),
            'heart_rate':        safe_float(row.get("Heart rate", np.nan)),
            'respiratory_rate':  safe_float(row.get("Respiratory rate", np.nan)),
            'oxygen_saturation': safe_float(row.get("Oxygen saturation in Arterial blood", np.nan)),
        }
        
        # Apply the modifiers to the baseline
        pbaseline_mod = apply_modifiers(
            baseline_dict=pbaseline,
            reason_description=reason_desc,
            pain_score=pain_score,
            height=body_height,
            weight=body_weight,
            temperature=body_temp
        )
        
        patient_start_time = base_time + timedelta(hours=i)
        
        ts_data = generator.generate_patient_series(
            patient_baseline=pbaseline_mod,
            duration_minutes=60,
            interval_seconds=5,
            start_time=patient_start_time
        )
        
        output_rows = []
        for idx, ts_row in ts_data.iterrows():
            output_rows.append([
                ts_row['timestamp'].isoformat(timespec='seconds'),
                patient_id,
                round(ts_row['diastolic_bp'], 1),
                round(ts_row['systolic_bp'], 1),
                round(ts_row['heart_rate'], 1),
                round(ts_row['respiratory_rate'], 1),
                round(ts_row['oxygen_saturation'], 1),
            ])
        
        time_series_ws.append_rows(output_rows, value_input_option='RAW')
    
    print("Time-series data with modifiers generated successfully.")

if __name__ == "__main__":
    main()