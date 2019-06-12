package org.mitre.synthea.export;

import java.lang.UnsupportedOperationException;
import java.net.URI;
import java.net.URISyntaxException;

public class AWSS3Exporter {
    /**
     * Write a new file with the given contents.
     * 
     * @param fileUri  S3 Uri to the new file.
     * @param contents The contents of the file.
     */
    public static void writeNewFile(String bucketURI, String folderName, String fileName, String contents)
            throws UnsupportedOperationException {
        try {
            URI outBucketURI = new URI(bucketURI).resolve(folderName);
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
   * Append contents to the end of a file.
   * 
   * @param uri     S3 Uri to the new file.
   * @param contents The contents of the file.
   */
    public static synchronized void appendToFile(String bucketURI, String folderName, String fileName, String contents) throws UnsupportedOperationException {
        try {
            URI outBucketURI = new URI(bucketURI).resolve(folderName);
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }
}