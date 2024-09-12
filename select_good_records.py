import argparse
from math import ceil 
import numpy as np
import pandas as pd
import random


parser = argparse.ArgumentParser(description='Select record for Coherent Eyes dataset')
parser.add_argument('file', help='file to process')
args = parser.parse_args()

df = pd.read_csv(args.file)

pdr = df[df["pdr_onset"] != '0000'].sort_values('pdr_onset', ascending=False)
pdr['weight'] =  1.0 - (pdr['count'] / pdr['count'].max())

selected_pdr = pdr.sample(n=int(ceil(len(df)/40)), weights='weight')

npdr = df[(df["npdr_onset"] != '0000') & (df["pdr_onset"] == '0000')].sort_values('npdr_onset', ascending=False)
npdr = npdr[npdr['npdr_onset'] <= '2023-09-06']  # must have at least 1 years history
npdr['weight'] =  1.0 - (npdr['count'] / npdr['count'].max())

selected_npdr = npdr.sample(n=int(ceil(len(df)/20)), weights='weight')

diabetes = df[(df["diabetes_onset"] != '0000') & (df["npdr_onset"] == '0000')].sort_values('diabetes_onset', ascending=False)
diabetes = diabetes[diabetes['diabetes_onset'] <= '2021-09-06']  # must have at least 3 years history
diabetes['weight'] =  1.0 - (diabetes['count'] / diabetes['count'].max())

selected_diabetes = diabetes.sample(n=int(ceil(len(df)/40)), weights='weight')

selected = pd.concat([selected_npdr, selected_pdr, selected_diabetes])

selected_files = selected['file']

[print(x) for x in selected_files]


files = [
  "/Users/dehall/synthea/nei/population1000_details.csv",
  "/Users/dehall/synthea/nei/population100_details.csv",
  "/Users/dehall/synthea/nei/population10_details.csv"
]
