# Lambda EC2 tools

This project aims to contains a collection of tools to administrate an EC2 instance estate using Amazon Lambda functions.

**Existing functionalities**

  - EC2 Instance Automated Snapshots

**Todo**

- EC2 Instance Automated Start and Stop

# Installation and Configuration
## prerequisites
* A linux host to execute the install scripts
* aws-cli installed version 1.11.35 or greater
* jq installed
* An IAM user with administrator privileges and access keys set up
* IAM user access keys set up in ~/.aws/credentials

## Create your config.json
* Clone the repository and copy config.json.sample to config.json
* Change the properties
  * region - the ascertain region you want the tools to be installed in
  * appName - the application name (used by the deploy script to name resources in AWS)

## Run the init.sh script
```./init.sh```
You are all done!

# EC2 Instance Automated Snapshots
 TODO
