package gov.epa.seqapass.backend.serviceThread;

//import gov.epa.seqapass.backend.dao.RService;
import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;
//import gov.epa.seqapass.common.CutoffData;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
//import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.base.Joiner;

public class RunLevel2 {
	
	private static Logger logger = LogManager.getLogger(RunLevel2.class);

	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private int userId = -1;
	private int accessionRunId = -1;
	private int level2RunId = -1;
	private int level2CddId = -1;
	private int level2startPosition = -1;
	/** This is the accession id submitted by the user */
	private String queryAccessionString = null;
	/** This is the canonicalized accession id */
	private String canonicalAccessionString = null;
	/**
	 * This is the top hit accession id from BLASTp (as determined by
	 * updateAccessionRunTopHit)
	 */
	// private String topHitAccessionString = null;

	private BLASTTools blastTools;
	private BLASTTools2 blastTools2;
	//private RService rService;

	/**
	 * Constructor for a new Level 2 analysis job. 4 items are passed for access
	 * purposes, and the accessionRunId and userId are a parameters
	 * 
	 * @param blastTools
	 *            - passing access to the blastTools object
	 * @param template
	 *            - passing access to the JdbcTemplate object
	 * @param keeper
	 *            - passing access to the NCBIKeeper object
	 * @param rService
	 *            - passing access to the RService object
	 * @param accessionRunId
	 *            - the associated accessionRunId
	 * @param userId
	 *            - userId requesting the run
	 */
//	public RunLevel2(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, RService rService,
//			int accessionRunId, int userId) {
//		this.jdbcTemplate = template;
//		this.ncbiKeeper = keeper;
//		this.blastTools = blastTools;
//		this.blastTools2 = blastTools.getBlastTools2();
//		this.rService = rService;
//		this.accessionRunId = accessionRunId;
//		this.userId = userId;
//	}
//
//
	/**
	 * Constructor for a new Level 2 analysis job. 4 items are passed for access
	 * purposes, and the accessionRunId and userId are a parameters
	 * 
	 * @param blastTools
	 *            - passing access to the blastTools object
	 * @param template
	 *            - passing access to the JdbcTemplate object
	 * @param keeper
	 *            - passing access to the NCBIKeeper object
	 * @param accessionRunId
	 *            - the associated accessionRunId
	 * @param userId
	 *            - userId requesting the run
	 */
	public RunLevel2(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, 
			int accessionRunId, int userId) {
		this.jdbcTemplate = template;
		this.ncbiKeeper = keeper;
		this.blastTools = blastTools;
		this.blastTools2 = blastTools.getBlastTools2();
		//this.rService = rService;
		this.accessionRunId = accessionRunId;
		this.userId = userId;
	}

	public boolean dispose() {
		return false;
	}

	public boolean kill() {
		return false;
	}

