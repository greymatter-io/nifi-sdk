# Grey Matter NiFi SDK

This project contains custom Apache NiFi processors, groovy scripts, and sample templates for integrating NiFi with Grey Matter Data.

## Processors

This repository contains custom processors packaged in a NAR file, as well as source scripts for groovy processors that may be manually loaded and tailored to specific needs.
Extensive documentation for the processors as well as sample flows can be found in the [doc](./doc/README.md) folder.

In general, the processors facilitate creating folder hierarchy, preparing upload file requests, and listing files.
Auxiliary processors support converting unix based file permissions and access control model (ACM) structures to the proprietary policy format suitable for Grey Matter Data.

Templates are available in the [nifi-templates](./nifi-templates) folder.

## Dependencies

- This project may be built with Maven from the gmd-sdk folder
- The project must be built with Java 8
- Maven downloads and compiles Scala code with version 2.12
- Builds on NiFi 1.12.1


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
docker-compose up -d # Skip this step if you are skipping tests
source .environment
mvn clean package
# To skip tests:
mvn package -DskipTests
```

The resulting NAR file should end up in the target folder

`gmd-sdk/nifi-data-nar/target`

as well as your M2 repository within

`~/.m2/repository/com/deciphernow/greymatter/nifi-greymatter-data-nar/`

## Using processors in NiFi
The easiest way to start using these processors is simply running the docker-compose file in `./gmd-sdk`. Otherwise you can follow the steps below:

1. Get the NAR from either the build steps above or from one of the releases in https://github.com/greymatter-io/nifi-sdk/releases.
2. Before starting NiFi, place the NAR file in `./lib`
 * To preload templates place them in `./conf/templates`
3. Start NiFi. Load the UI in your web browser. The default URL is http://localhost:8080/nifi.
4. To add a processor to your workflow follow the instructions at https://nifi.apache.org/docs/nifi-docs/html/getting-started.html#adding-a-processor
 * To add a template to your workflow follow the instructions at https://nifi.apache.org/docs/nifi-docs/html/getting-started.html#working-with-templates

Here are some helpful links for navigating and using NiFi in general as well as customizing your NiFi setup
* https://nifi.apache.org/docs/nifi-docs/html/user-guide.html
* https://nifi.apache.org/docs/nifi-docs/html/getting-started.html
* https://nifi.apache.org/docs/nifi-docs/html/administration-guide.html
