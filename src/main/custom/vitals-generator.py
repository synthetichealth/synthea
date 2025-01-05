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
# If using OpenAI or other modules, set that up...
# from openai import OpenAI
# client = OpenAI(api_key=OPENAI_API_KEY)

# ------------------------------------------
# 1. GOOGLE SHEETS FUNCTIONS (as you had)
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
            # If 'time-series' sheet doesn’t exist yet, you can create it:
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

# ------------------------------------------------
# 2. VITAL SIGNS GENERATOR CLASS (unchanged except
#    we add a 'start_time' parameter for convenience)
# ------------------------------------------------
class VitalSignsGenerator:
    def __init__(self, seed=None):
        if seed is not None:
            np.random.seed(seed)
        
        # Normal ranges (from your previous script)
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
            return np.array([baseline]*len(time_points))  # fallback

    def generate_patient_series(
        self, 
        patient_baseline, 
        duration_minutes=60, 
        interval_seconds=5,
        start_time=None
    ):
        """
        Generate a DataFrame of vital sign time series for one patient.
        If start_time is not provided, default to now.
        """
        if start_time is None:
            start_time = datetime.now()
        
        n_points = int((duration_minutes * 60) / interval_seconds)
        time_points = np.arange(n_points)

        timestamps = [
            start_time + timedelta(seconds=int(i * interval_seconds)) 
            for i in time_points
        ]
        
        data = {'timestamp': timestamps}
        
        # For each vital sign in the baseline:
        for vital_sign, baseline_val in patient_baseline.items():
            if np.isnan(baseline_val):
                # If we don't have a baseline from sheet, use middle of normal range
                low, high = self.normal_ranges.get(vital_sign, (60, 100))
                baseline_val = (low + high) / 2.0
            
            # Add variation
            values = self.add_natural_variation(baseline_val, time_points, vital_sign)
            data[vital_sign] = values
        
        return pd.DataFrame(data)

# ---------------------------------------------
# 3. MAIN LOGIC: READ ENCOUNTERS, GENERATE DATA
# ---------------------------------------------
def main():
    gc = setup_google_sheets()
    source_ws, time_series_ws = get_worksheets(gc)
    
    # 3.1. Load the "Encounters" sheet into a Pandas DataFrame
    #     You can also do get_all_values() / get_all_records() 
    #     if your sheet’s top row is headers.
    encounters_data = source_ws.get_all_records()
    encounters_df = pd.DataFrame(encounters_data)
    
    # The columns we expect (based on your sample):
    # ID, PATIENT, START, STOP, ...,
    # Diastolic Blood Pressure, Systolic Blood Pressure, Heart rate, 
    # Respiratory rate, Oxygen saturation in Arterial blood, ...
    
    # 3.2. Prepare the VitalSignsGenerator
    generator = VitalSignsGenerator(seed=42)
    
    # 3.3. We will generate 1 hour of 5-second data for each row 
    #      (i.e. each "encounter" or patient).
    # 
    # "One hospital bed" logic: 
    #   - The first patient starts at 7:00:00 pm
    #   - Next patient starts exactly 1 hour later, etc.
    # 
    #   That means patient i starts at 7:00 pm + i hours.
    
    base_time = datetime(2025, 1, 1, 19, 0, 0)  # e.g. Jan 1, 2025 at 7:00 PM
    
    # 3.4. Create a header row for the "time-series" sheet
    #      We'll define the columns for the new sheet
    headers = [
        "timestamp",
        "patient_id",
        "diastolic_bp",
        "systolic_bp",
        "heart_rate",
        "respiratory_rate",
        "oxygen_saturation"
    ]
    
    # Clear the "time-series" sheet or just overwrite from the top:
    time_series_ws.clear()
    time_series_ws.append_row(headers)
    
    # 3.5. For each encounter row, parse the baseline vitals,
    #      generate the time series, and append to the sheet.
    
    for i, row in encounters_df.iterrows():
        patient_id = row.get("PATIENT", f"Unknown_{i}")
        
        # baseline extraction from columns (handle missing / blank)
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
        
        # Start time for this patient’s 1-hour chunk
        patient_start_time = base_time + timedelta(hours=i)
        
        # Generate the 1-hour data
        ts_data = generator.generate_patient_series(
            patient_baseline=pbaseline, 
            duration_minutes=60, 
            interval_seconds=5,
            start_time=patient_start_time
        )
        
        # Convert to rows that match the “headers” format
        # e.g., each row: [timestamp, patient_id, diastolic, systolic, hr, rr, sat]
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
        
        # Append to the "time-series" sheet
        # Note: If you have many thousands of rows per patient, 
        # you may need batch appends or chunking. 
        time_series_ws.append_rows(output_rows, value_input_option='RAW')
    
    print("Time-series data generation complete. Check the 'time-series' sheet.")

# ---------------------------------------------
# 4. RUN IT
# ---------------------------------------------
if __name__ == "__main__":
    main()
