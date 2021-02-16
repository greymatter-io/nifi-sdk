# GetOidForPath

## Description:

A processor that creates a folder structure in Grey Matter Data based on a given path.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Object Policy</b> | ${gmdata.objectpolicy} |  | An interface (JSON) representation of a lisp like language that conveys that access constraints for retrieval of the file once stored in Grey Matter Data. This flexible policy allows for translating complex authorization schemes from a variety of systems. <br /><b>Supports Expression Language: true</b> |
| Original Object Policy | ${gmdata.originalobjectpolicy} |  | A static string representing the original object policy from the source system. This may be a JSON structure but escaped into a string format.<br /><b>Supports Expression Language: true</b> |
| Security | ${gmdata.security} |  | A JSON representation of the security block used for user interfaces, consisting of a label, foreground, and background.<br /><b>Supports Expression Language: true</b> |
| <b>Remote URL</b> | ${gmdata.remoteurl} | | The RESTful endpoint for Grey Matter Data. This will be configured with the endpoint as routed through a local Grey Matter Proxy.<br /><b>Supports Expression Language: true</b> |
| SSL Context Service | | | The [SSL Context Service](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-ssl-context-service-nar/1.11.4/org.apache.nifi.ssl.StandardSSLContextService/) used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy. |
| Userfield Folder Object Policy | ${gmdata.userfieldfolderdobjectpolicy} | | When provided, this is an override object policy to be assigned to the created userfield folder if the folder does not yet exist.<br /><b>Supports Expression Language: true</b> |
| Userfield Folder Original Object Policy | ${gmdata.userfieldfolderoriginalobjectpolicy} | | When provided, this is an override original object policy to be assigned to the created userfield folder if the folder does not yet exist.<br /><b>Supports Expression Language: true</b> |
| Userfield Folder Security | ${gmdata.userfieldfoldersecurity} | | An interface (JSON) representation of the security block used for user interfaces, consisting of a label, foreground, and background that should be applied when creating the userfield folder.<br /><b>Supports Expression Language: true</b> |
| Intermediate Folder Prefix | ${gmdata.intermediatefolderprefix} | | When provided, this path indicates intermediate folders that should exist between the userfield folder, and the folders passed in.<br /><b>Supports Expression Language: true</b> |
| Intermediate Folder Object Policy | ${gmdata.intermediatefolderobjectpolicy} | | When provided, this is an override policy to be assigned to any created intermediate folders as represented by the Intermediate Folder Prefix.<br /><b>Supports Expression Language: true</b> |
| Intermediate Folder Original Object Policy | ${gmdata.intermediatefolderoriginalobjectpolicy} | | When provided, this is an override original object policy to be assigned to the created intermediate folder if the folder does not yet exist.<br /><b>Supports Expression Language: true</b> |
| Intermediate Folder Security | ${gmdata.intermediatefoldersecurity} | | An interface (JSON) representation of the security block used for user interfaces, consisting of a label, foreground, and background that should be applied when creating intermediate folders that prefix the provided filename path.<br /><b>Supports Expression Language: true</b> |
| Attributes to Send | ${gmdata.attributestosend} | | Regular expression that defines which attributes to send as HTTP headers in the request. If not defined, no attributes are sent as headers. Also any dynamic properties set will be sent as headers. The dynamic property key will be the header key and the dynamic property value will be interpreted as expression language will be the header value.<br /><b>Supports Expression Language: true</b> |
| Http Timeout | 5 | | The duration. in seconds, to wait before an http connection times out. <br /><b>Supports Expression Language: true (Variable Registry Only)</b> |

### Dynamic Properties:

Dynamic Properties allow the user to specify an arbitrary name and value of a property. Any dynamic properties set in this processor will be sent as headers.

| Name | Value | Description |
| --- | --- | --- |
| Header name | Attribute Expression Language | Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property. You can also set a hardcoded value without using expression language.
<br /><b>Supports Expression Language: true</b> |

### Relationships: 

| Name | Description |
| --- | --- |
| success | Any FlowFile that is successfully transferred is routed to this relationship |
| failure | Any FlowFile that fails to be transferred is routed to this relationship | 

### Reads Attributes:

| Name | Description |
| --- | --- |
| path | The path for the file that needs to be created as folders. |

### Writes Attributes:

| Name | Description |
| --- | --- |
| gmdata.parentoid | The object id of the parent folder created by the processor. |

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
[GetPolicies](./GetPolicies.md),
[ListFiles](./ListFiles.md),
[PrepareWriteRequest](./PrepareWriteRequest.md)
