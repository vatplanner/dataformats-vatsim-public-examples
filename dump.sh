#!/bin/bash

args="$@"

mvn compile exec:java -Dexec.mainClass=org.vatplanner.dataformats.vatsimpublic.examples.dump.Dump -Dexec.args="${args}"
