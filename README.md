# PostScript Kotlin Interpreter
CPTS 355 - Programming Language Design

## Overview
This project implements a simplified line-oriented PostScript interpreter written in **Kotlin** using **Gradle**.  
It is designed for incremental development in a course environment where semantic features are added over time.

## Build, Run & Tests
Requires JDK and Gradle (the Gradle wrapper is included via the Kotlin plugin).
````
To build the project:
./gradlew build
./gradlew installDist

To run with dynamic scoping (default):
./gradlew run
or
./build/install/postscript-interpreter/bin/postscript-interpreter

To run with lexical scoping:
./gradlew run --args="--lexical"
or
./build/install/postscript-interpreter/bin/postscript-interpreter --lexical

To execute tests:
./gradlew test
````
Note: Use the `./build/install/postscript-interpreter/bin/postscript-interpreter` instead of `./gradlew run`
for a better representation of the postscript repl (if you use ./gradlew run there will be progress bars all over the terminal, which can make it slightly difficult to understand what you typed). 
