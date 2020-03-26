import csv
from datetime import datetime, timedelta

headers = ['time', 'Encounter for Test', 'Terminal', 'Wait Until Exposure']

one_week = timedelta(days=7)
one_second_less_than_a_week = one_week - timedelta(milliseconds=1)
start = datetime(2019, 11, 1)
end = start + one_second_less_than_a_week
start_prob = 0.01
weekly_increase = 0.10
max_prob = 0.75
lucky_ones = 0.1
week = 0

with open('covid19_prob.csv', 'w', newline='') as csvfile:
  writer = csv.DictWriter(csvfile, fieldnames=headers)
  writer.writeheader()
  while end < datetime.now():
    row = {}
    prob_of_covid19 = start_prob + (week * weekly_increase)
    if prob_of_covid19 > max_prob:
      prob_of_covid19 = max_prob

    row['time'] = "{:0.0f}-{:0.0f}".format(start.timestamp() * 1000, end.timestamp() * 1000)
    row['Encounter for Test'] = prob_of_covid19
    row['Wait Until Exposure'] = 1 - prob_of_covid19 - lucky_ones
    row['Terminal'] = lucky_ones
    writer.writerow(row)
    start += one_week
    end += one_week
    week += 1
