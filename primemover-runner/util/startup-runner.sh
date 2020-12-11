#!/bin/sh -e
cd /opt/primemover-runner
/usr/bin/java -jar /opt/primemover-runner/primemover-runner-0.0.1-SNAPSHOT.jar > /dev/null 2>&1 &
