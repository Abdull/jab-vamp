package com.geeshenk.jabvamp;

import java.io.IOException;
import java.net.URL;

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
    public void analyzeFileWithUnicodeCharacters() throws Exception {
        log.info("About to start TrackAnalyzer");
        TrackAnalyzer.main(new String[]{"--debug", MAJORANA_PATH});
        //TrackAnalyzer.main(new String[]{MAJORANA_PATH_UNICODE});
    }
    
    @Test
    public void testdriveFfmpegCliWrapper() throws IOException {
        URL url = ClassLoader.getSystemResource("ffmpeg-20160428-git-78baa45-win64-static/bin/ffmpeg.exe");
        log.info("url is {}", url);
        String ffmpegPath = url.getPath();
        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe( ClassLoader.getSystemResource("ffmpeg-20160428-git-78baa45-win64-static/bin/ffprobe.exe").getPath() );
        //String outputPath = "src/test/resources/output.mp4";
        String outputPath = tempFolder.newFile("output.mp4").getAbsolutePath();
        log.info("outputPath is {}", outputPath);
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
       log.info("finished encoding");
       log.debug("this message is debug only");
       log.trace("this message is trace only");
    }
}
