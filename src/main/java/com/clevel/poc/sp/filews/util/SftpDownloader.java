package com.clevel.poc.sp.filews.util;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SftpDownloader {
    private static Logger log = LoggerFactory.getLogger(SftpDownloader.class);

    private static final String SFTP_HOST = "10.9.9.84";
    private static final int SFTP_PORT = 22;
    private static final String SFTP_USER = "arsusr";
    private static final String SFTP_PASS = "xqwlqlvlnqmfzucSkiccXecVryfZ48zr5QcfxK";
    private static final String REMOTE_DIR = "/opt/clevel/datafile/REPORT/DOC/OUTBOX";
    private static final String ARCHIVE_DIR = "/opt/clevel/datafile/REPORT/DOC/OUTBOX/ARCHIVE";


    public static List<String> downloadProjectFiles(String project, String localDir) throws Exception {
        List<String> downloadedFileNames = new ArrayList<>();
        Session session = null;
        ChannelSftp sftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
            session.setPassword(SFTP_PASS);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> files = sftp.ls(REMOTE_DIR);
            log.debug("Files in remote directory {}: {}", REMOTE_DIR, files);

            for (ChannelSftp.LsEntry entry : files) {
                String fileName = entry.getFilename();
                if (entry.getAttrs().isDir() || ".".equals(fileName) || "..".equals(fileName)) {
                    continue; // Skip directories and special entries
                }

                // Only process files that match the project pattern
//                if (!fileName.contains(project)) {
//                    continue;
//                }

                String remoteFilePath = REMOTE_DIR + "/" + fileName;
                String localFilePath = localDir + File.separator + fileName;

                try (OutputStream output = new FileOutputStream(localFilePath)) {
                    sftp.get(remoteFilePath, output);
                } catch (FileNotFoundException e) {
                    log.error("File not found: {}", localFilePath, e);
                    throw e;
                } catch (IOException e) {
                    log.error("I/O error while writing to file: {}", localFilePath, e);
                    throw e;
                }

                // After download, move file to archive directory on SFTP
                String remoteArchivePath = ARCHIVE_DIR + "/" + fileName;
                try {
                    sftp.rename(remoteFilePath, remoteArchivePath);
                    log.info("Moved file to archive: {}", remoteArchivePath);
                } catch (SftpException e) {
                    log.error("Failed to move file to archive: {}", remoteArchivePath, e);
                    throw e;
                }

                downloadedFileNames.add(fileName);
            }
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        return downloadedFileNames;
    }


    public static void deleteRemoteFile(String remoteFileName) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
        session.setPassword(SFTP_PASS);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftp = (ChannelSftp) channel;

        try {
            String fullPath = REMOTE_DIR + "/" + remoteFileName;
            sftp.rm(fullPath);
            log.debug("Deleted SFTP file: {}", fullPath);
        } catch (SftpException e) {
            log.error("Failed to delete remote file: {}", remoteFileName, e);
            throw e;
        } finally {
            sftp.disconnect();
            session.disconnect();
        }
    }
}