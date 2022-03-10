package thefoorbarfighters.gsengage.controllers;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ApiConnectionClient {

    private Map<String, Object> mapResponse;

    private Path fileResponse;

    public Map<String, Object> getMapResponse() {
        return mapResponse;
    }

    public Path getFileResponse() {
        return fileResponse;
    }

    public void sendGet(String url) throws Exception {
        URI uri = new URI(url);
        RestTemplate restTemplate = new RestTemplate();
        mapResponse = restTemplate.getForObject(uri, Map.class);
    }

    public void sendPost(String url, Object rawData, boolean parseFile) throws Exception {
        URI uri = new URI(url);
        RestTemplate restTemplate = new RestTemplate();
        if (parseFile) {
            byte[] reportBytes = restTemplate.postForObject(uri, rawData, byte[].class);
            Path tempFile = Files.createTempFile(null, null);
            Files.write(tempFile, reportBytes);
            fileResponse = tempFile;
        } else {
            mapResponse = restTemplate.postForObject(uri, rawData, Map.class);
        }
    }
}