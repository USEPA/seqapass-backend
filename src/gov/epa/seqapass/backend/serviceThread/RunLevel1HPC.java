package gov.epa.seqapass.backend.serviceThread;

import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;
import gov.epa.seqapass.backend.hpcAccess.HPCConnector;
import gov.epa.seqapass.backend.hpcAccess.Polling;
import gov.epa.seqapass.backend.hpcAccess.SBatchObject;
import gov.epa.seqapass.backend.utility.Timer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

public class RunLevel1HPC {

	private static Logger logger = LogManager.getLogger(RunLevel1HPC.class);
	
	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private int accessionRunId = -1;

	/** This is the submitted query accession id */
	private String queryAccessionString = null;
	/** This is the submitted query accession id's taxid */
	private int queryAccessionTaxid = -1;

	/** This is the canonical accession id */
	private String canonicalAccessionString = null;
	/** This is the canonical accession id's taxid */
	private int canonicalAccessionTaxid = -1;
	private AccessionRun accessionRun;
	private Set<String> queryTaxidAccessionsAboveIdentity = null;
	private BLASTTools blastTools;
	private BLASTTools2 blastTools2;

	private List<String> nameList;
	private String outputFastaFilePath;
	private HashMap<Integer, ArrayList<String>> tempFilesMap = new HashMap<>();
	
	private String commonDirectory = "BLASTp_temp_files";
	HPCConnector hpcConnector = new HPCConnector("/bin/sudo", "seqapass", commonDirectory);
	private String commonTempPathLocal = hpcConnector.getCommonRootLocal() + "/" + commonDirectory;
	
	// --------------INFO FOR WRITING SHELL SCRIPT---------------------------------
	private int wordSize = 3;
	private int numThreads = 4;
	private int eVal = 10;
	private int outfmt = 5;
	private int maxTimeAllotted = 20;
	private int maxTargetSeqs = 20000;
	private String javaStartingHeapSize = "-Xms200M";
	private String javaMaximumHeapSize = "-Xmx6G";
	private String javaWithPath = "/usr/bin/java";
	private String parserCommand = "BLASTpXMLParser";
	private String scriptCommandPath = null;
	private String command = "/work/SEQAPASS/update/moving/webdata/ncbi-blast-2.8.1+/bin/blastp";
	private String pathToDB = "/work/SEQAPASS/blast-nr-files/blast-nr-db_version_4/nr";

	// ----------------INFO FOR POLLING/UPDATING THREADS--------------------------
	private boolean polling = true;
	private boolean updating = true;
	private boolean go = true;
	// ---------------------------------------------------------------------------

	/**
	 * Constructor for a new Level 1 analysis job. 3 items are passed for access
	 * purposes, and the accessionRunId is a parameter
	 * 
	 * @param blastTools     - passing access to the blastTools object
	 * @param template       - passing access to the JdbcTemplate object
	 * @param keeper         - passing access to the NCBIKeeper object
	 * @param accessionRunId - the newly created accessionRunId
	 */
	public RunLevel1HPC(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, AccessionRun accessionRun) {
		this.jdbcTemplate = template;
		this.ncbiKeeper = keeper;
		this.blastTools = blastTools;
		this.blastTools2 = blastTools.getBlastTools2();
		this.accessionRun = accessionRun;
		this.canonicalAccessionString = accessionRun.getCanonicalAccessionString();
		this.queryAccessionString = accessionRun.getQueryAccessionString();
		this.accessionRunId = accessionRun.getAccessionRunId();
	}

	public boolean dispose() {
		return false;
	}

	public boolean kill() {
		return false;
	}

	@SuppressWarnings("rawtypes")
	public Map<String, List> run() {
		ArrayList<String> tempFilesForJob = new ArrayList<String>();

		//NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		if (accessionRunId < 0) {
			// System.out.println("BLASTp failed because accessionRunId < 0");
			logger.error("BLASTp failed because accessionRunId < 0");
			return null;
		}

