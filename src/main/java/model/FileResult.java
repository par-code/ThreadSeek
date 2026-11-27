package model;

import java.time.Instant;

/** Details of a file that matched the search filters. */
public class FileResult {
    private final String fileName;
    private final String filePath;
    private final long fileSize;
    private final long lastModified;

    public FileResult(String fileName, String filePath, long fileSize, long lastModified) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
    }

    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public long getFileSize() { return fileSize; }
    public long getLastModified() { return lastModified; }

    @Override
    public String toString() {
        return "File Name: " + fileName
                + "\nFile Path: " + filePath
                + "\nFile Size: " + fileSize + " bytes"
                + "\nLast Modified: " + Instant.ofEpochMilli(lastModified);
    }
}
