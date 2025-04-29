package gov.epa.seqapass.backend.serviceThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;
import gov.epa.seqapass.backend.hpcAccess.HPCConnector;
import gov.epa.seqapass.backend.hpcAccess.Polling;
import gov.epa.seqapass.backend.hpcAccess.SBatchObject;
import gov.epa.seqapass.backend.utility.Timer;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.ProteinDataBankRow;

public class RunLevel4 {

	private String commonDirectory;
	private HPCConnector hpcConnector;
	private int jobFragmentNumber = -1;
	private String commonScriptPathLocal;
	private String testDir = "backend_test";
	private String prodDir = "backend_prod";
	private String inputScriptsOutput = "input_scripts_output";
	private HashMap<Integer, ArrayList<String>> tempFilesMap = new HashMap<>();

	private static Logger logger = LogManager.getLogger(RunLevel4.class);

	// --------------INFO FOR WRITING SHELL SCRIPT---------------------------------
	private int numThreads = 4;
	private int scavengerMax = 72; // 3 day max for scavenger queue
	private int computeMax = 168;
	private String itasserCommand; // Defined below using ncbiProvider info
	private String pathToLibDir; // Defined below using ncbiProvider info
	private String scriptCommandPath = null;
	private String pathToSendToSBatch = null;

	// ----------------INFO FOR POLLING/UPDATING THREADS--------------------------
	private boolean go = true;
	private boolean polling = true;
	private boolean updating = true;
	// ---------------------------------------------------------------------------

	private JdbcTemplate jdbcTemplate;
	private NCBIProvider ncbiProvider;
	private BLASTTools blastTools;
	private BLASTTools2 blastTools2;
	private int accessionRunId = -1;
	private int userId = -1;
	private String jobName = "";
	private String templateInputString;
	private String templateInputPDBString;
	private List<LevelFourAccessionRow> accessionData;
	private int level4RunId = -1;
	private int tmAlignRunId = -1;
	private int sourceLevel = -1;
	private int level2RunId = -1;

	private String tmAlignQueryJobName;
	LevelFourAccessionRow queryAccessionData;

	public RunLevel4(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper NCBIKeeper,
			LevelFourRequestableRow levelFourRequestableRow) {
		this.jdbcTemplate = template;
		this.ncbiProvider = NCBIKeeper.getPreferredNCBIProvider();
		this.blastTools = blastTools;
		this.blastTools2 = blastTools.getBlastTools2();
		this.accessionRunId = levelFourRequestableRow.getAccessionRunId();
		this.userId = levelFourRequestableRow.getUserId();
		this.jobName = levelFourRequestableRow.getJobName().replaceAll(" ", "_");
		this.templateInputString = levelFourRequestableRow.getTemplate();
		this.templateInputPDBString = levelFourRequestableRow.getTemplatePDB();
		this.accessionData = levelFourRequestableRow.getAccessionData();
		this.level4RunId = levelFourRequestableRow.getLevel4RunId();
		this.sourceLevel = levelFourRequestableRow.getSourceLevel();
		this.level2RunId = levelFourRequestableRow.getLevel2RunId();
	}

	public RunLevel4(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper NCBIKeeper,
			LevelFourRequestableRow levelFourRequestableRow, int tmAlignRunId) {
		this.jdbcTemplate = template;
		this.ncbiProvider = NCBIKeeper.getPreferredNCBIProvider();
		this.blastTools = blastTools;
		this.blastTools2 = blastTools.getBlastTools2();
		this.accessionRunId = levelFourRequestableRow.getAccessionRunId();
		this.userId = levelFourRequestableRow.getUserId();
		this.jobName = levelFourRequestableRow.getJobName().replaceAll(" ", "_");
		this.templateInputString = levelFourRequestableRow.getTemplate();
		this.templateInputPDBString = levelFourRequestableRow.getTemplatePDB();
		this.accessionData = levelFourRequestableRow.getAccessionData();
		this.level4RunId = levelFourRequestableRow.getLevel4RunId();
		this.sourceLevel = levelFourRequestableRow.getSourceLevel();
		this.tmAlignRunId = tmAlignRunId;
		this.level2RunId = levelFourRequestableRow.getLevel2RunId();

		this.queryAccessionData = levelFourRequestableRow.getQueryAccessionData();
		this.tmAlignQueryJobName = levelFourRequestableRow.getQueryJobName();

	}

	public RunLevel4(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper NCBIKeeper) {
		this.jdbcTemplate = template;
		this.ncbiProvider = NCBIKeeper.getPreferredNCBIProvider();
		this.blastTools = blastTools;
		this.blastTools2 = blastTools.getBlastTools2();
	}

	/**
	 * The allFastaDefs is an array of one line strings each <b><u>not</u> starting
	 * with '&gt;'</b> . The content is either supplied by the user or generated by
	 * blastdbcmd (or both). It is interleaved with the allFastaSeqs to create the
	 * full fasta file to be run through COBALT. The zeroth item is the template
	 * fasta definition.
	 */
	private List<String> allFastaDefs = new ArrayList<String>();

	/**
	 * The allFastaSeqs is an array of (potentially multiline) amino acid sequences.
	 * The content is either supplied by the user or generated by blastdbcmd (or
	 * both). It is interleaved with the allFastaDefs to create the full fasta file
	 * to be run through COBALT. The zeroth item is the template fasta sequence.
	 */
	private List<String> allFastaSeqs = new ArrayList<String>();

	/**
	 * This method sets up a run (and returns null) or returns a string explanation
	 * for why the setup failed. Its parameters are already loaded into the
	 * RunLevel3 object, mostly from the levelThreeReportRow loaded upon
	 * instantiation.
	 * 
	 * @return null if no problem, or a String with the error message to report to
	 *         the user
	 */
	public String setupAndReportFailure() {
		try {
			jdbcTemplate.queryForObject(
					"SELECT id FROM level4_run WHERE accession_run_id = ? AND user_id = ? AND job_name = ?", int.class,
					accessionRunId, userId, jobName); // NO NEED TO CAPTURE id, IT WILL FAIL THE try IF IT DOES NOT
														// EXIST
			return "This run name is already selected";
		} catch (DataAccessException e1) {
			// DO NOTHING, it just means the job is available
		}
		if (jobName.matches("[^+ a-zA-Z0-9_-]")) {
			return "Job name must be alphanumeric or space, plus, minus, or underscore";
		}
		if (jobName.length() > 63) {
			return "Job name must be 63 characters or fewer";
		}
		String possibleFailure = parseTemplateInputString();
		if (possibleFailure != null) {
			return possibleFailure;
		}
		// NOW GET NEW JOB ID
		try {
			level4RunId = blastTools.insertLevel4RunReturnId(accessionRunId, userId, jobName, sourceLevel, level2RunId);
		} catch (SQLException e) {
			return "Could not submit Level 4 job";
		}
		return null;
	}

