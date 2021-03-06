## February
- New processors
  - GetFileProperties
- Release 1.0.4
- Enhancements to processors
  - Add optional property `Intermediate Folder Prefix` to GetFileProperties
  - Add support for beginning or ending slashes in URL paths in processors
  - Add support for removing dot segments from paths in GetFileProperties
  - Add support for spaces in paths in GetFileProperties
- Release 1.0.5  

## January
- Update http error message to include request URI
- Release 1.0.3

## December
- Update GetPolicies to be consistent with NiFi 1.12.1 InvokeHttp TLS config

## November
- Update README.md to provide more guidance on processor usage within NiFi

## October
- Upgraded to build on and support NiFi 1.12.1
- Improved processors configuration of Timeouts via additional Properties
- Retry loops in templates reduced from 5 to 2, and backpressure sizing set
- Release 1.0.2
- New Processors
  - S3RequestSplit

## September
- Enhancements to Processors
  - ListFiles support for expression language
  - GetOIDForPath support for expression language
- Bugfixes for GetOIDForPath
  - Expose status code for error
- Release 1.0.1

## July 2020
- Cleaning up repository
- Added nifi flow templates
  - FTP to GM Data

## June 2020
- Initialized Changelog
- Documented nifi flow templates
  - File System to GM Data (static)
  - File System to GM Data (dynamic)
  - File System to GM Data (multi-instance)
  - File System to GM Data (with file split)
  - GM Data to File System
- Integration Testing additions
  - Prepare Write Request processor
  - Split Files script processor
  - Join Files script processor
  - Remove Split Files script processor
- Integration Testing enhancements
  - Get Policies processor
- Bugfixes for
  - Prepare Write Request processor
  - Split Files script processor
  - Join Files script processor
  - File Summary Report script processor
- Docker environment
  - GM Data from 1.0.2 to 1.1.1 tag
  - GM JWT Security from 1.0.1 to 1.1.1 tag


## May 2020

- Documentation
  - Get OID For Path processor
  - Prepare Write Request processor
  - Get Policies processor
  - Build Permissions processor
- New Processors
  - Split Files script processor
  - Join Files script processor
  - Remote Split Files script processor
  - File Summary Report script processor
- Enhance Processors
  - Prepare Write Request processor
  - Get OID For Path processor
  - Build Permissions processor
  - List Files processor
- Docker environment
  - GM Data from latest to 1.0.2 tag
  - GM JWT Security from latest to 1.0.1 tag

## April 2020

- New Processors
  - List Files processor
- Enhance Processors
  - RequestBody renamed to Prepare Write Request
  