	/**
	 * This method runs Level 2 analysis assuming the accessionRunId class variable
	 * has been set, and the domainAccessionNum is provided. The return is the
	 * level2_run id number.
	 * 
	 * @param domainAccessionNum
	 * @return int - the level2_run id number for the newly created run. The status
	 *         can be accessed using this number.
	 */
	public int runLevelTwo() {
		if (accessionRunId < 0 || level2CddId < 0 || level2RunId < 0 || level2startPosition < 0) {
			//System.out.println(
			//		"Something wrong with level 2 run:  level2RunId, level2CddId, accessionRunId, level2startPosition (in order) are:"
			//				+ level2RunId + ", " + level2CddId + ", " + accessionRunId + ", " + level2startPosition);
			logger.error("Something wrong with level 2 run:  level2RunId, level2CddId, accessionRunId, "
					+ "level2startPosition (in order) are: {}, {}, {}, {}", 
					level2RunId, level2CddId, accessionRunId, level2startPosition);
			return -1;
		}

		String query = "SELECT canonical_accession_id , top_hit_accession_id FROM accession_run WHERE id = ? LIMIT 1";
		Map<String, Object> aMap = jdbcTemplate.queryForMap(query, accessionRunId);
		canonicalAccessionString = (String) aMap.get("canonical_accession_id");
		queryAccessionString = (String) aMap.get("top_hit_accession_id");
		//System.out.println("Got data for Level 2 run: canonicalAccessionString = " + canonicalAccessionString
		//		+ " and queryAccessionString = " + queryAccessionString);
		logger.info("Got data for Level 2 run: canonicalAccessionString = {} and queryAccessionString = {}", 
				canonicalAccessionString, queryAccessionString);

		// return -1;
		// }

		// String query = "SELECT top_hit_accession_id, ncbi_version_id FROM
		// accession_run WHERE id = ? LIMIT 1";
		// List<Map<String, Object>> rows = jdbcTemplate.queryForList(query,
		// accessionRunId);
		// if (rows.size() == 0) {
		// System.out.println("There was no accession_id and / or ncbi_version for
		// accession_run: " + accessionRunId);
		// return -1;
		// }
		// Map<String, Object> oneRow = rows.get(0);
		// topHitAccessionString = (String) oneRow.get("top_hit_accession_id");
		// System.out.println("OK, the top hit accession_id is: " +
		// topHitAccessionString);

		// int ncbiVersion = (int) oneRow.get("ncbi_version_id");

		List<String> accessionsToRun = new ArrayList<String>();
		// accessionsToRun.add(topHitAccessionString);

		// NEXT QUERY - GET ACCESSION IDS TO RUN
		// CHECK TO SEE IF WE SHOULD ONLY GET HITS WITH CDD COUNT > 0 ??!?
		query = "SELECT distinct hit_canonical_id FROM accession_hit WHERE accession_run_id = ? AND rbh_status IN ('Y','N') order by id";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, accessionRunId);
		if (rows.size() == 0) {
			return -1;
		}
		// System.out.println("The query is: " + query + "\n and the accessionRunId is "
		// + accessionRunId);
		int rowsToRun = rows.size();
		//System.out.println("Got " + rowsToRun + " hit accession ids to add to query accession id");
		logger.info("Got {} hit accession ids to add to query accession id", rowsToRun);
		
