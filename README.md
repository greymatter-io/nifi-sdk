# Grey Matter NiFi SDK

This project contains custom Apache NiFi processors, groovy scripts, and sample templates for integrating with Grey Matter Data.

## Processors

This repository contains custom processors packaged in a NAR file, as well as source scripts for groovy processors that may be manually loaded and tailored to specific needs.
Extensive documentation for the processors as well as sample flows can be found in the [doc](./doc/README.md) folder.

In general, the processors facilitate creating folder hierarchy, preparing upload file requests, and listing files.
Auxiliary processors support converting unix based file permissions and access control model (ACM) structures to the proprietary policy format suitable for Grey Matter Data.

Templates are available in the [nifi-templates](./nifi-templates) folder.

## Dependencies

- This project may be built with Maven from the gmd-sdk folder
- The project must be built with Java 8
- maven downloads and compiles Scala code with version 2.12


## Testing
This is built and tested with Maven.
To run unit tests:

```bash
cd gmd-sdk
docker-compose up -d
source .environment
mvn clean test
```

## Building
This is built and installed into local repository with Maven.
To build the processors:

```bash
cd gmd-sdk
docker-compose up -d
source .environment
mvn clean install
```

The resulting NAR file should end up in the target folder as well as your M2 repository within
```bash
~/.m2/repository/com/deciphernow/greymatter/nifi-greymatter-data-nar/
```
