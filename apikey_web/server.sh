#!/bin/bash

JAR_FILE=target/scala-2.11/apikey_web.jar

java -Dconfig.file="./application.conf" -jar ${JAR_FILE} -Xmx2048m -Xms2048m
