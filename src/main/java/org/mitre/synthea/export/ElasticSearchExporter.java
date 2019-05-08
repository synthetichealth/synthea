package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.parser.IParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * Exports all the fhir json files in the out folder into ElasticSearch with dynamic mapping.
 */
public class ElasticSearchExporter {

    private FhirContext ctx = null;
    private RestHighLevelClient client = null;

    private static ElasticSearchExporter SINGLETON = null;
    private String indexName = null;
    private String indexType = null;
    private HashMap<String,Integer> report;

    private ElasticSearchExporter() {
        String esurl = Config.get("exporter.elastic.url");
        String esprotocol = Config.get("exporter.elastic.protocol");
        Integer esport = Integer.parseInt(Config.get("exporter.elastic.port"));
        String esuser = Config.get("exporter.elastic.user");
        String espassword = Config.get("exporter.elastic.password");


        indexName = Config.get("exporter.elastic.indexname");
        indexType = Config.get("exporter.elastic.indextype");
        ctx = FhirContext.forDstu2();
        report = new HashMap<>();
        RestClientBuilder builder = null;
        if (esuser != null && espassword != null){
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(esuser, espassword));
            builder = RestClient.builder(new HttpHost(esurl, esport, esprotocol))
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        }
                    });
        }else{
            builder = RestClient.builder(new HttpHost(esurl, esport, esprotocol));
        }


        client = new RestHighLevelClient(builder.build());
    }

    public static ElasticSearchExporter getInstance() {
        if (SINGLETON == null) {
            SINGLETON = new ElasticSearchExporter();
            return SINGLETON;
        } else {
            return SINGLETON;
        }
    }

    public HashMap<String,Integer> getReport(){
        return this.report;
    }

    public void export() {
        String baseDirectory = Config.get("exporter.baseDirectory");

        try (Stream<Path> filePathStream = Files.walk(Paths.get(baseDirectory))) {

            filePathStream.forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {
                    System.out.println("file getting indexed in elasticsearch: " + filePath);
                    String content = null;
                    try {
                        content = new String(Files.readAllBytes(filePath));


                        final IParser parser = ctx.newJsonParser();
                        parser.parseResource(content);
                        Bundle bundle = parser.parseResource(Bundle.class, content);
                        List<Bundle.Entry> entries = bundle.getEntry();
                        int count = 0;
                        for (Bundle.Entry entry : entries) {
                            IResource resource = entry.getResource();
                            System.out.println(resource.getResourceName());
                            if (resource instanceof Bundle || resource == null) {
                                continue;//don't want to insert bundles
                            }
                            String resourceName = resource.getResourceName().toLowerCase();
                            System.out.println("FHIR ResourceName:"+resourceName);

                            String id = null;
                            try {
                                id = resource.getId().getValueAsString();
                            } catch (Exception eee) {
                                //don't really care
                                System.out.println("Cannot find resource id. Skip indexing");
                                continue;
                            }


                            String json = parser.encodeResourceToString(resource);

                            IndexRequest request = new IndexRequest(
                                    indexName+resourceName,
                                    indexType,
                                    id);
                            if ("true".equalsIgnoreCase(Config.get("exporter.elastic.isunittest"))){
                                count++;
                                continue;
                            }
                            request.source(json, XContentType.JSON);
                            IndexResponse indexResponse = client.index(request);
                            String index = indexResponse.getIndex();
                            String type = indexResponse.getType();
                            id = indexResponse.getId();
                            long version = indexResponse.getVersion();
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                                count++;
                                System.out.println(resource.getResourceName() + " with docid::" + id + " created successfully. count="+count);
                            } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                                count++;
                                System.out.println(resource.getResourceName() + " with docid::" + id + " updated successfully. count="+count);
                            } else {
                                System.out.println(resource.getResourceName() + " with docid::" + id + " failed to be indexed.");
                            }


                        }//end for

                        System.out.println("TOTAL documents indexed for file:"+filePath+" is "+count);
                        report.put(filePath.toString(),count);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
