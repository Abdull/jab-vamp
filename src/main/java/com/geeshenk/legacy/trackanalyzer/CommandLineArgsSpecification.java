package com.geeshenk.legacy.trackanalyzer;

import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;

public class CommandLineArgsSpecification {
    
    @Parameter
    public List<String> providedAudioFilePaths = new ArrayList<>();
	
    @Parameter(names = {"--write-tags", "-w"}, description = "write to Tags")
	public boolean writeTags = false;

    @Parameter(names = "--hiquality", description = "don't downsample for bpm detection")
	public boolean hiQuality = false;
	
	@Parameter(names = {"--output","-o"}, description = "write results to text file")
	public String writeAnalysisResultsToFilePath = "";
	
	@Parameter(names = "--nobpm", description = "don't detect bpm")
	public boolean noBpm = false;
	
	@Parameter(names = {"--file-list", "--filelist", "-l"}, description = "text file containing list of audio files")
	public String pathToFileWithListOfMusicFilesMaybeMagicEmptyStringFilePath = "";

	@Parameter(names = {"--help","-h","-?"}, help = true)
	public boolean isHelpRequested;

	@Parameter(names = {"--debug", "-debug", "-d"}, description = "Used to debug com.geeshenk.legacy.trackanalyzer")
	public Boolean isDebugRequested = Boolean.FALSE;
}
