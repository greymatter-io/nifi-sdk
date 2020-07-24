# Grey Matter Data NiFi Processors

Documentation regarding the custom NiFi processors is enclosed in this folder.

## Custom NiFi Processors

The following processors are in the com.deciphernow.greymatter.data.nifi.processors package

| Name | Description |
| --- | --- |
| [BuildPermissions](./BuildPermissions.md) | A processor to replicate unix like file permissions of a file to upload to Grey Matter Data. |
| [GetOidForPath](./GetOidForPath.md) | A processor that creates a folder structure in Grey Matter Data based on a given path. |
| [GetPolicies](./GetPolicies.md) | A processor that will transform a provided permission structure and access control model into the objectPolicy and originalObjectPolicy for assignment into a Grey Matter Data event. |
| [ListFiles](./ListFiles.md) | Retrieves a listing of files from a Grey Matter Data instance. For each file that is listed, creates a FlowFile that represents the file. |
| [PrepareWriteRequest](./PrepareWriteRequest.md) | A processor that builds a Grey Matter Data compatible request body for a given file path. |

## Auxiliary Scripts

The scripts referenced below can be used with the native ExecuteGroovyScript processor to perform auxiliary tasks

| Name | Purpose |
| --- | --- |
| [Split Files Into 4GB Parts](./SplitFiles.md) | For files exceeding preset size, split to multiple fileparts to accomodate lower bandwidth or timeout constraints. | 
| [Remove Split Files When Done](./RemoveSplitFiles.md) | Deletes temporary split file from disk once its content loaded into a flowfile. |
| [Join Files From 4GB Parts](./JoinFiles.md) | Rejoins files that were previously split up. |
| [File Summary Report](./FileSummaryReport.md) | Append lines to a CSV file with file name and size of processed files. |

## Templates

The linked templates are example flows for making use of the custom processors and scripts

| Description | Template | Documentation |
| --- | --- | --- |
| Upload File System to Grey Matter Data with static permissions | [template](../nifi-templates/File_System_to_GM_Data_(Static_Permissions).xml) | [documentation](./flows/FileSystem_to_GM_Data_(Static).md) |
| Upload File System to Grey Matter Data with dynamic permissions | [template](../nifi-templates/File_System_to_GM_Data_(Dynamic_Permissions).xml) | [documentation](./flows/FileSystem_to_GM_Data_(Dynamic).md) |
| Upload File System to Multiple Instances of Grey Matter Data | [template](../nifi-templates/File_System_to_GM_Data_(Multiple_Disparate_Instances).xml) | [documentation](./flows/FileSystem_to_GM_Data_(Multi-Instance).md) |
| Upload File System to Grey Matter Data with File Splitting, Retry Handling, Multiple Instances, and Summary Report for Success and Failures | [template](../nifi-templates/File_System_to_GM_Data_(With_File_Splitting).xml) | [documentation](./flows/FileSystem_to_GM_Data_(With_File_Splitting).md) |
| Upload S3 to Grey Matter Data | [template](../nifi-templates/S3_to_GM_Data.xml) | [documentation](./flows/S3_to_GM_Data.md) |
| Download Grey Matter Data to File System | [template](../nifi-templates/Recreate_File_System_from_GMData.xml) | [documentation](./flows/GM_Data_to_FileSystem.md) |
