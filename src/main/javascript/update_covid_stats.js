// This script updates information on COVID-19 vaccinations based on the
// CDC provided API for that information.
//
// The script depends on the axios and csv-writer packages. It was tested
// using node.js 16.5.0.
//
// The script uses async / await becuase the JavaScript overlords have
// determined that the world comes to an end if anything blocks the
// main thread, even in a simple script, where it totally makes sense
// to do so.
const fs = require('fs');

const axios = require('axios');
const createCsvWriter = require('csv-writer').createObjectCsvWriter;

const limit = 1000;
let offset = 0;

const apiUrl = 'https://data.cdc.gov/resource/km4m-vcsb.json'

const ageCategories = ['Ages_12-15_yrs', 'Ages_16-17_yrs', 'Ages_18-29_yrs',
                       'Ages_30-39_yrs', 'Ages_40-49_yrs', 'Ages_50-64_yrs',
                       'Ages_65-74_yrs', 'Ages_75+_yrs'];


let output = {};
let ageDosePercentage = {};

let responseLength = 1000;

fetchAllCOVIDData().then(allData => {
  ageCategories.forEach(category => {
    const categoryData = allData.filter(row => row.demographic_category == category);
    for (i = 0; i < categoryData.length; i++) {
      // The API provides a cumulative count of doses provided. Since the query orders
      // the results by time, we can assume that the first entry is the amount of
      // doses administered on the first day. After that, it subtracts the value from
      // the previous day to get the count.
      let doseCount = 0;
      if (i == 0) {
        doseCount = categoryData[0].administered_dose1
      } else {
        doseCount = categoryData[i].administered_dose1 - categoryData[i - 1].administered_dose1
      }

      // There is some wonky data in the API. Currently, July 20 goes negative for 75+
      if (doseCount < 0) {
        doseCount = 0;
      }

      if (i == (categoryData.length - 1)) {
        ageDosePercentage[category] = categoryData[i].administered_dose1_pct;
      }

      const reportDate = categoryData[i].date;
      const formattedDate = formatDate(reportDate);
      let dateRow = output[formattedDate];
      if (dateRow == undefined) {
        dateRow = {};
      }
      dateRow[category] = doseCount;
      output[formattedDate] = dateRow;
    }
  })

  // The code to write out the headers is slightly ridiculous. It will
  // only write headers into the file if the headers are provided as
  // and array of objects with id and title properties.
  const csvWriter = createCsvWriter({
    path: '../resources/modules/lookup_tables/covid_dose_rates.csv',
    header: ['date'].concat(ageCategories).reduce((acc, label) => {
      acc.push({'id': label, 'title': label});
      return acc;
    }, [])
  });

  outputRows = [];

  for (const [key, value] of Object.entries(output)) {
    outputRows.push(Object.assign({'date': key}, value));
  }

  csvWriter.writeRecords(outputRows);

  let ageDosePercentageJSON = JSON.stringify(ageDosePercentage, null, 2);
  fs.writeFileSync('../resources/covid19/covid_first_shot_percentage_by_age.json',
                   ageDosePercentageJSON);
});



function formatDate(timestamp) {
  const date = new Date(timestamp);
  return `${date.getMonth() + 1}/${date.getDate()}/${date.getFullYear()}`
}

async function fetchAllCOVIDData() {
  let allData = [];
  while (responseLength == 1000) {
    let response = await fetchCOVIDDataPage(limit, offset);
    allData = allData.concat(response.data);
    responseLength = response.data.length;
    offset += response.data.length;
  }
  return allData;
}

async function fetchCOVIDDataPage(limit, offset) {
  // A 200ms pause to make sure the script doesn't bombard the API
  await sleep(200);
  try {
    const response = await axios.get(apiUrl, {
      params: {
        "$limit": limit,
        "$offset": offset,
        "$order": "date",
        "$where": `demographic_category in(${ageCategories.map(ac => `'${ac}'`).join(',')})`
      }
    });
    if (response.status == 200) {
      return response;
    } else {
      console.log(response);
    }
  } catch (error) {
    console.error(error);
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
