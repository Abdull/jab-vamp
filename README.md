# jab-vamp
jab vamp. Music collection BPM and key detection. Java-based and command-line friendly. Based on [*tfriedel/trackanalyzer*](https://github.com/tfriedel/trackanalyzer).

Build from the command-line as outlined in the following:

```
# get Jave 1.0.2 from http://www.sauronsoftware.it/projects/jave/download.php , then install its JAR artifact
mvn install:install-file -Dfile=jave-1.0.2.jar -DgroupId=it.sauronsoftware.jave -DartifactId=jave -Dversion=1.0.2 -Dpackaging=jar && \
mvn package
```

Once build has finished, start up like so:
```
java -jar target/jab-vamp-0.0.3-jar-with-dependencies.jar $(cygpath --mixed ~/my-music-directory/)
```