	private String parseTemplateInputString() {
//		System.out.println("Need to implement parseTemplateInputString");
//		// user supplied accession id so --> Confirm validity, get fasta, and separate parts
//		String templateTrim = templateInputString.trim();
//		String correctedAccesssionId = blastTools.getCorrectAccessionFromAccessionIdName(templateTrim);
//		if (correctedAccesssionId == null) {
//			// System.out.println("failure with template: " + templateTrim);
//			logger.error("Accession ID of template not found; failure with template: {}", templateTrim);
//			return "Accession ID of template not found";
//		}
//		if (!correctedAccesssionId.equals(templateTrim)) {
//			// System.out.println("Corrected template: " + correctedAccesssionId);
//			logger.warn("Corrected template: {}", correctedAccesssionId);
//		}
//		// First one, so don't need to confirm that there is not already one
//		allFastaDefs.add(correctedAccesssionId);
//
//		String[] fastaParts = blastTools.getFastaPartsFromAccession(correctedAccesssionId);
//		if (fastaParts == null) {
//			return "Could not get sequence from " + templateInputString;
//		}
//		allFastaSeqs.add(fastaParts[1]);
		return null; // All's well
	}

	/**
	 * This method runs BlastDBcmd to find needed FASTAs as well as updates
	 * priorities
	 * 
	 * @param
	 * @return int - exit codes for creation of fasta. 0=success, -1=failure
	 */
	public int runFASTAs() {
		if (accessionRunId < 0 || level4RunId < 0) {
			logger.error("Something wrong with level 4 run:  level4RunId, accessionRunId (in order) are: {}, {}",
					level4RunId, accessionRunId);
			return -1;
		} else {
			blastTools2.updateLevel4Status(level4RunId, "generating FASTAs");
			StringBuilder ncbiAccessionsStringBuilder = createStringBuilderOfLevelFourNCBIAccessions(accessionData);
			String inputSubjectPath = ncbiProvider.getPathTempFasta() + "/level4-" + level4RunId + ".txt";
			boolean wroteInputSubjectPath = writeInputSubjectPath(inputSubjectPath, ncbiAccessionsStringBuilder);
			if (!wroteInputSubjectPath) {
				logger.error("Write inputSubjectPath to fasta List failed");
				return -1;
			}
			// lookup FASTAs using blastDBcmd
			ProcessProvider generateFASTAs = createFastaProcessProvider(inputSubjectPath);
			String[] result = null;
			try {
				result = generateFASTAs.timeRun();
			} catch (InterruptedException | IOException e) {
				logger.error("Creating fasta for Level 4 failed");
				return -1;
			}
			Map<String, String> fastaLookupMap = buildFastaLookup(result);
			setLevelFourRowStatus(accessionData, fastaLookupMap);
			blastTools2.insertLevel4Data(level4RunId, accessionData);
			blastTools2.updateLevel4Status(level4RunId, "FASTAs complete");
			blastTools2.updateLevel4RunDate(level4RunId, "running");
			return 0;
		}
	}

