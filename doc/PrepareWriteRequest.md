# PrepareWriteRequest

## Description:

A processor that builds a Grey Matter Data compatible request body for a given file path.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Object Policy</b> | ${gmdata.objectpolicy} | | An interface (JSON) representation of a lisp like language that conveys that access constraints for retrieval of the file once stored in Grey Matter Data. This flexible policy allows for translating complex authorization schemes from a variety of systems.<br /><b>Supports Expression Language: true</b> | 
| <b>Folder Object ID</b> | ${gmdata.parentoid} | | A string representing the parent object identifier that acts as a reference to the folder item in Grey Matter Data that should enclose this file<br /><b>Supports Expression Language: true</b>|
| Original Object Policy | ${gmdata.originalobjectpolicy} | | <br /><b>Supports Expression Language: true</b>|
| Security | ${gmdata.security} | | A JSON representation of the security block used for user interfaces, consisting of a label, foreground, and background.<br /><b>Supports Expression Language: true</b>| 
| Action | C | C<br />R<br />U<br />D<br />P<br />X | A string denoting the action for the event that will be prepared |
| Custom | | | A JSON structure containing custom fields. |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| chunk.size | The maximum amount of the file to read into memory at a time when rewriting the stream. Default is 100000. |
| filename | The name of the file that will be uploaded. |
| file.size | The length of the file contents in bytes. |
| mime.type | The mime type for the file. This can be obtained via a call to FetchFile or IdentifyMimeType processors. |

### Writes Attributes:

None specified.

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
[BuildPermissions](./BuildPermissions.md),
[GetOidForPath](./GetOidForPath.md),
[GetPolicies](./GetPolicies.md),
[ListFiles](./ListFiles.md)
