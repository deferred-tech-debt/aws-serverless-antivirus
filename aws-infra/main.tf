provider "aws" {
  region = var.AWS_REGION
  default_tags {
    tags = {
      Environment = var.ENVIRONMENT
      Application = var.APP_NAMES
      Region      = var.AWS_REGION
      Product     = var.PRODUCT
      Cost        = var.COST
      Owner       = "AppDevops"
    }
  }
  }

terraform {
    backend "s3" {
    bucket         = "appusdev-app-state"
    key            = "s3-antivirus-devops/terraform.tfstate"
    region         = "us-gov-west-1"
    dynamodb_table = "s3-antivirus-state-locks"
    encrypt        = "true"
  }
}