	public int runItasser() {
		ArrayList<String> tempFilesForJob = new ArrayList<String>();
		logger.debug("Level4ID#" + level4RunId + "." + "Beginning Run I-TASSER Routine with template:" + templateInputString);

		String formatTemplate = "none";
		boolean useDefaultRestraint = true;
		if(templateInputString == null && templateInputPDBString == null) {
			//Do Nothing. No Template.
		} else if (templateInputString != null && templateInputPDBString == null) {
			if(!templateInputString.trim().isEmpty()) {
				//Template = PDBID
				formatTemplate = templateInputString.trim().replaceAll("[_:]", "+");
				blastTools.getBlastTools2().updateLevel4Template(level4RunId, formatTemplate, null);
			}
		} else if (templateInputString != null && templateInputPDBString != null) {
			if(!templateInputString.trim().isEmpty() && !templateInputPDBString.isEmpty()) {
				//Template = User-Defined
				formatTemplate = templateInputString;
				blastTools.getBlastTools2().updateLevel4Template(level4RunId, formatTemplate, templateInputPDBString);
				useDefaultRestraint = false;
			}
		}
		
//		// Retrieve template
//		String formatTemplate = "none";
//		if (templateInputString != null && !templateInputString.trim().isEmpty()) {
//			formatTemplate = templateInputString.trim().replaceAll("[_:]", "+");
//			blastTools.getBlastTools2().updateLevel4Template(level4RunId, formatTemplate);
//		}

		// Set common dir
		if (blastTools.isProd()) {
			commonDirectory = prodDir + "/" + inputScriptsOutput;
		} else {
			commonDirectory = testDir + "/" + inputScriptsOutput;
		}

		hpcConnector = new HPCConnector("/bin/sudo", "seqapass", commonDirectory);
		commonScriptPathLocal = hpcConnector.getCommonRootLocal() + "/" + commonDirectory;
		itasserCommand = hpcConnector.getCommonRootLocal() + "/itasser_world/submit_tools/runI-TASSER.pl";
		pathToLibDir = hpcConnector.getCommonRootLocal() + "/itasser_world/ITLIB";

		if (level4RunId < 0) {
			logger.fatal("Level4ID#" + level4RunId + "." + "Level 4 Run Id < 0");
			return -1;
		}

		if (accessionData.size() == 0) {
			return 0;
		}

		// Create level 4 project dir
		String projectDir = commonScriptPathLocal + "/" + level4RunId + "_" + jobName;
		try {
			if (!new File(projectDir).exists()) {
				Files.createDirectory(Paths.get(projectDir));
			}
			logger.debug("Level4ID#" + level4RunId + "." + "Project Dir = " + projectDir);
		} catch (Exception e1) {
			e1.printStackTrace();
			logger.fatal("Level4ID#" + level4RunId + ". Can't create project directory: " + projectDir);
			return -1;
		}

		// Create accession file
		String queryAccession = blastTools.selectQueryAccessionFromUserRunAccessionRun(accessionRunId);
		createAccessionFile(queryAccession, new File(projectDir));

		logger.debug("Level4ID#" + level4RunId + "." + "Query Accession = " + queryAccession);

		hpcConnector.setSbatchCommandFullPath("/usr/local/bin/sbatch");

		// Ensure in correct directory
		String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
		ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
		try {
			processBuilder.start();
		} catch (IOException e) {
			logger.error("Level4ID#" + level4RunId + ". Could not change directories to " + commonScriptPathLocal + " Proccess Error: " + e);
		}

		// Holds SBatchObjects for polling job status
		LinkedBlockingQueue<SBatchObject> linkedBlockingQueuePolling = new LinkedBlockingQueue<>();
		// Holds SBatchObjects for updating DB
		LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate = new LinkedBlockingQueue<>();
		// Holds all of the jobs that completed with no errors.
		ArrayList<SBatchObject> completedJobs = new ArrayList<SBatchObject>();

		Timer timer = new Timer();
		Polling poll = new Polling(linkedBlockingQueuePolling, linkedBlockingQueueUpdate, hpcConnector, blastTools, timer, null, null, null, this);
		Thread pollingThread = new Thread(poll);
		pollingThread.start();
		Updating update = new Updating(linkedBlockingQueueUpdate, jdbcTemplate, blastTools, accessionRunId, completedJobs, false, false, false, true, ncbiProvider.getId(), hpcConnector);
		Thread updatingThread = new Thread(update);
		updatingThread.start();

		// Maps a unique FASTA string to a list of LevelFourAccessionRows
		HashMap<String, ArrayList<LevelFourAccessionRow>> uniqueFASTAs = new HashMap<String, ArrayList<LevelFourAccessionRow>>();

		// Document duplicate FASTA & indexes of rows to run
		ArrayList<Integer> indexesOfRowsSelected = new ArrayList<Integer>(); // All non unique FASTA row indexes
		ArrayList<Integer> indexesOfUniqueRowsToRun = new ArrayList<Integer>(); // All unique FASTA row indexes

		for (LevelFourAccessionRow levelFourAccessionRow : accessionData) {
			if (levelFourAccessionRow.getStatus().contentEquals("FASTA created")) {
				String levelFourFastaString = levelFourAccessionRow.getFasta();
				if (uniqueFASTAs.containsKey(levelFourFastaString)) {
					ArrayList<LevelFourAccessionRow> listOfRowsWithFASTA = uniqueFASTAs.get(levelFourFastaString);
					listOfRowsWithFASTA.add(levelFourAccessionRow);
				} else {
					ArrayList<LevelFourAccessionRow> listOfRowsWithFASTA = new ArrayList<LevelFourAccessionRow>();
					listOfRowsWithFASTA.add(levelFourAccessionRow);
					uniqueFASTAs.put(levelFourFastaString, listOfRowsWithFASTA);

					// Only run row if the FASTA has not been seen
					indexesOfUniqueRowsToRun.add(accessionData.indexOf(levelFourAccessionRow));
				}
				// Document all rows for directory creation
				indexesOfRowsSelected.add(accessionData.indexOf(levelFourAccessionRow));
			}
		}

		// numberOfJobs reflects the number of unique FASTAs submitted
		int numberOfJobs = indexesOfUniqueRowsToRun.size();
		update.setTotalCount(numberOfJobs);
		update.setRemainingCount(numberOfJobs);

		if (numberOfJobs > 0) {
			blastTools2.updateLevel4Status(level4RunId, "I-TASSER running");

			// write template file to projectDir
			File templateFile = new File(projectDir + "/template_" + formatTemplate);
			try {
				PrintWriter printWriter = new PrintWriter(templateFile);
				printWriter.flush();
				printWriter.close();
			} catch (Exception e) {
				logger.error("Level4ID#" + level4RunId + ". Could not write template file to " + templateFile);
				return -1;
			}

			// write FASTA stats to projectDir
			File fastaStatsFile = new File(projectDir + "/FASTA_Stats.txt");
			try {
				PrintWriter printWriter = new PrintWriter(fastaStatsFile);
				for (String uniqueFASTAString : uniqueFASTAs.keySet()) {
					ArrayList<LevelFourAccessionRow> listOfRowsWithFASTA = uniqueFASTAs.get(uniqueFASTAString);
					StringBuilder accessionStringBuilder = new StringBuilder();
					LevelFourAccessionRow rowChosenToRunForFASTA = listOfRowsWithFASTA.get(0);
					for (LevelFourAccessionRow levelFourRow : listOfRowsWithFASTA) {
						if (listOfRowsWithFASTA.size() > 1) {
							accessionStringBuilder.append(levelFourRow.getNcbiAccession() + ", ");
						} else {
							accessionStringBuilder.append(levelFourRow.getNcbiAccession());
						}
					}
					printWriter.println(rowChosenToRunForFASTA.getNcbiAccession() + " | " + accessionStringBuilder.toString());
				}
				printWriter.flush();
				printWriter.close();
			} catch (Exception e) {
				logger.error("Level4ID#" + level4RunId + ". Could not write FASTA stats file to " + fastaStatsFile);
				return -1;
			}

			jobFragmentNumber = 0;

			// For all non unique rows
			for (int i : indexesOfRowsSelected) {
				LevelFourAccessionRow acc = accessionData.get(i);
				jobFragmentNumber++;

				// create sub directory for all rows
				String proteinDir = createSubDirectory(acc, projectDir, formatTemplate);
				if (proteinDir == null) {
					return -1;
				}

				int aaCount = acc.getFasta().length();
				int hourCount = aaCount / 8;
				if (hourCount > computeMax) {
					hourCount = computeMax;
				}

				String maxTimeAllotted = "03-00"; // days-hours
				int days = hourCount / 24;
				int hours = hourCount % 24;
				maxTimeAllotted = days + "-" + hours;

				String jobDir = proteinDir;
				String localJobName = jobName + "_" + acc.getNcbiAccession();

				logger.debug("Level4ID#" + level4RunId + "." + "Job Name = " + localJobName);

				// Write seq.fasta for all rows
				StringBuilder fastaStringBuilder = new StringBuilder();
				fastaStringBuilder.append(">" + acc.getNcbiAccession() + " ");
				fastaStringBuilder.append("; protein name:" + acc.getProteinName() + " ");
				fastaStringBuilder.append("; taxid:" + acc.getSpeciesTaxId() + " ");
				fastaStringBuilder.append("; common name:" + acc.getCommonName() + " ");
				fastaStringBuilder.append("; scientific name:" + acc.getScientificName());
				fastaStringBuilder.append("\n");
				// replace all 'X' amino acids with 'A' to allow sequence to run
				fastaStringBuilder.append(acc.getFasta().replaceAll("X", "A"));

				File seqFasta = new File(jobDir + "/seq.fasta");
				try {
					PrintWriter printWriter = new PrintWriter(seqFasta);
					printWriter.println(fastaStringBuilder.toString());
					printWriter.flush();
					printWriter.close();
				} catch (Exception e) {
					logger.error("Level4ID#" + level4RunId + "." + "Could not write seq.fasta to " + seqFasta.getPath());
					return -1;
				}

				// If the row index is supposed to be run
				if (indexesOfUniqueRowsToRun.contains(i)) {

					StringBuilder shellCommand = new StringBuilder();
					shellCommand.append(itasserCommand);
					shellCommand.append(" -libdir " + pathToLibDir);
					shellCommand.append(" -seqname " + localJobName);
					shellCommand.append(" -datadir " + jobDir);
					
					//If PDBID
					if(useDefaultRestraint) {
						shellCommand.append(" -restraint3 " + templateInputString.trim().replace("_", ":"));
					} else { //If user defined pdb
						File pdbContentFile = new File(jobDir + "/template_" + formatTemplate + ".pdb");
						try {
							PrintWriter printWriter = new PrintWriter(pdbContentFile);
							printWriter.println(templateInputPDBString);
							printWriter.flush();
							printWriter.close();
						} catch (Exception e) {
							logger.error("Level4ID#" + level4RunId + "." + "Could not write user defined pdb to " + pdbContentFile.getPath());
							return -1;
						}
						shellCommand.append(" -restraint4 " + pdbContentFile.getName());
					}

					// Create the run script for ITASSER
					String scriptName = "runITASSER_" + level4RunId + "_" + acc.getNcbiAccession() + ".sh"; // Ex:runITASSER_level4RunId_5766.sh
					scriptCommandPath = projectDir + "/" + scriptName;

					try {
						PrintWriter writer = new PrintWriter(scriptCommandPath);
						writer.println("#!/bin/csh");
						writer.println("#SBATCH --ntasks=1");
						writer.println("#SBATCH --time=" + maxTimeAllotted);
						writer.println("#SBATCH --account=seqapass");
						writer.println("#SBATCH --ntasks-per-node=" + numThreads);
						writer.println("#SBATCH --chdir=" + commonScriptPathLocal);
						writer.println("#SBATCH --error=" + jobDir + "/slurm-%j.err");
						writer.println("#SBATCH --output=" + jobDir + "/slurm-%j.out");
						writer.println();
						writer.println("cd " + commonScriptPathLocal);
						writer.println(shellCommand.toString()); // Write the command to the shell script file
						writer.flush();
						writer.close();
					} catch (FileNotFoundException e) {
						logger.error("Level4ID#" + level4RunId + "." + " I-TASSER Error: System could not write shell script {}", e);
						return -1;
					}

					pathToSendToSBatch = level4RunId + "_" + jobName + "/" + scriptName;
					tempFilesForJob.add(scriptCommandPath);				
					
					int jobIdReturned = hpcConnector.sbatch(pathToSendToSBatch); // Submit sbatch job
					logger.debug("Level4ID#" + level4RunId + "." + "Acc = " + acc.getNcbiAccession() + " Job Id = " + jobIdReturned);

					if (jobIdReturned == -1) {
						return -1;
					}

					timer.start();

					String pathToResultsOutput = jobDir + "/" + "slurm-" + jobIdReturned + ".out";
					String pathToJobErrorFile = jobDir + "/" + "slurm-" + jobIdReturned + ".err";
					tempFilesForJob.add(pathToResultsOutput);
					tempFilesForJob.add(pathToJobErrorFile);

					tempFilesMap.put(jobIdReturned, tempFilesForJob);

					ArrayList<LevelFourAccessionRow> listOfRowsWithFASTA = uniqueFASTAs.get(acc.getFasta());				
					
					SBatchObject sbatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput, jobFragmentNumber, accessionRunId, pathToJobErrorFile, maxTimeAllotted, acc.getNcbiAccession());
					sbatchObject.setNumberOfProteinsInFasta(aaCount);
					sbatchObject.setLevel4JobDir(jobDir);
					sbatchObject.setLevel4LocalJobName(localJobName);
					sbatchObject.setLevel4ProjectDir(projectDir);
					sbatchObject.setLevel4RunId(level4RunId);
					sbatchObject.setLevelFourAccessionRow(acc);
					sbatchObject.setLevelFourRowsWithCommonAccessions(listOfRowsWithFASTA);

					linkedBlockingQueuePolling.add(sbatchObject); // Add each sbatch job to the polling queue

				}
				//TODO: Check timing of this is OK
				blastTools.getBlastTools2().updateLevel4DataStatusAndDate(level4RunId, acc.getNcbiAccession(), "itasser", "running");
			}
		}

