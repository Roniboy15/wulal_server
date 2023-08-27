package com.example.demo.controller;


import com.example.demo.service.StorageService;
import org.json.JSONArray;
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
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "https://a-lions-roar.onrender.com"}) // This allows requests from the React app running on localhost:3000
@RequestMapping("/file")
@Slf4j

public class StorageController {

    @Autowired
    private StorageService service;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam(value = "file") MultipartFile file) {
        return new ResponseEntity<>(service.uploadFile(file), HttpStatus.OK);
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<String> downloadFile(@PathVariable String fileName,
                                               @RequestParam(required = false) String folder) {
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
        try {
            List<String> fileNames = service.listFilesInFolder(folder);

            JSONArray jsonArray = new JSONArray();

            for (String fileName : fileNames) {
                byte[] data = service.downloadFile(fileName);
                JSONObject jsonFileContent = new JSONObject(new String(data, StandardCharsets.UTF_8));
                jsonArray.put(jsonFileContent);
            }

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-type", "application/json")
                    .body(jsonArray.toString());
        } catch (RuntimeException e) {
            log.error("Error while fetching the files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to fetch the files\"}");
        }
    }



    @DeleteMapping("/delete/{fileName}")
    public ResponseEntity<String> deleteFile(@PathVariable String fileName) {
        return new ResponseEntity<>(service.deleteFile(fileName), HttpStatus.OK);
    }
}