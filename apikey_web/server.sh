#!/bin/bash

JAR_FILE=target/scala-2.11/apikey_web.jar

PORT=8091 java -jar ${JAR_FILE} -Xmx2048m -Xms2048m
