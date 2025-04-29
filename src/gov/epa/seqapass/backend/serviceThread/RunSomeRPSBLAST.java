package gov.epa.seqapass.backend.serviceThread;

import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;
import gov.epa.seqapass.backend.hpcAccess.HPCConnector;
import gov.epa.seqapass.backend.hpcAccess.Polling;
import gov.epa.seqapass.backend.hpcAccess.SBatchObject;
import gov.epa.seqapass.backend.utility.Timer;
import gov.epa.seqapass.common.Partition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.base.Joiner;

public class RunSomeRPSBLAST {
  private JdbcTemplate jdbcTemplate;
  private NCBIKeeper ncbiKeeper;
  private int preferredNcbiProviderId = -1;
  private BLASTTools blastTools;
  private int totalNumberToRun = -1;
  private String queryAccessionString = null;
  private List<Integer> accessionHitIds = new ArrayList<Integer>();
  private List<String> accessionIdNames = new ArrayList<String>();
  private int accessionRunId = -1;
  private int userId = -1;
  private int jobFragmentNumber = -1;
  private String outputFileListPath;
  private String outputFileFastaPath;

  private String testDir = "backend_test";
  private String prodDir = "backend_prod";
  private String inputScriptsOutput = "input_scripts_output";

  private String commonDirectory;
  private HPCConnector hpcConnector;
  private String commonScriptPathLocal;

  private int totalCount = -1;
  private HashMap<Integer, ArrayList<String>> tempFilesMap = new HashMap<>();

  // --------------INFO FOR WRITING SHELL SCRIPT---------------------------------
  private String blastCommand = "/work/SEQAPASS/sheg_mirror/ncbi-blast-2.10.0+/bin/rpsblast";
  private String pathToCddData = "/work/SEQAPASS/blast-cdd-db/Cdd";
  private int numThreads = 4;
  private int slurmMem = 28; // gb
  private double eVal = 0.01;
  private int outfmt = 5;
  private int maxTimeAllotted = 20;
  private String javaWithPath = "/usr/bin/java";
  private String javaStartingHeapSize = "-Xms200M";
//		private String javaMaximumHeapSize = "-Xmx6G";
  private String javaMaximumHeapSize = "-Xmx4G";
  private String parserCommand = "RPSXMLParser";
  private String scriptCommandPath;

  // ----------------INFO FOR POLLING/UPDATING THREADS--------------------------
  private boolean polling = true;
  private boolean updating = true;
  private boolean go = true;
  // ---------------------------------------------------------------------------

  private static Logger logger = LogManager.getLogger(RunSomeRPSBLAST.class);

  public RunSomeRPSBLAST(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, AccessionRun accessionRun) {
    this.blastTools = blastTools;
    this.jdbcTemplate = template;
    this.ncbiKeeper = keeper;
//		this.accessionRun = accessionRun;
    this.userId = accessionRun.getUserId();
  }

  public boolean kill() {
    return false;
  }

  public int runAll() {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();

    // First : establish locations / names for version and test vs. prod, etc.
    if (blastTools.isProd()) {
      commonDirectory = prodDir + "/" + inputScriptsOutput;
    } else {
      commonDirectory = testDir + "/" + inputScriptsOutput;
    }
    hpcConnector = new HPCConnector("/bin/sudo", "seqapass", commonDirectory);
    commonScriptPathLocal = hpcConnector.getCommonRootLocal() + "/" + commonDirectory;

    blastCommand = hpcConnector.getCommonRootLocal() + "/ncbi/ncbi-blast-" + ncbiProvider.getBlastExecVersion()
        + "+/bin/rpsblast";
    pathToCddData = hpcConnector.getCommonRootLocal() + "/ncbi/blast-cdd-db_version_" + ncbiProvider.getUpdateVersion()
        + "/Cdd";

    if (accessionRunId < 0 || (accessionHitIds.size() != accessionIdNames.size())) {
      return -1;
    }
    if (accessionHitIds.size() == 0) {
      return 0;
    }

    int batchSize = -1; // Number of hit accessions per fasta
    if (accessionIdNames.size() <= 10000) {
      batchSize = 100;
    } else if (accessionIdNames.size() > 10000) {
      batchSize = 300;
    }

    totalCount = accessionIdNames.size(); // total number of hit accessions

    hpcConnector.setSbatchCommandFullPath("/usr/local/bin/sbatch");

    String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
    ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
    try {
      Process process = processBuilder.start();
    } catch (IOException e) {
      logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Could not change directories to "
          + commonScriptPathLocal + e);
      // System.out.println("Error-RPS: Could not change directories to " + commonScriptPathLocal + e);
    }

