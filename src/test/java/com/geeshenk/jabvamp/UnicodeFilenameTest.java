package com.geeshenk.jabvamp;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.geeshenk.legacy.trackanalyzer.TrackAnalyzer;

public class UnicodeFilenameTest {
    private final static Logger log = LoggerFactory.getLogger(UnicodeFilenameTest.class);
    private final static String MAJORANA_PATH = "src/test/resources/Dyz Lecticus - Majorana Fermion.mp3";
    private final static String MAJORANA_PATH_UNICODE = "src/test/resources/Dyz Lecticus - Majorana Fermion - 𝖀𝖓𝖎𝖈𝖔𝖉𝖊.mp3";
    @Test
    public void analyzeFileWithUnicodeCharacters() throws Exception {
        log.info("About to start TrackAnalyzer");
        //TrackAnalyzer.main(new String[]{MAJORANA_PATH});
        TrackAnalyzer.main(new String[]{MAJORANA_PATH_UNICODE});
    }
}
