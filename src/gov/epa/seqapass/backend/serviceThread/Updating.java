package gov.epa.seqapass.backend.serviceThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import gov.epa.seqapass.backend.hpcAccess.HPCConnector;
import gov.epa.seqapass.backend.hpcAccess.SBatchObject;
import gov.epa.seqapass.common.LevelFourAccessionRow;

public class Updating implements Runnable {

	private int remainingCount;
	private int accessionRunId;
	private int totalCount; // total number of SBatch jobs
	private JdbcTemplate jdbcTemplate;
	private BLASTTools blastTools;
	private ArrayList<SBatchObject> toRemove = new ArrayList<>();
	private ArrayList<SBatchObject> sBatchObjectsList = new ArrayList<>();
	ArrayList<SBatchObject> completedJobs;
	LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate; // Holds all of the completed jobs(ready for DB update)
	// private ArrayList<String> results = new ArrayList<String>(); // each list is
	// stored as (Y/N Answer,accessionRunId,hitCanonicalId)
	private Integer ncbiProviderId;
	private String rpsQuery = null;
	private HPCConnector hpcConnector;
	private boolean isRBH;
	private boolean isRPS;
//  private boolean isBLASTp;
	private boolean isLevel4;

	private static Logger logger = LogManager.getLogger(Updating.class);

	public Updating(LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate, JdbcTemplate jdbcTemplate,
			BLASTTools blastTools, int accessionRunId, ArrayList<SBatchObject> completedJobs, boolean isRBH,
			boolean isRPS, boolean isBLASTp, boolean isLevel4, Integer ncbiProviderId, HPCConnector hpcConnector) {
		this.linkedBlockingQueueUpdate = linkedBlockingQueueUpdate;
		this.jdbcTemplate = jdbcTemplate;
		this.blastTools = blastTools;
		this.accessionRunId = accessionRunId;
		this.completedJobs = completedJobs;
		this.isRBH = isRBH;
		this.isRPS = isRPS;
//      this.isBLASTp = isBLASTp;
		this.isLevel4 = isLevel4;

		this.ncbiProviderId = ncbiProviderId;
		this.hpcConnector = hpcConnector;

	}

	@Override
	public void run() {

		boolean shouldRun = true;

		while (shouldRun) {
			try {
				SBatchObject s = linkedBlockingQueueUpdate.poll(5000, TimeUnit.MILLISECONDS); // Pop the first sbatch
				// object from the queue
				if (s != null) {
					sBatchObjectsList.add(s);
				}
				for (SBatchObject sBatchObject : sBatchObjectsList) {
					if (sBatchObject.getOutputFilePath().compareTo("stop") != 0) { // If not the null job
						boolean updated = runUpdate(sBatchObject);
						logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": Updated? = " + updated);
						if (updated) {
							toRemove.add(sBatchObject);
							completedJobs.add(sBatchObject);
							logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": complete. Now remaining: " + remainingCount);
						} else {
							toRemove.add(sBatchObject);
						}
					} else {
						logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": Popped the stop job.");
					}
				}

				for (SBatchObject sBatchObject : toRemove) {
					sBatchObjectsList.remove(sBatchObject); // remove the job object from the list
				}
				toRemove.clear();

				if (sBatchObjectsList.size() == 1 && sBatchObjectsList.get(0).getOutputFilePath().compareTo("stop") == 0) {
					logger.info("AccID#" + accessionRunId + ".stop: One SBatch left in list && is the stop job.");
					shouldRun = false; // Done updating the database
				}
			} catch (InterruptedException e) {
				logger.error("AccID#" + accessionRunId + ": Something went wrong in the run method of the Updating class.  Error is\n" + e);
			}
		}
	}

	private boolean runUpdate(SBatchObject sBatchObject) throws InterruptedException {
		long freeMemory = Runtime.getRuntime().freeMemory();
		long maxMemory = Runtime.getRuntime().maxMemory();
		long collectionTime = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(mxBean -> mxBean.getCollectionTime()).sum();

		logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": Available Memory = " + freeMemory + " : " + "Max Memory = " + maxMemory + "GC Time= " + collectionTime);

