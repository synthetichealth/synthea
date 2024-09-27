package org.mitre.synthea.helpers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PersonnummerGenerator {

    private static final Set<String> generatedPersonnummers = new HashSet<>();
    private static final Random random = new Random();

    public static String generateUniquePersonnummer(Long time, String gender) {
        String personnummer;
        int[] dateComponents = getDateFromTimestamp(time);
        int year = dateComponents[0];
        int month = dateComponents[1];
        int day = dateComponents[2];

        // Keep generating until a unique personnummer is found
        do {
            personnummer = generatePersonnummer(year, month, day, gender);
        } while (!generatedPersonnummers.add(personnummer)); // Adds and checks for uniqueness

        return personnummer;
    }

    private static String generatePersonnummer(int year, int month, int day, String gender) {
        String dateOfBirth = String.format("%04d%02d%02d", year, month, day);

        String individualNumber = generateLastfour(gender);
        System.out.println(individualNumber);

        return dateOfBirth + "-" + individualNumber;
    }

    private static String generateLastfour(String gender) {
        int firstTwoDigits = random.nextInt(100);
        int thirdDigit;

        // Determine the third digit based on gender
        if (gender.equals("M")) {
            thirdDigit = (1 + random.nextInt(5)) * 2 - 1;
        } else {
            thirdDigit = 0 + random.nextInt(5) * 2;
        }
        int lastDigit = random.nextInt(10);

        return String.format("%02d%d%d", firstTwoDigits, thirdDigit, lastDigit);
    }

    private static int[] getDateFromTimestamp(long timestamp) {
        // Convert the timestamp to Instant
        Instant instant = Instant.ofEpochMilli(timestamp);

        // Convert Instant to LocalDateTime using UTC offset
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        // Retrieve the year, month, and day
        int year = dateTime.getYear();
        int month = dateTime.getMonthValue(); // Month is 1-based
        int day = dateTime.getDayOfMonth();

        // Return the year, month, and day
        return new int[] { year, month, day };
    }
}
