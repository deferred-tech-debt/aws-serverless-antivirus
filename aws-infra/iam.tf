data aws_iam_policy_document "app_scanner_policy" {
  statement {
    sid = "ECRLambdaPolicyStatement"
    effect = "Allow"
    actions = [
      "ecr:DescribeImageScanFindings",
      "ecr:GetLifecyclePolicyPreview",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:DescribeImageReplicationStatus",
      "ecr:DescribeRepositories",
      "ecr:ListTagsForResource",
      "ecr:BatchGetRepositoryScanningConfiguration",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetRepositoryPolicy",
      "ecr:GetLifecyclePolicy"
    ]
    resources = [var.ECR_ARN]
  }
  statement {
    sid = "S3LambdaPolicyStatement"
    effect = "Allow"
    actions = [
      "s3:GetObjectVersionTagging",
      "s3:PutObjectTagging",
      "s3:DeleteObjectTagging",
      "s3:GetObjectAttributes",
      "s3:DeleteObjectVersionTagging",
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:GetObjectVersionAttributes",
      "s3:PutBucketTagging",
      "s3:GetObjectTagging",
      "s3:PutObjectVersionTagging",
      "s3:GetBucketVersioning",
      "s3:GetObjectVersion",
      "cloudwatch:*",
      "sqs:*",
      "elasticfilesystem:ClientMount",
      "elasticfilesystem:ClientRootAccess",
      "elasticfilesystem:ClientWrite",
      "elasticfilesystem:DescribeMountTargets",
      "elasticfilesystem:DescribeFileSystems",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "ec2:CreateNetworkInterface",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DeleteNetworkInterface",
      "ec2:AssignPrivateIpAddresses",
      "ec2:UnassignPrivateIpAddresses"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role" "app_scanner_lambda_role" {
  name   = "app${var.ENVIRONMENT}-app_scanner_lambda_role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Sid    = ""
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      }
    ]
  })
}

resource "aws_iam_policy" "app_scanner_lambda_policy" {
  name         = "app${var.ENVIRONMENT}-app_scanner_lambda_policy"
  path         = "/"
  description  = "AWS IAM Policy for managing aws lambda role"
  policy = data.aws_iam_policy_document.app_scanner_policy.json
}

resource "aws_iam_role_policy_attachment" "attach_iam_policy_to_iam_role" {
 role        = aws_iam_role.app_scanner_lambda_role.name
 policy_arn  = aws_iam_policy.app_scanner_lambda_policy.arn
}