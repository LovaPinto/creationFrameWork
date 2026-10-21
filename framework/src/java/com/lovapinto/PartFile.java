package com.lovapinto;

import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;

public class PartFile {
    private String name;
    private String fileName;
    private String contentType;
    private long size;
    private byte[] bytes;
    private Part part;

    public PartFile() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public Part getPart() {
        return part;
    }

    public void setPart(Part part) {
        this.part = part;
    }

    public InputStream getInputStream() throws IOException {
        if (part != null) {
            return part.getInputStream();
        }
        return null;
    }
}