    LinkedBlockingQueue<SBatchObject> linkedBlockingQueuePolling = new LinkedBlockingQueue<>(); // Holds
    // SBatchObjects for
    // polling job
    // status
    LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate = new LinkedBlockingQueue<>(); // Holds
    // SBatchObjects for
    // updating DB

    ArrayList<SBatchObject> completedJobs = new ArrayList<SBatchObject>(); // Holds all of the jobs that completed
    // with no errors.

    Timer timer = new Timer();
    // ----Create threads needed for polling and updating the database---------
    Polling poll = new Polling(linkedBlockingQueuePolling, linkedBlockingQueueUpdate, hpcConnector, blastTools, timer,
        null, this, null, null);
    Thread pollingThread = new Thread(poll);
    pollingThread.start();
    Updating update = new Updating(linkedBlockingQueueUpdate, jdbcTemplate, blastTools, accessionRunId, completedJobs,
        false, true, false, false, ncbiProvider.getId(), hpcConnector);
    Thread updatingThread = new Thread(update);
    updatingThread.start();
//		update.setTotalCount(totalCount);
//		update.setRemainingCount(totalCount);

    Partition<String> partition = Partition.ofSize(accessionIdNames, batchSize);
    int numberOfJobs = partition.size();
    Partition<Integer> partition2 = Partition.ofSize(accessionHitIds, batchSize);

    update.setTotalCount(numberOfJobs);
    update.setRemainingCount(numberOfJobs);

    if (partition.size() > 0) {
      for (int i = 0; i < partition.size(); i++) {
        ArrayList<String> tempFilesForJob = new ArrayList<String>();
        setJobFragmentNumber(i);
        ArrayList<String> batchAccessionIds = (ArrayList<String>) partition.get(i);
        if (!createTempFasta(batchAccessionIds)) {
          logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not create the fasta file");
          // System.out.println("Error-RPS: Could not create the fasta file");
          return -1;
        }

        List<Integer> batchHitIds = partition2.get(i);

        tempFilesForJob.add(outputFileFastaPath);
        tempFilesForJob.add(outputFileListPath);

        String scriptName = "runRPSBlast_" + accessionRunId + "_" + jobFragmentNumber + ".sh"; // Ex:runRPSBlast_5766_0.sh
        scriptCommandPath = commonScriptPathLocal + "/" + scriptName;

        tempFilesForJob.add(scriptCommandPath);

        StringBuilder shellCommand = new StringBuilder();
        shellCommand.append(blastCommand);
        shellCommand.append(" -query " + outputFileFastaPath);
        shellCommand.append(" -db " + pathToCddData);
        shellCommand.append(" -evalue " + eVal);
        shellCommand.append(" -outfmt " + outfmt);
        shellCommand.append(" | ");
        shellCommand.append(javaWithPath + " ");
        shellCommand.append(javaStartingHeapSize + " ");
        shellCommand.append(javaMaximumHeapSize + " ");
        shellCommand.append(parserCommand + " ");
        shellCommand.append(preferredNcbiProviderId);

        try {
          PrintWriter writer = new PrintWriter(scriptCommandPath);
          writer.println("#!/bin/csh");
          writer.println("#SBATCH --ntasks=1");
          writer.println("#SBATCH --time=00:" + maxTimeAllotted + ":00");
          writer.println("#SBATCH --account=seqapass");
          writer.println("#SBATCH --ntasks-per-node=" + numThreads);
          writer.println("#SBATCH --mem=" + slurmMem + "gb");
          writer.println("#SBATCH --chdir=" + commonScriptPathLocal);
          writer.println("#SBATCH --error=" + commonScriptPathLocal + "/slurm-%j.err");
          writer.println();
          writer.println("cd " + commonScriptPathLocal);
          writer.println(shellCommand.toString()); // Write the command to the shell script file
          writer.flush();
          writer.close();
        } catch (FileNotFoundException e) {
          logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber + ": System could not write shell script" + e);
          // System.out.println("Error-RPS: System could not write shell script" + e);
        }

        int jobIdReturned = hpcConnector.sbatch(scriptName); // Submit sbatch job
        logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": SBATCH job id = " + jobIdReturned);
        // System.out.println("RPS: Job Id = " + jobIdReturned);

        if (jobIdReturned == -1) {
          return -1;
        }

        if (i == 0) {
          timer.start();
        }

        String pathToResultsOutput = commonScriptPathLocal + "/" + "slurm-" + jobIdReturned + ".out";
        String pathToJobErrorFile = commonScriptPathLocal + "/" + "slurm-" + jobIdReturned + ".err";

        tempFilesForJob.add(pathToResultsOutput);
        tempFilesForJob.add(pathToJobErrorFile);

        tempFilesMap.put(jobIdReturned, tempFilesForJob);