		String query = "SELECT canonical_accession_id, ncbi_version_id FROM accession_run WHERE id = ? AND status = 'submitted' LIMIT 1";
		int count = 0;
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		while (rows.size() == 0) {
			// RACE CONDITION, MAY CAUSE THIS TO COME BACK EMPTY?
			rows = jdbcTemplate.queryForList(query, accessionRunId);
			count++;
			if (count > 100) {
				break;
			}
			try {
				Thread.sleep((long) 100);
			} catch (InterruptedException e) {
				// System.out.println("Having trouble sleeping!");
				logger.error("Having trouble sleeping!");
			}
		}

		if (rows.size() == 0) {
			//System.out.println(
			//		"BLASTp failed because we could not get the canonical_accession_id from the accession_run table for id: "
			//				+ accessionRunId);
			logger.error("BLASTp failed because we could not get the canonical_accession_id from the accession_run table for id: {}", 
					accessionRunId);
			// There were no hits from BLASTp, so manage response gracefully
			blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
			blastTools.updateBlastpStatus("no hits found", accessionRunId);
			blastTools2.setAccessionRunCompletion(accessionRunId);

			Map<String, List> emptyResults = new HashMap<String, List>();
			List<Integer> rbhToRunIds = new ArrayList<Integer>();
			List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

			List<Integer> rpsToRunIds = new ArrayList<Integer>();
			List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
			emptyResults.put("rbhIds", rbhToRunIds);
			emptyResults.put("rpsIds", rpsToRunIds);
			emptyResults.put("rbhAccs", rbhAccessionIdsToRunIds);
			emptyResults.put("rpsAccs", rpsAccessionIdsToRunIds);
			return emptyResults;
		}

		Map<String, Object> row1 = rows.get(0);
		Object value1 = row1.get("canonical_accession_id");
		Object value2 = row1.get("ncbi_version_id");

		if (value1 == null || ((String) value1).equals("") || value2 == null || ((int) value2) < 0) {
			//System.out.println(
			//		"BLASTp failed because: value1 == null || ((String) value1).equals(\"\") || value2 == null || ((int) value2) < 0 "
			//				+ value1 + " and " + value2);
			logger.error("BLASTp failed because: value1 == null || ((String) value1).equals(\"\") || "
					+ "value2 == null || ((int) value2) < 0 {} and {}", 
					value1, value2);
			return null;
		}
		//System.out.println("No need to set canonicalAccessionString to " + value1 + " because it is already "
		//		+ canonicalAccessionString);
		logger.info("No need to set canonicalAccessionString to {} because it is already {}",
				value1, canonicalAccessionString);

		
		String[] changeDirectoryCommand = { "cd", commonTempPathLocal };
		ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
		try {
			processBuilder.start();
		} catch (IOException e) {
			// System.out.println("Error-BLASTp: Could not change directories to " + commonTempPathLocal + e);
			logger.error("Error-BLASTp: Could not change directories to {} {}", commonTempPathLocal, e);
		}
		
		if (!createFasta(canonicalAccessionString)) {
			// System.out.println("BLASTp failed because it could not create a Fasta File for: " + canonicalAccessionString);
			logger.error("BLASTp failed because it could not create a Fasta File for: {}", 
					canonicalAccessionString);
			// There were no hits from BLASTp, so manage response gracefully
			blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
			blastTools.updateBlastpStatus("no hits found", accessionRunId);
			blastTools2.setAccessionRunCompletion(accessionRunId);

			Map<String, List> emptyResults = new HashMap<String, List>();
			List<Integer> rbhToRunIds = new ArrayList<Integer>();
			List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

			List<Integer> rpsToRunIds = new ArrayList<Integer>();
			List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
			emptyResults.put("rbhIds", rbhToRunIds);
			emptyResults.put("rpsIds", rpsToRunIds);
			emptyResults.put("rbhAccs", rbhAccessionIdsToRunIds);
			emptyResults.put("rpsAccs", rpsAccessionIdsToRunIds);
			return emptyResults;
		}
		
