package com.geeshenk.jabvamp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.geeshenk.legacy.trackanalyzer.TrackAnalyzer;

public class TrackAnalyzerDirectoryTest {
    
    private final static String DIRECTORY_WITHOUT_SPACES_AND_SOME_FILE_PATH = "src/test/resources/tracks/01_Konnekt-Keep_It_Burning.mp3";
    
    private final static String DIRECTORY_WITH_SPACES_AND_SOME_FILE_PATH = "src/test/resources/tracks/directory with spaces/01_Konnekt-Keep_It_Burning.mp3";
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    @Test
    public void analyzeTrackInDirectoryWithoutSpaces() throws Exception {
        TrackAnalyzer.main( new String[]{DIRECTORY_WITHOUT_SPACES_AND_SOME_FILE_PATH} );
    }
    
    @Test
    public void analyzeTrackInDirectoryWithSpaces() throws Exception {
        TrackAnalyzer.main( new String[]{DIRECTORY_WITH_SPACES_AND_SOME_FILE_PATH} );
    }
    
}
