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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import com.google.common.base.Joiner;

public class RunSomeRBHBLAST {

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
  private String outputFastaFilePath;

  private String testDir = "backend_test";
  private String prodDir = "backend_prod";
  private String inputScriptsOutput = "input_scripts_output";

  private String commonDirectory;
  private HPCConnector hpcConnector;
  private String commonScriptPathLocal;

  private Set<String> queryTaxidAccessionsAboveIdentity = null;
//	private String topHitIdString = null;

  private int originalSubjectTaxId = -1;
  private String originalCanonicalAccessionId = null;
  private int totalCount = -1;
  private HashMap<Integer, ArrayList<String>> tempFilesMap = new HashMap<>();

//--------------INFO FOR WRITING SHELL SCRIPT---------------------------------
  private String blastCommand; // Defined below using ncbiProvider info
  private String pathToNrData; // Defined below using ncbiProvider info
  private String accListFullPath = null;
  private int wordSize = 3;
  private int numThreads = 4;
  private int slurmMem = 28; // gb
  private int eVal = 1000;
  private int outfmt = 5;
  private int maxTimeAllotted = 20;
  private String javaWithPath = "/usr/bin/java";
  private String javaStartingHeapSize = "-Xms200M";
//	private String javaMaximumHeapSize = "-Xmx6G";
  private String javaMaximumHeapSize = "-Xmx4G";
//	private String parserCommand = "RBHXMLParser";
  private String parserCommand = "ParserRBH";

  private String scriptCommandPath;

//----------------INFO FOR POLLING/UPDATING THREADS--------------------------
  private boolean polling = true;
  private boolean updating = true;
  private boolean go = true;
//---------------------------------------------------------------------------

  private static Logger logger = LogManager.getLogger(RunSomeRBHBLAST.class);

  public RunSomeRBHBLAST(JdbcTemplate template, NCBIKeeper keeper, BLASTTools blastTools, AccessionRun accessionRun) {
    this.jdbcTemplate = template;
    this.ncbiKeeper = keeper;
    this.blastTools = blastTools;
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
        + "+/bin/blastp";
    pathToNrData = hpcConnector.getCommonRootLocal() + "/ncbi/blast-nr-db_version_" + ncbiProvider.getUpdateVersion()
        + "/nr";

    if (accessionRunId < 0 || (accessionHitIds.size() != accessionIdNames.size())) {
      logger.fatal("AccID#" + accessionRunId + "." + jobFragmentNumber
          + ":  Can't run RBH. accessionHitIds.size() != accessionIdNames.size(): " + accessionHitIds.size() + "!="
          + accessionIdNames.size());
      return -1;
    }
    if (accessionHitIds.size() == 0) {
      return 0;
    }

    String query = "SELECT query_taxid FROM accession_run WHERE id = ? AND ncbi_version_id = ? LIMIT 1";
    try {
      originalSubjectTaxId = jdbcTemplate.queryForObject(query, int.class, accessionRunId, ncbiProvider.getId());
    } catch (DataAccessException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Couldn't get query_taxid. JDBC Error: " + e);
      return -1;
    }

    String canonicalQuery = "SELECT canonical_accession_id FROM accession_run WHERE id = ? AND ncbi_version_id = ? LIMIT 1";
    try {
      originalCanonicalAccessionId = jdbcTemplate.queryForObject(canonicalQuery, String.class, accessionRunId,
          ncbiProvider.getId());
    } catch (DataAccessException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber
          + ":  Couldn't get canonical_accession_id. JDBC Error: " + e);
      return -1;
    }

    blastTools.createAccListIfNecessary(originalSubjectTaxId); // Path not on /work/SEQAPASS
    accListFullPath = commonScriptPathLocal + "/acclists_v" + ncbiProvider.getUpdateVersion() + "/"
        + originalSubjectTaxId + ".acclist"; // use acc list from /work/SEQAPASS/sheg_mirror

    if (accListFullPath == null) {
      logger.fatal("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  accPath is null. Returning -1.");
      return -1;
    }

