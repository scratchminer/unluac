#!/bin/sh
cd $(dirname $0)/src
javac -d ../build ./**/*.java
cd ../build
jar cvef unluac.Main ../../unluac.jar .