		for (Map<String, Object> row : rows) {
			String hitAccession = (String) row.get("hit_canonical_id");
			accessionsToRun.add(hitAccession);
		}
		String[] pathsToFastas = createLevel2TempFastas(accessionsToRun);
		if (pathsToFastas.length != 2) {
			if (pathsToFastas.length == 1) {
				blastTools2.updateLevel2Status(level2RunId, "not enough hits");
				int hitCount = Integer.parseInt(pathsToFastas[0]);
				return hitCount;
			}
			blastTools2.updateLevel2Status(level2RunId, "failed");
			return -1;
		}
		ProcessProvider level2compare = new ProcessProvider();
		level2compare.setUserID(0);
		level2compare.setJobID(0);
		level2compare
				.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.selfAlignProcess));
		level2compare.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
		List<String> argVals = new ArrayList<String>();
		argVals.add(pathsToFastas[0]);
		argVals.add(pathsToFastas[1]);
		argVals.add(20000 + "");
		argVals.add(5 + "");
		level2compare.setArgVals(argVals);
		String level2XML = null;
		blastTools2.updateLevel2Status(level2RunId, "BLASTp started");
		try {
			String[] blastPresults = level2compare.timeRun();
			level2XML = blastPresults[0];
			String stdErr = blastPresults[1];
			blastTools.logJobTime("level2Blast", accessionRunId, 1, rowsToRun, stdErr);
		} catch (InterruptedException | IOException e) {
			blastTools2.updateLevel2Status(level2RunId, "failed");
			return -1;
		}
		if (level2XML == null || level2XML.equals("")) {
			blastTools2.updateLevel2Status(level2RunId, "failed");
			return -1;
		}
		// System.out.println("Level 2 still going -1");
		blastTools2.updateLevel2Status(level2RunId, "analyzing");
		// System.out.println("Level 2 still going 0");

		List<ArrayList<String>> allResults;
		try {
			String hitPatternString = "<Hit>(.*?)</Hit>";
			Pattern hitPattern = Pattern.compile(hitPatternString);
			Matcher hitMatcher = hitPattern.matcher(level2XML);
			allResults = new ArrayList<ArrayList<String>>();
			while (hitMatcher.find()) {
				String oneHit = hitMatcher.group(1);
				allResults.addAll(processOneLevel2Hit(level2RunId, oneHit));
			}
		} catch (Exception e) {
			// System.out.println("e is :" + e);
			// e.printStackTrace();
			return -1;
		}
		// System.out.println("Level 2 still going 1");

		double maxBitScore = Double.parseDouble(allResults.get(0).get(8));
		blastTools2.updateLevel2MaxBitScore(level2RunId, maxBitScore);
		// System.out.println("Level 2 still going 2");

		List<String> nameList = blastTools.getBLASTpXMLTypeNames();
		nameList.add(0, "level2_run_id");
		nameList.add(1, "hit_accession_id");
		// System.out.println("Level 2 still going 3");
		blastTools.saveLevel2Batch(nameList, allResults);
		//CutoffData primaryCutoffData = blastTools2.generateLevelTwoPrimaryCutoff(rService, level2RunId);
		//CutoffData fullCutoffData = blastTools2.generateLevelTwoFullCutoff(rService, level2RunId);
//		CutoffData primaryCutoffData = blastTools2.generateLevelTwoPrimaryCutoff(level2RunId);
//		CutoffData fullCutoffData = blastTools2.generateLevelTwoFullCutoff(level2RunId);
		// System.out.println("Level 2 still going 4");

