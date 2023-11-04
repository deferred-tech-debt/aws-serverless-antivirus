package com.serverless.lambda.scanner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectTaggingRequest;
import com.amazonaws.services.s3.model.GetObjectTaggingResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.SetObjectTaggingRequest;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.serverless.lambda.enums.Status;
import com.serverless.lambda.exceptions.ClamAVScannerException;
import com.serverless.lambda.exceptions.FileTooLargeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

public class Handler implements RequestHandler<SQSEvent, Status> {
    private static final Logger LOG = LogManager.getLogger(Handler.class);
    private static final long MAX_BYTES = 4000000000L; // 4GB
    private static final String SERVERLESS_CLAM_SCAN = "serverless-clam-scan";
    private static final String SCAN_STATUS = "scan-status";
    private static final String EFS_MOUNT_PATH = "EFS_MOUNT_PATH";
    private static final String EFS_DEF_PATH = "EFS_DEF_PATH";
    private static final String PRODUCTION_BUCKET_NAME = "PRODUCTION_BUCKET";
    private static final String QUARANTINE_BUCKET_NAME = "QUARANTINE_BUCKET";
    private static final String CLAM_AV_PARAMETERS = "CLAM_AV_PARAMETERS";

    public Handler() {
    }

    @Override
    public Status handleRequest(SQSEvent sqsEvent, Context context) {
        String srcKey = null;
        String srcBucket = null;
        String payloadPath = null;
        String tmpPath = null;
        try {
            logDebug("SQS Event Records {}", sqsEvent.getRecords());
            logInfo("S3 Notification JSON {}", sqsEvent.getRecords().get(0).getBody());
            S3EventNotification s3EventNotification = S3EventNotification.parseJson(sqsEvent.getRecords().get(0).getBody());
            List<S3EventNotification.S3EventNotificationRecord> records = s3EventNotification.getRecords();
            S3EventNotification.S3EventNotificationRecord record = records.get(0); // scan only one file in one execution context
            srcBucket = record.getS3().getBucket().getName();
            logInfo("Source Bucket {}", srcBucket);

            String objectVersionId = record.getS3().getObject().getVersionId();

            // Object key may have spaces or unicode non-ASCII characters.
            srcKey = record.getS3().getObject().getUrlDecodedKey();
            logInfo("Source Object Key {}", srcKey);

            AmazonS3 amazonS3 = AmazonS3Client.builder().withRegion(record.getAwsRegion()).build();
            GetObjectTaggingResult objectTaggingResult = amazonS3.getObjectTagging(new GetObjectTaggingRequest(srcBucket, srcKey));
            List<Tag> tags = objectTaggingResult.getTagSet();
            if(! CollectionUtils.isNullOrEmpty(tags)) {
                Optional<Tag> scanStatusTag = tags.stream().filter(tag -> tag.getKey().equals(SCAN_STATUS)).findFirst();
                if(scanStatusTag.isPresent()) {
                    Status existingScanStatus = Status.valueOf(scanStatusTag.get().getValue());
                    logInfo("File {} already scanned and it was found {} in last scan", srcKey, existingScanStatus.name());
                    if(existingScanStatus == Status.CLEAN) {
                        moveToProduction(amazonS3, srcBucket, srcKey, objectVersionId);
                        return Status.PRE_SCANNED_CLEAN;
                    } else if(existingScanStatus == Status.INFECTED) {
                        quarantine(amazonS3, srcBucket, srcKey, objectVersionId);
                        return existingScanStatus;
                    }
                }
            }

            String objectFullIdentifier = srcBucket + "/" + srcKey;
            logDebug("S3 Full Object Identifier {}", objectFullIdentifier);
            String mountPath = System.getenv(EFS_MOUNT_PATH);
            logDebug("Found EFS Mount Path {} by Env Key {}", mountPath, EFS_MOUNT_PATH);
            String definitionsPath = mountPath + "/" + System.getenv(EFS_DEF_PATH);
            logDebug("Found EFS Clam AV Definition Path {} by Env Key {}", definitionsPath, EFS_DEF_PATH);
            payloadPath = mountPath + "/" + context.getAwsRequestId();
            logInfo("Payload Path {}", payloadPath);
            tmpPath = payloadPath + "-tmp";
            logInfo("Payload Temp Path {}", tmpPath);

            logInfo("Creating parent directories if not exist {}/{}", payloadPath, srcKey);
            createDirIfNotExist(srcBucket, srcKey, payloadPath);
            logInfo("Creating parent directories if not exist {}/{}", tmpPath, srcKey);
            createDirIfNotExist(srcBucket, srcKey, tmpPath);

            String scanFilePath = payloadPath+ "/" + srcKey.replaceAll(" ", "_");
            logInfo("Downloading S3 Object {} from bucket {} on path {}", srcKey, srcBucket, scanFilePath);
            ObjectMetadata s3Object = downloadS3Object(amazonS3, srcBucket, srcKey, scanFilePath);
            logInfo("S3 Object {} downloaded on path {}", srcKey, scanFilePath);
            long s3ObjectFileSize = s3Object.getInstanceLength();
            logInfo("S3 Object Size {}", s3ObjectFileSize);

            scanFilePath = expandIfLargeArchive(srcBucket, srcKey, payloadPath, scanFilePath, s3ObjectFileSize, context);
            Status status = scan(srcBucket, srcKey, scanFilePath, definitionsPath, tmpPath, context);

            if (status.equals(Status.CLEAN)) {
                logInfo("Clean Scan for object {}", objectFullIdentifier);
                setScanStatus(srcBucket, srcKey, Status.CLEAN);
                moveToProduction(amazonS3, srcBucket, srcKey, objectVersionId);
            } else {
                LOG.warn("THREAT DETECTED!!! Scan found object INFECTED" + objectFullIdentifier);
                setScanStatus(srcBucket, srcKey, Status.INFECTED);
                quarantine(amazonS3, srcBucket, srcKey, objectVersionId);
            }
            return status;
        } catch (FileTooLargeException e) {
            LOG.error("File Too Large Error ", e);
            setScanStatus(e.getBucket(), e.getKey(), e.getStatus());
            throw e;
        } catch (ClamAVScannerException e) {
            LOG.error("CLAM AV Scan Error ", e);
            setScanStatus(e.getBucket(), e.getKey(), e.getStatus());
            throw e;
        } catch (Exception e) {
            LOG.error(String.format("Exception Occurred bucket: %s, key: %s", srcBucket, srcKey), e);
            setScanStatus(srcBucket, srcKey, Status.ERROR);
            throw new RuntimeException(e);
        } finally {
            if(payloadPath != null) {
                deleteFiles(payloadPath);
            }
            if(tmpPath != null) {
                deleteFiles(tmpPath);
            }
        }
    }

