#!/bin/bash

docker exec -it `docker ps -aqf "name=gmd-sdk_nifi_1"` /bin/bash
