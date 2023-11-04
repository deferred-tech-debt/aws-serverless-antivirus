data "aws_iam_policy_document" "app-scanner-queue-policy" {
  policy_id = "__default_policy_ID"
  statement {
    sid = "Scanner-queue-policy-statement"
    effect = "Allow"
    principals {
      type = "Service"
      identifiers = ["s3.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.app-scanner-queue.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_s3_bucket.app-s3-attachments.arn]
    }
  }
}

resource aws_s3_bucket "app-s3-attachments" {
  bucket = "app${var.REGION}${var.ENVIRONMENT}-app-s3-attachment-bucket"
  tags = {
    Name = "app${var.REGION}${var.ENVIRONMENT}-app-s3-attachment-bucket"
    Environment = var.ENVIRONMENT
  }
}

resource "aws_s3_bucket_versioning" "app-s3-attachments_bucket_versioning" {
  bucket = aws_s3_bucket.app-s3-attachments.bucket
  versioning_configuration {
    status = "Enabled"
  }
}

resource aws_s3_bucket "app-s3-attachments_production" {
  count = var.INCLUDE_S3_ATTACHMENT_PRODUCTION_BUCKET ? 1 : 0
  bucket = "app${var.REGION}${var.ENVIRONMENT}-app-s3-attachment-production-bucket"
  tags = {
    Name = "app${var.REGION}${var.ENVIRONMENT}-app-s3-attachment-production-bucket"
    Environment = var.ENVIRONMENT
  }
}

resource "aws_s3_bucket_versioning" "app-s3-attachments_production_bucket_versioning" {
  count = var.INCLUDE_S3_ATTACHMENT_PRODUCTION_BUCKET ? 1 : 0
  bucket = one(aws_s3_bucket.app-s3-attachments_production[*].bucket)
  versioning_configuration {
    status = "Enabled"
  }
}

resource aws_s3_bucket "app-s3-quarantine" {
  bucket = "app${var.REGION}${var.ENVIRONMENT}-app-s3-quarantine-bucket"
  tags = {
    Name = "app${var.REGION}${var.ENVIRONMENT}-app-s3-quarantine-bucket"
    Environment = var.ENVIRONMENT
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "app-s3-quarantine-lifecycle-configuration" {
  bucket = aws_s3_bucket.app-s3-quarantine.bucket
  rule {
    id     = "expiration"
    status = "Enabled"
    expiration {
      days = var.QUARANTINE_BUCKET_EXPIRATION_DAYS
    }
  }
}

resource "aws_sqs_queue" "app-scanner-queue" {
  name                       = "app${var.REGION}${var.ENVIRONMENT}-app-attachement-scanner-queue"
  delay_seconds              = 0
  visibility_timeout_seconds = 901
  max_message_size           = 262144
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 10
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.app-scanner-dead-letter-queue.arn
    maxReceiveCount     = 4
  })
}

resource "aws_sqs_queue_policy" "scanner-queue-policy" {
  policy    = data.aws_iam_policy_document.app-scanner-queue-policy.json
  queue_url = aws_sqs_queue.app-scanner-queue.url
}

resource "aws_sqs_queue" "app-scanner-dead-letter-queue" {
  name                       = "app${var.REGION}${var.ENVIRONMENT}-app-attachment-scanner-deadLetter-queue"
  delay_seconds              = 0
  visibility_timeout_seconds = 901
  max_message_size           = 262144
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 10
}

resource "aws_s3_bucket_notification" "app-s3-attachments-notification" {
  depends_on = [aws_sqs_queue_policy.scanner-queue-policy]
  bucket = "app${var.REGION}${var.ENVIRONMENT}-app-s3-attachment-bucket"
  queue {
    events = ["s3:ObjectCreated:*"]
    queue_arn = aws_sqs_queue.app-scanner-queue.arn
  }
}

resource "aws_efs_file_system" "clam_av_efs" {
  creation_token = "clam_av_efs"
  performance_mode = "generalPurpose"
  throughput_mode = "elastic"
  encrypted = "true"
  availability_zone_name = var.EFS_AVAILABILITY_ZONE_NAME
}

resource "aws_efs_mount_target" "clam_av_efs_mount_target" {
  file_system_id  = aws_efs_file_system.clam_av_efs.id
  subnet_id       = var.SUBNET_ID
  security_groups = var.SECURITY_GROUP_IDS
}

resource "aws_efs_access_point" "clam_av_access_point" {
  file_system_id = aws_efs_file_system.clam_av_efs.id
  depends_on = [aws_efs_file_system.clam_av_efs, aws_efs_mount_target.clam_av_efs_mount_target]
  posix_user {
    gid = 1000
    uid = 1000
  }
  root_directory {
    path = "/clam_av"
    creation_info {
      owner_gid   = 1000
      owner_uid   = 1000
      permissions = "777"
    }
  }
}

resource "aws_cloudwatch_log_group" "app-s3-scanner-lambda-log-group" {
  name = "/aws/lambda/app${var.REGION}${var.ENVIRONMENT}-app-s3-scanner-lambda"
  retention_in_days = var.RETENTION_CLOUDWATCH_LOGS
}

resource "aws_cloudwatch_log_subscription_filter" "app-s3-scanner-lambda-log-subscription" {
  destination_arn = var.DATADOG_KINESIS_FIREHOSE_ARN
  filter_pattern  = " "
  log_group_name  = aws_cloudwatch_log_group.app-s3-scanner-lambda-log-group.name
  role_arn        = var.DATADOG_KINESIS_FIREHOSE_ROLE_ARN
  name            = "datadog_stream_filter"
}

resource "aws_lambda_function" "app-s3-scanner-lambda" {
  function_name = "app${var.REGION}${var.ENVIRONMENT}-app-s3-scanner-lambda"
  role          = aws_iam_role.app_scanner_lambda_role.arn
  depends_on = [aws_cloudwatch_log_group.app-s3-scanner-lambda-log-group, aws_efs_access_point.clam_av_access_point]
  package_type = "Image"
  image_uri     = var.APP_SCANNER_IMAGE_URI
  timeout       = 900
  memory_size   = 4100
  ephemeral_storage {
    size = 512
  }
  environment {
    variables = {
      "EFS_DEF_PATH" : "def"
      "EFS_MOUNT_PATH" : "/mnt/clamav"
      "LOG_LEVEL" : "INFO"
      "QUARANTINE_BUCKET" : aws_s3_bucket.app-s3-quarantine.bucket
      "PRODUCTION_BUCKET" : var.INCLUDE_S3_ATTACHMENT_PRODUCTION_BUCKET ? one(aws_s3_bucket.app-s3-attachments_production[*].bucket) : aws_s3_bucket.app-s3-attachments.bucket
      "CLAM_AV_PARAMETERS" : var.CLAM_AV_PARAMETERS
    }
  }
  vpc_config {
    security_group_ids = var.SECURITY_GROUP_IDS
    subnet_ids         = [var.SUBNET_ID]
  }
  file_system_config {
    arn              = aws_efs_access_point.clam_av_access_point.arn
    local_mount_path = "/mnt/clamav"
  }
}

resource "aws_lambda_event_source_mapping" "app-s3-scanner-lambda-event-source" {
  function_name = aws_lambda_function.app-s3-scanner-lambda.function_name
  event_source_arn = aws_sqs_queue.app-scanner-queue.arn
  batch_size = 1
  enabled = true
  scaling_config {
    maximum_concurrency = var.MAX_CONCURRENT_SCANNING
  }
}

resource "aws_lambda_function_event_invoke_config" "app-s3-scanner-lambda-event-invoke-config" {
  function_name = aws_lambda_function.app-s3-scanner-lambda.function_name
  maximum_retry_attempts       = var.MAX_SCAN_RETRY
}

resource "aws_cloudwatch_log_group" "app-fresh-clam-lambda-log-group" {
  name = "/aws/lambda/app${var.REGION}${var.ENVIRONMENT}-app-fresh-clam-lambda"
  retention_in_days = var.RETENTION_CLOUDWATCH_LOGS
}

resource "aws_cloudwatch_log_subscription_filter" "app-fresh-clam-lambda-log-subscription" {
  destination_arn = var.DATADOG_KINESIS_FIREHOSE_ARN
  filter_pattern  = " "
  log_group_name  = aws_cloudwatch_log_group.app-fresh-clam-lambda-log-group.name
  role_arn        = var.DATADOG_KINESIS_FIREHOSE_ROLE_ARN
  name            = "datadog_stream_filter"
}

resource "aws_lambda_function" "app-fresh-clam-lambda" {
  function_name = "app${var.REGION}${var.ENVIRONMENT}-app-fresh-clam-lambda"
  role          = aws_iam_role.app_scanner_lambda_role.arn
  depends_on = [aws_cloudwatch_log_group.app-fresh-clam-lambda-log-group, aws_efs_access_point.clam_av_access_point]
  package_type = "Image"
  image_uri     = var.APP_FRESH_CLAM_IMAGE_URI
  timeout       = 900
  memory_size   = 2048
  ephemeral_storage {
    size = 512
  }
  environment {
    variables = {
      "EFS_DEF_PATH" : "def"
      "EFS_MOUNT_PATH" : "/mnt/clamav"
      "LOG_LEVEL" : "INFO"
    }
  }
  vpc_config {
    security_group_ids = var.SECURITY_GROUP_IDS
    subnet_ids         = [var.SUBNET_ID]
  }
  file_system_config {
    arn              = aws_efs_access_point.clam_av_access_point.arn
    local_mount_path = "/mnt/clamav"
  }
}

resource "aws_cloudwatch_event_rule" "app-fresh-clam-event-rule" {
  name = "app${var.REGION}${var.ENVIRONMENT}-app-fresh-clam-event-rule"
  description = "Trigger fresh clam lambda to update clamAV definitions"
  schedule_expression = var.FRESH_CLAM_LAMBDA_EVENT_SCHEDULE
}

resource "aws_cloudwatch_event_target" "app-fresh-clam-event-target" {
  arn  = aws_lambda_function.app-fresh-clam-lambda.arn
  rule = aws_cloudwatch_event_rule.app-fresh-clam-event-rule.name
  target_id = "fresh-clam-lambda"
}

resource "aws_lambda_permission" "app-fresh-clam-event-permission" {
  statement_id = "AllowExecutionFromCloudWatch"
  action = "lambda:InvokeFunction"
  function_name = aws_lambda_function.app-fresh-clam-lambda.function_name
  principal = "events.amazonaws.com"
  source_arn = aws_cloudwatch_event_rule.app-fresh-clam-event-rule.arn
}
