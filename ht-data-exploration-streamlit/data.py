import streamlit as st
import pandas as pd
from utils.utils import filter_dataframe

st.set_page_config(layout="wide")

st.title("Hypertension Dataset Data Exploration")

st.write(
    "Below you can find tabs for each of the tables in the hypertension \
    dataset, allowing you to explore the raw data."
)

(
    tab_allergies,
    tab_careplans,
    tab_conditions,
    tab_devices,
    tab_encounters,
    tab_imaging,
    tab_immunizations,
    tab_meds,
    tab_obs,
    tab_orgs,
    tab_patients,
    tab_procedures,
    tab_providers,
    tab_supplies,
) = st.tabs(
    [
        "Allergies",
        "Careplans",
        "Conditions ",
        "Devices",
        "Encounters",
        "Imaging Studies",
        "Immunizations",
        "Medications",
        "Observations",
        "Organisations",
        "Patients",
        "Procedures",
        "Providers",
        "Supplies",
    ]
)

def data_tabs(caption, file_name, tab_name):
    with tab_name:
        st.write(caption)

        df = pd.read_csv(f"../Sample_Data/csv_hypertension_1000/{file_name}.csv")
        st.dataframe(filter_dataframe(df, tab_name))

data_tabs("The allergies table includes any allergy diagnoses that the \
              patients in the dataset have. In the hypertension usecase this \
              table shall always be empty.", 'allergies', tab_allergies)

data_tabs("The careplans table includes any careplans that a patient has been \
        put on during their time in the simulation.", 'careplans', tab_careplans)

data_tabs("The conditions table includes any conditions that patients in the \
            dataset have been diagnosed with.", 'conditions', tab_conditions)

data_tabs("Table of medical devices the patients have. This will always \
          currently be empty in the hypertension dataset. Potentially included \
          in [future work](https://github.com/nhsengland/swpc_synthea/issues/22) \
          to include BP monitors for hypertension patients.", 'devices',
          tab_devices)

data_tabs("The encounters table includes any encounters that patients have with \
        the health service.", 'encounters', tab_encounters)

data_tabs("The imaging studies table includes any imaging done on patients in \
          the dataset. In the hypertension usecase this table shall always be \
          empty.", 'imaging_studies', tab_imaging)

data_tabs("The immunizations table includes any vaccines that patients get. In \
          the hypertension use-case only the  immunizations scheduled for \
          everyone occur, as well as the flu jab for the elderly. No \
          immunizations related to specific conditions or travel occur.", 
          'immunizations', tab_immunizations)

data_tabs("The medications table includes any medications that patients have \
          been prescribed.", 'medications', tab_meds)

data_tabs("Table of any measurements or observations or labs taken of the \
          patients.", 'observations', tab_obs)

data_tabs("Table with details on all of the organisations providing care to \
          patients. In this version of the dataset the state will always be \
          the South West (SW).", 'organizations', tab_orgs)

data_tabs("Table with details on all of the patients.", 'patients', tab_patients)

data_tabs("Table describing all of the procedures the patients go through.", 
          'procedures', tab_procedures)

data_tabs("Table describing all of the providers/clinicians working for the \
          organisations.", 'providers', tab_providers)

data_tabs("Table with all of the supplies used, e.g. glucose strips. This will \
          remain empty for the hypertension usecase.", 'supplies', tab_supplies)
