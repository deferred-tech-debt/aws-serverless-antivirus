package com.serverless.lambda.scanner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.util.IOUtils;
import com.serverless.lambda.ClamAVException;
import com.serverless.lambda.CommandExecutionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public class Handler implements RequestHandler<ScheduledEvent, Boolean> {
    private static final Logger LOG = LogManager.getLogger(Handler.class);
    private static final String EFS_MOUNT_PATH = "EFS_MOUNT_PATH";
    private static final String EFS_DEF_PATH = "EFS_DEF_PATH";

    public Handler() {
    }

    @Override
    public Boolean handleRequest(ScheduledEvent scheduledEvent, Context context) {
        try {
            String mountPath = System.getenv(EFS_MOUNT_PATH);
            logInfo("Found EFS Mount Path {} by Env Key {}", mountPath, EFS_MOUNT_PATH);
            String definitionsPath = mountPath + "/" + System.getenv(EFS_DEF_PATH);
            logInfo("Found EFS Clam AV Definition Path {} by Env Key {}", definitionsPath, EFS_DEF_PATH);

            File definitionFile = new File(definitionsPath);
            if(! definitionFile.exists()) {
                logInfo("Directory {} doesn't exists, creating now...", definitionsPath);
                definitionFile.mkdirs();
                logInfo("Directory {} created", definitionsPath);
            }

            Path freshclamPath = Path.of("/tmp/freshclam.conf");
            if(!freshclamPath.toFile().exists()) {
                Files.createFile(freshclamPath);
            }
            Files.copy(Path.of("freshclam.conf"), freshclamPath, StandardCopyOption.REPLACE_EXISTING);

            String command = "whoami";
            CommandExecutionResponse executionResponse = executeCommand(command, context.getRemainingTimeInMillis());
            String currentUser = executionResponse.getMessage();

            freshClamUpdate(definitionsPath, context, currentUser);
            logInfo("Fresh Clam Definition updated......");
            return true;
        } catch (Exception e) {
            LOG.error("Error while updating freshClam Definitions. ", e);
            throw new ClamAVException(e);
        }
    }

    private void freshClamUpdate(String definitionsPath, Context context, String currentUser) {

        String conf = "/tmp/freshclam.conf";
        String command = "freshclam --config-file=" + conf + " --stdout -u " + currentUser + " --datadir=" + definitionsPath;
        CommandExecutionResponse executionResponse = executeCommand(command, context.getRemainingTimeInMillis());
        logInfo("command '{}' returns code {}, msg {} & error msg {}", command, executionResponse.getReturnCode(), executionResponse.getMessage(), executionResponse.getErrorMessage());
        if (executionResponse.getReturnCode() != 0 || (executionResponse.getMessage() != null && executionResponse.getMessage().toUpperCase().contains("ERROR")) || Strings.isNotBlank(executionResponse.getErrorMessage())) {
            throw new ClamAVException("FreshClam exited with error : " + executionResponse);
        }
    }

    private CommandExecutionResponse executeCommand(String command, int timeout) {
        try {
            LOG.info("Executing command with timout " + timeout + " milli second..... " + command);
            Process process = Runtime.getRuntime().exec(command);
            if (process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                return new CommandExecutionResponse(process.exitValue(), IOUtils.toString(process.getInputStream()), IOUtils.toString(process.getErrorStream()));
            } else {
                String timeOutMsg = String.format("TimeOut within %d millisecond, executing command [%s]", timeout, command);
                process.destroy();
                return new CommandExecutionResponse(Integer.MIN_VALUE, null, timeOutMsg);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error(String.format("Error while executing command [%s]", command), e);
            throw new RuntimeException(e);
        }
    }

    private void logInfo(String msg, Object... params) {
        if (LOG.isInfoEnabled()) {
            LOG.info(msg, params);
        }
    }
}