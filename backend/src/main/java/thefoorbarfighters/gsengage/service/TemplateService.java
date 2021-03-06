package thefoorbarfighters.gsengage.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class TemplateService {

    @Autowired
    FileService fileService;

    @Autowired
    private AmazonS3 amazonS3;

    @Value("${s3.databucket.name}")
    private String s3DataBucketName;

    @Value("${s3.templatebucket.name}")
    private String s3TemplateBucketName;

    private static final Logger LOG = LoggerFactory.getLogger(TemplateService.class);

    private File convertMultiPartFileToFile(final MultipartFile multipartFile) {
        final File file = new File(multipartFile.getOriginalFilename());
        try (final FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(multipartFile.getBytes());
        } catch (IOException e) {
            LOG.error("Error {} occurred while converting the multipart file", e.getLocalizedMessage());
        }
        return file;
    }

    private Map<String, Object> createBaseResponse() {
        Map<String, Object> baseResponse = new HashMap<>();
        Map<String, Object> successCountResponse = new HashMap<>();
        Map<String, Object> failureCountResponse = new HashMap<>();
        successCountResponse.put("count", 0);
        failureCountResponse.put("count", 0);
        baseResponse.put("success", successCountResponse);
        baseResponse.put("failure", failureCountResponse);
        return baseResponse;
    }

    private Map<String, Object> jobSuccess(Map<String, Object> apiResponse) {
        Map<String, Object> tmpResponse = (Map<String, Object>) apiResponse.get("success");
        Integer newSuccessCount = ((Integer) tmpResponse.get("count")) + 1;
        tmpResponse.put("count", newSuccessCount);
        apiResponse.put("success", tmpResponse);
        return apiResponse;
    }

    private Map<String, Object> jobFail(Map<String, Object> apiResponse) {
        Map<String, Object> tmpResponse = (Map<String, Object>) apiResponse.get("failure");
        Integer newFailureCount = ((Integer) tmpResponse.get("count")) + 1;
        tmpResponse.put("count", newFailureCount);
        apiResponse.put("failure", tmpResponse);
        return apiResponse;
    }

    public Map<String, Object> uploadTemplate(Map<String, Object> rawData) {
        Map<String, Object> serviceResponse = createBaseResponse();
        Map<String, Object> correctValue = new HashMap<>();
        correctValue.put("data", "");
        correctValue.put("sum", false);

        // get new Template Name
        Map<String, Object> metadata = (Map<String, Object>) rawData.get("metadata");
        String defaultReportName = metadata.get("filename").toString();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss").format(new java.util.Date());
        String[] arr = defaultReportName.split("\\.");
        String templateName = arr[0] + "_" + timeStamp + ".json";

        try {
            Map<String, Object> compiled = (Map<String, Object>) rawData.get("compiled");
            //iterate through every key
            for (Map.Entry<String, Object> sheet : compiled.entrySet()) {
                List<List> tables = (List<List>) sheet.getValue();

                for (List<Map> tableMap : tables) {
                    for (Map<String, Object> table : tableMap) {
                        for (Map.Entry<String, Object> attribute : table.entrySet()) {
                            table.put(attribute.getKey(), correctValue);
                        }
                    }
                }
            }

            MultipartFile fileToUpload = null;
            ObjectMapper objmapper = new ObjectMapper();
            String stringReport = objmapper.writeValueAsString(compiled);
            byte[] byteReport = stringReport.getBytes();
            fileToUpload = new MockMultipartFile(templateName, templateName, ContentType.APPLICATION_JSON.toString(), byteReport);
            if (fileToUpload != null) {
                File file = convertMultiPartFileToFile(fileToUpload);
                final PutObjectRequest putObjectRequest = new PutObjectRequest(s3TemplateBucketName, templateName, file);
                amazonS3.putObject(putObjectRequest);
                Files.delete(file.toPath());
                Map<String, Object> tmpSuccessResponse = (Map<String, Object>) serviceResponse.get("success");
                serviceResponse.put("success", tmpSuccessResponse);
                jobSuccess(serviceResponse);
            }
        } catch (Exception e) {
            jobFail(serviceResponse);
            e.printStackTrace();
        }
        return serviceResponse;
    }

    public List<Map<String,Object>> getAllTemplates() {
        List<Map<String,Object>> serviceResponse = new ArrayList<>();

        ListObjectsV2Request req = new ListObjectsV2Request();
        req.setBucketName(fileService.getBucketName("template"));
        ListObjectsV2Result result;
        result = amazonS3.listObjectsV2(req);

        for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
            String fullName = objectSummary.getKey();
            Map<String, Object> objData = new HashMap<>();

            try {
                String fileName = fullName.substring(0, fullName.lastIndexOf("."));
                InputStreamResource s3Data = new InputStreamResource(fileService.downloadFile("template", fullName));
                InputStream s3DataStream = s3Data.getInputStream();
                ObjectMapper objmapper = new ObjectMapper();
                Map<String, Object> templateData = objmapper.readValue(s3DataStream, Map.class);
                Date lastModified = objectSummary.getLastModified();
                objData.put("fileName", fileName);
                objData.put("templateData", templateData);
                objData.put("lastModified", lastModified);
                serviceResponse.add(objData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return serviceResponse;
    }

    public Map<String, Object> getTemplate(String templateName) {
        Map<String, Object> serviceResponse = createBaseResponse();
        try {
            InputStreamResource s3Data = new InputStreamResource(fileService.downloadFile("template", templateName));
            InputStream s3DataStream = s3Data.getInputStream();
            ObjectMapper objmapper = new ObjectMapper();
            Map<String, Object> templateData = objmapper.readValue(s3DataStream, Map.class);

            Map<String, Object> tmpSuccessResponse = (Map<String, Object>) serviceResponse.get("success");
            tmpSuccessResponse.put(templateName, templateData);
            jobSuccess(serviceResponse);
        } catch (Exception e) {
            jobFail(serviceResponse);
            e.printStackTrace();
        }
        return serviceResponse;
    }
}