# ListFiles

## Description:

Retrieves a listing of files from a Grey Matter Data instance. For each file that is listed, creates a FlowFile that represents the file.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Remote URL</b> | ${gmdata.remoteurl} | | The RESTful endpoint for Grey Matter Data. This will be configured with the endpoint as routed through a local Grey Matter Proxy. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| SSL Context Service | | | The [SSL Context Service](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-ssl-context-service-nar/1.11.4/org.apache.nifi.ssl.StandardSSLContextService/) used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy. |
| <b>Input Directory</b> | | | The input directory from which files to pull files. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| <b>Recurse Subdirectories</b> | true | true<br />false | Indicates whether to list files from subdirectories of the directory. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| URL Filter Argument | | | When present, this will be added as querystring arguments for requests to the /list call. Supported querystring keys are childCount, count, last, and tstamp. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| <b>File Filter</b> | [^\.].* | | Only files whose names match the given regular expression will be picked up. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| Path Filter | | | When Recurse Subdirectories is true, then only subdirectories whose path matches the given regular expression will be scanned. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| <b>Minimum File Age</b> | 0 | | The minimum age,in seconds, that a file must be in order to be pulled; any file younger than this amount of time (according to last modification date) will be ignored. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| Maximum File Age | | | The maximum age, in seconds, that a file must be in order to be pulled; any file older than this amount of time (according to last modification date) will be ignored. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| <b>Minimum File Size</b> | 0 | | The minimum size, in bytes, that a file must be in order to be pulled. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |
| Maximum File Size | | | The maximum size, in bytes, that a file must be in order to be pulled. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |

### Dynamic Properties:

Dynamic Properties allow the user to specify both the name and value of a property.

| Name | Value | Description |
| --- | --- | --- |
| Header name | Attribute Expression Language | Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property.
<br /><b>Supports Expression Language: true</b> |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

None specified.

### Writes Attributes:

| Name | Description |
| --- | --- |
| filename | Name of the file. |
| path | Path to the file relative to the Input Directory provided. |
| file.owner | First identity in objectPolicy that is granted yield-all or yield C R U D X P. | 
| file.size | Length of the file content in bytes. |
| file.lastModifiedTime | A tstamp value associated with the file. | 
| file.creationTime | A tstamp value associated with the file.|
| mime.type | Mime type for the file. |
| gmdata.fileurl | A URL suitable for retrieving the file, built up using the Remote URL in configuration, and the OID associated with the file. |
| gmdata.oid | Object identifier to reference the file. |
| gmdata.parentoid | Object identifier to reference the folder immediately containing the file. |
| gmdata.objectpolicy | JSON structure denoting the object policy for the file defining the access constraints. |
| gmdata.originalobjectpolicy | Any original object policy associated with the file. |
| gmdata.security | JSON structure containing a label, foreground and background information as hints for user interface displays. |
| gmdata.custom | JSON structure containing any custom information associated with the file. |
| gmdata.sha256 | A SHA 256 hash of the file contents. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component does not allow an incoming relationship.

### System Resource Considerations:

None specified.

### See Also:

[Readme](./README.md),
[BuildPermissions](./BuildPermissions.md),
[GetOidForPath](./GetOidForPath.md),
[GetPolicies](./GetPolicies.md),
[PrepareWriteRequest](./PrepareWriteRequest.md)
