# BuildPermissions

## Description:

A processor to replicate unix like file permissions of a file to upload to Grey Matter Data.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>File Owner</b> | ${file.owner} | | The user identifier of the file.<br /><b>Supports Expression Language: true</b> |
| <b>File Group</b> | ${file.group} | | The group identifier of the file.<br /><b>Supports Expression Language: true</b> |
| <b>File Other</b> | group/_everyone | | The other / all users identifier of the file.<br /><b>Supports Expression Language: true</b> |
| <b>File Permissions</b> | ${file.permissions} | | The permissions string (e.g. rwxr-xr-x) of the file.<br /><b>Supports Expression Language: true</b> |
| Resource Mapping | | | Used for overriding the value of the File Owner or Group with a replacement value from a JSON structure in the format {name1:newvalue1,name2:newvalue2}. For example `{"daveborncamp": "user/cn=daveborncamp,o=whatever,c=us", "engineers": "group/decipher/engineers"}` maps any file.owner or file.group named daveborncamp to user/cn=daveborncamp,o=whatever,c=us in the final permissions structure. Likewise in this sample, if the file.owner or file.group value is engineers, it would be mapped to group/decipher/engineers.<br /><b>Supports Expression Language: true</b> |

### Relationships: 

| Name | Description |
| --- | --- |
| Success | A successful relationship. Successfully built the permissions json object. |
| Failure | A failed relationship. Failed to build the permissions json object. |

### Reads Attributes:

| Name | Description |
| --- | --- |
| file.owner | The owner of the file as it appears on the Unix system file permissions. The user who owns the file. |
| file.group | The group of the file as it appears on the Unix system file permissions. The group who has access to the file. | 
| file.permissions | The permissions of the file as it appears on the Unix system file permissions. For example `rw-r--r--`. |

### Writes Attributes:

| Name | Description |
| --- | --- |
| permission | A permission structure that can be sent to the Data Policy Converter along with an access control model. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

None specified.

### See Also:

[Readme](./README.md),
[GetOidForPath](./GetOidForPath.md),
[GetPolicies](./GetPolicies.md),
[ListFiles](./ListFiles.md),
[PrepareWriteRequest](./PrepareWriteRequest.md)