    int batchSize = -1; // Number of hit accessions per fasta file
    if (accessionIdNames.size() <= 10000) {
      batchSize = 100;
    } else if (accessionIdNames.size() > 10000) {
      batchSize = 300;
    }

    totalCount = accessionIdNames.size(); // total number of hit accession for the original query protein

    // HPCConnector hpcConnectorr = new HPCConnector(sudoCommand, user, commonPath)
    hpcConnector.setSbatchCommandFullPath("/usr/local/bin/sbatch");

    String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
    ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
    try {
      Process process = processBuilder.start();
    } catch (IOException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not change directories to "
          + commonScriptPathLocal + " Proccess Error: " + e);
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
        this, null, null, null);
    Thread pollingThread = new Thread(poll);
    pollingThread.start();
    Updating update = new Updating(linkedBlockingQueueUpdate, jdbcTemplate, blastTools, accessionRunId, completedJobs,
        true, false, false, false, ncbiProvider.getId(), hpcConnector);
    Thread updatingThread = new Thread(update);
    updatingThread.start();

//		update.setTotalCount(totalCount);
//		update.setRemainingCount(totalCount);

    // ------------------------------------------------------------------------

    Partition<String> partition = Partition.ofSize(accessionIdNames, batchSize); // Split accessionIdNames into
    // batches

    int numberOfJobs = partition.size();
    update.setTotalCount(numberOfJobs);
    update.setRemainingCount(numberOfJobs);

    if (partition.size() > 0) {
      for (int i = 0; i < partition.size(); i++) { // For each batch created

        ArrayList<String> tempFilesForJob = new ArrayList<String>();

        setJobFragmentNumber(i);
        ArrayList<String> batchAccessionIds = (ArrayList<String>) partition.get(i);

        if (!createTempFasta(batchAccessionIds)) {
          logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not create fasta file");
          return -1;
        }

        tempFilesForJob.add(outputFastaFilePath);
        tempFilesForJob.add(outputFileListPath);

        String scriptName = "runRBHBlast_" + accessionRunId + "_" + jobFragmentNumber + ".sh"; // Ex:runBlastp_5766_0.sh
        scriptCommandPath = commonScriptPathLocal + "/" + scriptName;

        tempFilesForJob.add(scriptCommandPath);

//				String shellCommand = command + " -query " + outputFastaFilePath + " -db " + pathToNrData + " -seqidlist "
//						+ accListFullPath + " -word_size " + wordSize + " -num_threads " + numThreads + " -evalue "
//						+ eVal + " -outfmt " + outfmt + " | " + javaWithPath + " " + javaStartingHeapSize + " "
//						+ javaMaximumHeapSize + " " + parserCommand + " " + originalCanonicalAccessionId + " "
//						+ originalSubjectTaxId + " " + accessionRunId;

        StringBuilder shellCommand = new StringBuilder();
        shellCommand.append(blastCommand);
        shellCommand.append(" -query " + outputFastaFilePath);
        shellCommand.append(" -db " + pathToNrData);
        shellCommand.append(" -seqidlist " + accListFullPath);
        shellCommand.append(" -word_size " + wordSize);
        shellCommand.append(" -num_threads " + numThreads);
        shellCommand.append(" -evalue " + eVal);
        shellCommand.append(" -outfmt " + outfmt);
        shellCommand.append(" | ");
        shellCommand.append(javaWithPath + " ");
        shellCommand.append(javaStartingHeapSize + " ");
        shellCommand.append(javaMaximumHeapSize + " ");
        shellCommand.append(parserCommand + " ");
        String originalCanonicalAccessionIdEscaped = originalCanonicalAccessionId.replaceAll("\\|","\\\\|");
        shellCommand.append(originalCanonicalAccessionIdEscaped + " ");
        //shellCommand.append(originalCanonicalAccessionId + " ");
        shellCommand.append(originalSubjectTaxId + " ");
        shellCommand.append(accessionRunId);

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
          logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  System could not write shell script");
        }

        int jobIdReturned = hpcConnector.sbatch(scriptName); // Submit sbatch job
        logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Job Id = " + jobIdReturned);

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

        SBatchObject sbatchObject = new SBatchObject(jobIdReturned, pathToResultsOutput, jobFragmentNumber,
            accessionRunId, pathToJobErrorFile, String.valueOf(maxTimeAllotted), queryAccessionString, originalCanonicalAccessionId);

        sbatchObject.setNumberOfProteinsInFasta(batchAccessionIds.size());

        linkedBlockingQueuePolling.add(sbatchObject); // Add each sbatch job to the polling queue

      } // END for each batch created

      SBatchObject stopJob = new SBatchObject(-1, "stop", -1, -1, null, String.valueOf(0), queryAccessionString, originalCanonicalAccessionId);
      linkedBlockingQueuePolling.add(stopJob);

      while (go) { // once this exits, polling and updating are complete
        if (!pollingThread.isAlive() && polling) {
          logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Polling has completed for run ");
          polling = false;
        }
        if (!updatingThread.isAlive() && updating) {
          updating = false;
        }
        if ((polling == false) && (updating == false)) {
          logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Polling and Updating have completed for run");
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

      String updateQuery = "UPDATE accession_hit SET rbh_status = ? WHERE accession_run_id =? AND rbh_status = ?";
      try {
        jdbcTemplate.update(updateQuery, new PreparedStatementSetter() {
          @Override
          public void setValues(java.sql.PreparedStatement ps) throws SQLException {
            ps.setString(1, "N");
            ps.setInt(2, accessionRunId);
            ps.setString(3, "queued");
          }
        });

      } catch (Exception e) {
        logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not update the DB with N answers");
      }

      if (update.getRemainingCount() == 0) {
        logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": ALL SBATCH JOBS HAVE BEEN COMPLETED");
        deleteTempFiles(completedJobs);
        blastTools.updateAccessionHitRbhStatus(accessionRunId);
        blastTools.beginAnalysis(true, false, accessionRunId, userId);
        return 0;
      } else {
        return -1;
      }
//			if ((update.getRemainingCount() == 0) && (completedJobs.size() == numberOfJobs)) {
//				System.out.println(
////						"ALL SBATCH JOBS FOR ORIGINAL PROTEIN " + queryAccessionIdName + " HAVE BEEN COMPLETED");
//						"ALL SBATCH RBH JOBS FOR ORIGINAL PROTEIN " + queryAccessionString + " HAVE BEEN COMPLETED");
//				deleteTempFiles(completedJobs);
//				blastTools.updateAccessionHitRbhStatus(accessionRunId);
//				blastTools.beginAnalysis(true, false, accessionRunId, userId);
//				return 0;
//			} else {
//				if (update.getRemainingCount() != 0) {
//					System.out.println(
//							"Error-RBH: The total number of database hits was not equal to the total number of hit accessions for run "
//									+ accessionRunId);
//
//				} else if (completedJobs.size() != numberOfJobs) {
//					System.out.println("Error-RBH: One or more of the sbatch RBH jobs for run " + accessionRunId + " failed");
//				}
//				return -1;
//			}

    } else {
      logger.fatal("AccID#" + accessionRunId + "." + jobFragmentNumber
          + ":  Could not partition. Partition size should be greater than 0, but is equal to " + partition.size()
          + ". Returning -1.");
      return -1;
    }

  }

  public SBatchObject resubmitJob(SBatchObject expiredSBatchObject) {
    String[] changeDirectoryCommand = { "cd", commonScriptPathLocal };
    ProcessBuilder processBuilder = new ProcessBuilder(changeDirectoryCommand);
    try {
      Process process = processBuilder.start();
    } catch (IOException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not change directories to "
          + commonScriptPathLocal + ". Proccess Error: " + e);
    }

    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Resubmitting SBatch job "
        + expiredSBatchObject.getJobId());

    String timeAllotted = expiredSBatchObject.getTimeAllotted();
    int timeAllottedDoubled = (Integer.parseInt(timeAllotted) * 2); // Double the time of the old sbatch object

    ArrayList<String> expiredJobFilesList = tempFilesMap.get(expiredSBatchObject.getJobId()); // get the files that
    // the expired
    // sbatch object
    // used
    String fastaFSAPath = expiredJobFilesList.get(0); // record the old fsa path
    String fastaTXTPath = expiredJobFilesList.get(1); // record the old txt path
    String oldScriptPath = expiredJobFilesList.get(2); // record the old script path

    String scriptName = "runRBHBlast_" + accessionRunId + "_" + expiredSBatchObject.getJobFragmentNumber() + "_try_"
        + timeAllottedDoubled + ".sh"; // Ex:runBlastp_5766_0_1.sh ;
    String scriptPath = commonScriptPathLocal + "/" + scriptName;

    File expiredShellScriptFile = new File(oldScriptPath);
    expiredShellScriptFile.delete(); // delete old script

    StringBuilder shellCommand = new StringBuilder();
    shellCommand.append(blastCommand);
    shellCommand.append(" -query " + fastaFSAPath);
    shellCommand.append(" -db " + pathToNrData);
    shellCommand.append(" -seqidlist " + accListFullPath);
    shellCommand.append(" -word_size " + wordSize);
    shellCommand.append(" -num_threads " + numThreads);
    shellCommand.append(" -evalue " + eVal);
    shellCommand.append(" -outfmt " + outfmt);
    shellCommand.append(" | ");
    shellCommand.append(javaWithPath + " ");
    shellCommand.append(javaStartingHeapSize + " ");
    shellCommand.append(javaMaximumHeapSize + " ");
    shellCommand.append(parserCommand + " "); // This should just be the command with no path!
    String originalCanonicalAccessionIdEscaped = originalCanonicalAccessionId.replaceAll("\\|","\\\\|");
    shellCommand.append(originalCanonicalAccessionIdEscaped + " ");
    //shellCommand.append(originalCanonicalAccessionId + " ");
    shellCommand.append(originalSubjectTaxId + " ");
    shellCommand.append(accessionRunId);

    try {
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
      writer.println(shellCommand.toString());
      writer.flush();
      writer.close();

    } catch (FileNotFoundException e) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  System could not write to shell script. " + e);
    }

    int jobIdReturned = hpcConnector.sbatch(scriptName);
    if (jobIdReturned == -1) {
      logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Could not resubmit SBatch job");
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
        queryAccessionString, originalCanonicalAccessionId);
    resubmitSBatchObject.setNumberOfProteinsInFasta(expiredSBatchObject.getNumberOfProteinsInFasta());
    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  After resubmitting, old job "
        + expiredSBatchObject.getJobId() + " is now " + resubmitSBatchObject.getJobId());
    // Delete files from old sbatch object
    File expiredErrFile = new File(expiredSBatchObject.getErrorFilePath());
    expiredErrFile.delete();
    File expiredOutFile = new File(expiredSBatchObject.getOutputFilePath());
    expiredOutFile.delete();

    tempFilesMap.remove(expiredSBatchObject.getJobId()); // remove mapping of old sbatch object

    return resubmitSBatchObject;
  }

  // DONT NEED THIS METHOD
  public String parseRBHHPC() {
    return null;
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

  private boolean createTempFasta(List<String> fastasForTemp) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();

    if (accessionRunId < 0) {
      return false;
    }

    if (fastasForTemp.isEmpty()) {
      return false;
    }
    String firstAccessionFixedName = fastasForTemp.get(0).replaceAll("\\|", "-");
    String outputFileRoot = "run" + accessionRunId + "_RBH_" + getJobFragmentNumber() + "_" + firstAccessionFixedName;

    outputFileListPath = commonScriptPathLocal + "/" + outputFileRoot + ".txt";
    outputFastaFilePath = commonScriptPathLocal + "/" + outputFileRoot + ".fsa";

    logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Trying to create a fasta for "
        + queryAccessionString + " with " + fastasForTemp.size() + " pieces");

    try {
      PrintWriter fastaList = new PrintWriter(outputFileListPath);
      fastaList.print(Joiner.on("\n").join(fastasForTemp));
      fastaList.flush();
      fastaList.close();
      logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ":  Succeeded in creating a fasta for "
          + queryAccessionString + " with " + fastasForTemp.size() + " pieces");
    } catch (Exception e) {
      return false;
    }

    File fastaFile = new File(outputFastaFilePath);
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
    argVals.add(outputFastaFilePath);
    createFastas.setArgVals(argVals);
    // System.out.println("Ready to run blastDbCMD with output fasta file path: " +
    // outputFileFastaPath);
    try {
      createFastas.run();
    } catch (InterruptedException | IOException e) {
      // TODO Auto-generated catch block
      // System.out.println("It failed with!: " + e);

      e.printStackTrace();
      return false;
    }
    // System.out.println("It ran!");

    fastaFile = new File(outputFastaFilePath);
    if (fastaFile.length() > 0) {
      // System.out.println("Sould be good with: " + outputFileFastaPath);

      return true;
    }
    // System.out.println("No data in file " + outputFileFastaPath);

    return false;
  }

  private int countAccessionHitsLeftToRun() {
    if (accessionRunId < 0) {
      return -1;
    }
    String query = "SELECT count(*) FROM accession_hit WHERE accession_run_id = ? AND rbh_status IN ('queued','started')";
    return jdbcTemplate.queryForObject(query, int.class, accessionRunId);
  }

  public List<Integer> getAccession_hit_ids() {
    return accessionHitIds;
  }

  public void setAccessionHitIds(List<Integer> accession_run_ids) {
    this.accessionHitIds = accession_run_ids;
  }

  public int getPreferredNcbiProviderId() {
    return preferredNcbiProviderId;
  }

  public void setPreferredNcbiProviderId(int preferredNcbiProviderId) {
    this.preferredNcbiProviderId = preferredNcbiProviderId;
  }

  public void setAccession_hit_ids(Set<Integer> accession_run_ids) {
    for (Integer id : accession_run_ids) {
      this.accessionHitIds.add(id);
    }
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

  public int getSubjectTaxid() {
    return originalSubjectTaxId;
  }

  public void setSubjectTaxid(int subjectTaxid) {
    this.originalSubjectTaxId = subjectTaxid;
  }

  public int getTotalNumberToRun() {
    if (accessionRunId < 0) {
      return -1;
    }

    if (totalNumberToRun < 0) {
      String query = "select count(id) from accession_hit where accession_run_id = ? AND rbh_status IS NOT NULL AND rbh_status != 'not run'";
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

  public String getQueryAccessionString() {
    return queryAccessionString;
  }

  public void setQueryAccessionString(String queryAccessionString) {
    this.queryAccessionString = queryAccessionString;
  }

  public List<String> getAccessionIdNames() {
    return accessionIdNames;
  }

  public void setAccessionIdNames(List<String> accessionIdNames) {
    this.accessionIdNames = accessionIdNames;
  }

  public Set<String> getQueryTaxidAccessionsAboveIdentity() {
    return queryTaxidAccessionsAboveIdentity;
  }

  public void setQueryTaxidAccessionsAboveIdentity(Set<String> queryTaxidAccessionsAboveIdentity) {
    this.queryTaxidAccessionsAboveIdentity = queryTaxidAccessionsAboveIdentity;
  }

//	public void setQueryAccessionIdName(String queryAccessionIdName) {
//		this.queryAccessionIdName = queryAccessionIdName;
//	}

//	public String lookupTopHitIdString() {
//		if (topHitIdString == null) {
//			if (accessionRunId < 0) {
//				return null;
//			}
//			String query = "select top_hit_accession_id from accession_run where id = ? limit 1";
//			try {
//				topHitIdString = jdbcTemplate.queryForObject(query, String.class, accessionRunId);
//			} catch (DataAccessException e) {
//				System.out.println("Attempting to run rbhBLAST with accession_run.id: " + accessionRunId
//						+ " and it does not have a top_hit_accession_id!");
//				return null;
//			}
//		}
//		return topHitIdString;
//	}
}
