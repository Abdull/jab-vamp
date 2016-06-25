# jab vamp
*jab vamp*. Fast music collection BPM (*beats per minute*) and key detection.

* Any kind of music file format
* Java-based and command-line friendly
* Crawls directories for track files.
* Analysis takes 5 seconds per average track(if we assume as average track an MP3 file with 320 kbit/s and 5 minutes length) on an outdated 2008 PC.
    * Easily crawls 750 tracks within one hour on outdated hardware
* Leverages multicore CPU architectures
* Forked from [*tfriedel/trackanalyzer*](https://github.com/tfriedel/trackanalyzer).

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

java -jar target/jab-vamp-0.0.4-SNAPSHOT-jar-with-dependencies.jar "C:\Windows\path\to\someMusicFileThatFFmpegSupports.mp3" /unix/path/to/someMusicFileThatFFmpegSupports.flac $(cygpath --mixed ~/my-music-directory/)
```

After executing this command, soon, output similar to the following will appear on *stdout*:

```
/path/to/someMusicFileThatFFmpegSupports.mp3;2A;132.2
~/my-music-directory/fileB.mp3;7B;136.4
~/my-music-directory/recursive/directory/fileC.flac;10A;133.3

...
```

# Examples
    
    # in Windows environments, jab vamp expects a Windows-style path, enclosed within double quotes or single quotes 
    java -jar target/jab-vamp-0.0.4-SNAPSHOT-jar-with-dependencies.jar "C:\Users\Someuser\Music"
    
    # in Windows Cygwin environments, you can use the cygpath command in order to convert Unix-style paths to Windows-style paths.
    java -jar target/jab-vamp-0.0.4-SNAPSHOT-jar-with-dependencies.jar $(cygpath --windows /cygdrive/c/Users/Gerrit/Music/)
