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
region=$(jq -r '.region' config.json)
appName=$(jq -r '.appName' config.json)

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
scriptDir="file://${scriptDir//////}//cloudformation//$appName.json"


# Create the cloudformation stack
echo
echo Creating cloudformation stack $appName in $region to create:
echo "   - IAM Role ec2SnapshotTakerLambdaExecutionRole"
echo "   - Lambda Function ec2SnapshotTaker"
aws cloudformation create-stack \
    --capabilities CAPABILITY_NAMED_IAM \
    --stack-name $appName \
    --template-body $scriptDir \
    --region $region >/dev/null

echo "Waiting for the stack to complete creation, this can take a while"
sleep 5

aws cloudformation wait stack-create-complete \
    --stack-name $appName \
    --region $region

if [[ $? != 0 ]]; then
  echo "Login to cloudformation front end and have a look at the event logs"
  exit 1
fi
cd -

echo "Cloudformation Stack now fully created"

echo
# Updating Lambda Functions
cd functions
for f in $(ls -1); do
  echo "Updating $f runtime to java8"
  #Updating the function code
  aws lambda update-function-configuration \
    --region $region \
    --function-name $f \
    --runtime java8
  echo "Updating function $f end"
done


exit



#./deploy.sh
