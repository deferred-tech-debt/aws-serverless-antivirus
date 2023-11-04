package com.serverless.lambda.exceptions;

import com.serverless.lambda.enums.Status;

public class ClamAVScannerException extends RuntimeException {

    private String bucket;
    private String key;
    private String source;
    private Status status;

    public ClamAVScannerException(String message, String bucket, String key, String source, Status status) {
        super(message);
        this.bucket = bucket;
        this.key = key;
        this.source = source;
        this.status = status;
    }

    public ClamAVScannerException(Throwable cause, String bucket, String key, String source, Status status) {
        super(cause);
        this.bucket = bucket;
        this.key = key;
        this.source = source;
        this.status = status;
    }

    @Override
    public String toString() {
        return "ClamAVScannerException{" +
                "bucket='" + bucket + '\'' +
                ", key='" + key + '\'' +
                ", source='" + source + '\'' +
                ", status=" + status +
                "} " + super.toString();
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public Status getStatus() {
        return status;
    }
}
