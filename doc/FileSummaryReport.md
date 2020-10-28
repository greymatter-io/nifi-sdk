# File Summary Report

## Description

This is an auxiliary script that can be used as part of a NiFi flow to append lines to a comma separated value (CSV) file that contain information about processed files.

### Variables

Variables that can be configured are near the top of the script

| Name | Description |
| --- | --- |
| csvfile | The path and name of the file to write output to |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| uuid | The unique universal identifier assigned to the flowfile when it was created. |
| filename | The name of the file that was read from filesystem. |
| absolute.path | The absolute.path is the absolute path of the file's directory on filesystem. |
| file.size | The number of bytes in the file in filesystem. | 
| split.part | If the file was split from a larger file, then this is the part number (1 based). |
| split.originalfilename | If the file was split from a larger file, then this is the original file name and absolute path. |

### Writes Attributes:

New flowfiles are created as a result of this processor. For each flowfile, these attributes are written:

| Name | Description |
| --- | --- |
| filesummaryreport.error.message | This attribute will contain the error message if an exception is raised. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

1. The NiFi process needs to have read/write access to the value specified for the CSV File (csv variable)
2. Each flowfile processed will append a new line to the file. There is no truncation, so over time it is possible that this can fill the disk if not managed.


### See Also:

[Readme](./README.md),
[Split Files Into 4GB Parts](./SplitFiles.md),
[Split S3 Requests Into 4GB Parts](./S3RequestSplit.md),
[Remove Split Files When Done](./RemoveSplitFiles.md),
[Join Files From 4GB Parts](./JoinFiles.md),
[Upload File System to Grey Matter Data with File Splitting](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

### Script Contents:

Use the native ExecuteGroovyScript processor, and set the [linked script](../nifi-script-processors/FileSummaryReport.groovy) as the Script Body