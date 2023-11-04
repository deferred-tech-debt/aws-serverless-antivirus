# aws-serverless-antivirus
AWS Serverless solution to scan malware file when uploaded to S3 bucket. The solution is built upon Clam AV open-source antivirus feature.  Project includes 2 Lambda (Java &amp; Docker) and Terraform script to build serverless infra-structure.

[Terraform Scripts](aws-infra)

TF script to setup AWS Serverless Infrastructure to execute scanning on malware files & regularly update Clam AV Virus definition.

[Scanner Lamda](scanner)

Dockerize - JAVA Lambda to scan files being uploaded to S3 bucket. Lambda is triggered via SQS queue.

[Fresh Clam (Update Virus Definition)](freshclam)

Dockerize - JAVA Lambda to update Clam AV Virus definition. Lambda is triggered via Cloud Watch Cron Event in regular schedule.

[Serverless Design Document](.docs/Serveless%20Design.pdf)

High level detail of aws-serverless-antivirus design.