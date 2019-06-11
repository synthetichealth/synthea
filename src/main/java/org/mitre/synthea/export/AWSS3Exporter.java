package org.mitre.synthea.export;

import java.lang.UnsupportedOperationException;
import java.net.URI;

public class AWSS3Exporter {
    /**
   * Write a new file with the given contents.
   * 
   * @param fileUri     S3 Uri to the new file.
   * @param contents The contents of the file.
   */
    public static void writeNewFile(URI fileUri, String contents) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
   * Append contents to the end of a file.
   * 
   * @param uri     S3 Uri to the new file.
   * @param contents The contents of the file.
   */
    public static synchronized void appendToFile(URI fileUri, String contents) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}