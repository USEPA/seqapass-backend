package gov.epa.seqapass.backend.hpcAccess;

import java.util.ArrayList;

import gov.epa.seqapass.common.LevelFourAccessionRow;

public class SBatchObject {

	private int jobId;
	private String outputFilePath;
	private int jobFragmentNumber;
	private int accessionRunId;
	private String errorFilePath;
	private String timeAllotted;
	private String queryAccessionIdString;
	private String queryAccessionCanonicalIdString;
	
	private int level4RunId;
	private String level4JobDir;
	private String level4ProjectDir;
	private String level4LocalJobName;
	private int numberOfProteinsInFasta;
	private ArrayList<LevelFourAccessionRow> levelFourRowsWithCommonAccessions;
	private LevelFourAccessionRow levelFourAccessionRow;
	
	public SBatchObject(int jobId, String outputFilePath, int jobFragmentNumber, int accessionRunId, String errorFilePath, String timeAllotted, String queryAccessionIdString) {
		this.jobId = jobId;
		this.outputFilePath = outputFilePath;
		this.jobFragmentNumber = jobFragmentNumber;
		this.accessionRunId = accessionRunId;
		this.errorFilePath = errorFilePath;
		this.timeAllotted = timeAllotted;
		this.queryAccessionIdString = queryAccessionIdString;
	}
	
	public SBatchObject(int jobId, String outputFilePath, int jobFragmentNumber, int accessionRunId, String errorFilePath, String timeAllotted, String queryAccessionIdString, String queryAccessionCanonicalIdString) {
		this.jobId = jobId;
		this.outputFilePath = outputFilePath;
		this.jobFragmentNumber = jobFragmentNumber;
		this.accessionRunId = accessionRunId;
		this.errorFilePath = errorFilePath;
		this.timeAllotted = timeAllotted;
		this.queryAccessionIdString = queryAccessionIdString;
		this.queryAccessionCanonicalIdString = queryAccessionCanonicalIdString;
	}
	
	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getOutputFilePath() {
		return outputFilePath;
	}

	public void setOutputFilePath(String outputFilePath) {
		this.outputFilePath = outputFilePath;
	}
	
	public int getJobFragmentNumber() {
		return jobFragmentNumber;
	}


	public void setJobFragmentNumber(int jobFragmentNumber) {
		this.jobFragmentNumber = jobFragmentNumber;
	}

	public int getAccessionRunId() {
		return accessionRunId;
	}

	public void setAccessionRunId(int accessionRunId) {
		this.accessionRunId = accessionRunId;
	}

	public String getErrorFilePath() {
		return errorFilePath;
	}

	public void setErrorFilePath(String errorFilePath) {
		this.errorFilePath = errorFilePath;
	}

	public String getTimeAllotted() {
		return timeAllotted;
	}

	public int getLevel4RunId() {
		return level4RunId;
	}

	public void setLevel4RunId(int level4RunId) {
		this.level4RunId = level4RunId;
	}

	public String getLevel4JobDir() {
		return level4JobDir;
	}

	public void setLevel4JobDir(String level4JobDir) {
		this.level4JobDir = level4JobDir;
	}

	public String getLevel4LocalJobName() {
		return level4LocalJobName;
	}

	public String getLevel4ProjectDir() {
		return level4ProjectDir;
	}

	public void setLevel4ProjectDir(String level4ProjectDir) {
		this.level4ProjectDir = level4ProjectDir;
	}

	public void setLevel4LocalJobName(String level4LocalJobName) {
		this.level4LocalJobName = level4LocalJobName;
	}

	public int getNumberOfProteinsInFasta() {
		return numberOfProteinsInFasta;
	}

	public void setNumberOfProteinsInFasta(int numberOfProteinsInFasta) {
		this.numberOfProteinsInFasta = numberOfProteinsInFasta;
	}

	public LevelFourAccessionRow getLevelFourAccessionRow() {
		return levelFourAccessionRow;
	}

	public void setLevelFourAccessionRow(LevelFourAccessionRow levelFourAccessionRow) {
		this.levelFourAccessionRow = levelFourAccessionRow;
	}

	public ArrayList<LevelFourAccessionRow> getLevelFourRowsWithCommonAccessions() {
		return levelFourRowsWithCommonAccessions;
	}

	public void setLevelFourRowsWithCommonAccessions(ArrayList<LevelFourAccessionRow> levelFourRowsWithCommonAccessions) {
		this.levelFourRowsWithCommonAccessions = levelFourRowsWithCommonAccessions;
	}

	public String getQueryAccessionIdString() {
		return queryAccessionIdString;
	}

	public void setQueryAccessionIdString(String queryAccessionIdString) {
		this.queryAccessionIdString = queryAccessionIdString;
	}

	public String getQueryAccessionCanonicalIdString() {
		return queryAccessionCanonicalIdString;
	}

	public void setQueryAccessionCanonicalIdString(String queryAccessionCanonicalIdString) {
		this.queryAccessionCanonicalIdString = queryAccessionCanonicalIdString;
	}

	
}
