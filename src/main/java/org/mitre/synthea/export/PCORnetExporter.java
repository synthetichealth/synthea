package org.mitre.synthea.export;

import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mitre.synthea.export.ExportHelper.*;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

public class PCORnetExporter {
    /**
     * Writer for demographics.csv.
     */
    private OutputStreamWriter demographics;
    /**
     * Writer for conditions.csv.
     */
    private OutputStreamWriter conditions;
    /**
     * Writer for encounters.csv.
     */
    private OutputStreamWriter encounters;
    /**
     * Writer for diagnoses.csv.
     */
    private OutputStreamWriter diagnoses;
    /**
     * Charset for specifying the character set of the output files.
     */
    private Charset charset = Charset.forName(Config.get("exporter.encoding"));

    /**
     * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
     */
    private static final String NEWLINE = System.lineSeparator();

    /**
     * Thread-safe monotonically increasing transactionId.
     */
    private AtomicLong transactionId;

    /**
     * Constructor for the PCORnetExporter - initialize the specified files and store
     * the writers in fields.
     */
    private PCORnetExporter() {
        init();
    }

    void init() {
        try {
            File output = Exporter.getOutputFolder("csv/pcornet", null);
            output.mkdirs();
            Path outputDirectory = output.toPath();

            if (Config.getAsBoolean("exporter.csv.folder_per_run")) {
                // we want a folder per run, so name it based on the timestamp
                String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
                String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
                outputDirectory = outputDirectory.resolve(subfolderName);
                outputDirectory.toFile().mkdirs();
            }

            String includedFilesStr = Config.get("exporter.csv.included_files", "").trim();
            String excludedFilesStr = Config.get("exporter.csv.excluded_files", "").trim();

            List<String> includedFiles = Collections.emptyList();
            List<String> excludedFiles = Collections.emptyList();

            if (!includedFilesStr.isEmpty() && !excludedFilesStr.isEmpty()) {
                System.err.println(
                        "CSV exporter: Included and Excluded file settings are both set -- ignoring both");
            } else {
                if (!includedFilesStr.isEmpty()) {
                    includedFiles = propStringToList(includedFilesStr);

                    if (!includedFiles.contains("patients.csv")) {
                        System.err.println("WARNING! CSV exporter is set to not include patients.csv!");
                        System.err.println("This is probably not what you want!");
                    }

                } else {
                    excludedFiles = propStringToList(excludedFilesStr);
                }
            }

            boolean append = Config.getAsBoolean("exporter.csv.append_mode");
            demographics = getWriter(outputDirectory, "demographics.csv", append, includedFiles, excludedFiles);
            conditions = getWriter(outputDirectory, "conditions.csv", append, includedFiles,
                    excludedFiles);
            encounters = getWriter(outputDirectory, "encounters.csv", append, includedFiles,
                    excludedFiles);
            diagnoses = getWriter(outputDirectory, "diagnoses.csv", append, includedFiles,
                    excludedFiles);

            if (!append) {
                writeCSVHeaders();
            }
        } catch (IOException e) {
            // wrap the exception in a runtime exception.
            // the singleton pattern below doesn't work if the constructor can throw
            // and if these do throw ioexceptions there's nothing we can do anyway
            throw new RuntimeException(e);
        }

        this.transactionId = new AtomicLong();
    }

    /**
     * Helper function to convert a list of files directly from synthea.properties to filenames.
     * @param fileListString String directly from Config, ex "patients.csv,conditions , procedures"
     * @return normalized list of filenames as strings
     */
    private static List<String> propStringToList(String fileListString) {
        List<String> files = Arrays.asList(fileListString.split(","));
        // normalize filenames -- trim, lowercase, add .csv if not included
        files = files.stream().map(f -> {
            f = f.trim().toLowerCase();
            if (!f.endsWith(".csv")) {
                f = f + ".csv";
            }
            return f;
        }).collect(Collectors.toList());

        return files;
    }

