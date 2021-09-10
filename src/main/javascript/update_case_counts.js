// This script updates information on COVID-19 cases based on information
// obtained from the Our World In Data information.
//
// This script requires that curl be installed, and executes it as a
// child process to update the case count data.
const fs = require('fs');
const createCsvWriter = require('csv-writer').createObjectCsvWriter;
const child_process = require('child_process');

const csvWriter = createCsvWriter({
  path: '../resources/modules/lookup_tables/covid19_prob.csv',
  header: ['time','Check Vaccine Status',
           'Terminal','Wait Until Exposure'].reduce((acc, label) => {
    acc.push({'id': label, 'title': label});
    return acc;
  }, [])
});

records = []

child_process.execSync('curl -s -o owid-covid19-cases.json https://raw.githubusercontent.com/owid/covid-19-data/master/public/data/owid-covid-data.json')

const rawJson = fs.readFileSync('owid-covid19-cases.json');
const caseData = JSON.parse(rawJson);

caseData['USA']['data'].forEach(dayRow => {
  const day = new Date(dayRow['date']);
  const startTime = day.getTime();
  const endTime = startTime + (24 * 60 * 60 * 1000) - 1;
  // Multiplying by 4 since the COVID-19 module tests for infection
  // every 4 days
  let dayChance = (dayRow['new_cases_per_million'] / 1000000) * 4;
  if (isNaN(dayChance)) {
    dayChance = 0;
  }
  const row = {};
  row['time'] = `${startTime}-${endTime}`;
  row['Check Vaccine Status'] = dayChance;
  row['Terminal'] = 0.0001;
  row['Wait Until Exposure'] = 1 - (dayChance + 0.0001);
  records.push(row);
});

csvWriter.writeRecords(records);