package com.serverless.lambda.exceptions;

import com.serverless.lambda.enums.Status;

public class FileTooLargeException extends ClamAVScannerException {

    public FileTooLargeException(String message, String bucket, String key, String source, Status status) {
        super(message, bucket, key, source, status);
    }
}
