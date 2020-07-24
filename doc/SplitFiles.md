# Split Files Into 4GB Parts

## Description

This is an auxiliary script that can be used as part of a NiFi flow to break up larger files into smaller segments.  

### Variables

Variables that can be configured are near the top of the script

| Name | Description |
| --- | --- |
| tf | Temp folder location to write split files. The NiFi process must have read/write access |
| mfs | Maximum Filesize that will be processed. Any file with a size over this will be sent to failure relationship |
| fps | File Part Size to split on (see man page for split command) |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| filename | The name of the file that was read from filesystem. |
| path | The path for the file that needs to be created as folders. |
| absolute.path | The absolute.path is set to the absolute path of the file's directory on filesystem. For example, if the Input Directory property is set to /tmp, then files picked up from /tmp will have the path attribute set to "/tmp/". If the Recurse Subdirectories property is set to true and a file is picked up from /tmp/abc/1/2/3, then the path attribute will be set to "/tmp/abc/1/2/3/". |
| file.owner | The user that owns the file in filesystem. | 
| file.group | The group that owns the file in filesystem. |
| file.permissions | The permissions for the file in filesystem. This is formatted as 3 characters for the owner, 3 for the group, and 3 for other users. For example rw-rw-r--. |
| file.size | The number of bytes in the file in filesystem. | 
| file.lastModifiedTime | The timestamp of when the file in filesystem was last modified as 'yyyy-MM-dd'T'HH:mm:ssZ'. |
| file.lastAccessTime | The timestamp of when the file in filesystem was last accessed as 'yyyy-MM-dd'T'HH:mm:ssZ'. |
| file.creationTime | The timestamp of when the file in filesystem was created as 'yyyy-MM-dd'T'HH:mm:ssZ'. |

### Writes Attributes:

New flowfiles are created as a result of this processor. For each flowfile, these attributes are written:

| Name | Description |
| --- | --- |
| splitfiles.error.message | This attribute will contain the error message if an exception is raised. |
| split.part | The part number (1 based) for the file. |
| split.totalparts | The total number of parts making up the original file. |
| split.originalfilename | The original file name and absolute path. |
| filename | The filename for this split part. |
| path | The relative path to this split part based upon the original relative path. |
| absolute.path | The absolute path to the split part within the temporary folder location. |
| file.owner | The user that owns the file part in the sytem, carried over from the original file. |
| file.group | The group that owns the file part in the system, carried over from the original file. |
| file.permissions | The permissions for the file part in filesystem. This is formatted as 3 characters for the owner, 3 for the group, and 3 for other users. For example rw-rw-r--. |
| file.size | The number of bytes in this file part. |
| file.LastModifiedTime | The timestamp of when the file in filesystem was last modified as 'yyyy-MM-dd'T'HH:mm:ssZ', carried over from the original file. |
| file.LastAccessTime | The timestamp of when the file in filesystem was last accessed as 'yyyy-MM-dd'T'HH:mm:ssZ', carried over from the original file. |
| file.creationTime | The timestamp of when the file in filesystem was created as 'yyyy-MM-dd'T'HH:mm:ssZ', carried over from the original file. | 

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

1. The NiFi process needs to have read/write access to the value specified for the Temporary Folder location (tf variable)
2. The Split command will use disk space in the temporary folder location until those files are subsequently removed. Flowfiles should take this into account and apply adequate backpressure to ensure the disk does not fill up.  


### See Also:

[Readme](./README.md),
[Remove Split Files When Done](./RemoveSplitFiles.md),
[Join Files From 4GB Parts](./JoinFiles.md),
[File Summary Report](./FileSummaryReport.md),
[Upload File System to Grey Matter Data with File Splitting](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml)

### Script Contents:

Use the native ExecuteGroovyScript processor, and set the [linked script](../nifi-script-processors/SplitFiles.groovy) as the Script Body
