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
region=$(jq -r '.region' config.json)
appName=$(jq -r '.appName' config.json)

cd functions

echo
# Updating Lambda Functions
for f in $(ls -1); do
  echo "Building function $f"
  cd $f
  gradle build
  if [[ $? != 0 ]]; then
    echo "Failed building function $f"
    exit 1
  fi

  #Updating the function code
  echo
  echo "Deploying Code for function $f"
  aws lambda update-function-code \
    --region $region \
    --function-name $f \
    --zip-file fileb://./build/distributions/$f.zip
  cd ..
  echo "Function $f updated"
done

cd ..
