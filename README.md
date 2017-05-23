# EC2 Volumes Automated Snapshots

This project is composed of a set of AWS Lambda Functions to automate EC2 volumes snapshots creation and deletion.

## Version
 * 1.0.0 - Initial Release

## Automated Snapshots Creation
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

* Only volumes attached to an instance are considered for a snapshot, unattached volumes are ignored
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

## Automated Snapshots Deletion
The Lambda function ec2SnapshotDeleter is being called daily by a Cloudwatch schedule event.
It will delete any snapshot with a DeletionDate lower or equal to today's date.

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
  * deploymentRegions _[string]_ - the AWS regions you want the application to be deployed in
  * appName _string_ - the application name (used by the deploy script to name resources in AWS)

E.G.:
```
  {
    "deploymentRegions": [ "us-east-1", "eu-west-1" ],
    "appName": "lambdaEc2Snapshots"
  }
```

## Run the init.sh script
```./init.sh```

You are all done now!

## Disable AWS Access Keys
Once the the install is complete it is recommended to disable the AWS Access Keys and delete ~/.aws/credentials. You won't need those anymore.
You can also delete the IAM user if it was a temporary one.


### Still left TODO
* Test how it would perform with a large estate. Can it finish in the allocated 5 minutes? Are there too many API calls? Do we need to create threads to run each instance in parallel?