//		long ourMemoryMeasurment = 200000000;
		long ourMemoryMeasurment = 100000000;
		long sleepTime = 2000;
		int loopCount = 0;
		while (freeMemory < ourMemoryMeasurment) {
			logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": free memory is less than " + ourMemoryMeasurment);
			freeMemory = Runtime.getRuntime().freeMemory();
			Thread.sleep(sleepTime);
			if (loopCount > 20) {
				logger.warn("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": wait loops: " + loopCount + " Available Memory = " + freeMemory + " : " + "Max Memory = " + maxMemory + "GC Time= " + collectionTime);
				loopCount = 0;
			}
			loopCount++;
		}
		
//		while(collectionTime < ?) {
//			collectionTime = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(mxBean -> mxBean.getCollectionTime()).sum();
//			Thread.sleep(?);	
//		}

		boolean updateComplete = false;
		logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber() + ": About to run update with " + remainingCount + " remaining");
		remainingCount--;
		if (isRBH) {
			updateComplete = runUpdateRBHParseOnly(sBatchObject);
		} else if (isRPS) {
			updateComplete = runUpdateRPS(sBatchObject);
		} else if (isLevel4) {
			updateComplete = runUpdateLevel4(sBatchObject);
		}
		return updateComplete;
	}

	private boolean runUpdateLevel4(SBatchObject sBatchObject) {
		logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + " Job Id = " + sBatchObject.getJobId() + " completed with a status of " + sBatchObject.getLevelFourAccessionRow().getStatus() + " and results in " + sBatchObject.getLevel4JobDir());

		String jobStatusTest = hpcConnector.getJobStatus(sBatchObject.getJobId());
		logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + ". Updating status check = " + jobStatusTest);

		String jobStatus = sBatchObject.getLevelFourAccessionRow().getStatus();
		if (jobStatus != null) {
			if (sBatchObject.getLevelFourAccessionRow() != null) {
				if (jobStatus.toLowerCase().contentEquals("completed")) {
					String pathToJobDir = sBatchObject.getLevel4JobDir();
					File jobDir = new File(pathToJobDir);
					logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + ". Job Dir exists? = " + jobDir + " : " + jobDir.exists());
					if (jobDir.exists()) {
						File pdbFile = new File(pathToJobDir + "/model1.pdb");
						logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + ". PDB exists? = " + pdbFile + " : " + pdbFile.exists());
						if (pdbFile.exists()) {
							parsePDBFile(pdbFile, sBatchObject.getLevel4RunId(), sBatchObject.getLevelFourAccessionRow().getNcbiAccession());
							logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + "Parsed & Inserted PDB");
						}
						File cscoreFile = new File(pathToJobDir + "/cscore");
						logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + ". CSCORE exists? = " + cscoreFile + " : " + cscoreFile.exists());
						if (cscoreFile.exists()) {
							parseCScoreFile(cscoreFile, sBatchObject.getLevelFourAccessionRow());
							logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + "Parsed & Inserted CSCORE");
						}
						handleFASTADuplicates(sBatchObject, pdbFile, cscoreFile);
					}
				} else {
					logger.error("Level4ID#" + sBatchObject.getLevel4RunId() + "." + " JobId {} has status {} with jobname = {} and jobdir = {}", sBatchObject.getJobId(), jobStatus, sBatchObject.getLevel4JobDir());
				}
			} else {
				logger.error("LevelFourAccesionRow is null for: " + sBatchObject.getLevel4RunId() + " " + sBatchObject.getQueryAccessionIdString() + " " + sBatchObject.getLevel4JobDir());
			}

			blastTools.getBlastTools2().updateLevel4DataStatusAndDate(sBatchObject.getLevel4RunId(), sBatchObject.getQueryAccessionIdString(), "itasser", sBatchObject.getLevelFourAccessionRow().getStatus());
			logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + "Completed DB update for "+ sBatchObject.getQueryAccessionIdString());
		} else {
			logger.error("Level4ID#" + sBatchObject.getLevel4RunId() + ". jobStatus = null");
		}
		return true;
	}
	
	private void handleFASTADuplicates(SBatchObject sBatchObjectRan, File pdbFile, File cscoreFile) {
		logger.debug("Level4ID#" + sBatchObjectRan.getLevel4RunId() + "." + "Checking for FASTA duplicates.");
		String level4ProjectDir = sBatchObjectRan.getLevel4ProjectDir();
		ArrayList<LevelFourAccessionRow> listOfRowsWithDuplicateFASTA = sBatchObjectRan.getLevelFourRowsWithCommonAccessions();
		LevelFourAccessionRow rowThatWasRun = listOfRowsWithDuplicateFASTA.get(0);
		
		for(LevelFourAccessionRow dupFASTARow: listOfRowsWithDuplicateFASTA) {
			if(dupFASTARow.getNcbiAccession() != sBatchObjectRan.getLevelFourAccessionRow().getNcbiAccession()) {
				String level4ProteinDir = getSubDirName(dupFASTARow, level4ProjectDir, dupFASTARow.getTemplate());
				
				copyFile(pdbFile, new File(level4ProteinDir + "/" + pdbFile.getName()), sBatchObjectRan);
				copyFile(cscoreFile, new File(level4ProteinDir + "/" + cscoreFile.getName()), sBatchObjectRan);
				parsePDBFile(pdbFile, sBatchObjectRan.getLevel4RunId(), dupFASTARow.getNcbiAccession());
				parseCScoreFile(cscoreFile, dupFASTARow);
				dupFASTARow.setStatus(rowThatWasRun.getStatus());
				
				blastTools.getBlastTools2().updateLevel4DataStatusAndDate(dupFASTARow.getLevel4RunId(), dupFASTARow.getNcbiAccession(), "itasser", dupFASTARow.getStatus());
				logger.debug("Level4ID#" + dupFASTARow.getLevel4RunId() + "." + "Completed DB update for "+ dupFASTARow.getNcbiAccession());
			}
		}
	}	

	public void copyFile(File source, File dest, SBatchObject sBatchObject) {
		if (source != null && source.exists() && dest != null) {
			try {
				InputStream is = new FileInputStream(source);
				OutputStream os = new FileOutputStream(dest);
				byte[] buffer = new byte[1024];
				int length;
				while ((length = is.read(buffer)) > 0) {
					os.write(buffer, 0, length);
				}
				is.close();
				os.close();
				logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + "Successfully copied " + source.getPath() + " to " + dest.getPath());
			} catch (Exception e) {
				logger.debug("Level4ID#" + sBatchObject.getLevel4RunId() + "." + "Failed to copy " + source.getPath() + " to " + dest.getPath());
			}
		}
	}

	//ENSURE THIS STAYS THE SAME FROM RUNLEVEL4 CLASS
	private String getSubDirName(LevelFourAccessionRow row, String projectDir, String template) { 
		String commonName = row.getCommonName();
		commonName = commonName.replaceAll("[ ,]", "_");
		commonName = commonName.replaceAll("\\+", "-");
		commonName = commonName.replaceAll("[^ -~]", "");
		commonName = commonName.replaceAll("'", "");
		// Add accession to all directories
		String proteinDir = projectDir + "/" + commonName + "_" + row.getNcbiAccession() + "+";
		if (template != null && !template.trim().isEmpty()) {
			proteinDir = proteinDir + template;
		}
		return proteinDir;
	}

	public void parseCScoreFile(File cScoreFile, LevelFourAccessionRow row) {
		if (cScoreFile.exists()) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(cScoreFile));
				Stream<String> lines = reader.lines();
				Iterator<String> iterator = lines.iterator();
				ArrayList<String> labels = new ArrayList<String>();
				while (iterator.hasNext()) {
					String line = iterator.next();
					if (line.matches("(Model#)[\\s\\S]+")) {
						String[] splits = line.split("(\\s)+");
						for (String val : splits) {
							if (!val.contentEquals("(A)")) {
								labels.add(val);
							}
						}
					}
					if (line.matches("(model1)[\\s\\S]+")) {
						String[] splits = line.split("(\\s)+");
						if ((splits.length) == labels.size()) {
							for (int i = 0; i < splits.length; i++) {
								String val = splits[i];
								if (labels.get(i).contentEquals("C-score")) {
									row.setCscore(Double.parseDouble(val));
								} else if (labels.get(i).contentEquals("TM-score")) {
									String[] tmSplit = val.split("\\+-");
									if (tmSplit.length == 2) {
										String tmScore = tmSplit[0];
										row.setTm_score(Double.parseDouble(tmScore));
										String tmError = tmSplit[1];
										row.setTm_score_error(Double.parseDouble(tmError));
									}
								} else if (labels.get(i).contentEquals("RMSD")) {
									String[] rmsdSplit = val.split("\\+-");
									if (rmsdSplit.length == 2) {
										String rmsdScore = rmsdSplit[0];
										row.setRmsd(Double.parseDouble(rmsdScore));
										String rmsdError = rmsdSplit[1];
										row.setRmsd_error(Double.parseDouble(rmsdError));
									}
								} else if (labels.get(i).contentEquals("density")) {
									row.setDensity(Double.parseDouble(val));
								}
							}
						}
					}
				}
				blastTools.getBlastTools2().updateLevel4CScoreData(row.getLevel4RunId(), row, row.getNcbiAccession());
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void parsePDBFile(File pdbFile, int level4RunId, String queryAccession) {
		// Model 1 PDB: each val is separated by tab (\t), each line by new line (\n)
		if (pdbFile.exists()) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(pdbFile));
				Stream<String> lines = reader.lines();
				Iterator<String> iterator = lines.iterator();
				StringBuilder linesStringBuilder = new StringBuilder();
				while (iterator.hasNext()) {
					String line = iterator.next();
//					String [] splits = line.split("(\\s)+");
//					StringBuilder lineStringBuilder = new StringBuilder();
//					for(String val: splits) {
//						lineStringBuilder.append(val + "\t");
//					}
					// linesStringBuilder.append(lineStringBuilder.toString().trim() + "\n");
					linesStringBuilder.append(line + "\n");
				}
				reader.close();
				blastTools.getBlastTools2().updateLevel4PDBData(level4RunId, linesStringBuilder.toString(),
						queryAccession);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean runUpdateRPS(SBatchObject sBatchObject) {
		String beforeEqualString = "^[^=]*[^ =]";
		Pattern beforeEqualPattern = Pattern.compile(beforeEqualString);

		File outputFile = new File(sBatchObject.getOutputFilePath());

		List<String> nameList = blastTools.getBLASTpXMLTypeNames();
		nameList.add(0, "ncbi_version_id");
		nameList.add(1, "accession_id");

		ArrayList<ArrayList<String>> allValues = new ArrayList<ArrayList<String>>();

		try {
			Scanner scanner = new Scanner(outputFile);
			while (scanner.hasNextLine()) {
				String line1 = scanner.nextLine();
				if (line1.contains("ncbi_version_id")) {
					HashMap<String, String> oneRowMap = new HashMap<String, String>();
					ArrayList<String> oneRowList = new ArrayList<String>();
					String label1 = null;
					Matcher befEqMatcher = beforeEqualPattern.matcher(line1);
					if (befEqMatcher.find()) {
						label1 = befEqMatcher.group(0);
					}
					String value1 = null;
					value1 = line1.substring(label1.length() + 3);
					if (label1 != null && value1 != null && label1.endsWith(nameList.get(0))) {
						oneRowMap.put(label1, value1);
						oneRowList.add(value1);
					}
					for (int i = 1; i < 24; i++) {
						String nextLine = scanner.nextLine();
						String nextLabel = null;
						Matcher beforeEqualMatcher = beforeEqualPattern.matcher(nextLine);
						if (beforeEqualMatcher.find()) {
							nextLabel = beforeEqualMatcher.group(0);
						}
						String nextValue = null;
						nextValue = nextLine.substring(nextLabel.length() + 3);
						if (nextLabel != null && nextValue != null && nextLabel.endsWith(nameList.get(i))) {
							oneRowMap.put(nextLabel, nextValue);
							oneRowList.add(nextValue);
						}
					}
					allValues.add(oneRowList);
					if (allValues.size() == 400 || (scanner.hasNext() == false)) {
						logger.info("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber()
								+ ": RPS sending jobs to sendQueryRPS with " + allValues.size() + " values");
						blastTools.sendQueryRPS(nameList, allValues, sBatchObject);
						sendCommonDomain(allValues, sBatchObject.getJobFragmentNumber());
						allValues.clear();
					}
				}
			}
			scanner.close();
			trackTotalCountRPS();
			return true;
		} catch (Exception e) {
			logger.fatal("AccID#" + accessionRunId + "." + sBatchObject.getJobFragmentNumber()
					+ ": RPS update could not read file: " + sBatchObject.getOutputFilePath() + ".  Error is " + e);
			return false;
		}

	}

	private void sendCommonDomain(ArrayList<ArrayList<String>> allValues, int jobFragmentNumber) {
		logger.info("AccID#" + accessionRunId + "." + jobFragmentNumber + ": Sending " + allValues.size()
				+ " common domains");
		jdbcTemplate.batchUpdate(
				"INSERT IGNORE INTO common_domain (cdd_id, ncbi_version_id, full_definition) VALUES(?,?,?)",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						Integer cddId = -1;
						try {
							cddId = Integer.parseInt(allValues.get(i).get(5));
						} catch (NumberFormatException e) {
							logger.error("AccID#" + accessionRunId + "." + jobFragmentNumber + ": xml_Hit_accession: "
									+ allValues.get(i).get(5) + " field from the xml parser was not an integer!");
						}
						String fullDefinition = allValues.get(i).get(4);
						ps.setInt(1, cddId);
						ps.setInt(2, ncbiProviderId);
						ps.setString(3, fullDefinition);
					}

					@Override
					public int getBatchSize() {
						return allValues.size();
					}
				});
	}

