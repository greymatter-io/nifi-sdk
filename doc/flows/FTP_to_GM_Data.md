# FTP Server to Grey Matter Data

The template for transferring files from an FTP server to an instance of Grey Matter Data.

## Template Location

Download template for [Retrieve from FTP and Upload File System to Grey Matter Data](../../nifi-templates/FTP_GM_Data.xml)

## Flow visualization

![File System to GM Data](visualizations/FTP_to_GM_Data.png)

## Steps

---

### 1. List Files in FTP Server

**Description**: Creates flow files based upon files in an FTP server with optional recursive handling.

**Native Processor**: [org.apache.nifi.ListFTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.ListFTP/index.html)

**Key Configuration to Adjust**:

- _Hostname_ - The template defaults to `ftp.docker`. You should change this for your environment.
- _Port_ - The template defaults to `21`.
- _Username_ - The template defaults to `username`. You should change this for your environment.
- _Password_ - The template defaults to `mypass`, stored as a sensitive value. You should change this for your environment.
- _Remote Path_ - The template defaults to `/`.
- _Search Recursively_ - The template defaults to `true`.  This is most likely the scenario you will want but worth noting to consider in edge cases.  
- _Connection Mode_ - The template defaults to `Active`.  The default for this processor is `Passive`. Use the appropriate setting for the FTP server you are connecting to.

---

### 2. Set FlowFile File Content

**Description**: Retrieves file content from an FTP server and puts into the flow file for processing.

**Native Processor**: [org.apache.nifi.FetchFTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.FetchFTP/index.html)

**Key Configuration to Adjust**:

These values should match those in the List Files in FTP Server step.

- _Hostname_ - The template defaults to `ftp.docker`. You should change this for your environment.
- _Port_ - The template defaults to `21`.
- _Username_ - The template defaults to `username`. You should change this for your environment.
- _Password_ - The template defaults to `mypass`, stored as a sensitive value. You should change this for your environment.
- _Connection Mode_ - The template defaults to `Active`.  The default for this processor is `Passive`. Use the appropriate setting for the FTP server you are connecting to.

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
- _Object Policy_ - The default setting in the template grants full permissions to a user having nifinpe@example.com email address, as well as read, execute, and create privileges to everyone else.  Most implementations will want to use a robust object policy tailored to the given use case, and in conjuction with properties Userfield Folder Object Policy, and Intermediate Folder Object Policy.
- _Original Object Policy_ - The template effectively leaves this as the default setting which will yield an empty policy.  For integrations that are migrating data, this field is intended to store a value representative of an originating access control policy in JSON format.  Similarly, the properties Userfield Folder Original Object Policy and Intermediate Folder Original Object Policy are worth attention.
- _Security_ - The default labels for this, along with Userfield Folder Security and Intermediate Folder Security should be tailored accordingly to represent an overall label for that level of the folder hierarchy and coloring for user interface purposes.  
- _Remote URL_ - As the name implies, this endpoint should be changed to reflect the root of the Grey Matter Data instance for which this processor will interrogate to create folder hierarchy.  The default value in the template is actually traversing through a proxy that will supersede the USER_DN value.
- _SSL Context Service_ - When communicating with Grey Matter Data, a client keystore and trust store may be established.  A valid PKI certificate will be needed to upload files into Grey Matter Data.
- _USER_DN_ - This dynamic property is prepopulated with a subject distinguished name from the certificate to use for identity.  The same will be populated and overwritten automatically when communicating with Grey Matter Data through an edge proxy.  This value only needs to be set when communicating directly to Grey Matter Data without an intermediary.

---

### 5. Prepare Request for GM Data

**Description**: Transform file to multipart/form-data request and assembles JSON representation of metadata.

**Custom Processor**: [com.deciphernow.greymatter.PrepareWriteRequest](../PrepareWriteRequest.md)

**Key Configuration to Adjust**:
- _Object Policy_ - The default setting in the template grants full permissions to a user having nifinpe@example.com email address, as well as read only permissions to everyone else.  Most implementations will want to use a robust object policy tailored to the given use case.
- _Security_ - The default labels for this should be tailored accordingly to represent an overall label for the file being uploaded and coloring for user interface purposes.
- _Custom_ - The default value for this is an example only, and most production purposes will either want to clear this, or populate a custom json structure.

---

### 6. Send to GM Data

**Description**: Send the prepared HTTP request to GM Data. Successful response is JSON which could be further processed.

**Native Processor**: [org.apache.nifi.InvokeHTTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.11.4/org.apache.nifi.processors.standard.InvokeHTTP/index.html)

**Key Configuration to Adjust**:
- _Remote URL_ - The Remote URL for the Grey Matter Data service that the file should be uploaded to.  This should be based upon the same Remote URL used for Building the Folder Hierarchy. At this time, the value specified here should be in the format of <br />`https://{name-or-address}:{port}/{path-to-grey-matter-data}/write`
- _SSL Context Service_ - When communicating with Grey Matter Data, a client keystore and trust store may be established.  A valid PKI certificate will be needed to upload files into Grey Matter Data. 
- _Content-Type_ - Expects the flowfile attribute to be set for ${mime.type}, which originated in step named 'Identify Mime Type'
- _USER_DN_ - This dynamic property is prepopulated with a subject distinguished name from the certificate to use for identity.  The same will be populated and overwritten automatically when communicating with Grey Matter Data through an edge proxy.  This value only needs to be set when communicating directly to Grey Matter Data without an intermediary.



