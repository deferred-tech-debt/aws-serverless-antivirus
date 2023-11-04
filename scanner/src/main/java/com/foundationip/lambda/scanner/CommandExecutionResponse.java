package com.serverless.lambda.scanner;

public class CommandExecutionResponse {

    private String message;
    private int returnCode;

    private String errorMessage;

    public CommandExecutionResponse(int returnCode, String message, String errorMessage) {
        this.message = message;
        this.returnCode = returnCode;
        this.errorMessage = errorMessage;
    }

    public String getMessage() {
        return message;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "CommandExecutionResponse{" +
                "message='" + message + '\'' +
                ", returnCode=" + returnCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
