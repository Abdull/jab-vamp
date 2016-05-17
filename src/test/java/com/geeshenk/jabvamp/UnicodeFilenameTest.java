package com.geeshenk.jabvamp;

import java.io.File;
import java.io.IOException;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.geeshenk.legacy.trackanalyzer.TrackAnalyzer;

public class UnicodeFilenameTest {
    private final static Logger log = LoggerFactory.getLogger(UnicodeFilenameTest.class);
    private final static String MAJORANA_PATH = "src/test/resources/Dyz Lecticus - Majorana Fermion.mp3";
    private final static String MAJORANA_PATH_UNICODE = "src/test/resources/Dyz Lecticus - Majorana Fermion - 𝖀𝖓𝖎𝖈𝖔𝖉𝖊.mp3";
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    
    @Test
    public void runJabvampWithDebugFlag() throws Exception {
        TrackAnalyzer.main(new String[]{"--debug", MAJORANA_PATH});
    }
    
    @Test
    public void analyzeFileWithoutUnicodeCharacters() throws Exception {
        TrackAnalyzer.main(new String[]{MAJORANA_PATH});
    }
    
    @Test
    public void analyzeFileWithUnicodeCharacters() throws Exception {
        TrackAnalyzer.main(new String[]{MAJORANA_PATH_UNICODE});
    }
    
    @Test
    public void testdriveFfmpegCliWrapper() throws IOException {
        FFmpeg ffmpeg = new FFmpeg();
        FFprobe ffprobe = new FFprobe();
        String outputPath = tempFolder.newFile("output.mp4").getAbsolutePath();
        FFmpegBuilder builder = new FFmpegBuilder()
          .setInput(MAJORANA_PATH_UNICODE)     // Filename, or a FFmpegProbeResult
          .overrideOutputFiles(true) // Override the output if it exists
          .addOutput(outputPath)   // Filename for the destination
            .setFormat("mp4")        // Format is inferred from filename, or can be set
            .disableSubtitle()       // No subtiles
            .setAudioChannels(1)         // Mono audio
            .setAudioCodec("aac")        // using the aac codec
            .setAudioSampleRate(48_000)  // at 48KHz
            .setAudioBitRate(32768)      // at 32 kbit/s
            .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
            .done();
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
       // Run a one-pass encode
       executor.createJob(builder).run();
    }
    
    @Test
    public void decodeFile() throws IOException {
        File unicodeAudioFile = new File(MAJORANA_PATH_UNICODE);
        File waveOutputFile = tempFolder.newFile("output.wav");
        TrackAnalyzer.decodeInputFileToWaveAudioFileWith4410Samples(unicodeAudioFile, waveOutputFile);
        log.info("Finished decoding");
    }
    
}
