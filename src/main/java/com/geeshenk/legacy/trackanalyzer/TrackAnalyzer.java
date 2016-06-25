/**
 * ***********************************************************************
 *
 * Copyright 2012 Thomas Friedel
 * Adapted 2016 by jab vamp developers
 * 
 * This file is part of com.geeshenk.legacy.trackanalyzer.
 *
 * com.geeshenk.legacy.trackanalyzer is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * com.geeshenk.legacy.trackanalyzer is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * com.geeshenk.legacy.trackanalyzer. If not, see <http://www.gnu.org/licenses/>.
 *
 ************************************************************************
 */
package com.geeshenk.legacy.trackanalyzer;

import at.ofai.music.beatroot.BeatRoot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.*;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.beust.jcommander.JCommander;

public class TrackAnalyzer {
    
    private final static org.slf4j.Logger log = LoggerFactory.getLogger(TrackAnalyzer.class);
    
    CommandLineArgsSpecification thisRuntimeArgsBuilder = new CommandLineArgsSpecification();
    BufferedWriter writeListWriter;
    List<String> audioFilesPaths = new ArrayList<>();
    public final KeyFinder keyFinder;
    public final ApplicationParameters applicationParameters;
    
    public static void main(String[] args) throws Exception {
        
        // reconfigure JUL for slf4j
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        Logger.getLogger("global").setLevel(Level.FINEST);
        
        TrackAnalyzer ta = new TrackAnalyzer(args);
        ta.run();
    }
    
    TrackAnalyzer(String[] cmdArgs) throws Exception {
        JCommander thisRuntimeJCommanderConfiguration = new JCommander(thisRuntimeArgsBuilder, cmdArgs);
        thisRuntimeJCommanderConfiguration.setProgramName("com.geeshenk.legacy.trackanalyzer");
        
        // check for debug request
        if (thisRuntimeArgsBuilder.isDebugRequested) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            try {
              JoranConfigurator configurator = new JoranConfigurator();
              configurator.setContext(context);
              // Call context.reset() to clear any previous configuration, e.g. default 
              // configuration. For multi-step configuration, omit calling context.reset().
              context.reset(); 
              configurator.doConfigure( getClass().getResourceAsStream("/logback-debug.xml") );
            }
            catch (JoranException je) {
              // StatusPrinter will handle this
            }
            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
        }
        
        // check for (no audio files paths provided) or (help request)
        if ( thisRuntimeArgsBuilder.providedAudioFilePaths.isEmpty() && Utils.isEmpty(thisRuntimeArgsBuilder.pathToFileWithListOfMusicFilesMaybeMagicEmptyStringFilePath)
             ||  thisRuntimeArgsBuilder.isHelpRequested
           ) {
               thisRuntimeJCommanderConfiguration.usage();
               System.exit(-1);
            }
        
        // check if it is requested to read in a file-with-a-list-of-audio-file...
        if ( ! Utils.isEmpty(thisRuntimeArgsBuilder.pathToFileWithListOfMusicFilesMaybeMagicEmptyStringFilePath)) {
            
            File audioFilesListFile = new File(thisRuntimeArgsBuilder.pathToFileWithListOfMusicFilesMaybeMagicEmptyStringFilePath);
            try (
                    FileReader audioFilesListFileReader = new FileReader(audioFilesListFile);
                    BufferedReader listOfMusicFilesBufferedReader = new BufferedReader(audioFilesListFileReader);
                    ) {
                // ... if so, then read all the filenames in the list and collect them in 'filenames'
                
                // use buffering, reading one line at a time
                // FileReader always assumes default encoding is OK!
                
                try {
                    String audiofileListCurrentAudioFilePath = null; // not declared within while loop
                    /*
                     * readLine is a bit quirky : it returns the content of a
                     * line MINUS the newline. it returns null only for the END
                     * of the stream. it returns an empty String if two newlines
                     * appear in a row.
                     */
                    while ((audiofileListCurrentAudioFilePath = listOfMusicFilesBufferedReader.readLine()) != null) {
                        audioFilesPaths.add(audiofileListCurrentAudioFilePath);
                    }
                }
                catch (IOException e) {
                }
            }
        }
        