		SBatchObject stopJob = new SBatchObject(-1, "stop", -1, -1, null, String.valueOf(0), null);
		linkedBlockingQueuePolling.add(stopJob);

		while (go) { // once this exits, polling and updating are complete
			if (!pollingThread.isAlive() && polling) {
				polling = false;
				logger.debug("Level4ID#" + level4RunId + "." + "Polling Completed");
			}
			if (!updatingThread.isAlive() && updating && (polling == false)) {
				updating = false;
				logger.debug("Level4ID#" + level4RunId + "." + "Updating Completed");
			}
			if ((polling == false) && (updating == false)) {
				go = false;
				logger.debug("Level4ID#" + level4RunId + "." + "Polling && Updated Completed");
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		timer.stop();	
		
		blastTools2.updateLevel4Status(level4RunId, "I-TASSER complete");
		blastTools2.updateLevel4RunDate(level4RunId, "complete"); // will need to be moved after tmalign is added.
		logger.debug("Level4ID#" + level4RunId + "." + "Completed DB Updates");
		logger.info("Level4ID#" + level4RunId + "." + "is done");

		return 0;
	}

	public void runLevel4Update(int level4RunId) {
		Updating updating = new Updating(null, jdbcTemplate, blastTools, accessionRunId, null, false, false, false,
				false, ncbiProvider.getId(), hpcConnector);
		String jobName = queryForJobName(level4RunId);
		if (jobName != null) {
			String pathToJobDir = getBasePathForLevel4JobDir(jobName, level4RunId);
			String queryAccession = getLevel4QueryAccession(pathToJobDir);
			if (queryAccession != null) {
				LevelFourAccessionRow row = new LevelFourAccessionRow();
				row.setStatus("complete");
				row.setLevel4RunId(level4RunId);
				ArrayList<File> proteinDirs = getProteinDirs(pathToJobDir);
				for (File proteinDir : proteinDirs) {
					String rowAccession = parseForRowAccession(proteinDir.getName());
					if (rowAccession != null) {
						row.setNcbiAccession(rowAccession);
						HashMap<String, File> resultFiles = getLevelFourResultFiles(proteinDir);
						File cscore = resultFiles.get("cscore");
						File model1pdb = resultFiles.get("pdb");
						if (cscore != null && cscore.exists() && model1pdb != null && model1pdb.exists()) {
							updating.parseCScoreFile(cscore, row);
							updating.parsePDBFile(model1pdb, level4RunId, rowAccession);
						}
						blastTools.getBlastTools2().updateLevel4DataStatusAndDate(level4RunId, rowAccession, "itasser",
								"completed");
					} else {
						logger.error("runLevel4Update rowAccession = null");
					}
				}
			} else {
				logger.error("runLevel4Update queryAccession = null");
			}
		} else {
			logger.error("runLevel4Update jobName = null");
		}
	}

	private String parseForRowAccession(String proteinDirName) {
		Pattern accessionPattern = Pattern.compile("(.*)_([A-za-z]+[0-9]+.[0-9]+)\\+(.*)+");
		Matcher matcher = accessionPattern.matcher(proteinDirName);
		if (matcher.matches()) {
			return matcher.group(2);
		}
		return null;
	}

	private String getBasePathForLevel4JobDir(String jobName, int level4RunId) {
		String baseDir = "/work/SEQAPASS";
		if (blastTools.isProd()) {
			commonDirectory = baseDir + "/" + prodDir + "/" + inputScriptsOutput;
		} else {
			commonDirectory = baseDir + "/" + testDir + "/" + inputScriptsOutput;
		}
		return commonDirectory + "/" + level4RunId + "_" + jobName;
	}

	private HashMap<String, File> getLevelFourResultFiles(File proteinDir) {
		HashMap<String, File> resultFiles = new HashMap<String, File>();
		for (File file : proteinDir.listFiles()) {
			if (file.getName().contentEquals("cscore")) {
				resultFiles.put("cscore", file);
			} else if (file.getName().contentEquals("model1.pdb")) {
				resultFiles.put("pdb", file);
			}
		}
		return resultFiles;
	}

	private String queryForJobName(int level4RunId) {
		String query = "SELECT job_name FROM level4_run WHERE id = ?";
		try {
			String jobName = jdbcTemplate.queryForObject(query, String.class, level4RunId);
			return jobName;
		} catch (DataAccessException e) {
			logger.error("Could not retrieve jobName for runLevel4Update");
			return null;
		}
	}

	private String getLevel4QueryAccession(String jobDir) {
		File jobDirectory = new File(jobDir);
		File[] files = jobDirectory.listFiles();
		for (File file : files) {
			Pattern accessionFilePattern = Pattern.compile("accession_(.*)");
			if (accessionFilePattern.matcher(file.getName()).matches()) {
				File accessionFile = file;
				Pattern accessionPattern = Pattern.compile("accession_(.*)_(.*)");
				Matcher matcher = accessionPattern.matcher(accessionFile.getName());
				if (matcher.matches()) {
					return matcher.group(1);
				}
			}
		}
		return null;
	}

	private ArrayList<File> getProteinDirs(String jobDir) {
		ArrayList<File> proteinDirs = new ArrayList<>();
		File[] files = new File(jobDir).listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				Pattern nonProteinDir = Pattern.compile("([0-9]+)_(.*)");
				if (!nonProteinDir.matcher(file.getName()).matches()) { // Does not match the nonProteinDir (we want the
																		// protein dir)
					File proteinDir = file;
					proteinDirs.add(proteinDir);
				}
			}
		}
		return proteinDirs;
	}

	public void handleAllLevel1UniprotAccessions(int accessionRunId) {
		ArrayList<String> level1Accessions = blastTools.getNCBIAccessionsFromAccessionRunId(accessionRunId);
		ArrayList<String> existingUniprotAccessions = blastTools.getNCBIAccessionsFromUniProtTable();
		ArrayList<String> accessionsThatNeedUniprot = compareLevel1AccesionsToTableAccessions(level1Accessions,
				existingUniprotAccessions);
		HashMap<String, String> accMap = findUniProtIds(accessionsThatNeedUniprot);
		blastTools.insertUniprotIds(accMap);
	}

	private ArrayList<String> compareLevel1AccesionsToTableAccessions(ArrayList<String> level1Accessions,
			ArrayList<String> uniprotAccessions) {
		ArrayList<String> toRunInUniprot = new ArrayList<String>();
		if (level1Accessions != null && uniprotAccessions != null) {
			for (String accession : level1Accessions) {
				if (!uniprotAccessions.contains(accession)) {
					toRunInUniprot.add(accession);
				}
			}
		}
		return toRunInUniprot;
	}