//		blastTools2.insertLevelTwoCutoff(level2RunId, primaryCutoffData, fullCutoffData);
		blastTools2.setLevel2RunComplete(level2RunId);

		blastTools2.createLevelTwoReports(accessionRunId, level2RunId, -1, 1);

		int toxCastUserID = blastTools2.getToxCastUserId();
		if (userId == toxCastUserID) {
			blastTools2.createLevelTwoReports(accessionRunId, level2RunId, userId, 2); // creates for public_reports
																						// destination
		}
		blastTools2.updateLevel2Status(level2RunId, "complete");
		return allResults.size();
	}

	/**
	 * This creates the fasta files necessary for Level 2 analysis. The template
	 * protein is the first in the list provided in the accesssionsForTemp List.
	 * Ranges for each (including template protein) are fixed to those matching the
	 * CDD in rpsBlast for the specified domainAccessionNum. The first fastaFilePath
	 * in the resulting String[] points to a single protien fasta (the template),
	 * and the subsequent one points to the multiple fasta file (in which the first
	 * is the template).
	 * 
	 * @param accessionsForTemp
	 *            - the first protein is used as the template
	 * @param domainAccessionNum
	 *            - the numeric xml_Hit_accession (referring to a CDD) found in the
	 *            rps_result table
	 * @return String[2] full paths to the temporary fasta files: single, multiple
	 */
	private String[] createLevel2TempFastas(List<String> accessionsForTemp) {
		// System.out.println("Trying createLevel2TempFastas");
		logger.debug("Trying createLevel2TempFastas");

		NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		int ncbiVersionID = ncbiProvider.getId();
		//System.out.println("ncbiVersionID = " + ncbiVersionID);

		//System.out.println("accessionsForTemp.size = " + accessionsForTemp.size());
		//System.out.println("domainAccessionNum = " + level2CddId);
		logger.info("ncbiVersionID = {}", ncbiVersionID);

		logger.info("accessionsForTemp.size = {}", accessionsForTemp.size());
		logger.info("domainAccessionNum = {}", level2CddId);

		if (accessionsForTemp.size() < 2) {
			return null;
		}

		if (level2CddId < 0) {
			return null;
		}

		String list = "";
		try {
			list = Joiner.on("','").join(accessionsForTemp);
		} catch (Exception e1) {
			// Mostly in case some null values
			logger.error("Exception caught: {}", e1);
			e1.printStackTrace();
		}

//		String query = "SELECT accession_id, `xml_Hsp_query-from`, `xml_Hsp_query-to` "
//				+ "FROM rps_result WHERE ncbi_version_id = ? " + " AND accession_id IN ('" + list
//				+ "') AND `xml_Hit_accession` = ? " + "GROUP BY accession_id, xml_Hsp_num";
		
		String query = "SELECT accession_id, `xml_Hsp_query-from`, `xml_Hsp_query-to` "
				+ "FROM rps_result WHERE ncbi_version_id IN (SELECT DISTINCT a.id FROM version a, version b "
				+ "WHERE a.update_version = b.update_version AND b.id = ?) AND accession_id IN ('" + list
				+ "') AND `xml_Hit_accession` = ? " + "GROUP BY accession_id, xml_Hsp_num";

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, ncbiVersionID, level2CddId);
		// System.out.println("query = " + query);

		if (rows.size() == 0) {
			// System.out.println("No hits!");
			logger.error("No hits!");
			return null;
		}
		// System.out.println("Got this many hits : " + rows.size());
		logger.info("Got this many hits : {}", rows.size());

		// if (rows.size() != accessionsForTemp.size()) {
		// return null;
		// }
		Map<String, String> rangeForFasta = new HashMap<String, String>();
		// String accessionStringForFasta = null;
		for (Map<String, Object> row : rows) {
			String accessionId = (String) row.get("accession_id");
			int queryFrom = (int) row.get("xml_Hsp_query-from");
			int queryTo = (int) row.get("xml_Hsp_query-to");
			rangeForFasta.put(accessionId, queryFrom + "-" + queryTo);
			// String lineToAdd = accessionId + " " + queryFrom + "-" + queryTo + "\n";
			// if (accessionId.equals(accessionsForTemp.get(0)) && queryFrom ==
			// level2startPosition) {
			// // accessionStringForFasta = lineToAdd;
			// } else {
			// b.append(lineToAdd);
			// }
		}
		List<String> activeAccessionIds = new ArrayList<String>();
		StringBuilder b = new StringBuilder();
		for (String accession : accessionsForTemp) {
			if (rangeForFasta.containsKey(accession)) {
				b.append(accession + " " + rangeForFasta.get(accession) + "\n");
				activeAccessionIds.add(accession);
			}
		}
		if (activeAccessionIds.size() < 3) {
			// System.out.println("Not enough hits!");
			logger.warn("Not enough hits!");
			String[] result = new String[1];
			result[0] = activeAccessionIds.size() + "";
			return result;
		}

		String[] twoPaths = new String[2];
		String outputFileRoot = ncbiProvider.getPathTempFasta() + "/level2-" + level2RunId + ":"
				+ accessionsForTemp.get(0);
		// String inputQueryPath = outputFileRoot + "-self.txt";
		twoPaths[0] = outputFileRoot + "-self.fsa";
		String inputSubjectPath = outputFileRoot + "-from_" + accessionsForTemp.get(1) + ".txt";
		twoPaths[1] = outputFileRoot + "-from_" + accessionsForTemp.get(1) + ".fsa";
		// System.out.println("twoPaths: " + twoPaths[0] + " and " + twoPaths[1]);
		logger.info("twoPaths: {} and {}", twoPaths[0], twoPaths[1]);

		// try {
		// PrintWriter fastaList = new PrintWriter(inputQueryPath);
		// fastaList.print(accessionStringForFasta);
		// fastaList.flush();
		// fastaList.close();
		// } catch (Exception e) {
		// System.out.println("Write inputQueryPath to fasta List failed");
		// return null;
		// }

		try {
			PrintWriter fastaList = new PrintWriter(inputSubjectPath);
			fastaList.print(b.toString());
			fastaList.flush();
			fastaList.close();
		} catch (Exception e) {
			// System.out.println("Write inputSubjectPath to fasta List failed");
			logger.error("Write inputSubjectPath to fasta List failed");
			return null;
		}
		// System.out.println("Write fasta lists succeeded");
		logger.debug("Write fasta lists succeeded");

		ProcessProvider createTargetFastas = new ProcessProvider();
		createTargetFastas.setUserID(0);
		createTargetFastas.setJobID(0);
		createTargetFastas
				.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.getManyFastasProcess));
		createTargetFastas.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
		List<String> argVals1 = new ArrayList<String>();
		argVals1.add(ncbiProvider.getPathNrData());
		argVals1.add("prot");
		argVals1.add(inputSubjectPath);
		argVals1.add(">%a %s");
		createTargetFastas.setArgVals(argVals1);

		String[] result;
		try {
			result = createTargetFastas.timeRun();
		} catch (InterruptedException | IOException e) {
			// System.out.println("Creating fasta for Level 2 file failed");
			logger.error("Creating fasta for Level 2 file failed");
			return new String[1];
		}

		String oneFastaPatternString = ">([^ ]+) ([A-Z]+)";
		Pattern oneFastaPattern = Pattern.compile(oneFastaPatternString);
		Matcher fastaMatcher = oneFastaPattern.matcher(result[0]);
		Map<String, String> fastaLookup = new HashMap<String, String>();
		while (fastaMatcher.find()) {
			String accessionKey = fastaMatcher.group(1);
			String accessionFasta = fastaMatcher.group(2);
			fastaLookup.put(accessionKey, accessionFasta);
		}
		String singleFasta = "";
		StringBuilder targetContents = new StringBuilder();
		//System.out.println("total accessions " + accessionsForTemp.size());
		//System.out.println("accessions with fastas " + fastaLookup.size());
		//System.out.println("activeAccessionIds " + activeAccessionIds.size());
		logger.info("total accessions {}", accessionsForTemp.size());
		logger.info("accessions with fastas {}", fastaLookup.size());
		logger.info("activeAccessionIds {}", activeAccessionIds.size());

		for (int i = 0; i < accessionsForTemp.size(); i++) {
			String accessionTarget = accessionsForTemp.get(i);
			if (fastaLookup.containsKey(accessionTarget)) {
				String theFasta = ">" + accessionTarget + "\n" + fastaLookup.get(accessionTarget) + "\n";
				if (i == 0) {
					singleFasta = theFasta;
				}
				targetContents.append(theFasta);
			}
		}

		if (targetContents.length() < 1 || singleFasta.length() < 5) {
			// System.out.println("Making two fasta files for level 2 failed");
			logger.error("Making two fasta files for level 2 failed");
			return new String[1];
		}
		try {
			PrintWriter fastaOut = new PrintWriter(twoPaths[0]);
			fastaOut.print(singleFasta);
			fastaOut.flush();
			fastaOut.close();
		} catch (Exception e) {
			// System.out.println("Writing single fasta for level 2 failed");
			logger.error("Writing single fasta for level 2 failed");
			return new String[1];
		}
		try {
			PrintWriter fastaOut = new PrintWriter(twoPaths[1]);
			fastaOut.print(targetContents.toString());
			fastaOut.flush();
			fastaOut.close();
		} catch (Exception e) {
			// System.out.println("Writing target fastas for level 2 failed");
			logger.error("Writing target fastas for level 2 failed");
			return new String[1];
		}
		return twoPaths;
	}

	public int getAccession_run_id() {
		return accessionRunId;
	}

	public void setAccession_run_id(int accessionRunId) {
		this.accessionRunId = accessionRunId;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int createAddAndGetNewLevel2RunId() {
		if (level2RunId > -1 || level2CddId < 0 || accessionRunId < 0) {
			//System.out.println("Didn't set id.  level2RunId, level2CddId, accessionRunId (in order) are:" + level2RunId
			//		+ ", " + level2CddId + ", " + accessionRunId);
			logger.error("Didn't set id.  level2RunId, level2CddId, accessionRunId (in order) are: {}, {}, {}", 
					level2RunId, level2CddId, accessionRunId);
			return -1;
		}
		// FOR NOW, ALLOW NON-ADMIN USERS TO RE-SUBMIT, BECAUSE OTHERWISE, THEY WILL NOT
		// BE ABLE TO SEE THAT A PREVIOUSLY RUN JOB WAS DONE
		// System.out.println("Trying to get level 2 run id (new or otherwise) ...");
		// String query = "SELECT id FROM level2_run WHERE accession_run_id = ? AND
		// cdd_accession_num = ? AND start_position = ? LIMIT 1";
		// try {
		// level2RunId = jdbcTemplate.queryForObject(query, Integer.class,
		// accessionRunId, level2CddId, level2startPosition);
		// } catch (DataAccessException e1) {
		// // DO NOTHING, IT JUST MEANS IT HAS NOT BEEN RUN YET
		// System.out.println("This level 2 run id is new!");
		// }
		// if (level2RunId >= 0) {
		// System.out.println("This level 2 run was already found: " + level2RunId);
		// return level2RunId;
		// }
		// // NOT FOUND, SO ADD IT
		// System.out.println("User id for this level 2 run is " + userId);

		try {
			level2RunId = blastTools.insertLevel2RunReturnId(accessionRunId, userId, level2CddId, level2startPosition);
		} catch (SQLException e) {
			// Apparently this combination has already been submitted, so level2RunId will
			// remain -1
		}
		// System.out.println("Inserted run into level 2, and value was: " + level2RunId);
		logger.info("Inserted run into level 2, and value was: {}", level2RunId);
		return level2RunId;
	}

	public String getLevel2StatusFromDB() {
		if (level2RunId < 0 || accessionRunId < 0 || level2CddId < 0) {
			//System.out.println("Couldn't get status.  level2RunId, level2CddId, accessionRunId (in order) are:"
			//		+ level2RunId + ", " + level2CddId + ", " + accessionRunId);
			logger.error("Couldn't get status.  level2RunId, level2CddId, accessionRunId (in order) are: {}, {}, {}", 
					level2RunId, level2CddId, accessionRunId);
			return null;
		}
		String query = "SELECT status FROM level2_run WHERE id = ?";
		try {
			return jdbcTemplate.queryForObject(query, String.class, level2RunId);
		} catch (DataAccessException e1) {
			logger.error("Job Not Found! {}", e1);
			return "Job Not Found!";
		}
	}

	/**
	 * This method creates a List of LinkedLists for one "Hit" entry from the XML
	 * output from BLASTp. It requires a zero or greater accession_run_id whcih is
	 * prepended to the start of all results lists.
	 * 
	 * @param accession_run_id
	 * @param oneHit
	 *            A String containing the XML content of one "Hit" result from
	 *            BLASTp
	 * @return
	 */
	private List<ArrayList<String>> processOneLevel2Hit(int level2RunId, String oneHit) {
		// System.out.println("Starting processOneLevel2Hit with leve2RunId: " +
		// level2RunId + " and a hit string of length: "
		// + oneHit.length());
		if (level2RunId < 0) {
			return null;
		}
		List<ArrayList<String>> results = new ArrayList<ArrayList<String>>();
		// System.out.println("Still going 0");

		StringBuilder pattern1Builder = new StringBuilder();
		StringBuilder pattern2Builder = new StringBuilder();
		// System.out.println("Still going 1 with blastTools: " + blastTools);
		// System.out.println("Still going 1 with blastTools.blastPxmlTypes: " +
		// blastTools.getBlastPxmlTypes());
		// System.out.println("Still going 1 with blastTools.blastPxmlTypes.keySet(): "
		// + blastTools.getBlastPxmlTypes().keySet());

		for (String name : blastTools.getBlastPxmlTypes().keySet()) {
			if (name.startsWith("Hit_")) {
				pattern1Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
			} else if (name.startsWith("Hsp_")) {
				pattern2Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
			}
		}
		// System.out.println("Still going 2");

		Pattern pattern1 = Pattern.compile(pattern1Builder.toString());
		Pattern pattern2 = Pattern.compile(pattern2Builder.toString());

		// String pattern3String = "gi\\|\\d+\\|([^\\|]+)\\|([^\\|]+)\\|([^\\|]+)$";
		// Pattern pattern3 = Pattern.compile(pattern3String);
		// System.out.println("Still going 3");

		LinkedList<String> baseValues = new LinkedList<String>();
		baseValues.add(level2RunId + "");
		Matcher matcher1 = pattern1.matcher(oneHit);
		if (matcher1.find()) {
			String parsedAccession = null;
			for (int i = 1; i < matcher1.groupCount() + 1; i++) {
				String value = StringEscapeUtils.unescapeXml(matcher1.group(i));
				// The second entry is "Hit_id", but it is always of form "Subject_##"
				// The fourth entry is "Hit_accession", but it is same as "Hit_id"
				// The third entry is "Hit_def", and so we must use this to get the accession_id
				if (i == 3) {
					parsedAccession = value;
				}
				baseValues.add(value);
			}
			// Now, the parsedAccession must be added as the second item (after
			// accession_run_id);
			if (parsedAccession.equals(canonicalAccessionString)) {
				baseValues.add(1, queryAccessionString);
			} else {
				baseValues.add(1, parsedAccession);
			}
		} else {
			return null;
		}
		// System.out.println("Still going 4");

		Matcher matcher2 = pattern2.matcher(oneHit);
		while (matcher2.find()) {
			ArrayList<String> allValues = new ArrayList<String>();
			allValues.addAll(baseValues);
			for (int i = 1; i < matcher2.groupCount() + 1; i++) {
				allValues.add(StringEscapeUtils.unescapeXml(matcher2.group(i)));
			}
			results.add(allValues);
		}
		if (results.get(0).size() == baseValues.size()) {
			return null;
		}
		// System.out.println("Finished processOneLevel2Hit with leve2RunId: " +
		// level2RunId);
		return results;
	}

	public int getLevel2RunId() {
		return level2RunId;
	}

	public void setLevel2RunId(int level2RunId) {
		this.level2RunId = level2RunId;
	}

	public int getLevel2CddId() {
		return level2CddId;
	}

	public void setLevel2CddId(int level2CddId) {
		this.level2CddId = level2CddId;
	}

	public int getLevel2startPosition() {
		return level2startPosition;
	}

	public void setLevel2startPosition(int level2startPosition) {
		this.level2startPosition = level2startPosition;
	}

	public String getCanonicalAccessionString() {
		return canonicalAccessionString;
	}

	public void setCanonicalAccessionString(String canonicalAccessionString) {
		this.canonicalAccessionString = canonicalAccessionString;
	}

	public String getQueryAccessionString() {
		return queryAccessionString;
	}

	public void setQueryAccessionString(String queryAccessionString) {
		this.queryAccessionString = queryAccessionString;
	}

	// public String getTopHitAccessionString() {
	// return topHitAccessionString;
	// }
	//
	// public void setTopHitAccessionString(String topHitAccessionString) {
	// this.topHitAccessionString = topHitAccessionString;
	// }
}
