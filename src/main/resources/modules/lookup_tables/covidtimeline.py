import csv
from datetime import datetime, timedelta

headers = ['time', 'Call Infection Submodule', 'Terminal', 'Wait Until Exposure']

four_days = timedelta(days=4)
one_second_less_than_four_days = four_days - timedelta(milliseconds=1)
start = datetime(2020, 1, 20)
end = start + one_second_less_than_four_days
start_prob = 0.0001
max_prob = 0.75
lucky_ones = 0.01
cycle = 0

with open('covid19_prob.csv', 'w', newline='') as csvfile:
  writer = csv.DictWriter(csvfile, fieldnames=headers)
  writer.writeheader()
  while end < datetime(2020, 4, 3):
    row = {}
    prob_of_covid19 = start_prob * (2 ** cycle)
    if prob_of_covid19 > max_prob:
      prob_of_covid19 = max_prob
    row['time'] = "{:0.0f}-{:0.0f}".format(start.timestamp() * 1000, end.timestamp() * 1000)
    row['Call Infection Submodule'] = prob_of_covid19
    row['Wait Until Exposure'] = 1 - prob_of_covid19 - lucky_ones
    row['Terminal'] = lucky_ones
    writer.writerow(row)
    start += four_days
    end += four_days
    cycle += 1
