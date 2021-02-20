#!/bin/bash

args="$@"

cd $(dirname "$0")

mvn compile exec:java -Dexec.mainClass=org.vatplanner.dataformats.vatsimpublic.examples.dump.Dump -Dexec.args="${args}"
