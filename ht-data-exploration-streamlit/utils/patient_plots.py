import matplotlib.pyplot as plt
import pandas as pd
import streamlit as st

import plotly.graph_objects as go

def date_format(date_str):
    truncated_str = date_str[:10]
    return pd.to_datetime(truncated_str)

def date_first_ht_med(meds_df, patient_id):
    patient_ht_meds = meds_df[(meds_df["PATIENT"] == patient_id) & (meds_df["REASONCODE"] == "hypertension_dx")]
    first_date = patient_ht_meds["START"].min()
    return first_date

def plot_bp(patient_id, obs_df, conds, meds):
    ht_conds = conds[conds["DESCRIPTION"] == "Essential hypertension (disorder)"]
    ht_patients = list(ht_conds["PATIENT"])
    if patient_id in ht_patients:
        ht_diagnosis_date = ht_conds[ht_conds["PATIENT"] == patient_id]["START"].iloc[0]
        ht_diagnosis_date = date_format(ht_diagnosis_date)
    else:
        ht_diagnosis_date = None
    sys_values = obs_df[(obs_df["DESCRIPTION"].str.contains("Systolic")) & (obs_df["PATIENT"] == patient_id)]
    dia_values = obs_df[(obs_df["DESCRIPTION"].str.contains("Diastolic")) & (obs_df["PATIENT"] == patient_id)]
    fig, ax = plt.subplots(1,1, figsize=(6,3))
    ax.xaxis.set_major_locator(plt.MaxNLocator(5))
    ax.plot(sys_values["DATE"].apply(date_format), sys_values["VALUE"].astype(float), label = "systolic")
    ax.plot(dia_values["DATE"].apply(date_format), dia_values["VALUE"].astype(float), label = "diastolic")

    title = f"BP Measurements for Patient: {patient_id}"

    if ht_diagnosis_date:
        ax.axvline(ht_diagnosis_date, label="HT Diagnosis", c="r", linestyle = "--")
    
    # ht_meds_date = date_first_ht_med(meds, patient_id)
    # ax.axvline(ht_diagnosis_date, label="HT Meds", c="b", linestyle = "--")

    ax.set_title(title, fontdict={'fontsize':10})
    plt.xticks(rotation = 45)
    plt.legend()
    st.pyplot(fig, use_container_width=False)

def create_sankey_diagram(df=None):
    total_hypertension_patients = 800
    total_no_hypertension_patients = 200
    total_medicated = 750
    total_unmedicated = 50
    medicated_dead = 20
    medicated_alive = 730
    unmedicated_dead = 10
    unmedicated_alive = 40
    no_hypertension_alive = 190
    no_hypertension_dead = 10

    fig = go.Figure(data=[go.Sankey(
        node = dict(
            pad = 75,
            thickness = 10,
            line = dict(color = "black", width = 0.5),
            label = ["All patients", "Hypertension", "No Hypertension", "Medication", "No Medication", "Dead", "Alive"],
            # x = [0, 0, 0, 1, 1, 2, 2],
            # y = [0, 1, 2, 1, 2, 1, 2],
            color = "green"
        ),
            link = dict(
            source = [0, 0, 1, 1, 3, 3, 4, 4, 2, 2],
            target = [1, 2, 3, 4, 5, 6, 5, 6, 6, 5],
            value = [total_hypertension_patients, total_no_hypertension_patients,
                     total_medicated, total_unmedicated, medicated_dead,
                     medicated_alive, unmedicated_dead, unmedicated_alive,
                     no_hypertension_alive, no_hypertension_dead]
    ),
    arrangement="fixed")] 
    )

    

    fig.update_layout(
        font=dict(size=15, color='black')
    )

    return fig