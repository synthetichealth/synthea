package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.mdht.uml.cda.util.BasicValidationHandler;
import org.eclipse.mdht.uml.cda.util.CDAUtil;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * Uses Model Driven Health Tools (MDHT) to validate exported CCDA R2.1.
 * https://github.com/mdht/mdht-models
 */
public class CCDAExporterTest {
    @Test public void testCCDAExport() throws Exception
    {
        CDAUtil.loadPackages();
    	TestHelper.exportOff();
    	Config.set("exporter.ccda.export", "true");
    	
    	List<String> validationErrors = new ArrayList<String>();
    	
    	int numberOfPeople = 10;
        Generator generator = new Generator(numberOfPeople);
        for(int i=0; i < numberOfPeople; i++)
        {
        	int x = validationErrors.size();
            Person person = generator.generatePerson(i);    
            String ccdaXml = CCDAExporter.export(person, System.currentTimeMillis());
            InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
            try {
                CDAUtil.load(inputStream, new BasicValidationHandler() {
                    public void handleError(Diagnostic diagnostic) {
                    	System.out.println("ERROR: " + diagnostic.getMessage());
                        validationErrors.add(diagnostic.getMessage());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            int y = validationErrors.size();
            if(x != y) {
            	Exporter.export(person, System.currentTimeMillis());
            }
        }

        assertEquals(0, validationErrors.size());
    }
}
