package org.mitre.synthea.export.rif.identifiers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.Test;

public class PassportTest {

    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^X\\d{8}X$");

    @Test
    public void testFormatIsAlwaysTenCharacters() {
        Passport passport = new Passport(0);
        for (int i = 0; i < 10000; i++) {
            String value = passport.toString();
            assertTrue("Unexpected format: " + value, PASSPORT_PATTERN.matcher(value).matches());
            passport = passport.next();
        }
    }

    @Test
    public void testNextIncrementsSequentially() {
        Passport first = new Passport(0);
        Passport second = first.next();
        assertEquals("X00000001X", second.toString());
    }

    @Test
    public void testParseRoundTrip() {
        Passport original = new Passport(12345678L);
        String str = original.toString();
        Passport parsed = Passport.parse(str);
        assertEquals(original, parsed);
        assertEquals(str, parsed.toString());
    }

    @Test
    public void testMinValue() {
        Passport passport = new Passport(Passport.MIN_PASSPORT);
        assertEquals("X00000000X", passport.toString());
    }

    @Test
    public void testMaxValueDoesNotThrow() {
        // Should not throw IllegalArgumentException at the upper bound
        Passport passport = new Passport(Passport.MAX_PASSPORT);
        assertTrue(PASSPORT_PATTERN.matcher(passport.toString()).matches());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueBeyondMaxThrows() {
        new Passport(Passport.MAX_PASSPORT + 1);
    }
}