        // add audio file paths from command line
        log.debug("About to iterate through provided audio file paths");
        for (String currentProvidedPath : thisRuntimeArgsBuilder.providedAudioFilePaths) {
            log.debug("processing {}", currentProvidedPath);
            File currentFileOrDirectory = new File(currentProvidedPath);
            if ( currentFileOrDirectory.isDirectory() ) {
                log.debug("{} is a directory. Entering recursion", currentProvidedPath);
                List<String> foundAudioFiles = crawlDirectoryForAudioFiles(currentFileOrDirectory);
                audioFilesPaths.addAll(foundAudioFiles);
            }
            else {
                log.debug("{} is a regular file", currentProvidedPath);
                audioFilesPaths.add(currentProvidedPath);
            }
        }
        // audioFilesPaths.addAll(thisRuntimeArgsBuilder.providedAudioFilePaths);
        
        // check if it is requested to write results to a file
        if ( ! Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath) ) {
            try (BufferedWriter localWriteListBufferedWriter = new BufferedWriter(
                        new FileWriter(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) ){
                writeListWriter = localWriteListBufferedWriter;
            } catch (IOException e) { // writeAnalysisResultsToFilePath not accessible for some reason
                log.error("could not create analysis report file", e);
                System.exit(-1);
            }
        }
        keyFinder = new KeyFinder();
        applicationParameters = new ApplicationParameters();
        applicationParameters.setHopSize(8192);
    }
    
    /**
     * 
     * @param directory a File that is a directory. It is used as a seed for finding *normal file* children and recursively crawling directory children
     * @return
     */
    private static List<String> crawlDirectoryForAudioFiles(File directory) {
        
        List<String> result = new ArrayList<>();
        File[] filesAndDirectoriesArray = directory.listFiles();
        
        List<File> filesAndDirectories = Arrays.asList(filesAndDirectoriesArray);
        
        for (File currentFileOrDirectory : filesAndDirectories) {
            if ( currentFileOrDirectory.isDirectory() ) {
                result.addAll( crawlDirectoryForAudioFiles(currentFileOrDirectory) );
            }
            else {
                result.add( currentFileOrDirectory.getAbsolutePath() );
            }
        }
        
        return result;
    }
    
    /**
     * Decodes an audio file (mp3, flac, wav, etc. everything which can be
     * decoded by ffmpeg) to a downsampled wav file.
     *
     * @param input
     *            an audio file which will be decoded to wav
     * @param wavoutput
     *            the output wav file
     * @throws IllegalArgumentException
     * @throws InputFormatException
     * @throws EncoderException
     */
    public static void decodeInputFileToWaveAudioFileWith4410Samples(File input, File wavoutput) throws IOException {
        decodeInputFileToWaveAudioFile(input, wavoutput, 4410);
    }
    
    /**
     * Decodes an audio file (mp3, flac, wav, etc. everything which can be
     * decoded by ffmpeg) to a downsampled wav file.
     *
     * @param input
     *            an audio file which will be decoded to wav
     * @param wavoutput
     *            the output wav file
     * @param samplerate
     *            the samplerate of the output wav.
     * @throws IllegalArgumentException
     * @throws InputFormatException
     * @throws EncoderException
     */
    public static void decodeInputFileToWaveAudioFile(File input, File wavoutput, int samplerate) throws IOException {
        FFmpeg ffmpeg = new FFmpeg();
        
        FFprobe ffprobe = new FFprobe();
        
        String outputPath = wavoutput.getAbsolutePath();
        FFmpegBuilder builder = new FFmpegBuilder()
          .setInput( input.getAbsolutePath() ) 
          // .setInput("\"" + input.getAbsolutePath() + "\"") // putting all path arguments within double quotes so that ffmpeg is happy, see http://stackoverflow.com/a/22780128/923560 
        
          .overrideOutputFiles(true)
          .addOutput(outputPath)
            .setAudioChannels(1) // Mono audio
            .setAudioCodec("pcm_s16le")
            .setAudioSampleRate(samplerate)
            .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
            .done();
        
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        // Run a one-pass encode
        executor.createJob(builder).run();
        
    }
    
    /**
     * this writes a line to a txt file with the result of the detection process
     * for one file.
     *
     * @param filename
     *            the filename of the audio file we just processed
     * @param key
     *            the result of the key detector (or "-")
     * @param bpm
     *            the result of the bpm detector (or "-")
     * @param wroteTags
     *            true if tags were written successfully
     */
    public void writeDetectionResultToReportFile(String filename, String key, String bpm,
            boolean wroteTags) {
        if (!Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) {
            try {
                writeListWriter.write(filename + ";" + key + ";" + bpm + ";" + wroteTags);
                writeListWriter.newLine();
                writeListWriter.flush();
            } catch (IOException e) {
                log.error("Could not add detection result for audio file " + filename + " to report file", e);
            }
        }
    }
    
    /**
     * writes bpm and key to KEY_START and BPM fields in the tag
     *
     * @param filename
     * @param formattedBpm
     * @param key
     */
    public boolean updateTags(String filename, String formattedBpm, String key) {
        File file = new File(filename);
        try {
            AudioFile f = AudioFileIO.read(file);
            if (!setCustomTag(f, "KEY_START", key)) {
                throw new IOException("Error writing Key Tag");
            }
            if (!thisRuntimeArgsBuilder.noBpm) {
                Tag tag = f.getTag();
                if (tag instanceof Mp4Tag) {
                    if (!setCustomTag(f, "BPM", formattedBpm)) {
                        throw new IOException("Error writing BPM Tag");
                    }
                }
                tag.setField(FieldKey.BPM, formattedBpm);
            }
            f.commit();
            return true;
        } catch (Exception e) {
            log.error("problem with tags in file " + filename);
            return false;
        }
    }
    
    /**
     * runs key and bpm detector on
     *
     * @filename, optionally writes tags
     * @param originalAudioPath
     * @return
     */
    public boolean analyzeTrack(String originalAudioPath, boolean writeTags) {
        String tempAbsolutePath = "";
        WaveAudioSpecificationsAndData tempFile2AudioSpecsAndData = new WaveAudioSpecificationsAndData();
        File waveTempFile1 = null;
        File waveTempFile2 = null;
        
        /////////// create and prepare temp audio files
        try {
            waveTempFile1 = File.createTempFile("keyfinder", ".wav");
            waveTempFile2 = File.createTempFile("keyfinder2", ".wav");
            tempAbsolutePath = waveTempFile1.getAbsolutePath();
            // Delete temp file when program exits.
            waveTempFile1.deleteOnExit();
            waveTempFile2.deleteOnExit();
            log.debug("About to decode {} to temporary wave file", originalAudioPath);
            decodeInputFileToWaveAudioFile(new File(originalAudioPath), waveTempFile1, 44100);
            log.debug("About to decode (copy ?) temporary wave file to second temporary wave file...");
            decodeInputFileToWaveAudioFileWith4410Samples(waveTempFile1, waveTempFile2);
            log.debug("... finished decoding to second temporary wave file.");
        } catch (Exception e) {
            log.error("Exception while decoding " + originalAudioPath, e);
            if (waveTempFile1.length() == 0) {
                writeDetectionResultToReportFile(originalAudioPath, "-", "-", false);
                deleteTempFiles(waveTempFile1, waveTempFile2);
                return false;
            }
        }
        
        KeyDetectionResult keyDetectionResult;
        try {
            tempFile2AudioSpecsAndData.loadFromAudioFile(waveTempFile2.getAbsolutePath());
            keyDetectionResult = keyFinder.findKey(tempFile2AudioSpecsAndData, applicationParameters);
            if (keyDetectionResult.globalKeyEstimate == ApplicationParameters.key_t.SILENCE) {
                log.info("SILENCE");
            }
        } catch (Exception e) {
            log.error("exception while finding musical key for file " + originalAudioPath, e);
            writeDetectionResultToReportFile(originalAudioPath, "-", "-", false);
            deleteTempFiles(waveTempFile1, waveTempFile2);
            return false;
        }
        
        String formattedBpm = "0";
        if (!thisRuntimeArgsBuilder.noBpm) {
            // get bpm
            if (thisRuntimeArgsBuilder.hiQuality) {
                try {
                    // decodeAudioFile(new File(filename), temp, 44100);
                    // TODO hiquality stuff
                } catch (Exception e) {
                    log.warn("could not decode " + originalAudioPath + " for high-quality BPM detection", e);
                }
            }
            
            log.debug("about to get BPM for original file {}", originalAudioPath);
            double bpm = BeatRoot.getBPM(tempAbsolutePath);
            log.debug("BPM result for original file {} is {}", originalAudioPath, bpm);
            
            if (Double.isNaN(bpm) && !thisRuntimeArgsBuilder.hiQuality) {
                try {
                    // bpm couldn't be detected. try again with a higher-quality wav.
                    log.debug("BPM could not be detected for " + originalAudioPath + ". Trying again with sound higher quality....");
                    decodeInputFileToWaveAudioFile(new File(originalAudioPath), waveTempFile1, 44100);
                    bpm = BeatRoot.getBPM(tempAbsolutePath);
                    if (Double.isNaN(bpm)) {
                        log.error("BPM could not be detected for high-quality " + originalAudioPath);
                    } else {
                        log.info("BPM detected in high quality for " + originalAudioPath);
                    }
                } catch (Exception ex) {
                    writeDetectionResultToReportFile(originalAudioPath, "-", "-", false);
                }
            } else if (Double.isNaN(bpm) && thisRuntimeArgsBuilder.hiQuality) {
                log.error("BPM could not be detected for high-quality " + originalAudioPath);
            }
            if (!Double.isNaN(bpm)) {
                formattedBpm = new DecimalFormat("#.#").format(bpm).replaceAll(
                        ",", ".");
            }
        }
        log.info("{};{};{}", originalAudioPath,
                ApplicationParameters.camelotKey(keyDetectionResult.globalKeyEstimate), formattedBpm);
        
        boolean wroteTags = false;
        if (thisRuntimeArgsBuilder.writeTags) {
            wroteTags = updateTags(originalAudioPath, formattedBpm,
                    ApplicationParameters.camelotKey(keyDetectionResult.globalKeyEstimate));
        }
        writeDetectionResultToReportFile(originalAudioPath,
                ApplicationParameters.camelotKey(keyDetectionResult.globalKeyEstimate), formattedBpm,
                wroteTags);
        deleteTempFiles(waveTempFile1, waveTempFile2);
        return true;
    }
    
    private void deleteTempFiles(File temp, File temp2) {
        if (temp != null) {
            temp.delete();
        }
        if (temp2 != null) {
            temp2.delete();
        }
    }
    
    /**
     * This is the main loop of the program. For every file in the filenames
     * list, the file gets decoded and downsampled to a 4410 hz mono wav file.
     * Then key and bpm detectors are run, the result is logged in a txt file
     * and written to the tag if possible.
     */
    public void run() throws ExecutionException {
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> pool;
        pool = new ExecutorCompletionService<Boolean>(threadPool);
        
        for (String filename : audioFilesPaths) {
            // new worker thread
            pool.submit(new WorkTrack(filename));
        }
        for (int i = 0; i < audioFilesPaths.size(); i++) {
            // Compute the result
            try {
                pool.take().get();
            } catch (InterruptedException e) {
                log.error("Problem with taking and/or calculating some work package", e);
            }
        }
        threadPool.shutdown();
        if (!Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) {
            try {
                writeListWriter.close();
            } catch (IOException e) {
                log.error("Could not close report file", e);
            }
        }
        //System.exit(0);
    }
    
    /**
     * This will write a custom ID3 tag (TXXX). This works only with MP3 files
     * (Flac with ID3-Tag not tested).
     *
     * @param description
     *            The description of the custom tag i.e. "catalognr" There can
     *            only be one custom TXXX tag with that description in one MP3
     *            file
     * @param text
     *            The actual text to be written into the new tag field
     * @return True if the tag has been properly written, false otherwise
     */
    public static boolean setCustomTag(AudioFile audioFile, String description,
            String text) throws IOException {
        FrameBodyTXXX txxxBody = new FrameBodyTXXX();
        txxxBody.setDescription(description);
        txxxBody.setText(text);
        
        // Get the tag from the audio file
        // If there is no ID3Tag create an ID3v2.3 tag
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        if (tag instanceof AbstractID3Tag) {
            // If there is only a ID3v1 tag, copy data into new ID3v2.3 tag
            if (!(tag instanceof ID3v23Tag || tag instanceof ID3v24Tag)) {
                Tag newTagV23 = null;
                if (tag instanceof ID3v1Tag) {
                    newTagV23 = new ID3v23Tag((ID3v1Tag) audioFile.getTag()); // Copy old tag data
                }
                if (tag instanceof ID3v22Tag) {
                    newTagV23 = new ID3v23Tag((ID3v22Tag) audioFile.getTag()); // Copy old tag data
                }
                audioFile.setTag(newTagV23);
                tag = newTagV23;
            }
            
            AbstractID3v2Frame frame = null;
            if (tag instanceof ID3v23Tag) {
                if (((ID3v23Tag) audioFile.getTag()).getInvalidFrames() > 0) {
                    throw new IOException("read some invalid frames!");
                }
                frame = new ID3v23Frame("TXXX");
            } else if (tag instanceof ID3v24Tag) {
                if (((ID3v24Tag) audioFile.getTag()).getInvalidFrames() > 0) {
                    throw new IOException("read some invalid frames!");
                }
                frame = new ID3v24Frame("TXXX");
            }
            
            frame.setBody(txxxBody);
            
            try {
                tag.setField(frame);
            } catch (FieldDataInvalidException e) {
                log.error("Problem setting " + frame + " to audio file " + audioFile.getFile().getAbsolutePath(), e);
                return false;
            }
        } else if (tag instanceof FlacTag) {
            try {
                ((FlacTag) tag).setField(description, text);
            } catch (KeyNotFoundException e) {
                log.error("Could not set field " + description + "," + text + " to audio file " + audioFile.getFile().getAbsolutePath(), e );
                return false;
            } catch (FieldDataInvalidException ex) {
                return false;
            }
        } else if (tag instanceof Mp4Tag) {
            // TagField field = new
            // Mp4TagTextField("----:com.apple.iTunes:"+description, text);
            TagField field;
            field = new Mp4TagReverseDnsField(Mp4TagReverseDnsField.IDENTIFIER
                    + ":" + "com.apple.iTunes" + ":" + description,
                    "com.apple.iTunes", description, text);
            // TagField field = new Mp4TagTextField(description, text);
            try {
                tag.setField(field);
            } catch (FieldDataInvalidException e) {
                log.error("Could not set field " + field + " to audio file " + audioFile.getFile().getAbsolutePath(), e );
                return false;
            }
        } else if (tag instanceof VorbisCommentTag) {
            try {
                ((VorbisCommentTag) tag).setField(description, text);
            } catch (KeyNotFoundException e) {
                log.error("Could not set field " + description + "," + text + " to audio file " + audioFile.getFile().getAbsolutePath() + ". Key not found.", e );
                return false;
            } catch (FieldDataInvalidException e) {
                log.error("Could not set field " + description + "," + text + " to audio file " + audioFile.getFile().getAbsolutePath() + ". Field data invalid.", e );
                return false;
            }
        } else {
            // tag not implented
            log.warn("Could not write key information for audio file " + audioFile.getFile().getAbsolutePath() + " to tag because tag format " + tag.getClass().getCanonicalName() + " is not supported.");
            return false;
        }
        
        // write changes in tag to file
        try {
            audioFile.commit();
        } catch (CannotWriteException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    /**
     * one worker thread for analyzing a track
     */
    private final class WorkTrack implements Callable<Boolean> {

        String filename;

        WorkTrack(String filename) {
            this.filename = filename;
        }

        @Override
        public Boolean call() {
            return analyzeTrack(filename, thisRuntimeArgsBuilder.writeTags);
        }
    }
}
