package com.geeshenk.jabvamp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.geeshenk.legacy.trackanalyzer.TrackAnalyzer;

public class TrackAnalyzerUnicodeTest {
    
    private final static String MAJORANA_PATH_UNICODE = "src/test/resources/tracks/Dyz Lecticus - Majorana Fermion - 𝖀𝖓𝖎𝖈𝖔𝖉𝖊.mp3";
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    @Test
    public void analyzeFileWithUnicodeCharacters() throws Exception {
        TrackAnalyzer.main( new String[]{MAJORANA_PATH_UNICODE} );
    }
    
}