        blastTools.setRpsCurrentAccessionIds("started", batchHitIds, accessionRunId);
        SBatchObject sbatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput, jobFragmentNumber,
            accessionRunId, pathToJobErrorFile, String.valueOf(maxTimeAllotted), queryAccessionString);

        sbatchObject.setNumberOfProteinsInFasta(batchAccessionIds.size());

        linkedBlockingQueuePolling.add(sbatchObject); // Add each sbatch job to the polling queue

      }

      SBatchObject stopJob = new SBatchObject(-1, "stop", -1, -1, null, String.valueOf(0), queryAccessionString);
      linkedBlockingQueuePolling.add(stopJob);

      while (go) { // once this exits, polling and updating are complete
        if (!pollingThread.isAlive() && polling) {
          logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Polling has completed for run");
          // System.out.println("RPS: Polling has completed for run " + accessionRunId);
          polling = false;
        }
        if (!updatingThread.isAlive() && updating) {
          updating = false;
        }
        if ((polling == false) && (updating == false)) {
          logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Polling and Updating have completed for run");
          // System.out.println("RPS: Polling and Updating have completed for run " + accessionRunId);
          go = false;
        }
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }

      timer.stop();

//			if(completedJobs.size() == numberOfJobs) {
      if (update.getRemainingCount() == 0) {
        logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": ALL SBATCH RPS JOBS FOR ORIGINAL PROTEIN "
            + queryAccessionString + " HAVE BEEN COMPLETED");
        // System.out.println("ALL SBATCH RPS JOBS FOR ORIGINAL PROTEIN " + queryAccessionString + " HAVE BEEN
        // COMPLETED");
        deleteTempFiles(completedJobs);
        blastTools.setRpsCurrentAccessionIds("finished", accessionHitIds, accessionRunId);
        blastTools.updateRpsCDDCountsWTopHit(accessionRunId);
        blastTools.beginAnalysis(false, true, accessionRunId, userId);
        return 0;
      } else {
        logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber + ": One or more of the sbatch jobs for run failed");
        // System.out.println("Error-RPS: One or more of the sbatch jobs for run " + accessionRunId + " failed");
        return -1;
      }

    } else {
      logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber
          + ": Could not partition run. Partition size should be greater than 0, but is equal to " + partition.size());
      // System.out.println("Error-RPS: Could not partition run " + accessionRunId
      // + ": partition size should be greater than 0, but is equal to " + partition.size());
      return -1;
    }

  }

  public SBatchObject resubmitJob(SBatchObject expiredSBatchObject) {
    String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
    ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
    try {
      Process process = processBuilder.start();
    } catch (IOException e) {
      logger.warn("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Could not change directories to "
          + commonScriptPathLocal + "\nException:" + e);
      // System.out.println("Error-RPS: Could not change directories to " + commonScriptPathLocal + e);
    }

    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Resubmitting SBatch job "
        + expiredSBatchObject.getJobId());
    // System.out.println("RPS: Resubmitting SBatch job " + expiredSBatchObject.getJobId());

    String timeAllotted = expiredSBatchObject.getTimeAllotted();
    int timeAllottedDoubled = (Integer.parseInt(timeAllotted) * 2); // Double the time of the old sbatch object

    ArrayList<String> expiredJobFilesList = tempFilesMap.get(expiredSBatchObject.getJobId()); // get the files that
    // the expired
    // sbatch object
    // used
    String fastaFSAPath = expiredJobFilesList.get(0); // record the old fsa path
    String fastaTXTPath = expiredJobFilesList.get(1); // record the old txt path
    String oldScriptPath = expiredJobFilesList.get(2); // record the old script path

    String scriptName = "runRPSBlast_" + accessionRunId + "_" + expiredSBatchObject.getJobFragmentNumber() + "_try_"
        + timeAllottedDoubled + ".sh";
    String scriptPath = commonScriptPathLocal + "/" + scriptName;

    File expiredShellScriptFile = new File(oldScriptPath);
    expiredShellScriptFile.delete();

    StringBuilder shellCommand = new StringBuilder();
    shellCommand.append(blastCommand);
    shellCommand.append(" -query " + fastaFSAPath);
    shellCommand.append(" -db " + pathToCddData);
    shellCommand.append(" -evalue " + eVal);
    shellCommand.append(" -outfmt " + outfmt);
    shellCommand.append(" | ");
    shellCommand.append(javaWithPath + " ");
    shellCommand.append(javaStartingHeapSize + " ");
    shellCommand.append(javaMaximumHeapSize + " ");
    shellCommand.append(parserCommand + " ");
    shellCommand.append(preferredNcbiProviderId);

    try {
      // PrintWriter writer = new PrintWriter(scriptCommandPath);
      PrintWriter writer = new PrintWriter(scriptPath);
      writer.println("#!/bin/csh");
      writer.println("#SBATCH --ntasks=1");
      writer.println("#SBATCH --time=00:" + timeAllottedDoubled + ":00");
      writer.println("#SBATCH --account=seqapass");
      writer.println("#SBATCH --ntasks-per-node=" + numThreads);
      writer.println("#SBATCH --mem=" + slurmMem + "gb");
      writer.println("#SBATCH --chdir=" + commonScriptPathLocal);
      writer.println("#SBATCH --error=" + commonScriptPathLocal + "/slurm-%j.err");
      writer.println();
      writer.println("cd " + commonScriptPathLocal);
      writer.println(shellCommand.toString()); // Write the command to the shell script file
      writer.flush();
      writer.close();
    } catch (FileNotFoundException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber
          + ": System could not write shell script.\nException: " + e);
      // System.out.println("Error-RPS: System could not write shell script" + e);
    }

    int jobIdReturned = hpcConnector.sbatch(scriptName);
    if (jobIdReturned == -1) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Could not resubmit job");
      // System.out.println("Error-RPS: Could not resubmit job");
    }

    ArrayList<String> resubmitJobTempFilesList = new ArrayList<String>();
    String pathToResultsOutput = commonScriptPathLocal + "/" + "slurm-" + jobIdReturned + ".out";
    String pathToJobErrorFile = commonScriptPathLocal + "/" + "slurm-" + jobIdReturned + ".err";

    resubmitJobTempFilesList.add(fastaFSAPath);
    resubmitJobTempFilesList.add(fastaTXTPath);
    resubmitJobTempFilesList.add(scriptPath);
    resubmitJobTempFilesList.add(pathToResultsOutput);
    resubmitJobTempFilesList.add(pathToJobErrorFile);

    tempFilesMap.put(jobIdReturned, resubmitJobTempFilesList);

    SBatchObject resubmitSBatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput,
        expiredSBatchObject.getJobFragmentNumber(), accessionRunId, pathToJobErrorFile, String.valueOf(timeAllottedDoubled),
        queryAccessionString);

    resubmitSBatchObject.setNumberOfProteinsInFasta(expiredSBatchObject.getNumberOfProteinsInFasta());
    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": After resubmitting, old job "
        + expiredSBatchObject.getJobId() + "is now " + resubmitSBatchObject.getJobId());
    // System.out.println("RPS: After resubmitting, old job " + expiredSBatchObject.getJobId() + "is now "
    // + resubmitSBatchObject.getJobId());
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

