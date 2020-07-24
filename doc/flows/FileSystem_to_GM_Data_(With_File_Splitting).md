# File System to Grey Matter Data (With File Splitting)

The template for transferring files from the file system to a local on premises and a remote instance of Grey Matter Data that may be running in a Grey Matter service mesh, along with retry handling, splitting of large files into 4GB parts, and summary reports for success and failure.

## Template Location

Download template for [Upload File System to Grey Matter Data with File Splitting, Retry Handling, Multiple Instances, and Summary Report for Success and Failures](../../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

## Flow visualization

![File System to GM Data](visualizations/FileSystem_to_GM_Data_(With_File_Splitting).png)

This template is based on extensions to the following prior templates. As a reference aid, step numbers have been kept the same where logical:
- [Upload File System to Grey Matter Data with Static Permissions](../../nifi-templates/File_System_to_GM_Data_(Static_Permissions).xml)
- [Upload File System to Multiple Instances of Grey Matter Data](../../nifi-templates/File_System_to_GM_Data_(Multiple_Disparate_Instances).xml)

## Steps

At the top of this flow are three major sections referring to the `Source` and a `Target` for each of a `Remote` and `Local` instance.  The flow for `Remote` and `Local` are virtually the same, but with different configuration surrounding the SSL Context, user identity, target folder, custom data and reporting.  When customizing the configuration of this template for your own needs, you should refer to the like steps in the Remote target when configuring the Local target.

This flow is more complex then many other templates available in this project.  The steps are broken up by that depicted in the legend

Steps consisting of a number only represent the "happy path" or typical flow from start to finish.

Steps prefixed by an "F" denote those involved in handling `failure` conditions.

Steps prefixed by an "R" represent those that make up an alternate path for a `retry` loop

Steps prefixed by an "S" represent those that make up an alternate path for the `splitting` of large files in this template

Steps prefixed by an "X" indicate `extra` steps that provided auxiliary information.  For example, setting a flow file attribute for the sole purpose of it being logged by a later processor, or referenced in sample data.

The steps are documented by their major groupings

---

### 1. List Files in File System 

**Description**: Creates flow files based upon files in a directory path with optional recursive handling.

**Native Processor**: [org.apache.nifi.ListFile](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.ListFile/index.html)

**Key Configuration to Adjust**:

- _Input Directory_ - The template defaults to `/media/vbd`.  This most definitely will need to be changed for your specific use case.
- _Recurse Subdirectories_ - The template defaults to `true`.  This is most likely the scenario you will want but worth noting to consider in edge cases.  

---

### 2. Set FlowFile File Content

**Description**: Retrieves file content from disk and puts into the flow file for processing.

**Native Processor**: [org.apache.nifi.FetchFile](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.FetchFile/index.html)

In general, no adjustment is necessary from default configuration.

**Relationship & Backflow Guidance**:

In the success flow, the Back Pressure Object Threshold is set to 10 flow files, and a maximum size of 5 GB.  This is predominately to constrain the pressure from large files that were split into smaller 4GB file parts.  You may wish to adjust this value depending on the available space on your working drive.

---

### 3. Identify Mime Type

**Description**: Assigns mime.type attribute based upon the mime type detected from file content or file name. 

**Native Processor**: [org.apache.nifi.IdentifyMimeType](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.IdentifyMimeType/index.html)

In general, no adjustment is necessary from default configuration.

---

### 4. Build Folder Hierarchy

**Description**: Creates user folder, any optional intermediate prefix folders, and the folders that make up the path to the file.

**Custom Processor**: [com.deciphernow.greymatter.GetOidForPath](../GetOidForPath.md)

**Key Configuration to Adjust**:
- _Object Policy_ - The default setting in the template grants full permissions to a specified user, as well as read, execute, and create privileges to everyone else.  Most implementations will want to use a robust object policy tailored to the given use case, and in conjuction with properties Userfield Folder Object Policy, and Intermediate Folder Object Policy.
- _Original Object Policy_ - The template effectively leaves this as the default setting which will yield an empty policy.  For integrations that are migrating data, this field is intended to store a value representative of an originating access control policy in JSON format.  Similarly, the properties Userfield Folder Original Object Policy and Intermediate Folder Original Object Policy are worth attention.
- _Security_ - The default labels for this, along with Userfield Folder Security and Intermediate Folder Security should be tailored accordingly to represent an overall label for that level of the folder hierarchy and coloring for user interface purposes.  
- _Remote URL_ - As the name implies, this endpoint should be changed to reflect the root of the Grey Matter Data instance for which this processor will interrogate to create folder hierarchy.  The default value in the template is actually traversing through a proxy that will supersede the USER_DN value.
- _SSL Context Service_ - When communicating with Grey Matter Data, a client keystore and trust store may be established.  A valid PKI certificate will be needed to upload files into Grey Matter Data.
- _USER_DN_ - This dynamic property is prepopulated with a subject distinguished name from the certificate to use for identity.  The same will be populated and overwritten automatically when communicating with Grey Matter Data through an edge proxy.  This value only needs to be set when communicating directly to Grey Matter Data without an intermediary.

**Relationship & Backflow Guidance**:

In the success flow, the Back Pressure Object Threshold is set to 10 flow files, and a maximum size of 5 GB.  This is predominately to constrain the pressure from large files that were split into smaller 4GB file parts.  You may wish to adjust this value depending on the available space on your working drive.

---

### 5. Prepare Request for GM Data

**Description**: Transform file to multipart/form-data request and assembles JSON representation of metadata.

**Custom Processor**: [com.deciphernow.greymatter.PrepareWriteRequest](../PrepareWriteRequest.md)

**Key Configuration to Adjust**:
- _Object Policy_ - The default setting in the template grants full permissions to a specified user, as well as read only permissions to everyone else.  Most implementations will want to use a robust object policy tailored to the given use case.
- _Security_ - The default labels for this should be tailored accordingly to represent an overall label for the file being uploaded and coloring for user interface purposes.
- _Custom_ - The default value for this is an example only, and most production purposes will either want to clear this, or populate a custom json structure.

**Relationship & Backflow Guidance**:

In the success flow, the Back Pressure Object Threshold is set to 10 flow files, and a maximum size of 5 GB.  This is predominately to constrain the pressure from large files that were split into smaller 4GB file parts, effectively allowing up to 2 to queue before applying pressure.  You may wish to adjust this value depending on the available space on your working drive.

---

### 6. Send to GM Data

**Description**: Send the prepared HTTP request to GM Data. Successful response is JSON which could be further processed.

**Native Processor**: [org.apache.nifi.InvokeHTTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.InvokeHTTP/index.html)

**Key Configuration to Adjust**:
- _Remote URL_ - The Remote URL for the Grey Matter Data service that the file should be uploaded to.  This should be based upon the same Remote URL used for Building the Folder Hierarchy. At this time, the value specified here should be in the format of <br />`https://{name-or-address}:{port}/{path-to-grey-matter-data}/write`
- _SSL Context Service_ - When communicating with Grey Matter Data, a client keystore and trust store may be established.  A valid PKI certificate will be needed to upload files into Grey Matter Data. 
- _Content-Type_ - Expects the flowfile attribute to be set for ${mime.type}, which originated in step named 'Identify Mime Type'
- _USER_DN_ - This dynamic property is prepopulated with a subject distinguished name from the certificate to use for identity.  The same will be populated and overwritten automatically when communicating with Grey Matter Data through an edge proxy.  This value only needs to be set when communicating directly to Grey Matter Data without an intermediary.

---

### 7. Capture Success Files

**Description**: Prepares a summary CSV file listing all files that completed successfully

**Native Processor**: [org.apache.nifi.ExecuteGroovyScript](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-groovyx-nar/1.11.4/org.apache.nifi.processors.groovyx.ExecuteGroovyScript/index.html) with Custom Script [File Summary Report](../FileSummaryReport.md)

**Key Configuration to Adjust**:

- _Script Body_ - The following `Configurable Variables` at the top of the script can be altered

  - csvfile - Where the output file should be saved. The default in this template is `/home/nifi/reports/remote-success.csv` or `/home/nifi/reports/local-success.csv` depending on which target being configured

---

## Failure Steps

---

### F1. Split Files Log Attribute

**Description**: Logs all attributes to the nifi-app.log for diagnostic purposes if a failure arises in the Split Files Into 4GB Parts script processor.

**Native Processor**: [org.apache.nifi.LogAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.LogAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

In a production setting, you may want to eliminate this processing step altogether.  To do so, delete the inbound `failure` relationship, then delete the step.  Next, create new relationships from S2. Split Files into 4GB Parts to each of the Remote and Local Capture Failed Files represented as step F3.

---

### F2. Fetch File Errors Log Attribute

**Description**: Logs all attributes to the nifi-app.log for diagnostic purposes if a failure arises when setting the flow file content.

**Native Processor**: [org.apache.nifi.LogAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.LogAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

In a production setting, you may want to eliminate this processing step altogether.  To do so, delete the inbound `failure`, `not.found` and `permission.denied` relationships, then delete the step.  Next, create new relationships from 2. Set FlowFile File Content to each of the Remote and Local Capture Failed Files represented as step F3.

---

### F3. Capture Failed Files

The corollary to Step 7 for success file reporting, this failure step can be configured for both the Remote and Local targets

**Description**: Prepares a summary CSV file listing all files that failed to process

**Native Processor**: [org.apache.nifi.ExecuteGroovyScript](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-groovyx-nar/1.11.4/org.apache.nifi.processors.groovyx.ExecuteGroovyScript/index.html) with Custom Script [File Summary Report](../FileSummaryReport.md)

**Key Configuration to Adjust**:

- _Script Body_ - The following `Configurable Variables` at the top of the script can be altered

  - csvfile - Where the output file should be saved. The default in this template is `/home/nifi/reports/remote-failed.csv` or `/home/nifi/reports/local-failed.csv` depending on which target being configured

---

### F4. Remote Errors Log Attribute

**Description**: Logs all attributes to the nifi-app.log for diagnostic purposes if a failure arises when processing steps specific to remote (or local) target.

**Native Processor**: [org.apache.nifi.LogAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.LogAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

In a production setting, you may want to eliminate this processing step altogether.

---

## Retry Steps

---

### R1. Set Retry Counter if Not Set

**Description**: Establishes an attribute to use as a retry counter for this retry loop.

**Native Processor**: [org.apache.nifi.UpdateAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-update-attribute-nar/1.11.4/org.apache.nifi.processors.attributes.UpdateAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

---

### R2. Retry Continuation Check

**Description**: Applies conditional checks for routing the flow based upon requirements of this retry loop.  For example, this may check that the retry counter is beneath a threshold setting, or that an error encountered matches a specific type.

**Native Processor**: [org.apache.nifi.RouteOnAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.RouteOnAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.  By default, the retry handlers allow for up to 5 times running through the loop.  If you want to alter that value it is done within this processing step.

---

### R3. Increment Retry Counter

**Description**: Increment the counter and perform any minor attribute processing as part of the retry loop.  For example, clearing the state of an error message to ensure continuation checks on subsequent runs aren't subverted.

**Native Processor**: [org.apache.nifi.UpdateAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-update-attribute-nar/1.11.4/org.apache.nifi.processors.attributes.UpdateAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

---

## Split File Steps

---

### S1. Check for Large File

**Description**: Right out of the gate this template differs from the static template in that it has special handling for large files with sizes over 4GB.  A split step is injected between the normal steps 1 and 2.

**Native Processor**: [org.apache.nifi.RouteOnAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.RouteOnAttribute/index.html)

**Key Configuration to Adjust**:

By default, file splitting occurs at 4GB (42949672960 bytes).  This same size is applied for the actual split and join processors.  This size allows for a file part of 4GB or less to be sent over network into cloud providers within 1 hour time limit if the network bandwidth is at least 10 mbit/second.  [This calculator](https://techinternets.com/copy_calc) can be handy in determining time to transfer given size and bandwidth available.

If you alter the size used in this step, then you should also plan to change the size used in step S2 of this template, as well as related steps in other templates applied (e.g. Step 5 of [Download Grey Matter Data to File System](./GM_Data_to_FileSystem.md))

---

### S2. Split Files Into 4GB Parts

**Description**: Any file larger than 4GB will be split up on 4GB boundaries into multiple file parts.  Flow files will be created for each newly created file with updated path, absolute.path, filename, and size attributes.  Other attributes pertaining to file permissions, ownership, and date stamps will be carried over from the original.

**Native Processor**: [org.apache.nifi.ExecuteGroovyScript](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-groovyx-nar/1.11.4/org.apache.nifi.processors.groovyx.ExecuteGroovyScript/index.html) with Custom Script [Split Files Into 4GB Parts](../SplitFiles.md)

**Key Configuration to Adjust**:

- _Script Body_ - The following `Configurable Variables` at the top of the script can be altered

  - fps - This is the file part size to split on.  This should match the same sized used for joining files during an import.  The default is `4G`
  - tf - The temporary folder location that split files will be written to.  The NiFi process must have read/write access to this path.  If this value is changed, be sure to also update the same value in S3.
  - mfs - The maximum file size that will be processed. Any file with a size over this will be sent to the failure relationship.

**Relationship & Backflow Guidance**:

In the success flow, the Back Pressure Object Threshold is set to 500 flow files, and a maximum size of 1 GB.  500 flow files, at 4GB each will result in up to 2TB of disk space consumed on the working drive, plus any overage as the processing step can result in more flow files beyond the back pressure.  The system this was tested on used a working drive of about 3.5TB, to process over 20TB of data from an external drive.  If your available working drive is smaller than 2TB, then you should lower the Back Pressure Object Threshold.

---

### S3. Remove Split Files When Loaded

**Description**: Performs housekeeping on temporary split files produced in step S2 to clean up disk space.  When the size of the dataset being processed exceeds the free space available on the working storage area, files need to be cleaned up as soon as they can be.  Once the Set Flowfile File Content step has occurred, a copy of the file contents is retained separately and travels with the flow file.  This step supports removal of that file, as well as any parent folders leading to the temporary folder root if they are also empty.

**Native Processor**: [org.apache.nifi.ExecuteGroovyScript](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-groovyx-nar/1.11.4/org.apache.nifi.processors.groovyx.ExecuteGroovyScript/index.html) with Custom Script [Split Files Into 4GB Parts](../SplitFiles.md)

**Key Configuration to Adjust**: 
- _Script Body_ - The following `Configurable Variables` at the top of the script can be altered

  - tf - The temporary folder location that split files were written to.  The NiFi process must have read/write access to this path to be able to delete files. If this value is changed, be sure to also update the same value in S2.

---

## Extra Steps

### X1. Set Target Instance

**Description**: This is a simple step that creates a flow file attribute for denoting the target-instance value.  This is generally for diagnostic purposes and used in F4 step.

**Native Processor**: [org.apache.nifi.UpdateAttribute](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-update-attribute-nar/1.11.4/org.apache.nifi.processors.attributes.UpdateAttribute/index.html)

**Key Configuration to Adjust**:

In general, no adjustment is necessary from default configuration.

In a production setting, you may want to eliminate this processing step altogether.
