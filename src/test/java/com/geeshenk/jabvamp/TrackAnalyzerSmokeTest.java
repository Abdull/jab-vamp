package com.geeshenk.jabvamp;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.geeshenk.legacy.trackanalyzer.TrackAnalyzer;

public class TrackAnalyzerSmokeTest {
    private final static String SOME_AUDIO_FILE_PATH = "src/test/resources/tracks/Dyz Lecticus - Majorana Fermion.mp3";
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    @Test
    public void runJabvampWithDebugFlag() throws Exception {
        TrackAnalyzer.main( new String[]{"--debug", SOME_AUDIO_FILE_PATH} );
    }
    
    @Test
    public void decodeFile() throws IOException {
        File unicodeAudioFile = new File(SOME_AUDIO_FILE_PATH);
        File waveOutputFile = tempFolder.newFile("output.wav");
        TrackAnalyzer.decodeInputFileToWaveAudioFileWith4410Samples(unicodeAudioFile, waveOutputFile);
    }
    
}