//	private void findUniProtIds(List<Integer> indexesOfRowsToRun) {
//		
//		Map<String, String> ncbiUniProtMap = new HashMap<String, String>();
//		for (int i : indexesOfRowsToRun) {
//			LevelFourAccessionRow accRow = accessionData.get(i);
//			List<String> accList = blastTools.findCanonicalAccessionPlusIdenticalsFromTaxidString(accRow.getNcbiAccession());
//			//search list for first listed UniProtId
//			
//			String pattern = "[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}";
//		    Pattern uniProtPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
//		    for (String acc : accList) {
//		    	Matcher matcher = uniProtPattern.matcher(acc);
//		    	if (matcher.find()) {
//			        String value = matcher.group(1);
//			        ncbiUniProtMap.put(acc, matcher.group(1));
//			        break;  //exit loop after first match
//			    }
//		    }	
//		}
//		
//	}

	private HashMap<String, String> findUniProtIds(List<String> inputAccs) {

		HashMap<String, String> ncbiUniProtMap = new HashMap<String, String>();
		for (String inputAcc : inputAccs) {
			String foundAcc = findUniProtId(inputAcc);
//			if (foundAcc != null) {
			// remove any characters after decimal
			if (foundAcc != null) {
				foundAcc = foundAcc.split("\\.", 2)[0];
			}
			ncbiUniProtMap.put(inputAcc, foundAcc);
//			}
		}

		return ncbiUniProtMap;
	}

	public String findUniProtId(String inputAcc) {
		List<String> accList = blastTools.findCanonicalAccessionPlusIdenticalsFromTaxidString(inputAcc);
		// search list for first listed UniProtId
//		System.out.println("Acclist: ");
//		System.out.println(accList);

		String pattern = "[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}";
		Pattern uniProtPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		for (String acc : accList) {
			Matcher matcher = uniProtPattern.matcher(acc);
			if (matcher.find()) {
				return acc;
			}
		}
//	    System.out.println("No Swiss Prot match found");
		return null;

	}

	private void createAccessionFile(String queryAccession, File projectDir) {
		// String accName = queryAccession + "_v" + ncbiProvider.getCobaltDataVersion();
		String accName = "accession_" + queryAccession + "_v" + ncbiProvider.getCobaltDataVersion();
		File accFile = new File(projectDir + "/" + accName);
		try {
			accFile.createNewFile();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public SBatchObject resubmitJob(SBatchObject expiredSBatchObject) {
		String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
		ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
		try {
			processBuilder.start();
		} catch (IOException e) {
			logger.error("Lev4RunId#" + level4RunId + "." + jobFragmentNumber + ":  Could not change directories to "
					+ commonScriptPathLocal + " Proccess Error: " + e);
		}

		logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Resubmitting SBatch job "
				+ expiredSBatchObject.getJobId());

		String timeAllottedDoubled = expiredSBatchObject.getTimeAllotted();
		String[] timeSplitStrings = timeAllottedDoubled.split("-");
		if (timeSplitStrings.length == 2) {
			int days = Integer.parseInt(timeSplitStrings[0]);
			int hours = Integer.parseInt(timeSplitStrings[1]) + (days * 24); // Total Hours
			hours = hours * 2;
//			if (hours > 168) {
//				hours = 168;
//			}
//			if (hours > scavengerMax) {
//				hours = scavengerMax;
//			}
			if (hours > computeMax) {
				hours = computeMax;
			}

			String maxTimeAllotted = "03-00"; // days-hours
//			if (hourCount > 100) {
//				int days = (int) (hourCount / 24);
//				int hours = hourCount - (24 * days);
//				maxTimeAllotted = days + "-" + hours;
//			} else {
//				int hours = Math.min(hourCount + 6, 100);

			// assumes scavengerMax < 100
//			int hours = Math.min(hourCount + 6, scavengerMax);
//			maxTimeAllotted = "00-" + hours;

			days = hours / 24;
			hours = hours % 24;
			maxTimeAllotted = days + "-" + hours;
		}

		ArrayList<String> expiredJobFilesList = tempFilesMap.get(expiredSBatchObject.getJobId()); // get the files that
																									// the expired
																									// sbatch object
																									// used
		String oldScriptPath = expiredJobFilesList.get(0); // record the old script path

		String scriptName = "runITASSER_" + level4RunId + "_" + expiredSBatchObject.getQueryAccessionIdString()
				+ "_try_" + timeAllottedDoubled + ".sh"; // Ex:runITASSER_level4RunId_5766.sh
		String scriptPath = commonScriptPathLocal + "/" + level4RunId + "_" + jobName + "/" + scriptName;

		File expiredShellScriptFile = new File(oldScriptPath);
		expiredShellScriptFile.delete(); // delete old script

		StringBuilder shellCommand = new StringBuilder();
		shellCommand.append(itasserCommand);
		shellCommand.append(" -libdir " + pathToLibDir);
		shellCommand.append(" -seqname " + expiredSBatchObject.getLevel4LocalJobName());
		shellCommand.append(" -datadir " + expiredSBatchObject.getLevel4JobDir());

		try {
			PrintWriter writer = new PrintWriter(scriptPath);
			writer.println("#!/bin/csh");
//			 writer.println("#SBATCH --partition=scavenger"); //UNCOMMENT when scavenger partition is set up
			// writer.println("#SBATCH --requeue"); //UNCOMMENT when scavenger partition is
			// set up
			writer.println("#SBATCH --ntasks=1");
			writer.println("#SBATCH --time=" + timeAllottedDoubled);
			writer.println("#SBATCH --account=seqapass");
			// writer.println("#SBATCH --gid=seqapass");
			writer.println("#SBATCH --ntasks-per-node=" + numThreads);
			writer.println("#SBATCH --chdir=" + commonScriptPathLocal);
			writer.println("#SBATCH --error=" + expiredSBatchObject.getLevel4JobDir() + "/slurm-%j.err");
			writer.println("#SBATCH --output=" + expiredSBatchObject.getLevel4JobDir() + "/slurm-%j.out");
			writer.println();
			writer.println("cd " + commonScriptPathLocal);
			writer.println(shellCommand.toString()); // Write the command to the shell script file
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber
					+ ":  System could not write to shell script. " + e);
		}

		// Delete files from old sbatch object
		File expiredErrFile = new File(expiredSBatchObject.getErrorFilePath());
		expiredErrFile.delete();
		File expiredOutFile = new File(expiredSBatchObject.getOutputFilePath());
		expiredOutFile.delete();

		pathToSendToSBatch = level4RunId + "_" + jobName + "/" + scriptName;
		int jobIdReturned = hpcConnector.sbatch(pathToSendToSBatch);
		if (jobIdReturned == -1) {
			logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not resubmit SBatch job");
		}

		ArrayList<String> resubmitJobTempFilesList = new ArrayList<String>();
		String pathToResultsOutput = expiredSBatchObject.getLevel4JobDir() + "/" + "slurm-" + jobIdReturned + ".out";
		String pathToJobErrorFile = expiredSBatchObject.getLevel4JobDir() + "/" + "slurm-" + jobIdReturned + ".err";

		resubmitJobTempFilesList.add(scriptPath);
		resubmitJobTempFilesList.add(pathToResultsOutput);
		resubmitJobTempFilesList.add(pathToJobErrorFile);

		tempFilesMap.put(jobIdReturned, resubmitJobTempFilesList);

		SBatchObject resubmitSBatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput,
				expiredSBatchObject.getJobFragmentNumber(), accessionRunId, pathToJobErrorFile, timeAllottedDoubled,
				expiredSBatchObject.getQueryAccessionIdString());
		resubmitSBatchObject.setNumberOfProteinsInFasta(expiredSBatchObject.getNumberOfProteinsInFasta());
		resubmitSBatchObject.setLevel4JobDir(expiredSBatchObject.getLevel4JobDir());
		resubmitSBatchObject.setLevel4LocalJobName(expiredSBatchObject.getLevel4LocalJobName());

		logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  After resubmitting, old job "
				+ expiredSBatchObject.getJobId() + " is now " + resubmitSBatchObject.getJobId());
		tempFilesMap.remove(expiredSBatchObject.getJobId()); // remove mapping of old sbatch object

		return resubmitSBatchObject;
	}

	public int runTMAlign() {
		if (accessionRunId < 0 || level4RunId < 0) {
			logger.error(
					"Something wrong with level 4 TM-Align run:  level4RunId, accessionRunId (in order) are: {}, {}",
					level4RunId, accessionRunId);
			return -1;
		} else {
			int numAccessionSubmitted = submitTMAlign();
			return numAccessionSubmitted;
		}
	}

	private int submitTMAlign() {
		logger.debug("Inside submitTMAlign");
		String st = " \t";

		if (blastTools.isProd()) {
			commonDirectory = prodDir + "/" + inputScriptsOutput;
		} else {
			commonDirectory = testDir + "/" + inputScriptsOutput;
		}
		hpcConnector = new HPCConnector("/bin/sudo", "seqapass", commonDirectory);
		commonScriptPathLocal = hpcConnector.getCommonRootLocal() + "/" + commonDirectory;

		String level4ProjectDir = commonScriptPathLocal + "/" + level4RunId + "_" + jobName;

		String queryAccession = queryAccessionData.getNcbiAccession();
		String queryAccessionPDBSource = queryAccessionData.getPdbSource();
		String queryAccessionFormattedCommonName = queryAccessionData.getCommonName().replaceAll(" ", "_");
		String queryAccessionLevel4ProjectDir = commonScriptPathLocal + "/" + queryAccessionData.getLevel4RunId() + "_"
				+ tmAlignQueryJobName;
		String queryAccessionProteinDir = queryAccessionLevel4ProjectDir + "/" + queryAccessionFormattedCommonName + "_"
				+ queryAccession + "+";
		if (queryAccessionData.getTemplate() != null && !queryAccessionData.getTemplate().contentEquals("null")) {
			queryAccessionProteinDir = queryAccessionProteinDir + queryAccessionData.getTemplate();
		}
		System.out.println("Project Dir = " + level4ProjectDir);
		System.out.println("Query Accession = " + queryAccession);

		List<LevelFourAccessionRow> rows = accessionData;
		ArrayList<Integer> eligableRowList = getEligableRows(rows, level4ProjectDir);

		int numPairs = eligableRowList.size();
		if (eligableRowList.size() > 0) {
			int matrixSize = eligableRowList.size();
			String resultMatrix[][] = new String[matrixSize + 1][matrixSize + 1];
			resultMatrix[0][0] = "\t-\t";

			blastTools2.setTMAlignRunStatus("Running", tmAlignRunId, level4RunId);

			String tmAlignResultDirectoryPath = level4ProjectDir + "/" + tmAlignRunId + "_" + queryAccession;
			System.out.println("Creating TMAlignResultDirectory = " + tmAlignResultDirectoryPath);
			File tmAlignResultDirectory = new File(tmAlignResultDirectoryPath);
			if (!tmAlignResultDirectory.exists())
				tmAlignResultDirectory.mkdir();

			try {
				File tmAlignRunFile = new File(tmAlignResultDirectoryPath + "/tm_run.txt");
				PrintWriter runPrintWriter = new PrintWriter(tmAlignRunFile);
				// Add header line. Note that headers are left aligned.
				String header = new String("Accession1" + st + "Accession2" + st + "L1" + st + "L2" + st + "Value1" + st
						+ "Value2" + st + "AvgValue");
				runPrintWriter.println(header);
				String pairs = new String(
						"There are " + numPairs + " possible pairs of proteins to run through TM Align");
				runPrintWriter.println(pairs);

				String fromAccession = queryAccession;
				int fromCount = 0;
				if (queryAccessionData.getFasta() != null)
					fromCount = queryAccessionData.getFasta().length();

				File fp1;
				if (queryAccessionPDBSource != null && !queryAccessionPDBSource.contentEquals("I-TASSER")) {
					fp1 = new File(
							level4ProjectDir + "/PDBs/" + (tmAlignRunId + "_" + queryAccessionData.getNcbiAccession()
									+ "_" + queryAccessionData.getPdbSource() + ".pdb"));
					if (!fp1.exists()) {
						File pdbFile = createPDBFileForOther(queryAccessionData, level4ProjectDir);
						writePDBToOtherPDBFile(pdbFile, queryAccessionData.getPdb());
					}
				} else {
					fp1 = new File(queryAccessionProteinDir + "/model1.pdb");
				}

				int j = 0;
				resultMatrix[0][j + 1] = fromAccession;
				resultMatrix[j + 1][0] = fromAccession;
				resultMatrix[j + 1][j + 1] = "\t-\t";

				for (int eligableRowIndex : eligableRowList) {
					String toDir = createSubDirectoryName(rows.get(eligableRowIndex), level4ProjectDir,
							templateInputString);
					String toAccession = rows.get(eligableRowIndex).getNcbiAccession();
					int toCount = 0;
					if (rows.get(eligableRowIndex).getFasta() != null)
						toCount = rows.get(eligableRowIndex).getFasta().length();
					String val1;
					String val2;

					File fp2;
					if (rows.get(eligableRowIndex).getPdbSource() != null
							&& !rows.get(eligableRowIndex).getPdbSource().contentEquals("I-TASSER")) {
						fp2 = new File(level4ProjectDir + "/PDBs/"
								+ (tmAlignRunId + "_" + rows.get(eligableRowIndex).getNcbiAccession() + "_"
										+ rows.get(eligableRowIndex).getPdbSource() + ".pdb"));
					} else {
						fp2 = new File(toDir + "/model1.pdb");
					}

					if (!fp1.exists() && !fp2.exists()) {
						continue;
					}
					System.out.println("Running tm align with " + fp1 + " and " + fp2);
					HashMap<String, String> tmAlignResultsMap = tmAlign(fp1, fp2);
					System.out.println("mapped results = " + tmAlignResultsMap);

					val1 = tmAlignResultsMap.get("chain 1 tm score");
					val2 = tmAlignResultsMap.get("chain 2 tm score");

					// Only put normal runs into DB. Still OK to check
					if (queryAccession != null && fromAccession.contentEquals(queryAccession)) {
						blastTools2.insertLevel4TMAlignData(tmAlignRunId, level4RunId, fromAccession, toAccession,
								tmAlignResultsMap, rows.get(eligableRowIndex));
					}

					double avg_val = (Double.parseDouble(val1) + Double.parseDouble(val2)) / 2;

					if (queryAccession != null && fromAccession.contentEquals(queryAccession)) {
						String data = new String(fromAccession + st + toAccession + st + fromCount + st + toCount + st
								+ val1 + st + val2 + st + avg_val);
						runPrintWriter.println(data);
					}
					resultMatrix[j + 1][eligableRowIndex + 1] = val1;
					resultMatrix[eligableRowIndex + 1][j + 1] = val2;
				}

				runPrintWriter.close();

				// Sort tm_run.txt on column 7 in ascending order.
				// Note that header line is not included when sorting numeric data. It is
				// combined with the sorted data and then dumped to new output file.

				String tmRunPath = tmAlignResultDirectoryPath + "/tm_run.txt";
				String tmSortedPath = tmAlignResultDirectoryPath + "/tm_sorted.txt";
				File tmSortedFile = new File(tmSortedPath);
				if (!tmSortedFile.exists())
					tmSortedFile.createNewFile();

				File createSortedShellFile = new File(tmAlignResultDirectoryPath + "/createSorted.sh");
				PrintWriter printWriter = new PrintWriter(createSortedShellFile);
				printWriter.println("(head -n1 " + tmRunPath + " && tail -n+2 " + tmRunPath + " | sort -k7 -rn ) > "
						+ tmSortedPath);
				printWriter.close();
				String[] command = { "sh", createSortedShellFile.getPath() };
				ProcessBuilder processBuilder = new ProcessBuilder(command);
				try {
					processBuilder.start();
				} catch (IOException e) {
					e.printStackTrace();
				}

				File csvFile = new File(tmAlignResultDirectoryPath + "/tm_mat.csv");
				PrintWriter csvPrintWriter = new PrintWriter(csvFile);
				for (int i = 0; i < resultMatrix.length; i++) {
					csvPrintWriter.println("," + resultMatrix[i]);
				}
				csvPrintWriter.close();
				createSortedShellFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
			blastTools2.setTMAlignRunStatus("Complete", tmAlignRunId, level4RunId);
		} else {
			blastTools2.setTMAlignRunStatus("No eligible accessions", tmAlignRunId, level4RunId);
		}
		blastTools2.updateTMAlignRunDate(tmAlignRunId); // will need to be moved after tmalign is added.

		return numPairs;
	}

	private ArrayList<Integer> getEligableRows(List<LevelFourAccessionRow> rows, String projectDir) {
		ArrayList<Integer> eligableRowList = new ArrayList<Integer>();
		for (int rowNum = 0; rowNum < rows.size(); rowNum++) {
			LevelFourAccessionRow row = rows.get(rowNum);
			String proteinDir = createSubDirectoryName(rows.get(rowNum), projectDir, templateInputString);
			if (row.getPdbSource() != null && !row.getPdbSource().contentEquals("I-TASSER")) { // is other
				File pdbFile = createPDBFileForOther(row, projectDir);
				if (pdbFile != null && pdbFile.exists()) {
					eligableRowList.add(rowNum);
				}
			} else { // is from itasser
				File pdbFile = new File(proteinDir + "/model1.pdb");
				if (pdbFile.exists()) {
					eligableRowList.add(rowNum);
				}
			}

		}
		return eligableRowList;
	}

	private File createPDBFileForOther(LevelFourAccessionRow row, String projectDir) {
		File pdbDirectory = new File(projectDir + "/PDBs");
		if (!pdbDirectory.exists())
			pdbDirectory.mkdir();
		String pdbFileName = tmAlignRunId + "_" + row.getNcbiAccession() + "_" + row.getPdbSource() + ".pdb";
		File pdbFile = new File(pdbDirectory + "/" + pdbFileName);
		try {
			pdbFile.createNewFile();
			writePDBToOtherPDBFile(pdbFile, row.getPdb());
			return pdbFile;
		} catch (Exception e) {
			logger.error("Could not create pdb file for 'other' tmalign submission");
			return null;
		}
	}

	private void writePDBToOtherPDBFile(File pdbFile, String pdb) {
		try {
			PrintWriter printWriter = new PrintWriter(pdbFile);
			printWriter.println(pdb);
			printWriter.close();
		} catch (Exception e) {
			logger.error("Could not write pdb to 'other' pdb file");
		}
	}

	private HashMap<String, String> tmAlign(File pdbFile1, File pdbFile2) {
		logger.debug("Submitting TMAlign with PDB Files----\n" + pdbFile1 + "\n" + pdbFile2);

		HashMap<String, String> output = new HashMap<String, String>();

		String tmAlignLocation = "/work/SEQAPASS/itasser_world/I-TASSER5.1/bin/TMalign";
		String[] command = { tmAlignLocation, pdbFile1.getPath(), pdbFile2.getPath() };

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();
		Process process;
		try {
			process = processBuilder.start();

			InputStream stdOutStream = process.getInputStream();
			InputStreamReader stdOutStreamReader = new InputStreamReader(stdOutStream);
			BufferedReader stdOutBufferedReader = new BufferedReader(stdOutStreamReader);
			String stdOutLine;
			while ((stdOutLine = stdOutBufferedReader.readLine()) != null) {
				stdOutStringBuilder.append(stdOutLine);
			}

			InputStream stdErrStream = process.getErrorStream();
			InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
			BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
			String stdErrLine;
			while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
				stdErrStringBuilder.append(stdErrLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String stdOut = stdOutStringBuilder.toString(); // stdOut = a string containing many features of a single job

		Pattern pattern1 = Pattern.compile("Length of Chain_1: (\\d+) residues");
		Matcher matcher1 = pattern1.matcher(stdOut);
		if (matcher1.find()) {
			String chain1Length = matcher1.group(1);
			output.put("chain 1 length", chain1Length);
		}

		Pattern pattern2 = Pattern.compile("Length of Chain_2: (\\d+) residues");
		Matcher matcher2 = pattern2.matcher(stdOut);
		if (matcher2.find()) {
			String chain2Length = matcher2.group(1);
			output.put("chain 2 length", chain2Length);
		}

		Pattern pattern3 = Pattern
				.compile("Aligned length= (\\d+), RMSD=   ([0-9.]+), Seq_ID=n_identical\\/n_aligned= ([0-9.]+)");
		Matcher matcher3 = pattern3.matcher(stdOut);
		if (matcher3.find()) {
			String aligned = matcher3.group(1);
			output.put("aligned", aligned);
			String rmsd = matcher3.group(2);
			output.put("rmsd", rmsd);
			String identical = matcher3.group(3);
			output.put("identical", identical);
		}

		Pattern pattern4 = Pattern
				.compile("TM-score= ([0-9.]+) \\(if normalized by length of Chain_1, i.e., LN=(\\d+), d0=([0-9.]+)\\)");
		Matcher matcher4 = pattern4.matcher(stdOut);
		if (matcher4.find()) {
			String chain1TMScore = matcher4.group(1);
			output.put("chain 1 tm score", chain1TMScore);
			String chain1LengthB = matcher4.group(2);
			output.put("chain 1 length b", chain1LengthB);
			String chain1D0 = matcher4.group(3);
			output.put("chain 1 d0", chain1D0);
		}

		Pattern pattern5 = Pattern
				.compile("TM-score= ([0-9.]+) \\(if normalized by length of Chain_2, i.e., LN=(\\d+), d0=([0-9.]+)\\)");
		Matcher matcher5 = pattern5.matcher(stdOut);
		if (matcher5.find()) {
			String chain2TMScore = matcher5.group(1);
			output.put("chain 2 tm score", chain2TMScore);
			String chain2LengthB = matcher5.group(2);
			output.put("chain 2 length b", chain2LengthB);
			String chain2D0 = matcher5.group(3);
			output.put("chain 2 d0", chain2D0);
		}

		return output;
	}

	private StringBuilder createStringBuilderOfLevelFourNCBIAccessions(List<LevelFourAccessionRow> rows) {
		StringBuilder stringBuilder = new StringBuilder();
		for (LevelFourAccessionRow row : rows) {
			if (row.getPriority().toLowerCase().contains("high")
					|| row.getPriority().toLowerCase().contains("user selected")) {
				stringBuilder.append(row.getNcbiAccession() + "\n");
			}
		}
		return stringBuilder;
	}

	private ProcessProvider createFastaProcessProvider(String inputSubjectPath) {
		ProcessProvider generateFASTAs = new ProcessProvider();
		generateFASTAs.setUserID(0);
		generateFASTAs.setJobID(0);
		generateFASTAs
				.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.getManyFastasProcess));
		generateFASTAs.setExecPath(ncbiProvider.getBlastExecPath());
		List<String> argVals = new ArrayList<String>();
		argVals.add(ncbiProvider.getPathNrData());
		argVals.add("prot");
		argVals.add(inputSubjectPath);
		argVals.add(">%a %s");
		generateFASTAs.setArgVals(argVals);
		return generateFASTAs;
	}

	private Map<String, String> buildFastaLookup(String[] result) {
		String oneFastaPatternString = ">([^ ]+) ([A-Z]+)";
		Pattern oneFastaPattern = Pattern.compile(oneFastaPatternString);
		Map<String, String> fastaLookup = new HashMap<String, String>();
		for (int i = 0; i < result.length; i++) {
			Matcher fastaMatcher = oneFastaPattern.matcher(result[i]);
			while (fastaMatcher.find()) {
				String accessionKey = fastaMatcher.group(1);
				String accessionFasta = fastaMatcher.group(2);
				fastaLookup.put(accessionKey, accessionFasta);
			}
		}
		return fastaLookup;
	}

	private Map<String, String> buildFastaLookup(List<LevelFourAccessionRow> data) {
		Map<String, String> resultMap = new HashMap<String, String>();
		for (LevelFourAccessionRow row : data) {
			resultMap.put(row.getNcbiAccession(), row.getFasta());
		}

		return resultMap;
	}

	public void loadLevelTwoFASTAs(LevelFourRequestableRow requestObject) {

		Map<String, String> fastaLookupMap = buildFastaLookup(accessionData);
		setLevelFourRowStatus(accessionData, fastaLookupMap);
		for (LevelFourAccessionRow row : accessionData) {
			row.setStatus("FASTA created");
		}
		blastTools2.insertLevel4Data(level4RunId, accessionData);
		blastTools2.updateLevel4Status(level4RunId, "FASTAs complete");
		blastTools2.updateLevel4RunDate(level4RunId, "running");

		return;
	}

	private boolean writeInputSubjectPath(String inputSubjectPath, StringBuilder stringBuilderOfLevelFourRows) {
		try {
			PrintWriter fastaList = new PrintWriter(inputSubjectPath);
			fastaList.print(stringBuilderOfLevelFourRows.toString());
			fastaList.flush();
			fastaList.close();
			return true;
		} catch (Exception e) {
			logger.error("Write inputSubjectPath to fasta List failed");
			return false;
		}
	}

	private void setLevelFourRowStatus(List<LevelFourAccessionRow> rows, Map<String, String> fastaLookup) {
		for (LevelFourAccessionRow row : rows) {
			if (row.getFasta() == null) {
				row.setFasta(fastaLookup.get(row.getNcbiAccession()));
				if (row.getFasta() != null) {
					boolean isXFasta = fastaContainsX(row.getFasta());
//					if (isXFasta) {
//						row.setStatus("0.4 X aa");
//					} else {
					row.setStatus("FASTA created");
//					}
				}
			}
		}
	}

	private boolean fastaContainsX(String fasta) {
		Pattern xPattern = Pattern.compile("(X)\\w+");
		Matcher matcher = xPattern.matcher(fasta);
		if (matcher.find()) {
			return true;
		}
		return false;
	}

	private String createSubDirectoryName(LevelFourAccessionRow row, String projectDir, String template) {
		String commonName = row.getCommonName();
		commonName = commonName.replaceAll("[ ,]", "_");
		commonName = commonName.replaceAll("\\+", "-");
		commonName = commonName.replaceAll("[^ -~]", "");
		commonName = commonName.replaceAll("'", "");
		// Add accession to all directories
		String proteinDir = projectDir + "/" + commonName + "_" + row.getNcbiAccession() + "+";
		if (templateInputString != null && !templateInputString.trim().isEmpty()) {
			proteinDir = proteinDir + template;
		}
		return proteinDir;

	}

	private String createSubDirectory(LevelFourAccessionRow row, String projectDir, String formatTemplate) {
		String proteinDir = createSubDirectoryName(row, projectDir, formatTemplate);
		try {
			Files.createDirectory(Paths.get(proteinDir));
		} catch (IOException e1) {
			logger.fatal("Level4ID#" + level4RunId + "." + jobFragmentNumber + ": Can't create project directory: "
					+ proteinDir);
			// return null;
		}
		return proteinDir;
	}

	public int getAccessionRunId() {
		return accessionRunId;
	}

	public void setAccessionRunId(int accessionRunId) {
		this.accessionRunId = accessionRunId;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int getLevel4RunId() {
		return level4RunId;
	}

	public void setLevel4RunId(int level4RunId) {
		this.level4RunId = level4RunId;
	}

	public String getTemplateInputString() {
		return templateInputString;
	}

	public void setTemplateInputString(String templateInputString) {
		this.templateInputString = templateInputString;
	}

	public int getAccession_run_id() {
		return accessionRunId;
	}

	public void setAccession_run_id(int accession_run_id) {
		this.accessionRunId = accession_run_id;
	}

	public int getTmAlignRunId() {
		return tmAlignRunId;
	}

	public void setTmAlignRunId(int tmAlignRunId) {
		this.tmAlignRunId = tmAlignRunId;
	}

	public int getComputeMax() {
		return computeMax;
	}

	public void setComputeMax(int computeMax) {
		this.computeMax = computeMax;
	}

	public int getLevel2RunId() {
		return level2RunId;
	}

	public void setLevel2RunId(int level2RunId) {
		this.level2RunId = level2RunId;
	}

}
