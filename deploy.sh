#!/bin/bash

# Check if the AWS CLI is in the PATH
found=$(which aws)
if [ -z "$found" ]; then
  echo "Please install the AWS CLI: http://aws.amazon.com/cli/"
  exit 1
fi

# Check if jq is in the PATH
found=$(which jq)
if [ -z "$found" ]; then
  echo "Please install jq: http://stedolan.github.io/jq/"
  exit 1
fi

# Read other configuration from config.json
region=$(jq -r '.region' config.json)
appName=$(jq -r '.appName' config.json)

cd functions
echo "Building functions project"
gradle build
if [[ $? != 0 ]]; then
  echo "Failed building functions project"
  exit 1
fi


echo
# Updating Lambda Functions
for f in $(ls -1 src/main/java/com/gobslog/ec2/functions/); do
  functionName="${f%.*}"
  functionName="$(echo "$functionName" | sed 's/.*/\l&/')"

  #Updating the function code
  echo
  echo "Deploying Code for function $functionName"
  aws lambda update-function-code \
    --region $region \
    --function-name $functionName\
    --zip-file fileb://./build/distributions/functions.zip >/dev/null

  #Updating the function environment variables
  echo "Updating function environment Variables for $functionName"
  aws lambda update-function-configuration \
    --region $region \
    --function-name $functionName \
    --environment fileb://./config.json >/dev/null

  echo "Function $functionName updated"
done

cd ..