    /**
     * Write the headers to each of the CSV files.
     * @throws IOException if any IO error occurs
     */
    private void writeCSVHeaders() throws IOException {
        demographics.write("BIOBANK_FLAG,BIRTH_DATE,BIRTH_TIME,GENDER_IDENTITY,HISPANIC,PAT_PREF_LANGUAGE_SPOKEN," +
                "PATID,RACE,RAW_GENDER_IDENTITY,RAW_HISPANIC,RAW_PAT_PREF_LANGUAGE_SPOKEN,RAW_RACE," +
                "RAW_SEX,RAW_SEXUAL_ORIENTATION,SEX,SEXUAL_ORIENTATION");
        demographics.write(NEWLINE);
        conditions.write("CONDITIONID,PATID,ENCOUNTERID,REPORT_DATE,RESOLVE_DATE,ONSET_DATE," +
                "CONDITION_STATUS,CONDITION,CONDITION_TYPE,CONDITION_SOURCE," +
                "RAW_CONDITION_STATUS,RAW_CONDITION,RAW_CONDITION_TYPE,RAW_CONDITION_SOURCE");
        conditions.write(NEWLINE);
        encounters.write(
                "ADMIT_DATE,ADMIT_TIME,ADMITTING_SOURCE,DISCHARGE_DATE,DISCHARGE_DISPOSITION," +
                        "DISCHARGE_STATUS,DISCHARGE_TIME,DRG,DRG_TYPE,ENC_TYPE,ENCOUNTERID," +
                        "FACILITY_LOCATION,FACILITY_TYPE,FACILITYID,PATID,PAYER_TYPE_PRIMARY," +
                        "PAYER_TYPE_SECONDARY,PROVIDERID,RAW_ADMITTING_SOURCE,RAW_DISCHARGE_DISPOSITION," +
                        "RAW_DISCHARGE_STATUS,RAW_DRG_TYPE,RAW_ENC_TYPE,RAW_FACILITY_TYPE,RAW_PAYER_ID_PRIMARY," +
                        "RAW_PAYER_ID_SECONDARY,RAW_PAYER_NAME_PRIMARY,RAW_PAYER_NAME_SECONDARY,RAW_PAYER_TYPE_PRIMARY," +
                        "RAW_PAYER_TYPE_SECONDARY,RAW_SITEID");
        encounters.write(NEWLINE);
        diagnoses.write("ADMIT_DATE,DIAGNOSISID,DX,DX_DATE,DX_ORIGIN,DX_POA,DX_SOURCE,DX_TYPE,ENC_TYPE,ENCOUNTERID,PATID,PDX," +
                "PROVIDERID,RAW_DX,RAW_DX_POA,RAW_DX_SOURCE,RAW_DX_TYPE,RAW_PDX");
        diagnoses.write(NEWLINE);
    }

    /**
     * Thread safe singleton pattern adopted from
     * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
     */
    private static class SingletonHolder {
        /**
         * Singleton instance of the PCORnetExporter.
         */
        private static final PCORnetExporter instance = new PCORnetExporter();
    }

    /**
     * Get the current instance of the PCORnetExporter.
     *
     * @return the current instance of the PCORnetExporter.
     */
    public static PCORnetExporter getInstance() {
        return PCORnetExporter.SingletonHolder.instance;
    }

    /**
     * Add a single Person's health record info to the CSV records.
     *
     * @param person Person to write record data for
     * @param time   Time the simulation ended
     * @throws IOException if any IO error occurs
     */
    public void export(Person person, long time) throws IOException {
        String personID = demographic(person, time);

        for (HealthRecord.Encounter encounter : person.record.encounters) {

            String encounterID = encounter(person, person, personID, encounter);
            String payerID = encounter.claim.payer.uuid;


            for (HealthRecord.Entry condition : encounter.conditions) {
                /* condition to ignore codes other then retrieved from terminology url */
                if (!StringUtils.isEmpty(Config.get("generate.terminology_service_url"))
                        && !RandomCodeGenerator.selectedCodes.isEmpty()) {
                    if (RandomCodeGenerator.selectedCodes.stream()
                            .anyMatch(code -> code.code.equals(condition.codes.get(0).code))) {
                        condition(personID, encounterID, condition);
                        diagnosis(person, person, personID, encounterID, encounter, condition);
                    }
                } else {
                    condition(personID, encounterID, condition);
                    diagnosis(person, person, personID, encounterID, encounter, condition);
                }
            }
        }

        demographics.flush();
        encounters.flush();
        diagnoses.flush();
        conditions.flush();
    }

