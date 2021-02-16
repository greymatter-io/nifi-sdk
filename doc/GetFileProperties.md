# GetOidForPath

## Description:

Retrieves file properties of a GMData object.

### Tags:

gmdata

### Properties:

In the list below, the names of required properties appear in bold. Any other properties (not in bold) are considered optional. The table also indicates any default values, and whether a property supports the NiFi Expression Language.

| Name | Default Value | Allowable Values | Description |
| --- | --- | --- | --- |
| <b>Remote URL</b> | ${gmdata.remoteurl} | | The RESTful endpoint for Grey Matter Data. This will be configured with the endpoint as routed through a local Grey Matter Proxy.<br /><b>Supports Expression Language: true</b> |
| SSL Context Service | | | The [SSL Context Service](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-ssl-context-service-nar/1.11.4/org.apache.nifi.ssl.StandardSSLContextService/) used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy. |
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
| path | The path to the file from which properties of the file are pulled. |
| filename | The name of the file. |

### Writes Attributes:

| Name | Description |
| --- | --- |
| gmdata.status.code | The status code returned by GM Data when calling the props endpoint. |
| gmdata.file.props | The raw metadata of a file from GM Data or an error response. |

### State Management:

This component does not store state.

### Restricted:

This component is not restricted.

### Input Requirement:

This component allows an incoming relationship.

### System Resource Considerations:

None specified.

### See Also:

[Readme](./README.md)
