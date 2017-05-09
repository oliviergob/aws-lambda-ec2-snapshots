#!/bin/bash

# Check if the AWS CLI is in the PATH
found=$(which aws)
if [ -z "$found" ]; then
  echo "Please install the AWS CLI under your PATH: http://aws.amazon.com/cli/"
  exit 1
fi

# Check if jq is in the PATH
found=$(which jq)
if [ -z "$found" ]; then
  echo "Please install jq under your PATH: http://stedolan.github.io/jq/"
  exit 1
fi

# Read other configuration from config.json
cliProfile=$(jq -er '.cliProfile' config.json)
region=$(jq -r '.deploymentRegion' config.json)
appName=$(jq -r '.appName' config.json)

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cloudformation="file://${scriptDir//////}//cloudformation//$appName.json"


# Create the cloudformation stack
echo
echo Creating cloudformation stack $appName in $region to create:
echo '   - IAM Managed Policy ec2SnapShotTakerPolicy'
echo '   - IAM Managed Policy ec2SnapShotDeleterPolicy'
echo '   - IAM Role ec2SnapshotTakerLambdaExecutionRole'
echo '   - IAM Role ec2SnapshotDeleterLambdaExecutionRole'
echo '   - Lambda Function ec2SnapshotTaker'
echo '   - Lambda Function ec2SnapshotDeleter'
echo '   - Cloudwatch Daily Scheduled Rule to trigger ec2SnapshotTaker'
echo '   - Cloudwatch Daily Scheduled Rule to trigger ec2SnapshotDeleter'
aws cloudformation create-stack \
    --capabilities CAPABILITY_NAMED_IAM \
    --stack-name $appName \
    --template-body $cloudformation \
    --region $region >/dev/null

echo "Waiting for the stack to complete creation, this can take a while"
sleep 10

aws cloudformation wait stack-create-complete \
    --stack-name $appName \
    --region $region

if [[ $? != 0 ]]; then
  echo "Login to cloudformation front end and have a look at the event logs"
  exit 1
fi

echo "Cloudformation Stack now fully created"

echo
# Updating Lambda Functions
cd $scriptDir/functions
for f in $(ls -1 src/main/java/com/gobslog/ec2/functions/); do
  f="${f%.*}"
  f="$(echo "$f" | sed 's/.*/\l&/')"
  echo "Updating $f runtime to java8"
  #Updating the function code
  aws lambda update-function-configuration \
    --region $region \
    --function-name $f \
    --runtime java8
  echo "Updating function $f end"
done

echo
# Now deploying the code into the lambda functions
cd $scriptDir
./deploy.sh

exit
