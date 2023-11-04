variable "AWS_REGION" {
  description = "AWS region where resources will be created"
  type        = string
}
variable "ENVIRONMENT" {
  description = " Name of the envionment"
  type        = string
}
variable "REGION" {
  description = "short name of the environment region"
  type        = string
}
variable "ECR_ARN" {
  description = "Docker Image Repository ARN to pull & deploy image on Lambdas"
  type        = string
}
variable "APP_SCANNER_IMAGE_URI" {
  description = "Docker Image URI to be deploy on scanner Lambda"
  type        = string
}
variable "APP_FRESH_CLAM_IMAGE_URI" {
  description = "Docker Image URI to be deploy on fresh clam Lambda"
  type        = string
}
variable "APP_NAMES" {
  description = " Name of the Module"
  type        = string
}
variable "COST" {
  description = "Cost of the Environment"
  type        = string
}
variable "PRODUCT" {
  description = " Which Product"
  type        = string
}
variable "S3BUCKET" {
  description = " Name of the envionment"
  type        = string
}
variable "SUBNET_ID" {
  description = " Id for Subnets"
  type        = string
}
variable "SECURITY_GROUP_IDS" {
  description = " Id for Security groups"
  type        = list(string)
}
variable "VPC_ID" {
  description = " Id for Subnets"
  type        = string
}
variable "MAX_SCAN_RETRY" {
  description = " Maximum number of retries scanner queue triggers scanner lambda to scan file"
  type        = number
}
variable "MAX_CONCURRENT_SCANNING" {
  description = " Maximum number of parallel scanner lambda to be executed"
  type        = number
}
variable "FRESH_CLAM_LAMBDA_EVENT_SCHEDULE" {
  description = " Schedule expression to to trigger fresh clam lambda"
  type        = string
}
variable "RETENTION_CLOUDWATCH_LOGS" {
  description = " Retention period of cloud watch logs"
  type        = number
}
variable "QUARANTINE_BUCKET_EXPIRATION_DAYS" {
  description = " Quarantine bucket expiration days"
  type        = number
}
variable "DATADOG_KINESIS_FIREHOSE_ARN" {
  description = " ARN of data dog kinesis firehose delivery stream"
  type        = string
}
variable "DATADOG_KINESIS_FIREHOSE_ROLE_ARN" {
  description = " ARN of role to connect data dog kinesis firehose delivery stream"
  type        = string
}
variable "INCLUDE_S3_ATTACHMENT_PRODUCTION_BUCKET" {
  description = " Flag to include additional production bucket where file will be moved after clean scan"
  type        = bool
}
variable "EFS_AVAILABILITY_ZONE_NAME" {
  description = " EFS AVAILABILITY ZONE"
  type        = string
}
variable "CLAM_AV_PARAMETERS" {
  description = " CLamAV Parameters to override default value"
  type        = string
}
variable "VERSION" {
  default="$(TF_VAR_VERSION)"
  type   = string
}
variable "DEPLOY" {
  default = "$(TF_VAR_DEPLOY)"
  type = string
}