//| ncbi_version_id     | int(11)                   1
//| accession_id        | varchar(31)               2
//| xml_Hit_num         | int(11)                   3 setInt
//| xml_Hit_id          | varchar(32)               4
//| xml_Hit_def         | mediumtext                5
//| xml_Hit_accession   | int(11)                   6
//| xml_Hit_len         | int(11)                   7
//| xml_Hsp_num         | int(11)                   8
//| xml_Hsp_bit-score   | double                    9
//| xml_Hsp_score       | int(11)                  10
//| xml_Hsp_evalue      | double                   11
//| xml_Hsp_query-from  | int(11)                  12
//| xml_Hsp_query-to    | int(11)                  13
//| xml_Hsp_hit-from    | int(11)                  14
//| xml_Hsp_hit-to      | int(11)                  15
//| xml_Hsp_query-frame | int(11)                  16
//| xml_Hsp_hit-frame   | int(11)                  17
//| xml_Hsp_identity    | int(11)                  18
//| xml_Hsp_positive    | int(11)                  19
//| xml_Hsp_gaps        | int(11)                  20
//| xml_Hsp_align-len   | int(11)                  21
//| xml_Hsp_qseq        | mediumtext               22           
//| xml_Hsp_hseq        | mediumtext               23 
//| xml_Hsp_midline     | mediumtext               24               

	private String createQueryRPS() {
		StringBuilder queryBuilder = new StringBuilder();
		StringBuilder qMarksBuilder = new StringBuilder();
		queryBuilder.append("INSERT IGNORE INTO rps_result (`ncbi_version_id`,`accession_id`,");
		qMarksBuilder.append("?,");
		qMarksBuilder.append("?,");
		for (String key : blastTools.getBLASTpXMLTypeNames()) {
			queryBuilder.append("`xml_" + key + "`,");
			qMarksBuilder.append("?,");
		}
		queryBuilder.deleteCharAt(queryBuilder.length() - 1);
		qMarksBuilder.deleteCharAt(qMarksBuilder.length() - 1);
		queryBuilder.append(") VALUES (");
		queryBuilder.append(qMarksBuilder.toString());
		queryBuilder.append(")");
		logger.info("The rps Query is: {}", queryBuilder.toString());
		return queryBuilder.toString();
	}

	public void trackTotalCountRPS() {
		int percentDone = (((totalCount - remainingCount) * 100) / totalCount);
		if (remainingCount <= 0) {
			percentDone = 100;
		} // Deals with roundoff problem
		logger.info("totalCount = {} ; remainingCount = {} ; percentDone = {}", totalCount, remainingCount,
				percentDone);
		blastTools.updateAccessionRunRpsCompleteness(accessionRunId, percentDone);
	}

	private boolean runUpdateRBHParseOnly(SBatchObject sBatchObject) {
		File outputFile = new File(sBatchObject.getOutputFilePath()); // Try to open the file with the Y/N answers
		String queryAccessionCanonical = sBatchObject.getQueryAccessionCanonicalIdString();
		try {
			Scanner scanner = new Scanner(outputFile);
			Integer subjectTaxid = Integer.parseInt(scanner.nextLine());
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] lineParts = line.split("\\t");
				String canonicalAccessionId = lineParts[0];
				String subjectTopCanonicalId = lineParts[1];
				//This should handle the case where the query taxid is different than the canonical tax id.
			    //This replaces the query accession string returned by RBH blast with the identical canonical accession string
			    //to allow proper matching for ortholog determination.
			    String queryAccString = sBatchObject.getQueryAccessionIdString();
			    if (subjectTopCanonicalId.equals(queryAccString)) {
			    	System.out.println("Replacing " + subjectTopCanonicalId + " with " + queryAccessionCanonical);
			    	subjectTopCanonicalId = queryAccessionCanonical;
			    }
				
				Double bitscore = Double.parseDouble(lineParts[2]);
				Double evalue = Double.parseDouble(lineParts[3]);
				blastTools.sendQueryRBH(subjectTopCanonicalId, bitscore, evalue, canonicalAccessionId, subjectTaxid,
						ncbiProviderId, sBatchObject);
			}
			scanner.close();
			trackTotalCountRBH();
			return true;
		} catch (FileNotFoundException e) {
			logger.error("Error-RBH: Could not read {}", sBatchObject.getOutputFilePath());
			return false;
		}
	}

	public void trackTotalCountRBH() {
		int percentDone = (((totalCount - remainingCount) * 100) / totalCount);
		if (remainingCount <= 0) {
			percentDone = 100;
		} // Deals with roundoff problem
		blastTools.updateAccessionRunRbhCompleteness(accessionRunId, percentDone);
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public void setRemainingCount(int remainingCount) {
		this.remainingCount = remainingCount;
	}

	public int getRemainingCount() {
		return remainingCount;
	}

	public String getRpsQuery() {
		if (rpsQuery == null) {
			setRpsQuery(createQueryRPS());
		}
		return rpsQuery;
	}

	public void setRpsQuery(String rpsQuery) {
		this.rpsQuery = rpsQuery;
	}
}
