package org.mitre.synthea.helpers;

import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TerminologyTest {
    Terminology.Session session;
    Terminology term;

    @Before
    public void setUp() throws Exception {
        session = new Terminology.Session();
        term = new Terminology();
    }

    @Test public void testSession(){
        List<String> resultsList = new ArrayList<>();
        for(int i =0;i<8;i++){
            Pair<String,String> retVal =  session.getRandomCode("https://www.hl7.org/fhir/synthea/diabetes");
            String code = retVal.getValue();
            resultsList.add(code);
            assertTrue(code.equals("427089005") || code.equals("E08") || code.equals("250.00"));
        }
        boolean allEqual = resultsList.isEmpty() || resultsList.stream().allMatch(resultsList.get(0)::equals);
        assertFalse(allEqual);

        resultsList.clear();
        for(int i=0; i<8;i++){
            Pair<String,String> retVal = session.getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071");
            String code = retVal.getValue();
            resultsList.add(code);
            assertTrue(code.equals("248802009") || code.equals("V45.71") || code.equals("Z90.10"));
        }
        allEqual = resultsList.isEmpty() || resultsList.stream().allMatch(resultsList.get(0)::equals);
        assertFalse(allEqual);



    }
}