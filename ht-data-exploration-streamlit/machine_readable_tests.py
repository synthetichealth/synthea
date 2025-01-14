import pandas as pd
import streamlit as st
import matplotlib.pyplot as plt

df = pd.read_csv('machine_readable_test.csv')

st.header("Machine Readable Data")
st.write("The data tables in the previous tabs have been pivoted to all be in one table, that data is below.")
st.dataframe(df)

st.header("Using it to code the same plots as previous")

df_patients = df[(df["table name"]=='patients.csv')]

ethnicities = df_patients[df['field name']=='RACE'].value.value_counts()
fig, ax = plt.subplots(figsize=(8, 4))
ethnicities.plot(kind='bar', ax=ax, color='skyblue')
plt.xticks(rotation=0)

ax.set_title("Distribution of patients by ethnicity.")

st.pyplot(fig) 

patient_number = st.number_input(
        f"Pick a patient to see their route through healthcare over time, \
            there are 1000 patients to choose from.",
        min_value=0,
        max_value=1000
        )

df_chosen_patient = df[(df['field name']=='PATIENT') & (df['value']=='b54acf7e-a725-9fc7-0a14-8a5e298c4e69')]
st.write(df_chosen_patient)