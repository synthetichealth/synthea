from utils.patient_plots import *

# Point this to the csv subfolder of the output directory created by Synthea
output_path = "../Sample_data/csv_hypertension_1000"

conds = pd.read_csv(f"{output_path}/conditions.csv")
obs = pd.read_csv(f"{output_path}/observations.csv")
meds = pd.read_csv(f"{output_path}/medications.csv")
pats = pd.read_csv(f"{output_path}/patients.csv")
encounters = pd.read_csv(f"{output_path}/encounters.csv")

st.header("Summary Statistics on the Data")





col1, col2 = st.columns(2)

with col1:     
    ht_conds = conds[conds["DESCRIPTION"] == "Essential hypertension (disorder)"]
    patients = len(list(pats["Id"]))
    ht_patients = list(set(conds[conds["DESCRIPTION"] == "Essential hypertension (disorder)"]["PATIENT"]))
    num_ht = len(ht_patients)

    fig, ax = plt.subplots(1,1, figsize=(6,3))
    ax.bar(['Hypertension', 'No Hypertension'], [num_ht, patients-num_ht], color="lightcoral")

    title = f"Number of Patients with and without hypertension"

    ax.set_title(title, fontdict={'fontsize':10})
    plt.legend()
    st.pyplot(fig, use_container_width=False)  

    genders = pats.GENDER.value_counts()

    fig, ax = plt.subplots(figsize=(8, 4))
    genders.plot(kind='bar', ax=ax, color='mediumpurple')
    plt.xticks(rotation=0)

    ax.set_title("Distribution of patients by gender.")

    st.pyplot(fig)  

    

with col2:
    # print(pats.keys())
    ethnicities = pats.RACE.value_counts()

    fig, ax = plt.subplots(figsize=(8, 4))
    ethnicities.plot(kind='bar', ax=ax, color='skyblue')
    plt.xticks(rotation=0)

    ax.set_title("Distribution of patients by ethnicity.")

    st.pyplot(fig)  

    patient_number = st.slider(
        "Pick a patient to see their blood pressure over time",
        min_value=0,
        max_value=num_ht
        )
    
    plot_bp(ht_patients[patient_number], obs, conds, meds)