//	private String parseAndSaveRPSChunk(String rpsChunk) {
//		List<ArrayList<String>> allResults = new ArrayList<ArrayList<String>>();
//
//		String patternIterationQueryDefAndHitContentString = "<Iteration_query-def>(.*?)</Iteration_query-def>(.*)";
//		Pattern patternIterationQueryDefAndHitContent = Pattern.compile(patternIterationQueryDefAndHitContentString);
//		// For parsing pdb entries like: pdb|1JLY|A Chain A, Cryst...
//		String patternPDBAccessionIdString = "^pdb\\|([A-Z0-9]{4})\\|([A-Z0-9]+)";
//		Pattern patternPDBAccessionId = Pattern.compile(patternPDBAccessionIdString);
//
//		// NEW (2016-12) SETUP: accession_id is at start followed by space if not a pdb
//		String patternNonAccessionIdString = "^(\\S+)\\s";
//		Pattern patternNonAccessionId = Pattern.compile(patternNonAccessionIdString);
//
//		String iterationPatternString = "<Iteration>(.*?)</Iteration>";
//		Pattern iterationPattern = Pattern.compile(iterationPatternString);
//
//		Matcher iterationMatcher = iterationPattern.matcher(rpsChunk);
//
//		int endOfLastHit = -1;
//
//		while (iterationMatcher.find()) {
//			String oneIteartion = iterationMatcher.group(1);
//			endOfLastHit = iterationMatcher.end(1);
//
//			Matcher iterQueryDef = patternIterationQueryDefAndHitContent.matcher(oneIteartion);
//			String rpsQueryAccession = null;
//			if (iterQueryDef.find()) {
//				String queryDef = iterQueryDef.group(1);
//				String hitContent = iterQueryDef.group(2);
//				Matcher accPDBMatcher = patternPDBAccessionId.matcher(queryDef);
//				if (accPDBMatcher.find()) {
//					rpsQueryAccession = accPDBMatcher.group(1) + "_" + accPDBMatcher.group(2);
//				} else {
//					Matcher accMatcher = patternNonAccessionId.matcher(queryDef);
//					if (accMatcher.find()) {
//						String rpsQueryAccessionCandidate = accMatcher.group(1);
//						if (rpsQueryAccessionCandidate.matches("p..\\|\\|.*")) { // Handles the pir|| and prf|| cases
//							rpsQueryAccession = rpsQueryAccessionCandidate.substring(5);
//						} else {
//							rpsQueryAccession = rpsQueryAccessionCandidate;
//						}
//					}
//				}
//
//				if (rpsQueryAccession == null) {
//					System.out.println("queryDef didn't match accession form: " + queryDef);
//					continue;
//				}
//				if (rpsQueryAccession.matches(".*\\|.*")) {
//					System.out.println(
//							"Incomplete handling of cases in parseAndSaveRPSChunk method.  There is still a pipe in the rpsQueryAccession string: "
//									+ rpsQueryAccession);
//				}
//
//				String hitPatternString = "<Hit>(.*?)</Hit>";
//				Pattern hitPattern = Pattern.compile(hitPatternString);
//				Matcher hitMatcher = hitPattern.matcher(hitContent);
//				while (hitMatcher.find()) {
//					String oneHit = hitMatcher.group(1);
//					List<ArrayList<String>> thisList = processOneRpsBlastHit(rpsQueryAccession, oneHit);
//					if (thisList != null && thisList.size() > 0) {
//						allResults.addAll(thisList);
//					}
//				}
//			}
//		}
//		if (endOfLastHit == -1) { // WE DIDN'T GET ANY PIECES, SO THROW IT BACK AND TRY AGAIN
//			return rpsChunk;
//		}
//		List<String> nameList = blastTools.getBLASTpXMLTypeNames();
//		nameList.add(0, "ncbi_version_id");
//		nameList.add(1, "accession_id");
//
//		// List<String> nameList = new ArrayList<String>();
//		// nameList.add("ncbi_version_id");
//		// nameList.add("query_accession_id");
//		// nameList.add("subject_taxid");
//		// nameList.add("subject_accession_id");
//		// nameList.add("Hsp_num");
//		// nameList.add("percent_identity");
//		// nameList.add("evalue");
//		// nameList.add("bitscore");
//		// blastTools.saveRbhHitResultsBatch(nameList, allResults);
//		blastTools.saveRpsHitResultsBatchInOneBatch(nameList, allResults);
//		return rpsChunk.substring(endOfLastHit); // IT CAN'T HURT TO INCLUDE JUST A LITTLE EXTRA;
//	}

