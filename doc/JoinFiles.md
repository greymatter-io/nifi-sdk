# Join Files From 4GB Parts

## Description

This is an auxiliary script that can be used as part of a NiFi flow to rejoin files in a folder that were previously split up.  

### Variables

Variables that can be configured are near the top of the script

| Name | Description |
| --- | --- |
| fps | File Part Size previously split on. (see man page for split command) |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| baseOutputDirectory | Combined with the value for path this provides the equivalent of an absolute.path.  Recommend defining this as an attribute in a prior processor and use for Directory Output in PutFile |
| filename | The name of the file that was read from filesystem. |
| file.size | The number of bytes in the file in filesystem. | 
| path | The relative path to the file. |

### Writes Attributes:

Flowfile content may be rewritten as a result of this processor if it encounters what is perceived to be the last filepart in a multipart file set.

| Name | Description |
| --- | --- |
| joinfiles.error.message | This attribute will contain the error message if an exception is raised. |
| filename | The name of the file. This will be updated if the flowfile is written with joined content. |
| path | The path to the file. This will be updated if the flowfile is written with joined content. |
| absolute.path | The absolute path to the file on disk.  This will be set if the flowfile is rewritten. |
| file.size | The length of the file in bytes. This will be updated if the flowfile is written with joined content.  It should match the value of the fileSize attribute which is written as a byproduct. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

1. The NiFi process needs to have read/write access to the files denoted by the baseOutputDirectory and path.  
2. On successfully joining a file, file parts are deleted, as well as the directory placeholder.


### See Also:

[Readme](./README.md),
[Split Files Into 4GB Parts](./SplitFiles.md),
[Remove Split Files When Done](./RemoveSplitFiles.md),
[File Summary Report](./FileSummaryReport.md),
[Upload File System to Grey Matter Data with File Splitting](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

### Script Contents:

Use the native ExecuteGroovyScript processor, and set the [linked script](../nifi-script-processors/JoinFiles.groovy) as the Script Body