		hpcConnector.setSbatchCommandFullPath("/usr/local/bin/sbatch");
		
		
		if (outputFastaFilePath == null) {
			// System.out.println("BLASTp failed because to get the Fasta File path for: " + canonicalAccessionString);
			logger.error("BLASTp failed because to get the Fasta File path for: {}", canonicalAccessionString);
			return null;
		}
		
		
		LinkedBlockingQueue<SBatchObject> linkedBlockingQueuePolling = new LinkedBlockingQueue<>(); // Holds SBatchObjects for polling job status
		LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate = new LinkedBlockingQueue<>(); // Holds SBatchObjects for updating DB

		ArrayList<SBatchObject> completedJobs = new ArrayList<SBatchObject>(); // Holds all of the jobs that completed with no errors.

		Timer timer = new Timer();

		// ----Create threads needed for polling and updating the database---------
//		Polling poll = new Polling(linkedBlockingQueuePolling, linkedBlockingQueueUpdate, hpcConnector, blastTools,
//				timer, null, null, this);	
		Polling poll = new Polling(linkedBlockingQueuePolling, linkedBlockingQueueUpdate, hpcConnector, blastTools,
						timer, null, null, null, null);
		Thread pollingThread = new Thread(poll);
		pollingThread.start();
//		Updating update = new Updating(linkedBlockingQueueUpdate, jdbcTemplate, blastTools, accessionRunId,
//				completedJobs, false, false, true, this);
		Updating update = new Updating(linkedBlockingQueueUpdate, jdbcTemplate, blastTools, accessionRunId,
				completedJobs, false, false, true, false, null, hpcConnector);
		Thread updatingThread = new Thread(update);
		updatingThread.start();
		// ------------------------------------------------------------------------

		tempFilesForJob.add(outputFastaFilePath);

		String scriptName = "runBLASTp_" + accessionRunId + ".sh"; // Ex: runBLASTp_5766.sh
		scriptCommandPath = commonTempPathLocal + "/" + scriptName;

		tempFilesForJob.add(scriptCommandPath);

		StringBuilder shellCommand = new StringBuilder();
		shellCommand.append(command);
		shellCommand.append(" -query " + outputFastaFilePath);
		shellCommand.append(" -db " + pathToDB);
		shellCommand.append(" -word_size " + wordSize);
		shellCommand.append(" -num_threads " + numThreads);
		shellCommand.append(" -evalue " + eVal);
		shellCommand.append(" -max_target_seqs " + maxTargetSeqs);
		shellCommand.append(" -outfmt " + outfmt);
		shellCommand.append(" | ");
		shellCommand.append(javaWithPath + " ");
		shellCommand.append(javaStartingHeapSize + " ");
		shellCommand.append(javaMaximumHeapSize + " ");
		shellCommand.append(parserCommand + " ");
		shellCommand.append(queryAccessionString);

		try {
			PrintWriter writer = new PrintWriter(scriptCommandPath);
			writer.println("#!/bin/csh");
			writer.println("#SBATCH --ntasks=1");
			writer.println("#SBATCH --time=00:" + maxTimeAllotted + ":00");
			writer.println("#SBATCH --account=seqapass");
			writer.println("#SBATCH --ntasks-per-node=" + numThreads);
			writer.println("#SBATCH --chdir=" + commonTempPathLocal);
			writer.println("#SBATCH --error=" + commonTempPathLocal + "/slurm-%j.err");
			writer.println();
			writer.println("cd " + commonTempPathLocal);
			writer.println(shellCommand.toString()); // Write the command to the shell script file
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			// System.out.println("BLASTp Error: System could not write shell script" + e);
			logger.error("BLASTp Error: System could not write shell script {}", e);
			return null;
		}


		int jobIdReturned = hpcConnector.sbatch(scriptName); // Submit sbatch job
		// System.out.println("BLASTp: Job Id = " + jobIdReturned);
		logger.info("BLASTp: Job Id = {}", jobIdReturned);

		if (jobIdReturned == -1) {
			return null;
		}
		timer.start();

