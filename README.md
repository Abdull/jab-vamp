# jab vamp
*jab vamp*. Music collection BPM (*beats per minute*) and key detection. Any kind of music file format. Java-based and command-line friendly. Based on [*tfriedel/trackanalyzer*](https://github.com/tfriedel/trackanalyzer).


# Prerequisites
* Java 8
* Maven 
* ffmpeg ([install *ffmpeg*](https://ffmpeg.org/download.html) and have its `ffmpeg`/`ffmpeg.exe` executable (and its sibling `fprobe`,`ffprobe.exe`) on your `$PATH`).


# Building
Build `jab vamp` on the command line with *Maven*:
```
cd ~/git/

git clone git@github.com:Abdull/jab-vamp.git

cd ~/git/jab-vamp/

mvn clean package
```

# Executing
Once *jab vamp* has build successfully, you can start it up nd have it analyze music files and directory hierachies:
```
cd ~/git/jab-vamp/

java -jar target/jab-vamp-0.0.4-SNAPSHOT-jar-with-dependencies.jar /path/to/someMusicFileThatFFmpegSupports.mp3 $(cygpath --mixed ~/my-music-directory/)
```

After executing this command, soon, output similar to the following will appear on *stdout*:

```
/path/to/someMusicFileThatFFmpegSupports.mp3;2A;132.2
~/my-music-directory/fileB.mp3;7B;136.4
~/my-music-directory/recursive/directory/fileC.flac;10A;133.3

...

```
