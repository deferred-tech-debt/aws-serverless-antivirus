AWS_REGION                        = "us-gov-west-1"
ENVIRONMENT                       = "devops"
APP_NAMES                         = "s3-antivirus"
COST                              = "APP-USDEV"
PRODUCT                           = "APP"
REGION                            = "usw"
S3BUCKET                          = "appusdev-app-software"
SUBNET_ID                         = "subnet-id"
SECURITY_GROUP_IDS                = ["<security groups>>"]
VPC_ID                            = "<private vpc id>>"
EFS_AVAILABILITY_ZONE_NAME        = "us-gov-west-1a"
ECR_ARN                           = "arn:aws-us-gov:ecr:*:123456789:repository/*"
APP_SCANNER_IMAGE_URI             = "123456789.dkr.ecr.us-gov-west-1.amazonaws.com/scanner-lambda:1.0-SNAPSHOT"
APP_FRESH_CLAM_IMAGE_URI          = "123456789.dkr.ecr.us-gov-west-1.amazonaws.com/freshclam-lambda:1.0-SNAPSHOT"
MAX_SCAN_RETRY                    = 2
MAX_CONCURRENT_SCANNING           = 100
FRESH_CLAM_LAMBDA_EVENT_SCHEDULE  = "rate(120 minutes)"
RETENTION_CLOUDWATCH_LOGS         = 14
QUARANTINE_BUCKET_EXPIRATION_DAYS = 1
DATADOG_KINESIS_FIREHOSE_ARN      = "arn:aws-us-gov:firehose:us-gov-west-1:123456789:deliverystream/stream_logs"
DATADOG_KINESIS_FIREHOSE_ROLE_ARN = "arn:aws-us-gov:iam::123456789:role/appdevus-kinesis-ddlogs-role"
INCLUDE_S3_ATTACHMENT_PRODUCTION_BUCKET = false
CLAM_AV_PARAMETERS                = "--max-files=25000 --max-recursion=50"