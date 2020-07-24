# ListFiles

## Description:

Retrieves a listing of files from a Grey Matter Data instance. For each file that is listed, creates a FlowFile that represents the file.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Remote URL</b> | | | The RESTful endpoint for Grey Matter Data. This will be configured with the endpoint as routed through a local Grey Matter Proxy. |
| SSL Context Service | | | The [SSL Context Service](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-ssl-context-service-nar/1.10.0/org.apache.nifi.ssl.StandardSSLContextService/) used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy. |
| <b>Input Directory</b> | | | The input directory from which files to pull files. |
| <b>Recurse Subdirectories</b> | true | true<br />false | Indicates whether to list files from subdirectories of the directory. |

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
