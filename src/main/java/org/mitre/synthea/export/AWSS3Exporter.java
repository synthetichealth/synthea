package org.mitre.synthea.export;

import java.lang.UnsupportedOperationException;
import java.net.URI;
import java.net.URISyntaxException;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class AWSS3Exporter {
    /**
     * Write a new file with the given contents.
     * 
     * @param fileUri  S3 Uri to the new file.
     * @param contents The contents of the file.
     */
    public static void writeNewFile(String folderName, String fileName, String contents) {
        try {
            String bucketURI = Config.get("exporter.awsS3.uri");
            URI outBucketURI = new URI(bucketURI).resolve(folderName);
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
   * Append contents to the end of a file.
   * 
   * @param uri     S3 Uri to the new file.
   * @param contents The contents of the file.
   */
    public static synchronized void appendToFile(String folderName, String fileName, String contents) {
        try {
            String bucketURI = Config.get("exporter.awsS3.uri");
            URI outBucketURI = new URI(bucketURI).resolve(folderName);
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}