    private void quarantine(AmazonS3 amazonS3, String srcBucket, String srcKey, String objectVersionId) {
        String destBucket = System.getenv(QUARANTINE_BUCKET_NAME);
        amazonS3.copyObject(srcBucket, srcKey, destBucket, srcKey);
        logInfo("File {} copied from {} to {}", srcKey, srcBucket, destBucket);
        deleteFromS3(amazonS3, srcBucket, srcKey, objectVersionId);
    }

    private void moveToProduction(AmazonS3 amazonS3, String srcBucket, String srcKey, String objectVersionId) {
        String prodBucket = System.getenv(PRODUCTION_BUCKET_NAME);
        if(Strings.isNotBlank(prodBucket)) {
            if (!prodBucket.equalsIgnoreCase(srcBucket)) {
                logInfo("Moving file {} to configured production bucket {}", srcKey, prodBucket);
                amazonS3.copyObject(srcBucket, srcKey, prodBucket, srcKey);
                logInfo("File {} copied from {} to {}", srcKey, srcBucket, prodBucket);
                deleteFromS3(amazonS3, srcBucket, srcKey, objectVersionId);
            } else {
                logInfo("Production bucket {} configured same as source bucket", prodBucket);
            }
        }
    }

    private void deleteFromS3(AmazonS3 amazonS3, String srcBucket, String srcKey, String objectVersionId) {
        if(Strings.isBlank(objectVersionId)) {
            amazonS3.deleteObject(srcBucket, srcKey);
            logInfo("File {} deleted from {}", srcKey, srcBucket);
        } else {
            amazonS3.deleteVersion(srcBucket, srcKey, objectVersionId);
            logInfo("File {} with version {} deleted from {}", srcKey, objectVersionId, srcBucket);
        }
    }

