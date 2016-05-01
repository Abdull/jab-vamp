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
import it.sauronsoftware.jave.*;

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
import java.util.logging.Logger;

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

import com.beust.jcommander.JCommander;

public class TrackAnalyzer {

    CommandLineArgsSpecification thisRuntimeArgsBuilder = new CommandLineArgsSpecification();
    BufferedWriter writeListWriter;
    List<String> audioFilesPaths = new ArrayList<>();
    public final KeyFinder keyFinder;
    public final ApplicationParameters applicationParameters;
    
    public static void main(String[] args) throws Exception {
        TrackAnalyzer ta = new TrackAnalyzer(args);
        ta.run();
    }
    
    TrackAnalyzer(String[] cmdArgs) throws Exception {
        
        JCommander thisRuntimeJCommanderConfiguration = new JCommander(thisRuntimeArgsBuilder, cmdArgs);
        thisRuntimeJCommanderConfiguration.setProgramName("com.geeshenk.legacy.trackanalyzer");
        
        // check for (no audio files paths provided) or (help request)
        if ( thisRuntimeArgsBuilder.providedAudioFilePaths.isEmpty() && Utils.isEmpty(thisRuntimeArgsBuilder.listOfMusicFilesMaybeMagicEmptyStringFilePath)
             ||  thisRuntimeArgsBuilder.isHelpRequested
           ) {
               thisRuntimeJCommanderConfiguration.usage();
               System.exit(-1);
            }
        
        // check for debug request
        if (thisRuntimeArgsBuilder.isDebugRequested) {
            Logger.getLogger(TrackAnalyzer.class.getName()).setLevel(Level.ALL);
        } else {
            Logger.getLogger(TrackAnalyzer.class.getName()).setLevel(
                    Level.WARNING);
        }
        
        // check if it is requested to read in a file-with-a-list-of-audio-file...
        if ( ! Utils.isEmpty(thisRuntimeArgsBuilder.listOfMusicFilesMaybeMagicEmptyStringFilePath)) {
            
            File audioFilesListFile = new File(thisRuntimeArgsBuilder.listOfMusicFilesMaybeMagicEmptyStringFilePath);
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
        System.out.println("before");
        for (String currentProvidedPath : thisRuntimeArgsBuilder.providedAudioFilePaths) {
            System.out.println("between 1");
            File currentFileOrDirectory = new File(currentProvidedPath);
            if ( currentFileOrDirectory.isDirectory() ) {
                System.out.println("between 2");
                List<String> foundAudioFiles = crawlDirectoryForAudioFiles(currentFileOrDirectory);
                audioFilesPaths.addAll(foundAudioFiles);
            }
            else {
                System.out.println("between 3" + currentProvidedPath);
                audioFilesPaths.add(currentProvidedPath);
            }
        }
        // audioFilesPaths.addAll(thisRuntimeArgsBuilder.providedAudioFilePaths);
        
        // check if it is requested to write results to a file
        if ( ! Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath) ) {
            try (BufferedWriter localWriteListBufferedWriter = new BufferedWriter(
                        new FileWriter(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) ){
                writeListWriter = localWriteListBufferedWriter;
            } catch (IOException ex) { // writeAnalysisResultsToFilePath not accessible for some reason
                Logger.getLogger( TrackAnalyzer.class.getName() ).log(Level.SEVERE, null, ex);
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
    public static void decodeInputFileToWaveAudioFileWith4410Samples(File input, File wavoutput)
            throws IllegalArgumentException, InputFormatException,
            EncoderException {
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
    public static void decodeInputFileToWaveAudioFile(File input, File wavoutput, int samplerate) throws IllegalArgumentException,
            InputFormatException, EncoderException {
        assert wavoutput.getName().endsWith(".wav");
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("pcm_s16le");
        audio.setChannels(Integer.valueOf(1));
        audio.setSamplingRate(new Integer(samplerate));
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setFormat("wav");
        attrs.setAudioAttributes(audio);
        Encoder encoder = new Encoder();
        encoder.encode(input, wavoutput, attrs);

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
    public void logDetectionResult(String filename, String key, String bpm,
            boolean wroteTags) {
        if (!Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) {
            try {
                writeListWriter.write(filename + ";" + key + ";" + bpm + ";"
                        + wroteTags);
                writeListWriter.newLine();
                writeListWriter.flush();
            } catch (IOException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
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
            System.out.println("problem with tags in file " + filename);
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
            decodeInputFileToWaveAudioFile(new File(originalAudioPath), waveTempFile1, 44100);
            decodeInputFileToWaveAudioFileWith4410Samples(waveTempFile1, waveTempFile2);
        } catch (Exception ex) {
            Logger.getLogger(TrackAnalyzer.class.getName()).log(Level.WARNING,
                    "error while decoding" + originalAudioPath + ".");
            if (waveTempFile1.length() == 0) {
                logDetectionResult(originalAudioPath, "-", "-", false);
                deleteTempFiles(waveTempFile1, waveTempFile2);
                return false;
            }
        }
        
        KeyDetectionResult keyDetectionResult;
        try {
            tempFile2AudioSpecsAndData.loadFromAudioFile(waveTempFile2.getAbsolutePath());
            keyDetectionResult = keyFinder.findKey(tempFile2AudioSpecsAndData, applicationParameters);
            if (keyDetectionResult.globalKeyEstimate == ApplicationParameters.key_t.SILENCE) {
                System.out.println("SILENCE");
            }
        } catch (Exception ex) {
            Logger.getLogger(TrackAnalyzer.class.getName()).log(Level.SEVERE,
                    null, ex);
            logDetectionResult(originalAudioPath, "-", "-", false);
            deleteTempFiles(waveTempFile1, waveTempFile2);
            return false;
        }
        
        String formattedBpm = "0";
        if (!thisRuntimeArgsBuilder.noBpm) {
            // get bpm
            if (thisRuntimeArgsBuilder.hiQuality) {
                try {
                    // decodeAudioFile(new File(filename), temp, 44100);
                    // @todo hiquality stuff
                } catch (Exception ex) {
                    Logger.getLogger(TrackAnalyzer.class.getName()).log(
                            Level.WARNING,
                            "couldn't decode " + originalAudioPath
                                    + " for hiquality bpm detection.", ex);
                }
            }
            double bpm = BeatRoot.getBPM(tempAbsolutePath);
            if (Double.isNaN(bpm) && !thisRuntimeArgsBuilder.hiQuality) {
                try {
                    // bpm couldn't be detected. try again with a higher quality
                    // wav.
                    Logger.getLogger(TrackAnalyzer.class.getName()).log(
                            Level.WARNING,
                            "bpm couldn't be detected for " + originalAudioPath
                                    + ". Trying again.");
                    decodeInputFileToWaveAudioFile(new File(originalAudioPath), waveTempFile1, 44100);
                    bpm = BeatRoot.getBPM(tempAbsolutePath);
                    if (Double.isNaN(bpm)) {
                        Logger.getLogger(TrackAnalyzer.class.getName()).log(
                                Level.WARNING,
                                "bpm still couldn't be detected for "
                                        + originalAudioPath + ".");
                    } else {
                        Logger.getLogger(TrackAnalyzer.class.getName()).log(
                                Level.INFO,
                                "bpm now detected correctly for " + originalAudioPath);
                    }
                } catch (Exception ex) {
                    logDetectionResult(originalAudioPath, "-", "-", false);
                }
            } else if (Double.isNaN(bpm) && thisRuntimeArgsBuilder.hiQuality) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.WARNING,
                        "bpm couldn't be detected for " + originalAudioPath + ".");
            }
            if (!Double.isNaN(bpm)) {
                formattedBpm = new DecimalFormat("#.#").format(bpm).replaceAll(
                        ",", ".");
            }
        }
        System.out.printf("%s;%s;%s\n", originalAudioPath,
                ApplicationParameters.camelotKey(keyDetectionResult.globalKeyEstimate), formattedBpm);
        
        boolean wroteTags = false;
        if (thisRuntimeArgsBuilder.writeTags) {
            wroteTags = updateTags(originalAudioPath, formattedBpm,
                    ApplicationParameters.camelotKey(keyDetectionResult.globalKeyEstimate));
        }
        logDetectionResult(originalAudioPath,
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
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, e);
            }
        }
        threadPool.shutdown();
        if (!Utils.isEmpty(thisRuntimeArgsBuilder.writeAnalysisResultsToFilePath)) {
            try {
                writeListWriter.close();
            } catch (IOException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
        System.exit(0);
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
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, e);
                return false;
            }
        } else if (tag instanceof FlacTag) {
            try {
                ((FlacTag) tag).setField(description, text);
            } catch (KeyNotFoundException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
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
            } catch (FieldDataInvalidException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
                return false;
            }
        } else if (tag instanceof VorbisCommentTag) {
            try {
                ((VorbisCommentTag) tag).setField(description, text);
            } catch (KeyNotFoundException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
                return false;
            } catch (FieldDataInvalidException ex) {
                Logger.getLogger(TrackAnalyzer.class.getName()).log(
                        Level.SEVERE, null, ex);
                return false;
            }
        } else {
            // tag not implented
            Logger.getLogger(TrackAnalyzer.class.getName()).log(
                    Level.WARNING,
                    "couldn't write key information for "
                            + audioFile.getFile().getName()
                            + " to tag, because this format is not supported.");
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
