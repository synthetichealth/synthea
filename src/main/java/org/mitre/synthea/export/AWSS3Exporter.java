package org.mitre.synthea.export;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.internal.TransferManagerUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;

public class AWSS3Exporter {

    private static AmazonS3 client = AmazonS3ClientBuilder
            .standard()
            .withCredentials(new ProfileCredentialsProvider(Config.get("exporter.aws.profile_name")))
            .withRegion(Config.get("exporter.aws.bucket_region"))
            .build();


    /**
     *
     * @param person
     * @param folderName
     * @param fileName
     * @param contents
     * @throws UnsupportedOperationException
     */
    public static void writeNewFile(String folderName, String fileName, String contents) {
        client.putObject(Config.get("exporter.aws.bucket_name"), fileName, contents);
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