//	/**
//	 * This method creates a List of LinkedLists for one "Hit" entry from the XML
//	 * output from BLASTp. It requires a zero or greater accession_run_id which is
//	 * prepended to the start of all results lists.
//	 * 
//	 * @param accession_run_id
//	 * @param oneHit
//	 *            A String containing the XML content of one "Hit" result from
//	 *            rpsBLAST
//	 * @return
//	 */
//	public List<ArrayList<String>> processOneRpsBlastHit(String rpsQueryAccession, String oneHit) {
//		if (oneHit == null) {
//			return null;
//		}
//		List<ArrayList<String>> results = new ArrayList<ArrayList<String>>();
//
//		StringBuilder pattern1Builder = new StringBuilder();
//		StringBuilder pattern2Builder = new StringBuilder();
//
//		for (String name : blastTools.getBLASTpXMLTypeNames()) {
//			if (name.startsWith("Hit_")) {
//				pattern1Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
//			} else if (name.startsWith("Hsp_")) {
//				pattern2Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
//			}
//		}
//		Pattern pattern1 = Pattern.compile(pattern1Builder.toString());
//		Pattern pattern2 = Pattern.compile(pattern2Builder.toString());
//
//		List<String> baseValues = new ArrayList<String>();
//		baseValues.add(preferredNcbiProviderId + "");
//		baseValues.add(rpsQueryAccession);
//
//		Matcher matcher1 = pattern1.matcher(oneHit);
//		if (matcher1.find()) {
//			for (int i = 1; i < matcher1.groupCount() + 1; i++) {
//				String value = StringEscapeUtils.unescapeXml(matcher1.group(i));
//				baseValues.add(value);
//			}
//		} else {
//			return null;
//		}
//
//		Matcher matcher2 = pattern2.matcher(oneHit);
//		while (matcher2.find()) {
//			ArrayList<String> allValues = new ArrayList<String>();
//			allValues.addAll(baseValues);
//			for (int i = 1; i < matcher2.groupCount() + 1; i++) {
//				allValues.add(StringEscapeUtils.unescapeXml(matcher2.group(i)));
//			}
//			results.add(allValues);
//		}
//		if (results.get(0).size() == baseValues.size()) {
//			return null;
//		}
//		return results;
//	}

  // /**
  // * This method creates a List of Lists for one "Hit" entry from the XML output
  // from rpsBLAST. It requires a zero or greater
  // * accession_hit_id which is prepended to the start of all results lists.
  // *
  // * @param ncbi_version_id
  // * - int value in database and available through gitID()
  // * @param queryAccessionId
  // * @param oneHit
  // * @return a List of LinkedLists of Strings - the VALUES for a MySQL INSERT
  // statement
  // */
  // private List<LinkedList<String>> processOneRpsBlastHit(String
  // queryAccessionId, String oneHit) {
  // if (preferredNcbiProviderId < 0 || queryAccessionId == null || oneHit ==
  // null) {
  // return null;
  // }
  //
  // // ALL FIELDS: ncbi_version_id, query_accession_id, subject_taxid,
  // subject_accession_id, percent_identity, evalue, bitscore
  //
  // List<LinkedList<String>> results = new LinkedList<LinkedList<String>>();
  //
  // // String pattern1String =
  // "<Hit_id>gi\\|\\d+\\|[^\\|]+\\|([^\\|]+)\\|.*?<\\/Hit_id>";
  // String pattern1String =
  // "<Hit_id>gi\\|\\d+\\|[^\\|]+\\|+([^\\|]+).*?<\\/Hit_id>.*?<Hit_accession>(.*?)<\\/Hit_accession>";
  // // String pattern1String =
  // "<Hit_id>gi\\|\\d+\\|[^\\|]+\\|([^\\|]+)\\|.*?<\\/Hit_id>.*?<Hit_accession>(.*?)<\\/Hit_accession>";
  // // ------------------------------------------------------------------^ This
  // pipe is not found in pir accessions
  //
  // Pattern pattern1 = Pattern.compile(pattern1String);
  //
  // LinkedList<String> baseValues = new LinkedList<String>();
  // baseValues.add(preferredNcbiProviderId + "");
  // baseValues.add(queryAccessionId);
  // baseValues.add(subjectTaxid + "");
  // // BASE FIELDS: ncbi_version_id, query_accession_id, subject_taxid,
  // subject_accession_id
  //
  // Matcher matcher1 = pattern1.matcher(oneHit);
  // if (matcher1.find()) {
  // String idAccession = StringEscapeUtils.unescapeXml(matcher1.group(1));
  // String hitAccession = StringEscapeUtils.unescapeXml(matcher1.group(2));
  // if (idAccession.length() > hitAccession.length()) {
  // baseValues.add(idAccession);
  // } else {
  // baseValues.add(hitAccession);
  // }
  // } else {
  // System.out.println("Parsing this rbhBLAST result from xml failed: " +
  // oneHit);
  // return null;
  // }
  //
  // String pattern2String =
  // "<Hsp_num>(\\d+)</Hsp_num>.*?<Hsp_bit-score>(.*?)<\\/Hsp_bit-score>.*?<Hsp_evalue>(.*?)<\\/Hsp_evalue>.*?<Hsp_identity>(.*?)<\\/Hsp_identity>.*?<Hsp_align-len>(.*?)<\\/Hsp_align-len>";
  // Pattern pattern2 = Pattern.compile(pattern2String);
  //
  // // ADDITIONAL FIELDS: Hsp_num, percent_identity, evalue, bitscore
  //
  // Matcher matcher2 = pattern2.matcher(oneHit);
  // while (matcher2.find()) {
  // LinkedList<String> allValues = new LinkedList<String>();
  // allValues.addAll(baseValues);
  // int hspNum = Integer.parseInt(matcher2.group(1));
  // String bitScore = StringEscapeUtils.unescapeXml(matcher2.group(2));
  // String evalue = StringEscapeUtils.unescapeXml(matcher2.group(3));
  // double identity = Double.parseDouble(matcher2.group(4));
  // double alignLen = Double.parseDouble(matcher2.group(5));
  // double percentIdentity = (100.0 * identity) / alignLen;
  // String percentIdentityString = percentIdentity + "";
  // allValues.add(hspNum + "");
  // allValues.add(percentIdentityString);
  // allValues.add(evalue);
  // allValues.add(bitScore);
  // // System.out.println("allvalues size:" + allValues.size());
  // results.add(allValues);
  // }
  // if (results.get(0).size() == baseValues.size()) {
  // System.out.println("processOneRbhBlastHit got a oneHit with no data in it!");
  // return null;
  // }
  // return results;
  // }

  private boolean createTempFasta(List<String> fastasForTemp) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();

    if (accessionRunId < 0) {
      return false;
    }
    if (fastasForTemp.isEmpty()) {
      return false;
    }
    String firstAccessionFixedName = fastasForTemp.get(0).replaceAll("\\|", "-");
    String outputFileRoot = "run" + accessionRunId + "_RPS_" + getJobFragmentNumber() + "_" + firstAccessionFixedName;

    outputFileListPath = commonScriptPathLocal + "/" + outputFileRoot + ".txt";
    outputFileFastaPath = commonScriptPathLocal + "/" + outputFileRoot + ".fsa";

    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": About to try to create a fasta for "
        + queryAccessionString + " with " + fastasForTemp.size() + " pieces");

    // System.out.println("RPS: About to try to create a fasta for " + queryAccessionString + " with "
    // + fastasForTemp.size() + " pieces");

    try {
      PrintWriter fastaList = new PrintWriter(outputFileListPath);
      fastaList.print(Joiner.on("\n").join(fastasForTemp));
      fastaList.flush();
      fastaList.close();
      logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Succeeded in creating a fasta for "
          + queryAccessionString + " with " + fastasForTemp.size() + " pieces");

      // System.out.println("RPS: Succeeded in creating a fasta for " + queryAccessionString + " with "
      // + fastasForTemp.size() + " pieces");
    } catch (Exception e) {
      return false;
    }

    File fastaFile = new File(outputFileFastaPath);
    fastaFile.delete();
    ProcessProvider createFastas = new ProcessProvider();
    createFastas.setUserID(0);
    createFastas.setJobID(0);
    createFastas.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.saveTempFastaProcess));
    createFastas.setExecPath(ncbiProvider.getBlastExecPath());
    List<String> argVals = new ArrayList<String>();
    argVals.add(ncbiProvider.getPathNrData());
    argVals.add("prot");
    argVals.add(outputFileListPath);
    argVals.add(outputFileFastaPath);
    createFastas.setArgVals(argVals);
    try {
      createFastas.run();
      // String[] results = createFastas.run();
      // System.out.println("Stdout: " + results[0]);
      // System.out.println("Stderr: " + results[1]);
    } catch (InterruptedException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }

    fastaFile = new File(outputFileFastaPath);
    if (fastaFile.length() > 0) {

      return true;
    }
    return false;
  }

  // private boolean setCurrentAccessionIds(String status) {
  // if (!status.equals("queued") && !status.equals("started") &&
  // !status.equals("finished") && !status.equals("not run")) {
  // return false;
  // }
  // if (accessionHitIds.size() < 0 || accessionRunId < 0) {
  // return false;
  // }
  // int batchSize = 1000;
  // String updateQuery = "UPDATE accession_hit SET rps_status = '" + status + "'
  // WHERE id in (";
  // for (int batch = 0; batch <= accessionHitIds.size(); batch += batchSize) {
  // // TODO - double check this
  // int end = Math.min(batch + batchSize, accessionHitIds.size());
  // String batchString = Joiner.on(",").join(accessionHitIds.subList(batch,
  // end));
  // if (!batchString.equals("")) {
  // jdbcTemplate.update(updateQuery + batchString + ");");
  // }
  // }
  // return true;
  // }

  public List<Integer> getAccession_hit_ids() {
    return accessionHitIds;
  }

  public void setAccessionHitIds(List<Integer> accession_run_ids) {
    this.accessionHitIds = accession_run_ids;
  }

  public void setAccession_hit_ids(Set<Integer> accession_run_ids) {
    for (Integer id : accession_run_ids) {
      this.accessionHitIds.add(id);
    }
  }

  public int getPreferredNcbiProviderId() {
    return preferredNcbiProviderId;
  }

  public void setPreferredNcbiProviderId(int preferredNcbiProviderId) {
    this.preferredNcbiProviderId = preferredNcbiProviderId;
  }

  public int getAccession_run_id() {
    return accessionRunId;
  }

  public void setAccession_run_id(int accession_run_id) {
    this.accessionRunId = accession_run_id;
  }

  public int getJobFragmentNumber() {
    return jobFragmentNumber;
  }

  public void setJobFragmentNumber(int jobFragmentNumber) {
    this.jobFragmentNumber = jobFragmentNumber;
  }

  public void addAccession_hit_id(int accession_hit_id) {
    this.accessionHitIds.add(accession_hit_id);
  }

  //
  // public boolean isReRun() {
  // return reRun;
  // }
  //
  // public void setReRun(boolean reRun) {
  // this.reRun = reRun;
  // }

  public int getTotalNumberToRun() {
    if (accessionRunId < 0) {
      return -1;
    }
    if (totalNumberToRun < 0) {
      String query = "select count(id) from accession_hit where accession_run_id = ? AND rps_status IS NOT NULL AND rps_status != 'not run'";
      int count = jdbcTemplate.queryForObject(query, int.class, accessionRunId);
      if (count >= 0) {
        totalNumberToRun = count;
      }
    }

    return totalNumberToRun;
  }

  public void setTotalNumberToRun(int totalNumberToRun) {
    this.totalNumberToRun = totalNumberToRun;
  }

  public List<String> getAccessionIdNames() {
    return accessionIdNames;
  }

  public void setAccessionIdNames(List<String> accessionIdNames) {
    this.accessionIdNames = accessionIdNames;
  }

  private String getQueryAccessionString() {
    if (queryAccessionString == null) {
      if (accessionRunId < 0) {
        return null;
      }
      String query = "select top_hit_accession_id from accession_run where id = ? limit 1";
      try {
        queryAccessionString = jdbcTemplate.queryForObject(query, String.class, accessionRunId);
      } catch (DataAccessException e) {
        logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ": No accession_id!\nException: " + e);

        // System.out.println("RPS: Attempting to run rpsBLAST with accession_run.id: " + accessionRunId
        // + " and it does not have an accession_id!"); return null;
      }
    }
    return queryAccessionString;
  }

  public void setQueryAccessionString(String queryAccessionString) {
    this.queryAccessionString = queryAccessionString;
  }

  // private int setRemainingQueuedHitsToRun() {
  // if (accessionRunId < 0) {
  // return -1;
  // }
  // String query = "SELECT id, hit_accession_id FROM accession_hit WHERE
  // accession_run_id = ? AND rps_status = 'queued'";
  // List<Map<String, Object>> rows = jdbcTemplate.queryForList(query,
  // accessionRunId);
  // if (rows.size() == 0) {
  // return 0;
  // }
  // for (Map<String, Object> row : rows) {
  // int id = (int) row.get("id");
  // String accession_id = (String) row.get("accession_id");
  // accessionHitIds.add(id);
  // accessionIdNames.add(accession_id);
  // }
  // return accessionHitIds.size();
  // }

  private int countAccessionHitsLeftToRun() {
    if (accessionRunId < 0) {
      return -1;
    }
    String query = "SELECT count(*) FROM accession_hit WHERE accession_run_id = ? AND rps_status IN ('queued','started')";
    return jdbcTemplate.queryForObject(query, int.class, accessionRunId);
  }

  public void addAccessionIdName(String accession) {
    accessionIdNames.add(accession);
  }

  // @Retryable(maxAttempts=50, value=RuntimeException.class, backoff=
  // @Backoff(delay = 1000, multiplier=2))
  // private int updateCounts() {
  // if (!runRpsBlastOnQueryAccessionIfNeeded()) {
  // System.out.println("For some reason rpsBLAST would not or did not run for
  // query protein from run: " + accessionRunId);
  // return -1;
  // }
  // if (getAccessionId() == null) {
  // return -1;
  // }
  // int ncbiId = ncbiKeeper.getPreferredNCBIProviderID();
  // StringBuilder b = new StringBuilder();
  // b.append("UPDATE accession_hit f, ");
  // b.append(" (SELECT count(a.id) AS `count`, ");
  // b.append(" b.id ");
  // b.append(" FROM `rps_result` a, ");
  // b.append(" `accession_hit` b, ");
  // b.append(" `rps_result` d ");
  // b.append(" WHERE d.`accession_id` = ? ");
  // b.append(" AND b.`accession_run_id` = ? ");
  // b.append(" AND a.`ncbi_version_id` = ? ");
  // b.append(" AND d.`ncbi_version_id` = ? ");
  // b.append(" AND b.`rps_status` = 'finished' ");
  // b.append(" AND a.`xml_Hit_id` = d.`xml_Hit_id` ");
  // b.append(" AND a.`accession_id` = b.`hit_accession_id` ");
  // b.append(" AND d.`xml_Hsp_hit-from` >= a.`xml_Hsp_hit-from` ");
  // b.append(" AND d.`xml_Hsp_hit-to` <= a.`xml_Hsp_hit-to` ");
  // b.append("GROUP BY b.`hit_accession_id`) e ");
  // b.append(" SET f.`cdd_count` = e.`count` ");
  // b.append("WHERE e.`id` = f.`id` ");
  // long start = new Date().getTime();
  // int count = jdbcTemplate.update(b.toString(), accessionId, accessionRunId,
  // ncbiId, ncbiId);
  // // System.out.println("Query\n\n" + b.toString() + "\n\n with params: " +
  // accessionId + " , " + accessionRunId + " , " + ncbiId
  // // + " , " + ncbiId);
  // long end = new Date().getTime();
  // long time = end - start;
  // System.out.println(count + " cdd counts updated in " + time + " milliseconds
  // for rpsBLAST with accession_run.id = "
  // + accessionRunId);
  // start = end;
  // String query = "UPDATE accession_hit SET cdd_count = 0 WHERE cdd_count IS
  // NULL AND rps_status = 'finished' AND accession_run_id = ?";
  // int zeroCount = jdbcTemplate.update(query, accessionRunId);
  // end = new Date().getTime();
  // time = end - start;
  // System.out.println(zeroCount + " cdd counts with zero set in " + time + "
  // milliseconds for rpsBLAST with accession_run.id = "
  // + accessionRunId);
  // return count;
  // }
}
