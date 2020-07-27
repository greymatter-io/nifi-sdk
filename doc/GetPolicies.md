# GetPolicies

## Description:

A process that will transform a provided permission structure and access control model into the objectPolicy and originalObjectPolicy for assignment into a Grey Matter Data event.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Remote base URL</b> | | | The RESTful endpoint for the Data Policy service. |
| SSL Context Service | | | The [SSL Context Service](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-ssl-context-service-nar/1.11.4/org.apache.nifi.ssl.StandardSSLContextService/) used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy. |
| Connection Timeout | 5 secs | | Max wait time for connection to remote service. |
| Read Timeout | 15 secs | | Max wait time for response from remote service. |
| Attributes to Send | | | Regular expression that defines which attributes to send as HTTP headers in the request. If not defined, no attributes are sent as headers. Also any dynamic properties set will be sent as headers. The dynamic property key will be the header key and the dynamic property value will be interpreted as expression language will be the header value. |

### Dynamic Properties:

Dynamic Properties allow the user to specify both the name and value of a property.

| Name | Value | Description |
| --- | --- | --- |
| Header name | Attribute Expression Language | Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property.
<br /><b>Supports Expression Language: true</b> |

### Relationships: 

| Name | Description |
| --- | --- |
| Failure | The original FlowFile will be routed on any type of connection failure, timeout or general exception. It will have new attributes detailing the request. |
| Response | A Response FlowFile will be routed upon success (2xx status codes). If the 'Output Response Regardless' property is true then the response will be sent to this relationship regardless of the status code received. |

### Reads Attributes:

| Name | Description |
| --- | --- |
| acm | The access control model JSON struct that is representative of the access control attributes to be assigned to the files. |
| permission | A JSON struct comprised of the file permissions to be assigned. This is the input resulting from the [BuildPermissions](./BuildPermissions.md) processor | 

### Writes Attributes:

| Name | Description |
| --- | --- |
| getpolicies.status.code | The status code that is returned |
| getpolicies.status.message | The status message that is returned |
| getpolicies.response.body | In the instance where the status code received is not a success (2xx) then the response body will be put to the 'invokehttp.response.body' attribute of the request FlowFile. |
| getpolicies.request.url | The request URL |
| getpolicies.tx.id | The transaction ID that is returned after reading the response. |
| getpolicies.remote.dn | The DN of the remote server |
| getpolicies.java.exception.class | The Java exception class raised when the processor fails. |
| getpolicies.java.exception.message | The Java exception message raised when the processor fails. |
| user-defined | If the 'Put Response Body In Attribute' property is set then whatever it is set to will become the attribute key and the value would be the body of the HTTP response. |
| gmdata.objectpolicy | A fully populated object policy suitable to populate in a Grey Matter Data event for access control. |
| gmdata.originalobjectpolicy | A compound structure containing the inputs used to produce the object policy. |
| gmdata.security | The security banner information for the Object Policy. |
| gmdata.lisp | Object Policy lisp conversion information. |

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
[ListFiles](./ListFiles.md),
[PrepareWriteRequest](./PrepareWriteRequest.md)
