# Split S3 Requests Into 4GB Parts

## Description

This is an auxiliary script that can be used as part of a NiFi flow to break up S3 file requests for larger files into smaller segments.  NiFi 1.12.2+ adds support for range requests in the FetchS3Object processor.  For configurations where local disk storage is limited, this script can take the results of ListS3 processor, and create flow files that initialize attributes for the range requests.

### Variables

Variables that can be configured are near the top of the script

| Name | Description |
| --- | --- |

| mfs | Maximum Filesize that will be processed. Any file with a size over this will be sent to failure relationship. Default is 1TB |
| fps | File Part Size to split on (see man page for split command). Default is 4G |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

Expects to read attributes written by the ListS3 processor

| Name | Description |
| --- | --- |
| s3.bucket | The name of the s3 bucket |
| filename | The name of the file |
| s3.etag | The ETag that can be used to see if the file has changed |
| s3.isLatest | A boolean indicating if this is the latest version of the object |
| s3.LastModified | The last modified time in milliseconds since epoch in utc time |
| s3.length | The size of the object in bytes |
| s3.storeClass | The storage class of the object | 
| s3.version | The version of the object, if applicable |

### Writes Attributes:

New flowfiles are created as a result of this processor. For each flowfile, these attributes are written:

| Name | Description |
| --- | --- |
| s3.bucket | The name of the s3 bucket |
| filename | The name of the file |
| s3.etag | The ETag that can be used to see if the file has changed |
| s3.isLatest | A boolean indicating if this is the latest version of the object |
| s3.LastModified | The last modified time in milliseconds since epoch in utc time |
| s3.length | The size of the object in bytes |
| s3.storeClass | The storage class of the object | 
| s3.version | The version of the object, if applicable |
| s3requestsplit.error.message | This attribute will contain the error message if an exception is raised. |
| split.totalparts | If the incoming flowfile required splitting, this will be set to the total number of parts for the content |
| split.originalfilename | A copy of the value originating in filename. Useful as filename should be altered in a post process step after content retrieval to deconflict |
| split.part | Part number for ordering reassembly |
| split.suffix | A generated filename suffix consistent with the split command results for naming. Can be used after retrieval of ranged content for assigning a new name |
| s3-object-range-start | The byte to start the range request, 0 based |
| s3-object-range-length | The length of the range request to setup |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component requires an incoming relationship.

### System Resource Considerations:

1. Setting the file part size (fps) to a small value can result in an exceptionally high number of flowfiles created for large files.  
2. The maximum number of parts that will be generated is 17576 (26^3) to fit within the split.suffix limits of 3 letters.


### See Also:

[Readme](./README.md),
[Split Files Into 4GB Parts](./SplitFiles.md),
[Remove Split Files When Done](./RemoveSplitFiles.md),
[Join Files From 4GB Parts](./JoinFiles.md),
[File Summary Report](./FileSummaryReport.md),
[Upload File System to Grey Matter Data with File Splitting](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

### Script Contents:

Use the native ExecuteGroovyScript processor, and set the [linked script](../nifi-script-processors/SplitFiles.groovy) as the Script Body
