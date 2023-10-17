package com.example.demo.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class StorageService {

    @Value("${application.bucket.name}")
    private String bucketName;
    @Value("${smtp.host}")
    private String getSmtpHost;
    @Value("${smtp.username}")
    private String getSmtpUser;
    @Value("${smtp.password}")
    private String getSmtpPass;


    @Autowired
    private AmazonS3 s3Client;

    private Map<String, String> pendingQuotes = new HashMap<>();


    public String uploadFile(MultipartFile file) {
        File fileObj = convertMultiPartFileToFile(file);
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        s3Client.putObject(new PutObjectRequest(bucketName, fileName, fileObj));
        fileObj.delete();
        return "File uploaded : " + fileName;
    }


    public byte[] downloadFile(String fileName) {
        S3Object s3Object = s3Client.getObject(bucketName, fileName);
        S3ObjectInputStream inputStream = s3Object.getObjectContent();
        try {
            byte[] content = IOUtils.toByteArray(inputStream);
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
//
//    public String addQuote(String quote) {
//        String fileName = "quotes.json";
//        try {
//            // Fetch the JSON file from S3 bucket.
//            byte[] content = downloadFile(fileName);
//            JSONArray jsonArray;
//            // Parse the content of the file.
//            if (content != null && content.length > 0) {
//                String existingContent = new String(content, StandardCharsets.UTF_8);
//                jsonArray = new JSONArray(existingContent);
//            } else {
//                jsonArray = new JSONArray();
//            }
//            // Add the new quote.
//            jsonArray.put(new JSONObject().put("quote", quote));
//
//            // Convert the updated JSON array to byte array.
//            byte[] newContent = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
//
//            // Upload the updated file back to S3 bucket.
//            s3Client.putObject(new PutObjectRequest(bucketName, fileName, new ByteArrayInputStream(newContent), new ObjectMetadata()));
//
//            return "Quote added successfully";
//        }  catch (AmazonS3Exception e) {
//            // Log the detailed error message and request information
//            System.err.println("Error accessing S3. Request ID: " + e.getRequestId() + " Error Code: " + e.getErrorCode());
//            // Rethrow the exception to be handled by the calling method or global exception handler
//            throw new RuntimeException("Error adding quote", e);
//        }
//    }
//


    public String deleteFile(String fileName) {
        s3Client.deleteObject(bucketName, fileName);
        return fileName + " removed ...";
    }




    private String generateUniqueToken() {
        return UUID.randomUUID().toString();
    }


    public String submitQuoteForApproval(String quote) {
        try {
            // Step 1: Generate a unique token for this quote
            String token = generateUniqueToken();

            // Optional: Save this token and the associated quote to a database or temporary storage
            // so that when the email link is clicked, you can look up the associated quote.

            // Save this token and the associated quote in the pendingQuotes map
            pendingQuotes.put(token, quote);

            // Step 2: Send an email for approval with the token embedded in the links
            sendEmailWithJavaMail(quote, token);

            return "Approval email sent successfully";
        } catch (Exception e) {
            System.err.println("Error submitting quote for approval: " + e.getMessage());
            throw new RuntimeException("Error submitting quote for approval", e);
        }
    }




    private void sendEmailWithJavaMail(String quote, String token) {
        final String smtpHost = getSmtpHost;
        final String fromEmail = "jaron.111@hotmail.com";
        final String smtpUsername = getSmtpUser;
        final String smtpPassword = getSmtpPass;

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");  // Enable authentication
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", smtpHost);
        properties.put("mail.smtp.port", "587");

        // Use an Authenticator to supply the SMTP credentials
        Authenticator authenticator = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        };
        Session session = Session.getInstance(properties, authenticator);

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("jaron.111@hotmail.com"));
            message.setSubject("New Quote Submission");

            String approveLink = "https://wulal-886ecc4c7ff3.herokuapp.com/file/approve?token=" + token;
            String rejectLink = "https://wulal-886ecc4c7ff3.herokuapp.com/file/reject?token=" + token;

            String emailBody = "New Quote: " + quote + "\n\n" +
                    "Approve: " + approveLink + "\n" +
                    "Reject: " + rejectLink;

            message.setText(emailBody);

            // Send the email
            Transport.send(message, smtpUsername, smtpPassword);

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }



    public String approveQuote(String token) {
        if (pendingQuotes.containsKey(token)) {
            String approvedQuote = pendingQuotes.remove(token);
            return addQuoteToStorage(approvedQuote);
        }
        return "Invalid token or already processed";
    }

    public String rejectQuote(String token) {
        if (pendingQuotes.containsKey(token)) {
            pendingQuotes.remove(token);
            return "Quote rejected successfully";
        }
        return "Invalid token or already processed";
    }


    private String addQuoteToStorage(String quote) {
        String fileName = "quotes.json";
        try {
            byte[] content = downloadFile(fileName);
            JSONArray jsonArray;

            if (content != null && content.length > 0) {
                String existingContent = new String(content, StandardCharsets.UTF_8);
                jsonArray = new JSONArray(existingContent);
            } else {
                jsonArray = new JSONArray();
            }

            // This loop ensures that the quotes have the correct ids
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                obj.put("id", String.valueOf(i + 1));
            }

            // Create new quote object
            JSONObject newQuote = new JSONObject();
            newQuote.put("id", String.valueOf(jsonArray.length() + 1));
            newQuote.put("quote", quote);
            jsonArray.put(newQuote);

            byte[] newContent = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(new PutObjectRequest(bucketName, fileName, new ByteArrayInputStream(newContent), new ObjectMetadata()));

            return "Quote added successfully";
        } catch (AmazonS3Exception e) {
            System.err.println("Error accessing S3. Request ID: " + e.getRequestId() + " Error Code: " + e.getErrorCode());
            throw new RuntimeException("Error adding quote", e);
        } catch (JSONException e) {
            System.err.println("Error processing JSON.");
            throw new RuntimeException("Error processing JSON", e);
        }
    }





    private File convertMultiPartFileToFile(MultipartFile file) {
        File convertedFile = new File(file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convertedFile)) {
            fos.write(file.getBytes());
        } catch (IOException e) {
            log.error("Error converting multipartFile to file", e);
        }
        return convertedFile;
    }

    public List<String> listFilesInFolder(String folder) {
        ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(folder);
        ListObjectsV2Result result = s3Client.listObjectsV2(request);

        List<String> filenames = new ArrayList<>();
        for (S3ObjectSummary summary : result.getObjectSummaries()) {
            filenames.add(summary.getKey());
        }
        return filenames;
    }

}