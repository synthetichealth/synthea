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

        String basePnr = dateOfBirth + individualNumber;

        int checksum = calculateLuhnChecksum(basePnr);

        // Return the complete personnummer
        return dateOfBirth + "-" + individualNumber;
    }

    private static String generateLastfour(String gender) {
        int firstTwoDigits = random.nextInt(100); // Generate a number between 0 and 99
        int thirdDigit;

        // Determine the third digit based on gender
        if (gender.equals("M")) {
            thirdDigit = 1 + random.nextInt(5) * 2 - 1; // Odd number for male
        } else {
            thirdDigit = 0 + random.nextInt(5) * 2; // Even number for female
        }

        // Generate the last digit
        int lastDigit = random.nextInt(10); // Generate a number between 0 and 9

        // Combine and format as a 4-character string
        return String.format("%02d%d%d", firstTwoDigits, thirdDigit, lastDigit).substring(0, 4);
    }

    // Private helper method to calculate the Luhn checksum
    private static int calculateLuhnChecksum(String basePnr) {
        int sum = 0;
        for (int i = 0; i < basePnr.length(); i++) {
            int digit = Character.getNumericValue(basePnr.charAt(i));
            if (i % 2 == 0) { // Double every second digit (starting from index 0)
                digit *= 2;
                if (digit > 9) {
                    digit -= 9; // Subtract 9 from any results over 9
                }
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10; // Calculate checksum
    }

    // Private helper method to get the number of days in a given month and year
    private static int getDaysInMonth(int year, int month) {
        switch (month) {
            case 2: // February, check for leap year
                return (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11: // April, June, September, November
                return 30;
            default: // All other months have 31 days
                return 31;
        }
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
