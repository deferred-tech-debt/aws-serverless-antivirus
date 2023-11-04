FROM public.ecr.aws/lambda/java:11
LABEL name=lambda/java/clamav
LABEL version=1.0

ARG CACHE_DATE=1
RUN yum update -y \
    && yum install -y amazon-linux-extras \
    && yum update python -y \
    && PYTHON=python2 amazon-linux-extras install epel \
    && yum -y install clamav clamd p7zip \
    && yum clean all

ARG classes
ARG lib
ARG clamdConfig=clamd.conf

# Copy function code and runtime dependencies from Maven layout
COPY $classes ${LAMBDA_TASK_ROOT}
COPY $lib ${LAMBDA_TASK_ROOT}/lib
COPY $clamdConfig /etc/clamd.conf

CMD [ "com.serverless.lambda.scanner.Handler::handleRequest" ]