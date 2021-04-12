#!/bin/bash

mvn install:install-file -Dfile=./res/soot-infoflow-android-classes.jar -DgroupId=de.tud.sse -DartifactId=soot-infoflow-android -Dversion=2.8.0 -Dpackaging=jar