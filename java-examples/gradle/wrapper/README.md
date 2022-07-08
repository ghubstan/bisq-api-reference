# How to upgrade the Gradle version

Visit the [Gradle website](https://gradle.org/releases) and decide the:

 - desired version
 - desired distribution type
 - what is the sha256 for the version and type chosen above

Adjust the following command with tha arguments above and execute it twice:

```asciidoc
$ ./gradlew wrapper --gradle-version 7.3.3 \
    --distribution-type bin \
    --gradle-distribution-sha256-sum b586e04868a22fd817c8971330fec37e298f3242eb85c374181b12d637f80302
```

The first execution should automatically update:

- `java-examples/gradle/wrapper/gradle-wrapper.properties`

The second execution should then update:

- `java-examples/gradle/wrapper/gradle-wrapper.jar`
- `java-examples/gradlew`
- `java-examples/gradlew.bat`

The four updated files are ready to be committed.