    private ObjectMetadata downloadS3Object(AmazonS3 s3Client, String bucket, String key, String payloadPath) throws IOException {
        S3Object s3Object = s3Client.putObject(new PutObjectRequest().withMetadata(new ObjectMetadata()))getObject(bucket, key);
        try(InputStream is = s3Object.getObjectContent()) {
            Files.copy(is, Path.of(payloadPath), StandardCopyOption.REPLACE_EXISTING);
        }
        return s3Object.getObjectMetadata();
    }

    private void createDirIfNotExist(String bucket, String key, String path) {
        File file = new File(key);
        String subDirnName = file.getParent();
        Path fullPath = Paths.get(path);
        if (! StringUtils.isNullOrEmpty(subDirnName)) {
            fullPath = Paths.get(path, subDirnName);
        }
        if (Files.notExists(fullPath)) {
            try {
                Path dirPath = Files.createDirectories(fullPath);
                logInfo("Directory Created on path {}", dirPath);
                dirPath.toFile().deleteOnExit();
            } catch (IOException e) {
                reportFailure(bucket, key, fullPath.toString(), e);
            }
        }

    }

    private String expandIfLargeArchive(String bucket, String key, String downloadPath, String scanFilePath, long fileSize, Context context) {
        boolean isZipFile = false;

        String zipTestCommand = "7za t -y " + scanFilePath + " -p123";
        CommandExecutionResponse zipTestExecutionResponse = executeCommand(zipTestCommand, context.getRemainingTimeInMillis());
        logDebug("command {} returns code {}, msg {} & error msg {}", zipTestCommand, zipTestExecutionResponse.getReturnCode(), zipTestExecutionResponse.getMessage(), zipTestExecutionResponse.getErrorMessage());
        if (Arrays.asList(0, 1).contains(zipTestExecutionResponse.getReturnCode())) {
            isZipFile = true;
        }

        if (!isZipFile && fileSize > MAX_BYTES) {
            throw new FileTooLargeException(String.format("Scan File %s having size in %d bytes is too large to scan. Supported max file size is %d bytes.", key, fileSize, MAX_BYTES),
                    bucket, key, SERVERLESS_CLAM_SCAN, Status.SIZE_EXCEEDED);
        }
        if (isZipFile && fileSize > MAX_BYTES) {
            try {
                String command = "7za x -y " + scanFilePath + " -o" + downloadPath;
                CommandExecutionResponse executionResponse = executeCommand(command, context.getRemainingTimeInMillis());
                if (!Arrays.asList(0, 1).contains(executionResponse.getReturnCode())) {
                    throw new ZipException(String.format("7za exited with unexpected return code: %d & error msg %s ", executionResponse.getReturnCode(), executionResponse.getErrorMessage()));
                }
                deleteFiles(scanFilePath);
                List<File> largeFiles = new ArrayList<>();
                Files.walk(Path.of(downloadPath)).forEach(path -> {
                    File file = path.toFile();
                    if (!file.isDirectory() && file.length() > MAX_BYTES) {
                        largeFiles.add(file);
                    }
                });

                if (!largeFiles.isEmpty()) {
                    throw new FileTooLargeException(String.format("Archive %s contains files %s which are at greater than ClamAV max of %d bytes", key, largeFiles, MAX_BYTES),
                            bucket, key, SERVERLESS_CLAM_SCAN, Status.SIZE_EXCEEDED);
                }
                logInfo("Zip file {} extracted to {}", scanFilePath, downloadPath);
                return downloadPath;
            } catch (Exception e) {
                reportFailure(bucket, key, downloadPath, e);
            }
        }
        return scanFilePath;
    }

