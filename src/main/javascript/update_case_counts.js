const fs = require('fs');
const createCsvWriter = require('csv-writer').createObjectCsvWriter;

const csvWriter = createCsvWriter({
  path: '../resources/modules/lookup_tables/covid19_prob.csv',
  header: ['time','Call Infection Submodule',
           'Terminal','Wait Until Exposure'].reduce((acc, label) => {
    acc.push({'id': label, 'title': label});
    return acc;
  }, [])
});

records = []

const rawJson = fs.readFileSync('/Users/andrewg/Desktop/owid-covid-data.json');
const caseData = JSON.parse(rawJson);

caseData['USA']['data'].forEach(dayRow => {
  const day = new Date(dayRow['date']);
  const startTime = day.getTime();
  const endTime = startTime + (24 * 60 * 60 * 1000) - 1;
  const dayChance = dayRow['new_cases_per_million'] / 1000000;
  const row = {};
  row['time'] = `${startTime}-${endTime}`;
  row['Call Infection Submodule'] = dayChance;
  row['Terminal'] = 0.0001;
  row['Wait Until Exposure'] = 1 - (dayChance + 0.0001);
  records.push(row);
});

csvWriter.writeRecords(records);