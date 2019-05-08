package org.mitre.synthea.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ElasticSearchExporterTest {

    /**
     * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testElasticSearchExport() throws Exception {
        //TestHelper.exportOff();
        Config.set("exporter.elastic.export", "true");
        Config.set("exporter.elastic.isunittest", "true");

        File tempOutputFolder = tempFolder.newFolder();
        Config.set("exporter.baseDirectory", tempOutputFolder.toString());

        int numberOfPeople = 10;
        Generator generator = new Generator(numberOfPeople);
        for (int i = 0; i < numberOfPeople; i++) {
            generator.generatePerson(i);
        }
        // Adding post completion exports to generate organizations and providers CSV files
        ElasticSearchExporter exporter = ElasticSearchExporter.getInstance();
        exporter.export();
        HashMap<String,Integer> report = exporter.getReport();


        assertTrue(!report.isEmpty());


    }
}