    private Status scan(String bucket, String key, String scanFilePath, String definitionsPath, String tmpPath, Context context) {
        String clamAVParameters = System.getenv(CLAM_AV_PARAMETERS);
        logInfo("Clam AV parameters configured in environment variable '{}'", clamAVParameters);
        clamAVParameters = updateDefaultParams(clamAVParameters, context.getRemainingTimeInMillis());
        logInfo("Scan start for {} using definition path {}", scanFilePath, definitionsPath);
        try {
            String command = "clamscan -v --stdout "+ clamAVParameters +" --database=" + definitionsPath + " -r --tempdir=" + tmpPath + " " + scanFilePath;
            logInfo("Scan Command: {}", command);
            CommandExecutionResponse executionResponse = executeCommand(command, context.getRemainingTimeInMillis());
            logInfo("Scan Command Response: {}", executionResponse);
            if (executionResponse.getReturnCode() == 0) {
                return Status.CLEAN;
            } else if (executionResponse.getReturnCode() == 1) {
                return Status.INFECTED;
            }
            throw new RuntimeException(String.format("ClamAV exited with unexpected code: %d. Output: %s", executionResponse.getReturnCode(), executionResponse.getErrorMessage()));
        } catch (Exception e) {
            reportFailure(bucket, key, scanFilePath, e);
            return Status.ERROR;
        }
    }

    private String updateDefaultParams(String clamAVParameters, int timeout) {
        if(clamAVParameters == null) {
            clamAVParameters = "";
        }

        // Since these parameters are appended at the end, so they will not be overriden even if exists in env variable 'clamAVParameters'
        clamAVParameters += " --alert-exceeds-max=yes --max-filesize=" + MAX_BYTES + " --max-scansize=" + MAX_BYTES;

        // overriding max-scantime, if already exists in env variable. As it can't be greater than whatever time left in lamda execution
        return " --max-scantime="+ timeout + " " + clamAVParameters;
    }

    private CommandExecutionResponse executeCommand(String command, int timeout) {
        try {
            LOG.info("Executing command with timout " + timeout + " milli second..... " + command);
            Process process = Runtime.getRuntime().exec(command);
            if (process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                return new CommandExecutionResponse(process.exitValue(), IOUtils.toString(process.getInputStream()), IOUtils.toString(process.getErrorStream()));
            } else {
                String msg = IOUtils.toString(process.getInputStream());
                String errorMsg = IOUtils.toString(process.getErrorStream());
                String timeOutMsg = String.format("TimeOut within %d millisecond, executing command [%s] msg=%s, errorMsg=%s", timeout, command, msg, errorMsg);
                process.destroy();
                return new CommandExecutionResponse(Integer.MIN_VALUE, null, timeOutMsg);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error(String.format("Error while executing command [%s]", command), e);
            throw new RuntimeException(e);
        }
    }

    private void reportFailure(String bucket, String key, String path, Exception exception) {
        deleteFiles(path);
        throw new ClamAVScannerException(exception, bucket, key, SERVERLESS_CLAM_SCAN, Status.ERROR);
    }

    private void deleteFiles(String downloadPath) {
        deleteFiles(downloadPath, null);
    }

    private void deleteFiles(String downloadPath, String key) {
        if (key != null) {
            Path pathToDelete = Paths.get(downloadPath, key);
            try {
                Files.deleteIfExists(pathToDelete);
                logInfo("File Deleted {}", pathToDelete);
            } catch (IOException e) {
                LOG.error("Unable to Delete file {}", pathToDelete);
            }
        } else {
            File fileOrDir = new File(downloadPath);
            deleteFileRecursively(fileOrDir);
            logInfo("File / Directory deleted {}", fileOrDir);
        }
    }

    private boolean deleteFileRecursively(File fileOrDir) {
        if (! fileOrDir.isDirectory()) {
            logDebug("Deleting File {}", fileOrDir);
            return fileOrDir.delete();
        }

        File[] filesList = fileOrDir.listFiles();
        boolean isDeleted = true;
        if(filesList != null) {
            for (File file : filesList) {
                isDeleted &= deleteFileRecursively(file);
            }
        }
        return isDeleted;
    }

    private void setScanStatus(String bucket, String key, Status status) {
        AmazonS3 amazonS3 = AmazonS3Client.builder().build();
        List<Tag> tags = List.of(new Tag(SCAN_STATUS, status.name()));
        amazonS3.setObjectTagging(new SetObjectTaggingRequest(bucket, key, new ObjectTagging(tags)));
        logInfo("Updated Tag {} = {} for S3 Object {} under Bucket {}", SCAN_STATUS, status.name(), key, bucket);
    }

    private void logDebug(String msg, Object... params) {
        if(LOG.isDebugEnabled()) {
            LOG.debug(msg, params);
        }
    }

    private void logInfo(String msg, Object... params) {
        if(LOG.isInfoEnabled()) {
            LOG.info(msg, params);
        }
    }
}