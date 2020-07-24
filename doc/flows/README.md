# NiFi Flows for Grey Matter Data

Documentation for all of the NiFi flows for getting files into and out of Grey Matter Data using NiFi.

## Flows

| Name | Description |
| --- | --- |
| [File System to GM Data (Static Permissions)](./FileSystem_to_GM_Data_(Static).md) | Write Files to GM Data using the same permissions for all files |
| [File System to GM Data (Dynamic Permissions)](./FileSystem_to_GM_Data_(Dynamic).md) | Write Files to GM Data using permissions that emulate what is on the file system |
| [File System to GM Data (Multi-Instance)](./File_System_to_GM_Data_(Multi-Instance).md) | Basic flow to write Files to GM Data using the same permissions for all files and upload to multiple instances of GM Data |
| [File System to GM Data (With File Splitting)](./FileSystem_to_GM_Data_(With_File_Splitting).md) | Write Files to GM Data including support for splitting larger files, local and remote instance, retry handlers, and summary reports for success and failures. |
| [S3 to GM Data](./S3_to_GM_Data_local_and_remote.md) | Take files out of an Amazon S3 bucket and write them to GM Data |
| [FTP to GM Data](./FTP_to_GM_Data.md) | Migrate files from an FTP server to GM Data |
| [Download Grey Matter Data to File System](./GM_Data_to_FileSystem.md) | Recreate file system locally from GM Data |

