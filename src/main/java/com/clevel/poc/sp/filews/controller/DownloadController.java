package com.clevel.poc.sp.filews.controller;

import com.clevel.poc.sp.filews.util.FileTracker;
import com.clevel.poc.sp.filews.util.SftpDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/download")
public class DownloadController {
    private static Logger log = LoggerFactory.getLogger(DownloadController.class);

    private final String downloadDir = "/opt/clevel/tmp/dna-files"; // or any temp folder

    @GetMapping
    public ResponseEntity<Map<String, Object>> prepareDownload(@RequestParam String project) throws Exception {
        // Config
        File localDirFile = new File(downloadDir);
        if (!localDirFile.exists()) localDirFile.mkdirs();

        // Download files from SFTP
        List<String> fileNames = SftpDownloader.downloadProjectFiles(project, downloadDir);
        log.debug("Downloaded files: {}", fileNames);

        // Register for cleanup
        for (String fileName : fileNames) {
            FileTracker.register(fileName);
        }

        // Build download URLs
        List<String> urls = fileNames.stream()
                .map(fileName -> "http://localhost:8080/filews/download/file/" + fileName)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("files", urls);
        response.put("count", urls.size());
        response.put("expiresInMinutes", 30);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) throws IOException {
        // Prevent path traversal and empty filename
        if (fileName == null || fileName.trim().isEmpty() || fileName.contains("..")) {
            return ResponseEntity.badRequest().body(null);
        }

        File file = new File(downloadDir + "/" + fileName);

        // Make sure it's a file (not a directory)
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmDownload(@RequestBody List<String> fileNames) {
        for (String fileName : fileNames) {
            try {
                // 1. Delete local temp file
                File file = new File(downloadDir + "/" + fileName);
                if (file.exists()) {
                    file.delete();
                    FileTracker.unregister(fileName);
                }

                // 2. Delete from SFTP server
                SftpDownloader.deleteRemoteFile(fileName);
            } catch (Exception ex) {
                // Log and continue with other files
                System.err.println("Failed to delete file " + fileName + ": " + ex.getMessage());
            }
        }

        return ResponseEntity.ok("All files deleted (local and SFTP)");
    }


}
