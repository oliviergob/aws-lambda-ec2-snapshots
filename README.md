# Lambda EC2 tools

This project aims to contains a collection of tools to administrate an EC2 instance estate using Amazon Lambda functions.

**Existing functionalities**

  - EC2 Instance Automated Snapshots

**Todo**

- EC2 Instance Automated Start and Stop

# Installation and Configuration
## prerequisites
* A linux host to execute the install scripts
* [aws-cli](http://docs.aws.amazon.com/cli/latest/userguide/installing.html) installed version 1.11.35 or greater
* [jq](https://stedolan.github.io/jq/)
* An IAM user with administrator privileges and access keys set up (it can be a temporary user just for deploying the project)
* IAM user access keys set up in ~/.aws/credentials
* [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Gradle](https://gradle.org/install)

## Create your config.json
* Clone the repository and copy config.json.sample to config.json in the root directory
* Change the properties
  * region - the ascertain region you want the tools to be installed in
  * appName - the application name (used by the deploy script to name resources in AWS)

## Configure ec2SnapshotTaker
  * Copy config.json.sample to config.json in ./functions/ec2SnapshotTaker
  * Change the properties
    * REGIONS - comma separated list of regions to take snapshots in (will use default region if not configured or left blank)

## Run the init.sh script
```./init.sh```

You are all done now!

## Disable AWS Access Keys
Once the the install is complete it is recommended to disable the AWS Access Keys and delete ~/.aws/credentials. You won't need those anymore.
You can also delete the IAM user if it was a temporary one.

# EC2 Instance Automated Snapshots
The Lambda function ec2SnapshotTaker is being called daily by a Cloudwatch schedule event.
Snapshots can be configured at the EC2 instance level or at the volume level by adding a tag called SnapshotConfig.
SnapshotConfig value is a comma separated list of a letter representing the periodicity of the snapshot (D for Daily, W for Weekly and M for Monthly) followed by the retention period in days (integer).
Examples of valid SnapshotConfig values:

| Value  |  Description |
|--------|--------------|
| D7     | Daily Snapshots with a 7 days retention period   |
| M100   | Monthly Snapshots with a 100 days retention period |
| D7,W30,M365 | Daily Snapshots with a 7 days retention period / Weekly Snapshots with a 30 days retention period / Monthly snapshots with a 365 days retention period

The Snapshot Taker function follow those simple rules:

* Instance level configuration overrides volume configuration
* You can turn off snapshots by either not configuring a SnapshotConfig tag or by setting it to OFF followed by a comment of your choice
  * E.G.: OFF - No snapshot required for database volume  
* Monthly snapshots are taken on the first day of the month
  * This means if on the 2nd of March you configure a volume with Monthly snapshots, you will have to wait for the 1st of April for your first snapshot to be taken
* Weekly snapshots are taken on the first day of the week (Monday)
  * Same caveat as above
* Daily snapshots are taken every day
* Only one snapshot will be taken on one given day for one volume.
  * E.G. Assuming you configured a volume to have all three types of snapshots, on Monday 1st of March, only the monthly snapshot will be taken, both weekly and daily would be skipped as they would be exactly the same.


### Still left TODO
* Create the snapshotDeleter Lambda function. Currently snapshots have a deletion date but there is nothing deleting them
* Add a configurable list of tags to be carried over from the instance down to the snapshot. A user could want tag its snapshots with an application name or a project name already held by the instance
