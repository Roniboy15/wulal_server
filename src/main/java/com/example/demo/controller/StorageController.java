package com.example.demo.controller;


import com.example.demo.service.StorageService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000") // This allows requests from the React app running on localhost:3000
@RequestMapping("/file")
@Slf4j
public class StorageController {

    private final Bucket bucket;

    public StorageController() {
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }
    @Autowired
    private StorageService service;

//    @PostMapping("/upload")
//    public ResponseEntity<String> uploadFile(@RequestParam(value = "file") MultipartFile file) {
//        return new ResponseEntity<>(service.uploadFile(file), HttpStatus.OK);
//    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<String> downloadFile(@PathVariable String fileName,
                                               @RequestParam(required = false) String folder) {
        if (!bucket.tryConsume(1)) {
            log.warn("Too many requests. Rejecting request for folder: {}", folder);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\": \"Too many requests\"}");
        }
        try {
            String filePath;
            if (folder != null && !folder.isEmpty()) {
                filePath = folder + "/" + fileName;
            } else {
                filePath = fileName;
            }
            byte[] data = service.downloadFile(filePath);
            String jsonData = new String(data, StandardCharsets.UTF_8);

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-type", "application/json")
                    .body(jsonData);
        } catch (RuntimeException e) {
            log.error("Error while fetching the file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to fetch the file\"}");
        }
    }


    @GetMapping("/fetch")
    public ResponseEntity<String> fetchFilesFromFolder(@RequestParam(required = true) String folder) {

        String currentFile = ""; // This will hold the name of the file currently being processed

        if (!bucket.tryConsume(1)) {
            log.warn("Too many requests. Rejecting request for folder: {}", folder);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\": \"Too many requests\"}");
        }

        try {
            List<String> fileNames = service.listFilesInFolder(folder);

            JSONArray jsonArray = new JSONArray();
            for (String fileName : fileNames) {
                currentFile = fileName; // Update the currentFile variable
                byte[] data = service.downloadFile(fileName);
                String fileContent = new String(data, StandardCharsets.UTF_8);
                try {
                    JSONObject jsonFileContent = new JSONObject(fileContent);
                    jsonArray.put(jsonFileContent);
                } catch (JSONException je) {
                    log.warn("Invalid JSON content in file: {}. Skipping this file.", fileName);
                }
            }

            log.info("Successfully fetched files from folder: {}", folder);
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-type", "application/json")
                    .body(jsonArray.toString());
        } catch (RuntimeException e) {
            String fileName = Paths.get(currentFile).getFileName().toString();
            log.error("Error processing file '{}'. Details: {}", fileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to fetch the files\"}");
        }
    }


    @PostMapping("/addquote")
    public ResponseEntity<String> submitQuoteForApproval(@RequestBody String quote) {

        // Check if we can consume a token
        if (!bucket.tryConsume(1)) {
            log.warn("Too many requests. Rejecting request for adding a quote.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\": \"Too many requests\"}");
        }

        try {
            return new ResponseEntity<>(service.submitQuoteForApproval(quote), HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Error while adding the quote", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to add the quote\"}");
        }
    }


    @RequestMapping("/approve")
    public ResponseEntity<String> approve(@RequestParam String token) {
        String result = service.approveQuote(token);
        return ResponseEntity.ok(result);
    }

    @RequestMapping("/reject")
    public ResponseEntity<String> reject(@RequestParam String token) {
        String result = service.rejectQuote(token);
        return ResponseEntity.ok(result);
    }



//    @DeleteMapping("/delete/{fileName}")
//    public ResponseEntity<String> deleteFile(@PathVariable String fileName) {
//        return new ResponseEntity<>(service.deleteFile(fileName), HttpStatus.OK);
//    }
}