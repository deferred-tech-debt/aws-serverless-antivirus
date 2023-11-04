package com.serverless.lambda;

public class ClamAVException extends RuntimeException {

    public ClamAVException() {
    }

    public ClamAVException(String message) {
        super(message);
    }

    public ClamAVException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClamAVException(Throwable cause) {
        super(cause);
    }
}