    /**
     * Write a single Patient line, to patients.csv.
     *
     * @param person Person to write data for
     * @param time Time the simulation ended, to calculate age/deceased status
     * @return the patient's ID, to be referenced as a "foreign key" if necessary
     * @throws IOException if any IO error occurs
     */
    private String demographic(Person person, long time) throws IOException {
        // Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,
        // FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
        // CITY,STATE,COUNTY,ZIP,LAT,LON,HEALTHCARE_EXPENSES,HEALTHCARE_COVERAGE
        String personID = (String) person.attributes.get(Person.ID);

        // check if we've already exported this patient demographic data yet,
        // otherwise the "split record" feature could add a duplicate entry.
        if (person.attributes.containsKey("exported_to_pcornet_csv")) {
            return personID;
        } else {
            person.attributes.put("exported_to_pcornet_csv", personID);
        }

        StringBuilder s = new StringBuilder();

        for (String attribute : new String[] {
                "todo__biobank_flag", // TODO mock biobank flag
                Person.BIRTHDATE,
                "todo__birth_time", // TODO mock birth time
                Person.GENDER, // TODO gender vs sex
                "todo__hispanic",
                Person.FIRST_LANGUAGE,
                Person.ID,
                Person.RACE,
                Person.GENDER, // TODO gender vs sex
                "todo__raw_hispanic",
                Person.FIRST_LANGUAGE,
                Person.RACE,
                Person.GENDER, // TODO gender vs sex
                Person.SEXUAL_ORIENTATION,
                Person.GENDER, // TODO gender vs sex
                Person.SEXUAL_ORIENTATION
        }) {
            String value;
            if (attribute.equals(Person.BIRTHDATE)) {
                value = dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE));
            } else {
                value = (String) person.attributes.getOrDefault(attribute, "");
            }
            s.append(clean(value)).append(',');
        }

        s.deleteCharAt(s.length()-1); // remove trailing comma
        s.append(NEWLINE);
        write(s.toString(), demographics);

        return personID;
    }

    /**
     * Write a single Encounter line to encounters.csv.
     *
     * @param rand      Source of randomness to use when generating ids etc
     * @param personID  The ID of the person that had this encounter
     * @param encounter The encounter itself
     * @return The encounter ID, to be referenced as a "foreign key" if necessary
     * @throws IOException if any IO error occurs
     */
    private String encounter(RandomNumberGenerator rand, Person person, String personID,
                             HealthRecord.Encounter encounter) throws IOException {

        StringBuilder s = new StringBuilder();
        String encounterID = rand.randUUID().toString();
        String dischargeDispotion = "";
        String encounterStartDate = dateFromTimestamp(encounter.start);
        String encounterStartTime = iso8601Timestamp(encounter.start);
        String encounterStopDate = "";
        String encounterStopTime = "";
        if (encounter.stop != 0L) {
            encounterStopDate = dateFromTimestamp(encounter.stop);
            encounterStopTime = iso8601Timestamp(encounter.stop);
            if (person.alive(encounter.stop)) {
                dischargeDispotion = "A";
            } else {
                dischargeDispotion = "E";
            }
        } else {
            dischargeDispotion = "UN";
        }


        // ADMIT_DATE & ADMIT_TIME
        s.append(encounterStartDate).append(',');
        s.append(encounterStartTime).append(',');
        // ADMITTING_SOURCE // TODO
        s.append(',');
        // DISCHARGE_DATE
        s.append(encounterStopDate).append(',');
        // DISCHARGE_DISPOSITION
        s.append(dischargeDispotion).append(',');
        // DISCHARGE_STATUS
        if (encounter.discharge != null) {
            s.append(encounter.discharge.code);
        }
        s.append(',');
        // DISCHARGE_TIME
        s.append(encounterStopTime).append(',');
        // DRG
        s.append(',');
        // DRG_TYPE
        s.append(',');
        // ENC_TYPE
        s.append(clean(encounter.type)).append(',');
        // ENCOUNTERID
        s.append(encounterID).append(',');
        // FACILITY_LOCATION
        if (encounter.provider.zip != null && encounter.provider.zip.length() >= 5) {
            s.append(encounter.provider.zip, 0, 5);
        }
        s.append(',');
        // FACILITY_TYPE
        s.append(clean(encounter.provider.type)).append(',');
        // FACILITYID
        s.append(encounter.provider.id).append(',');
        // PATID
        s.append(personID).append(',');
        // PAYER_TYPE_PRIMARY // TODO
        s.append(',');
        // PAYER_TYPE_SECONDARY
        s.append(',');
        // PROVIDERID
        s.append(encounter.provider.id).append(',');
        // RAW_ADMITTING_SOURCE // TODO
        s.append(',');
        // RAW_DISCHARGE_DISPOSITION
        s.append(dischargeDispotion).append(',');
        // RAW_DISCHARGE_STATUS
        if (encounter.discharge != null) {
            s.append(encounter.discharge.code);
        }
        s.append(',');
        // RAW_DRG_TYPE
        s.append(',');
        // RAW_ENC_TYPE
        s.append(encounter.type).append(',');
        // RAW_FACILITY_TYPE
        s.append(clean(encounter.provider.type)).append(',');
        // RAW_PAYER_ID_PRIMARY
        s.append(encounter.claim.payer.getResourceID()).append(',');
        // RAW_PAYER_ID_SECONDARY
        s.append(',');
        // RAW_PAYER_NAME_PRIMARY
        s.append(encounter.claim.payer.getName()).append(',');
        // RAW_PAYER_NAME_SECONDARY
        s.append(',');
        // RAW_PAYER_TYPE_PRIMARY // TODO
        s.append(',');
        // RAW_PAYER_TYPE_SECONDARY
        s.append(',');
        // RAW_SITEID
        s.append(encounter.provider.getResourceLocationID());

        s.append(NEWLINE);
        write(s.toString(), encounters);
        return encounterID;
    }

    private String diagnosis(RandomNumberGenerator rand, Person person, String personID, String encounterId, HealthRecord.Encounter encounter, HealthRecord.Entry condition) throws IOException {
        StringBuilder s = new StringBuilder();
        String diagnosisId = rand.randUUID().toString();
        HealthRecord.Code coding = condition.codes.get(0);

        String dxPoa = "";
        Long onsetTime = person.record.presentOnset(coding.code);
        if (onsetTime == null) {
            dxPoa = "UN";
        } else if (onsetTime < encounter.start) {
            dxPoa = "Y";
        } else {
            dxPoa = "N";
        }

        // ADMIT_DATE
        s.append(dateFromTimestamp(encounter.start)).append(',');
        // DIAGNOSISID
        s.append(diagnosisId).append(',');
        // DX
        s.append(coding.code).append(',');
        // DX_DATE
        s.append(dateFromTimestamp(condition.start)).append(',');
        // DX_ORIGIN  // TODO
        s.append(',');
        // DX_POA
        s.append(dxPoa).append(',');
        // DX_SOURCE // TODO
        s.append("FI").append(',');
        // DX_TYPE
        s.append("SM").append(',');
        // ENC_TYPE
        s.append(encounter.type).append(',');
        // ENCOUNTERID
        s.append(encounterId).append(',');
        // PATID
        s.append(personID).append(',');
        // PDX
        s.append(',');
        // PROVIDERID
        s.append(encounter.clinician.getOrganization().id).append(',');
        // RAW_DX
        s.append(coding.code).append(',');
        // RAW_DX_POA
        s.append(dxPoa).append(',');
        // RAW_DX_SOURCE  // TODO
        s.append("FI").append(',');
        // RAW_DX_TYPE
        s.append("10").append(',');
        // RAW_PDX

        s.append(NEWLINE);
        write(s.toString(), diagnoses);
        return diagnosisId;
    }

    /**
     * Write a single Condition to conditions.csv.
     *
     * @param personID    ID of the person that has the condition.
     * @param encounterID ID of the encounter where the condition was diagnosed
     * @param condition   The condition itself
     * @throws IOException if any IO error occurs
     */
    private void condition(String personID, String encounterID, HealthRecord.Entry condition) throws IOException {
        // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
        StringBuilder s = new StringBuilder();

        s.append(dateFromTimestamp(condition.start)).append(',');
        if (condition.stop != 0L) {
            s.append(dateFromTimestamp(condition.stop));
        }
        s.append(',');
        s.append(personID).append(',');
        s.append(encounterID).append(',');

        HealthRecord.Code coding = condition.codes.get(0);

        s.append(coding.code).append(',');
        s.append(clean(coding.display));

        s.append(NEWLINE);
        write(s.toString(), conditions);
    }

    /**
     * Replaces commas and line breaks in the source string with a single space.
     * Null is replaced with the empty string.
     */
    private static String clean(String src) {
        if (src == null) {
            return "";
        } else {
            return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
        }
    }

    /**
     * Helper method to write a line to a File. Extracted to a separate method here
     * to make it a little easier to replace implementations.
     *
     * @param line   The line to write
     * @param writer The place to write it
     * @throws IOException if an I/O error occurs
     */
    private static void write(String line, OutputStreamWriter writer) throws IOException {
        synchronized (writer) {
            writer.write(line);
        }
    }

    /**
     * "No-op" writer to use to prevent writing to excluded files.
     * Note that this uses an Apache "NullOutputStream", but JDK11 provides its own.
     */
    private static final OutputStreamWriter NO_OP =
            new OutputStreamWriter(NullOutputStream.NULL_OUTPUT_STREAM);

    /**
     * Helper method to get the writer for the given output file.
     * Returns a "no-op" writer for any excluded files.
     *
     * @param outputDirectory Parent directory for output csv files
     * @param filename Filename for the current file
     * @param append True = append to an existing file, False = overwrite any existing files
     * @param includedFiles List of filenames that should be included in output
     * @param excludedFiles List of filenames that should not be included in output
     *
     * @return OutputStreamWriter for the given output file.
     */
    private OutputStreamWriter getWriter(Path outputDirectory, String filename, boolean append,
                                         List<String> includedFiles, List<String> excludedFiles) throws IOException {

        boolean excluded = (!includedFiles.isEmpty() && !includedFiles.contains(filename))
                || excludedFiles.contains(filename);
        if (excluded) {
            return NO_OP;
        }

        File file = outputDirectory.resolve(filename).toFile();
        // file writing may fail if we tell it to append to a file that doesn't already exist
        append = append && file.exists();
        return new OutputStreamWriter(new FileOutputStream(file, append), charset);
    }
}
