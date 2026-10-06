---
name: java-testing
description: Use this skill when testing Java code, including the Inf compiler written in Java.
---

# Java Testing

Prefer focused tests for the affected compiler stage.
Do not introduce new testing frameworks unless requested.

## Tool setup

Run from the repository root.
Use the JDK matching `java.version` in `pom.xml`, including support for that release's preview features. 
Set `JAVA_HOME` to the installed JDK if needed; check `java -version` and Maven's `-version` output.

Use existing Maven:
* Check PATH first, 
* then the configured IDE's bundled Maven installation.
  * Discover the IDE installation and project SDK locally.

An executable missing from PATH does not mean it must be installed.
In PowerShell, invoke a discovered executable with `& $mavenExecutable`.

## Running tests

Use the existing Maven/JUnit setup, combining affected test classes in one invocation:

```shell
mvn -q "-Dtest=ClassATest,ClassBTest" test
```

Check the exit code and `target/surefire-reports`.
Fix launcher or compilation failures before diagnosing assertions.
Do not mask failures with automatic retries.

## Snapshots

The snapshot framework deliberately fails when creating or force-updating snapshots.
For intentional updates, add `-DforceUpdateSnapshots=true` to the focused test command.
Review the snapshot diff, then rerun without that flag.
Do not regenerate existing snapshots merely to make a failing test pass.