		String pathToResultsOutput = commonTempPathLocal + "/" + "slurm-" + jobIdReturned + ".out";
		String pathToJobErrorFile = commonTempPathLocal + "/" + "slurm-" + jobIdReturned + ".err";

		tempFilesForJob.add(pathToResultsOutput);
		tempFilesForJob.add(pathToJobErrorFile);

		nameList = blastTools.getBLASTpXMLTypeNames();
		nameList.add(0,"hit_accession_id" );
		nameList.add(1,"hit_taxid" );
		nameList.add(2,"hit_canonical_id" );
		
		blastTools2.updateAccessionRunStatus(accessionRunId, "started");
		blastTools.updateBlastpStatus("started", accessionRunId);
		blastTools2.setAccessionRunBlastpWordSize(3, accessionRunId);
		blastTools2.setAccessionRunBlastpThreadCount(16, accessionRunId);

	
		tempFilesMap.put(jobIdReturned, tempFilesForJob);

		SBatchObject sbatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput, 1, accessionRunId,
				pathToJobErrorFile, String.valueOf(maxTimeAllotted),accessionRun.getQueryAccessionString());
		sbatchObject.setNumberOfProteinsInFasta(1);

		linkedBlockingQueuePolling.add(sbatchObject);

		SBatchObject stopJob = new SBatchObject(-1, "stop", -1, -1, null, String.valueOf(0), accessionRun.getQueryAccessionString());
		linkedBlockingQueuePolling.add(stopJob);

		while (go) { // once this exits, polling and updating are complete
			if (!pollingThread.isAlive() && polling) {
				// System.out.println("BLASTp: Polling has completed for run " + accessionRunId);
				logger.debug("BLASTp: Polling has completed for run {}", accessionRunId);
				polling = false;
			}
			if (!updatingThread.isAlive() && updating) {
				updating = false;
			}
			if ((polling == false) && (updating == false)) {
				// System.out.println("BLASTp: Polling and Updating have completed for run " + accessionRunId);
				logger.debug("BLASTp: Polling and Updating have completed for run {}" + accessionRunId);
				go = false;
			}
		}

		timer.stop();

		if (completedJobs.size() != 1) {
			// System.out.println("Error-BLASTp: The sbatch BLASTp job for run " + accessionRunId + "failed");
			logger.error("Error-BLASTp: The sbatch BLASTp job for run {} failed",
					accessionRunId);
			blastTools2.updateAccessionRunStatus(accessionRunId, "failed");
			return null;
		}
		
		String updateAccessionHitTable1 = 
				"UPDATE accession_hit ah, accession_run ar, accession_hit bh, accessionRun br "
				+ "SET ah.near_class_taxid = bh.near_class_taxid "
				+ "WHERE ah.near_class_taxid = 1 "
				+ "AND bh.near_class_taxid > 1 "
				+ "AND ah.hit_accession_id = bh.hit_accession_id "
				+ "AND ah.accession_run_id = ar.id "
				+ "AND bh.accession_run_id = br.id "
				+ "AND ar.ncbi_version_id = br.ncbi_version_id;";
		int query1 = jdbcTemplate.update(updateAccessionHitTable1);
		// System.out.println("BLASTp: Updating the db with query 1 completed with a job status of= " + query1);
		logger.debug("BLASTp: Updating the db with query 1 completed with a job status of = {}", query1);
		
		String updateAccessionHitTable2 = 
				"UPDATE accession_hit "
				+ "SET near_class_taxid = TAXID_at_rank_for_taxid(hit_taxid) "
				+ "WHERE near_class_taxid = 1;";
		int query2 = jdbcTemplate.update(updateAccessionHitTable2);
		// System.out.println("BLASTp: Updating the db with query 2 completed with a job status of= " + query2);
		logger.debug("BLASTp: Updating the db with query 2 completed with a job status of = {}", query2);

		query = "SELECT COUNT(*) FROM accession_hit WHERE accession_run_id = ?";
		int hitCount = jdbcTemplate.queryForObject(query, int.class, accessionRunId);
		// System.out.println("BLASTp-Debug 2 with: " + hitCount);
		logger.debug("BLASTp-Debug 2 with: {}", hitCount);

		if (hitCount == 0) {
			// System.out.println("No hits!");
			logger.warn("No hits!");
			// There were no hits from BLASTp, so manage response gracefully
			blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
			blastTools.updateBlastpStatus("no hits found", accessionRunId);
			blastTools2.setAccessionRunCompletion(accessionRunId);

			Map<String, List> emptyResults = new HashMap<String, List>();
			List<Integer> rbhToRunIds = new ArrayList<Integer>();
			List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

			List<Integer> rpsToRunIds = new ArrayList<Integer>();
			List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
			emptyResults.put("rbhIds", rbhToRunIds);
			emptyResults.put("rpsIds", rpsToRunIds);
			emptyResults.put("rbhAccs", rbhAccessionIdsToRunIds);
			emptyResults.put("rpsAccs", rpsAccessionIdsToRunIds);
			return emptyResults;
		}

		if (hitCount < 3) {
			// There was only one hit, so there is no possible cutoff plot and it makes no
			// sense to continue
			// System.out.println("Hit count = " + hitCount);
			logger.debug("Hit count = {}", hitCount);

			blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
			// System.out.println("Debug a");
			logger.debug("Debug a");

			blastTools.updateBlastpStatus("too few hits", accessionRunId);
			// System.out.println("Debug b");
			logger.debug("Debug b");

			blastTools2.setAccessionRunCompletion(accessionRunId);
			// System.out.println("Debug c");
			logger.debug("Debug c");

			Map<String, List> emptyResults = new HashMap<String, List>();
			List<Integer> rbhToRunIds = new ArrayList<Integer>();
			List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

			List<Integer> rpsToRunIds = new ArrayList<Integer>();
			List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
			emptyResults.put("rbhIds", rbhToRunIds);
			emptyResults.put("rpsIds", rpsToRunIds);
			emptyResults.put("rbhAccs", rbhAccessionIdsToRunIds);
			emptyResults.put("rpsAccs", rpsAccessionIdsToRunIds);
			return emptyResults;
		}
		blastTools.markBLASTpHitResultsFull4a(accessionRunId);
		Map<String, List> results = blastTools.getRBHnRPSinfoFull(accessionRunId);
		blastTools.updateBlastpStatus("complete", accessionRunId);
		deleteTempFiles(completedJobs);
		return results;

	}

	public SBatchObject resubmitJob(SBatchObject expiredSBatchObject) {
		String[] changeDirectoryCommand = { "cd", commonTempPathLocal };
		ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
		try {
			processBuilder.start();
		} catch (IOException e) {
			// System.out.println("Error-BLASTp: Could not change directories to " + commonTempPathLocal + e);
			logger.error("Error-BLASTp: Could not change directories to {} {}", commonTempPathLocal, e);
		}

		// System.out.println("BLASTp: Resubmitting SBatch job " + expiredSBatchObject.getJobId());
		logger.warn("BLASTp: Resubmitting SBatch job {}", expiredSBatchObject.getJobId());
		String timeAllotted = expiredSBatchObject.getTimeAllotted();
		int timeAllottedDoubled = (Integer.parseInt(timeAllotted) * 2); // Double the time of the old sbatch object
		ArrayList<String> expiredJobFilesList = tempFilesMap.get(expiredSBatchObject.getJobId()); // get the files that
																									// the expired
																									// sbatch object
																									// used
		String fastaFSAPath = expiredJobFilesList.get(0); // record the old fsa path
		String oldScriptPath = expiredJobFilesList.get(1); // record the old script path

		String scriptName = "runBLASTp_" + accessionRunId + "_try_" + timeAllottedDoubled + ".sh"; // Ex:
																									// runBLASTp_5766_try_20.sh
		String scriptPath = commonTempPathLocal + "/" + scriptName;

		File expiredShellScriptFile = new File(oldScriptPath);
		expiredShellScriptFile.delete(); // delete old script

		StringBuilder shellCommand = new StringBuilder();
		shellCommand.append(command);
		shellCommand.append(" -query " + fastaFSAPath);
		shellCommand.append(" -db " + pathToDB);
		shellCommand.append(" -word_size " + wordSize);
		shellCommand.append(" -num_threads " + numThreads);
		shellCommand.append(" -evalue " + eVal);
		shellCommand.append(" -max_target_seqs " + maxTargetSeqs);
		shellCommand.append(" -outfmt " + outfmt);
		shellCommand.append(" | ");
		shellCommand.append(javaWithPath + " ");
		shellCommand.append(javaStartingHeapSize + " ");
		shellCommand.append(javaMaximumHeapSize + " ");
		shellCommand.append(parserCommand + " ");
		shellCommand.append(queryAccessionString);

		try {
			PrintWriter writer = new PrintWriter(scriptPath);
			writer.println("#!/bin/csh");
			writer.println("#SBATCH --ntasks=1");
			writer.println("#SBATCH --time=00:" + timeAllottedDoubled + ":00");
			writer.println("#SBATCH --account=seqapass");
			writer.println("#SBATCH --ntasks-per-node=" + numThreads);
			writer.println("#SBATCH --chdir=" + commonTempPathLocal);
			writer.println("#SBATCH --error=" + commonTempPathLocal + "/slurm-%j.err");
			writer.println();
			writer.println("cd " + commonTempPathLocal);
			writer.println(shellCommand.toString()); // Write the command to the shell script file
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			// System.out.println("BLASTp Error: System could not write shell script" + e);
			logger.error("BLASTp Error: System could not write shell script {}", e);
		}

		int jobIdReturned = hpcConnector.sbatch(scriptName);
		if (jobIdReturned == -1) {
			// System.out.println("Error-BLASTp: Could not resubmit sbatch job");
			logger.error("Error-BLASTp: Could not resubmit sbatch job");
		}

		ArrayList<String> resubmitJobTempFilesList = new ArrayList<String>();
		String pathToResultsOutput = commonTempPathLocal + "/" + "slurm-" + jobIdReturned + ".out";
		String pathToJobErrorFile = commonTempPathLocal + "/" + "slurm-" + jobIdReturned + ".err";

		resubmitJobTempFilesList.add(fastaFSAPath);
		resubmitJobTempFilesList.add(scriptPath);
		resubmitJobTempFilesList.add(pathToResultsOutput);
		resubmitJobTempFilesList.add(pathToJobErrorFile);

		tempFilesMap.put(jobIdReturned, resubmitJobTempFilesList);

		SBatchObject resubmitSBatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput, 1, accessionRunId,
				pathToJobErrorFile, String.valueOf(timeAllottedDoubled) , accessionRun.getQueryAccessionString());

		//System.out.println("BLASTp: After resubmitting, old job " + expiredSBatchObject.getJobId() + "is now "
		//		+ resubmitSBatchObject.getJobId());
		logger.debug("BLASTp: After resubmitting, old job {} is now {}",
				expiredSBatchObject.getJobId(), resubmitSBatchObject.getJobId());

		// Delete files from old sbatch object
		File expiredErrFile = new File(expiredSBatchObject.getErrorFilePath());
		expiredErrFile.delete();
		File expiredOutFile = new File(expiredSBatchObject.getOutputFilePath());
		expiredOutFile.delete();

		tempFilesMap.remove(expiredSBatchObject.getJobId()); // remove mapping of old sbatch object

		return resubmitSBatchObject;

	}
	
	private void deleteTempFiles(ArrayList<SBatchObject> completedJobs) {

		for (SBatchObject sBatchObject : completedJobs) {
			ArrayList<String> tempFilesForJob = tempFilesMap.get(sBatchObject.getJobId());
			for (int i = 0; i < tempFilesForJob.size(); i++) {
				File file = new File(tempFilesForJob.get(i));
				file.delete();
			}
		}

	}
	
	
	public boolean createFasta(String accession) {
		NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		if (accession == null) {
			return false;
		}

		String outputFileRoot = "run_" + accessionRunId + "_BLASTp_" + queryAccessionString + "_fasta";
		outputFastaFilePath = commonTempPathLocal + "/" + outputFileRoot + ".fsa";

		// System.out.println("BLASTp: About to try to create a fasta for " + queryAccessionString);
		logger.info("BLASTp: About to try to create a fasta for {}", queryAccessionString);

		File fastaFile = new File(outputFastaFilePath);
		if (fastaFile.exists()) {
			fastaFile.delete();
		}

		ProcessProvider createFasta = new ProcessProvider();
		createFasta.setUserID(0);
		createFasta.setJobID(0);
		createFasta.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.saveFastaProcess));
		createFasta.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
		List<String> argVals = new ArrayList<String>();
		argVals.add(ncbiProvider.getPathNrData());
		argVals.add("prot");
		argVals.add(accession);
		argVals.add("%f");
		argVals.add(outputFastaFilePath);
		createFasta.setArgVals(argVals);
		try {
			String[] fastaRunResults = createFasta.run();
			if (fastaRunResults[1].length() > 3) {
				// System.out.println("Got an error creating a fasta: " + fastaRunResults[1]);
				logger.error("Got an error creating a fasta: {}", fastaRunResults[1]);
			}
		} catch (InterruptedException | IOException e) {
			return false;
		}

		if (fastaFile.length() > 0) {
			// System.out.println("BLASTp: Succeeded in creating a fasta for " + queryAccessionString);
			logger.debug("BLASTp: Succeeded in creating a fasta for {}", queryAccessionString);
			return true;
		}
		return false;
	}

	private boolean deleteFile(String fullPath) {
		try {
			File listFile = new File(fullPath);
			if (listFile.delete()) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}
	
	public List<String> getNameList(){
		return nameList;
	}

	public int getAccession_run_id() {
		return accessionRunId;
	}

	public void setAccession_run_id(int accessionRunId) {
		this.accessionRunId = accessionRunId;
	}

	public String getQueryAccessionString() {
		return queryAccessionString;
	}

	public void setQueryAccessionString(String queryAccessionString) {
		this.queryAccessionString = queryAccessionString;
	}

	public int getQueryAccessionTaxid() {
		return queryAccessionTaxid;
	}

	public void setQueryAccessionTaxid(int queryAccessionTaxid) {
		this.queryAccessionTaxid = queryAccessionTaxid;
	}

	// public List<String> getIdenticalsFromQuerySpecies() {
	// return identicalsFromQuerySpecies;
	// }
	//
	// public void setIdenticalsFromQuerySpecies(List<String>
	// identicalsFromQuerySpecies) {
	// this.identicalsFromQuerySpecies = identicalsFromQuerySpecies;
	// }

	public String getCanonicalAccessionString() {
		return canonicalAccessionString;
	}

	public void setCanonicalAccessionString(String canonicalAccessionString) {
		this.canonicalAccessionString = canonicalAccessionString;
	}

	public int getCanonicalAccessionTaxid() {
		return canonicalAccessionTaxid;
	}

	public void setCanonicalAccessionTaxid(int canonicalAccessionTaxid) {
		this.canonicalAccessionTaxid = canonicalAccessionTaxid;
	}

	public Set<String> getQueryTaxidAccessionsAboveIdentity() {
		return queryTaxidAccessionsAboveIdentity;
	}

	public void setQueryTaxidAccessionsAboveIdentity(Set<String> queryTaxidAccessionsAboveIdentity) {
		this.queryTaxidAccessionsAboveIdentity = queryTaxidAccessionsAboveIdentity;
	}

	// public String getTopHitAccessionString() {
	// return topHitAccessionString;
	// }
	//
	// public void setTopHitAccessionString(String topHitAccessionString) {
	// this.topHitAccessionString = topHitAccessionString;
	// }
}
