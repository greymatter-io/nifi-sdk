# Remove Files When Done

## Description

This is an auxiliary script that can be used as part of a NiFi flow to remove a split file once it has been loaded into a flow file.  This allows for cleaning up temporary files as they are being worked, to free up disk space.  In addition to removing the split file, the folder path leading to the split file part will also be removed if there are no other files contained within it.

### Variables

Variables that can be configured are near the top of the script

| Name | Description |
| --- | --- |
| tf | Temp folder location to read split files. The NiFi process must have read/write access. It should be the same value set for the variable in [Split Files Into 4GB Parts](./groovy-SplitFiles.md) script. |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| filename | The filename for this split part. |
| path | The relative path to this split part based upon the original relative path. |
| absolute.path | The absolute path to the split part within the temporary folder location. |
| split.part | The part number (1 based) for the file. |

### Writes Attributes:

Flowfiles that are transferred to the failure relationship will have an attribute whose value denotes the cause of the failure:

| Name | Description |
| --- | --- |
| removefile.error.message | This attribute will contain the error message if an exception is raised. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

1. The NiFi process needs to have read/write access to the value specified for the Temporary Folder location (tf variable)

### See Also:

[Readme](./README.md),
[Split Files Into 4GB Parts](./SplitFiles.md),
[Join Files From 4GB Parts](./JoinFiles.md),
[File Summary Report](./FileSummaryReport.md),
[Upload File System to Grey Matter Data with File Splitting](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

### Script Contents:

Use the native ExecuteGroovyScript processor, and set the [linked script](../nifi-script-processors/RemoveSplitFiles.groovy) as the Script Body
