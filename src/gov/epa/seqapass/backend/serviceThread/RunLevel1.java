package gov.epa.seqapass.backend.serviceThread;

import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.base.Joiner;

public class RunLevel1 {

  private JdbcTemplate jdbcTemplate;
  private NCBIKeeper ncbiKeeper;
  /** The <i>id</i> value in the accession_run table. This used in many tables to identify the Level 1 job. */
  private int accessionRunId = -1;

  /** This is the submitted query accession id */
  private String queryAccessionString = null;
  /** This is the submitted query accession id's taxid */
  private int queryAccessionTaxid = -1;
  // private List<String> identicalsFromQuerySpecies;

  /** This is the canonical accession id */
  private String canonicalAccessionString = null;
  /** This is the canonical accession id's taxid */
  private int canonicalAccessionTaxid = -1;
  private AccessionRun accessionRun;

  private Set<String> queryTaxidAccessionsAboveIdentity = null;

  // private String topHitAccessionString = null;

  private BLASTTools blastTools;
  private BLASTTools2 blastTools2;
  private static Logger logger = LogManager.getLogger(RunLevel1.class);

  /**
   * Constructor for a new Level 1 analysis job. 3 items are passed for access purposes, and the accessionRunId is a
   * parameter
   * 
   * @param blastTools     - passing access to the blastTools object
   * @param template       - passing access to the JdbcTemplate object
   * @param keeper         - passing access to the NCBIKeeper object
   * @param accessionRunId - the newly created accessionRunId
   */
  public RunLevel1(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, AccessionRun accessionRun) {
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
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    if (accessionRunId < 0) {
      logger.error("AccID#" + accessionRunId + ": BLASTp failed because accessionRunId < 0.  queryAccessionString: "
          + queryAccessionString);
//      System.out.println("BLASTp failed because accessionRunId < 0");
      return null;
    }
    // TODO - May want to add a further fail-safe against re-running a job.
    // Get the accession id and confirm that no other run has it.
    // If it does, use its id
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
        logger.error("AccID#" + accessionRunId + ": Can't sleep, even for 0.1 seconds!");
//        System.out.println("Having trouble sleeping!");
      }
    }
    if (rows.size() == 0) {
      logger.error("AccID#" + accessionRunId
          + ": BLASTp failed because we could not get the canonical_accession_id from the accession_run table");
      // System.out.println("BLASTp failed because we could not get the canonical_accession_id from the accession_run
      // table for id: " + accessionRunId);
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
      logger.error("AccID#" + accessionRunId
          + ": BLASTp failed because: value1 == null || ((String) value1).equals(\"\") || value2 == null || ((int) value2) < 0 "
          + value1 + " and " + value2);
      // System.out.println(
      // "BLASTp failed because: value1 == null || ((String) value1).equals(\"\") || value2 == null || ((int) value2) <
      // 0 "
      // + value1 + " and " + value2);
      return null;
    }
    logger.info("AccID#" + accessionRunId + ": No need to set canonicalAccessionString to " + value1
        + " because it is already " + canonicalAccessionString);
//    System.out.println(
//        "No need to set canonicalAccessionString to " + value1 + " because it is already " + canonicalAccessionString);

    // canonicalAccessionString = (String) value1;
    // int ncbi_version_id = (int) value2;
    // FIXME - LET'S NOT ASSUME THAT THE ncbi_version IS THE SAME AS THE
    // preferred
    // ONE!!! PASS ncbi_version_id SOME HOW

    if (!blastTools.createFastaFileIfNecessary(canonicalAccessionString)) {
      logger.error("AccID#" + accessionRunId + ": BLASTp failed because it could not create a Fasta File for: "
          + canonicalAccessionString);
      // System.out.println("BLASTp failed because it could not create a Fasta File for: " + canonicalAccessionString);
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
    String fullPath = blastTools.getFastaPath(canonicalAccessionString);
    if (fullPath == null) {
      logger.error("AccID#" + accessionRunId + ": BLASTp failed because to get the Fasta File path for: "
          + canonicalAccessionString);
      // System.out.println("BLASTp failed because to get the Fasta File path for: " + canonicalAccessionString);
      return null;
    }
    String outputFileName = "output_BLASTp_" + accessionRunId + ".xml";
    String pathToTempOut = ncbiProvider.getPathTempFasta() + "/" + outputFileName;

    ProcessProvider runBLASTp = new ProcessProvider();
    runBLASTp.setUserID(0);
    runBLASTp.setJobID(0);
    runBLASTp.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.findHitProteinsProcess));
    runBLASTp.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    List<String> argVals = new ArrayList<String>();
    argVals.add(fullPath);
    argVals.add(ncbiProvider.getPathNrData());
    argVals.add(3 + "");
    argVals.add(16 + "");
    argVals.add(10 + "");
    argVals.add(20000 + "");
    argVals.add(5 + "");
    argVals.add(pathToTempOut);

    runBLASTp.setArgVals(argVals);
    blastTools2.updateAccessionRunStatus(accessionRunId, "started");
    blastTools.updateBlastpStatus("started", accessionRunId);
    blastTools2.setAccessionRunBlastpWordSize(3, accessionRunId);
    blastTools2.setAccessionRunBlastpThreadCount(16, accessionRunId);
    try {
      String stdErr = runBLASTp.timeRunToFile();
      blastTools.logJobTime("BLASTp", accessionRunId, 1, 1, stdErr);
    } catch (InterruptedException | IOException e) {
      logger.error("AccID#" + accessionRunId + ": BLASTp failed to run Fasta File path for: " + canonicalAccessionString
          + "\nException is " + e);
      // System.out.println("BLASTp failed to run Fasta File path for: " + canonicalAccessionString);
      blastTools2.updateAccessionRunStatus(accessionRunId, "failed");
      return null;
    }
    blastTools.updateBlastpStatus("analyzing", accessionRunId);

    List<String> nameList = blastTools.getBLASTpXMLTypeNames();
    nameList.add(0, "accession_run_id");
    nameList.add(1, "hit_accession_id");
    nameList.add(2, "hit_taxid");
    nameList.add(3, "rps_status");
    nameList.add(4, "rbh_status");
    nameList.add(5, "hit_canonical_id");
    nameList.add(6, "near_class_taxid");

    int hitDefIndex = nameList.indexOf("Hit_def");

    Set<Integer> taxidsSeenSoFar = new HashSet<Integer>();
    setQueryTaxidAccessionsAboveIdentity(new HashSet<String>());
    boolean reachedIdentity = false;
    int queryLength = -1;
    int identityCanonicalIndex = -1;
    int identityQueryIndex = -1;

    // Compile patterns

    String queryLenString = "<BlastOutput_query-len>(\\d+)</BlastOutput_query-len>";
    Pattern queryLenPattern = Pattern.compile(queryLenString);

    String hitPatternString = "<Hit>(.*?)</Hit>";
    Pattern hitPattern = Pattern.compile(hitPatternString);

    StringBuilder patternHitTagsBuilder = new StringBuilder();
    StringBuilder patternHspTagsBuilder = new StringBuilder();

    // Map<Integer, Integer> taxidToClassTaxid = new
    // HashMap<Integer,Integer>();
    for (String name : blastTools.getBlastPxmlTypes().keySet()) {
      if (name.startsWith("Hit_")) {
        patternHitTagsBuilder.append("<" + name + ">(.*?)</" + name + ">.*?");
      } else if (name.startsWith("Hsp_")) {
        patternHspTagsBuilder.append("<" + name + ">(.*?)</" + name + ">.*?");
      }
    }
    Pattern patternHitTags = Pattern.compile(patternHitTagsBuilder.toString());
    Pattern patternHsps = Pattern.compile(patternHspTagsBuilder.toString());

    // FIXME: the use of the Genbank ID (gi) seems to have gone away with updateVersion 5.
    // Therefore the legacy regex below is replaced
    // But may be: gi|223653|prf||0905196B
//		String patternHitIdString = "gi\\|\\d+\\|[^\\|]+\\|([^\\|]+)\\|";

    String patternHitIdString = "gi\\|\\d+\\|[^\\|]+\\|{1,2}([^\\|]+)\\|";
    Pattern patternHitIdParse = Pattern.compile(patternHitIdString);

//		String patternHitDefString = ">gi\\|\\d+\\|([^\\|]+)\\|([^\\|]+)\\|([A-Za-z0-9]*)";
    String patternHitDefString = ">gi\\|\\d+\\|([^\\|]+)\\|{1,2}([^\\s\\|]+)\\s*\\|*([A-Za-z0-9]*)";
    Pattern patternHitDefParse = Pattern.compile(patternHitDefString);

    int updateVersion = ncbiProvider.getUpdateVersion();
    logger.debug("AccID#" + accessionRunId + ": NCBI update version = " + updateVersion);
    // System.out.println("NCBI update version = " + updateVersion);
    if (updateVersion >= 5) {
      // Some examples in updateVersion 5 are:
      // <Hit_id>gb|AAD52984.1|</Hit_id>
      // <Hit_id>dbj|BAL03259.1|</Hit_id>
      // <Hit_id>ref|XP_006715438.1|</Hit_id>
      // <Hit_id>pdb|1XAP|A</Hit_id>
      // <Hit_id>pdb|4JYG|A</Hit_id>
      // <Hit_id>pir|A37197|</Hit_id>
      // <Hit_id>prf||2120366D</Hit_id> <== note the two pipes!
      patternHitIdString = "(.+)"; // Capture the whole string now, the BLASTTools.parseAccession method handles all of
                                   // these now.
      patternHitIdParse = Pattern.compile(patternHitIdString);
//
//			Some examples in updateVersion 5 are:
//				... >pdb|5DI7|B Chain B
//			 	... >ref|XP_003255940.1| estrogen recepto
//				... >prf||1516344B ret
//			String patternHitDefString = ">gi\\|\\d+\\|([^\\|]+)\\|([^\\|]+)\\|([A-Za-z0-9]*)";
      patternHitDefString = ">([^>]+)";// Capture the whole fragment now, the BLASTTools.parseAccession method handles
                                       // all of these now.
      patternHitDefParse = Pattern.compile(patternHitDefString);
    }
    List<ArrayList<String>> hitRows = new ArrayList<ArrayList<String>>();
    logger.info("AccID#" + accessionRunId + ": Parsing results");

    try {
      Stream<String> stream = Files.lines(Paths.get(pathToTempOut));
      StringBuilder b = new StringBuilder();
      // int lineNumber = 0;
      for (String line : (Iterable<String>) stream::iterator) {
        // lineNumber++;
        // System.out.println("Line number: " + lineNumber);
//				System.out.println("beginning for loop for line" + line.substring(0,15));
        if (queryLength < 0 && line.matches("BlastOutput_query-len")) {
          Matcher queryLenMatcher = queryLenPattern.matcher(line);
          queryLength = Integer.parseInt(queryLenMatcher.group(1));
          logger.debug("AccID#" + accessionRunId + ": Got the query protein's length: " + queryLength);
          // System.out.println("Got the query protein's length: " + queryLength);
        }

        b.append(line);
        if (line.matches("</Hit>")) { // The end of a hit, time to
          // process
          String hitChunk = b.toString();
          b.setLength(0);

          Matcher hitMatcher = hitPattern.matcher(hitChunk);
          if (hitMatcher.find()) {
            String oneHit = hitMatcher.group(1);
            Matcher matcherOfHitTags = patternHitTags.matcher(oneHit);
            ArrayList<String> baseValues = new ArrayList<String>();
            baseValues.add(accessionRunId + ""); // Casts the int to a string
            List<String> dupAccessions = new ArrayList<String>();
            Map<String, Integer> dupPairs = new HashMap<String, Integer>();

            if (matcherOfHitTags.find()) {
              String parsedAccession = null;
              for (int i = 1; i < matcherOfHitTags.groupCount() + 1; i++) {
                String value = StringEscapeUtils.unescapeXml(matcherOfHitTags.group(i));
                // i ==2 "Hit_id"
                if (i == 2) {
                  if (updateVersion >= 5) {
                    parsedAccession = blastTools.parseAccession(value);
                    if (parsedAccession == null) {
                      logger.error(
                          "AccID#" + accessionRunId + ": ERROR! value: " + value + " fails to return an accession id!");
                      // System.out.println("ERROR! value: " + value + " fails to return an accession id!");
                    }
                  } else {
                    Matcher matcherHitId = patternHitIdParse.matcher(value);
                    if (matcherHitId.find()) {
                      String prefix = matcherHitId.group(1);
                      if (prefix.equals("prf||")) {
                        parsedAccession = prefix + matcherHitId.group(2);
                      } else if (prefix.equals("pir|")) {
                        parsedAccession = prefix + matcherHitId.group(2) + "|";
                      } else if (prefix.equals("pdb|")) {
                        parsedAccession = matcherHitId.group(2) + "_" + matcherHitId.group(3);
                      } else {
                        parsedAccession = matcherHitId.group(2);
                      }
                    }
                  }
                }

                // i == 3 "Hit_def" Look for additional (identical) proteins, perhaps from other species
                if (i == 3) {
                  // String unescaped =
                  // StringEscapeUtils.unescapeXml(value);
                  // System.out.println("Parsing: " +
                  // unescaped);

                  Matcher matcherHitDef = patternHitDefParse.matcher(value);
                  while (matcherHitDef.find()) {
                    if (updateVersion >= 5) {
                      String possibleDupAccession = blastTools.parseAccession(matcherHitDef.group(1));
                      if (possibleDupAccession != null) {
                        dupAccessions.add(possibleDupAccession);
                      }
                    } else {
                      String dbType = matcherHitDef.group(1);
                      String dupAccessionBase = matcherHitDef.group(2);

                      if (dbType.equals("pdb|")) {
                        dupAccessions.add(dupAccessionBase + "_" + matcherHitDef.group(3));
                      } else if (dbType.equals("pir|")) {
                        dupAccessions.add(dbType + dupAccessionBase + "|");
                      } else if (dbType.equals("prf||")) {
                        dupAccessions.add(dbType + dupAccessionBase);
                      } else {
                        dupAccessions.add(dupAccessionBase);
                      }
                    }
                  }
                }

                // i == 4 "Hit_accession". It doesn't have the suffix, but has the full PDB code (including _)
                // This should not be needed any more
                if (i == 4 && updateVersion < 5) {
                  if (parsedAccession == null || parsedAccession.length() < value.length()) {
                    parsedAccession = value;
                  }
                }
                baseValues.add(value);
              }

              // Now, the parsedAccession must be added as the second item (after accession_run_id)
              baseValues.add(1, parsedAccession);
              baseValues.add(2, "-1"); // place holder for hit_taxid
              baseValues.add(3, "queued");
              if (parsedAccession.equals(queryAccessionString)) {
                baseValues.add(4, "Y"); // RBH status is yes for identity
              } else {
                baseValues.add(4, "queued");
              }
              baseValues.add(5, parsedAccession);
              baseValues.add(6, "-1");

              // Now find all distinct species for this / these accession_ids
              dupAccessions.add(0, parsedAccession);
              query = "SELECT accession_id, taxid FROM protein WHERE ncbi_valid_versions|"
                  + Math.pow((updateVersion - 1), 2) + " and accession_id IN ('" + Joiner.on("','").join(dupAccessions)
                  + "')";
              rows = jdbcTemplate.queryForList(query);
              for (Map<String, Object> row : rows) {
                String accessionId = (String) row.get("accession_id");
                int taxid = (int) row.get("taxid");
                dupPairs.put(accessionId, taxid);
                if (accessionId.equals(canonicalAccessionString) && canonicalAccessionTaxid < 0) {
                  canonicalAccessionTaxid = taxid;
                  identityCanonicalIndex = hitRows.size();
                }
                if (accessionId.equals(queryAccessionString)) {
                  queryAccessionTaxid = taxid;
                }
              }
              /*
               * FIXME - The following is designed to catch strings parsed from xml output which do not appear as
               * accessions in the protein database. The intent was to remove them from the output
               */
              if (rows.size() < dupAccessions.size()) {
                logger.warn("AccID#" + accessionRunId + ": rows.size()= " + rows.size() + " AND dupAccessions.size()= "
                    + dupAccessions.size());
//                System.out
//                    .println("rows.size()= " + rows.size() + " AND dupAccessions.size()= " + dupAccessions.size());
                for (String dupAccession : dupAccessions) {
                  if (!dupPairs.containsKey(dupAccession)) {
//										dupAccessions.remove(dupAccession);
                    logger.warn("AccID#" + accessionRunId + ": dupAccession WITH NO taxid : " + dupAccession);
                    // System.out.println("dupAccession WITH NO taxid : " + dupAccession);
                  }
                }
              }
            } else {
              logger.warn("AccID#" + accessionRunId + ": patternHitTags.matcher Failed to get a base value");
              // System.out.println("Failed to get a base value");
              stream.close();
              return null;
            }

            Matcher matcherHsps = patternHsps.matcher(oneHit);
            if (matcherHsps.find()) { // WE CHANGED IT (see below)

//						while (matcherHsps.find()) { // May change this to only
              // take the first one by
              // making it an "if"
              // System.out.println("Got an Hsp value");
              ArrayList<String> hitRowTemplate = new ArrayList<String>();
              hitRowTemplate.addAll(baseValues);
              for (int i = 1; i < matcherHsps.groupCount() + 1; i++) {
                hitRowTemplate.add(StringEscapeUtils.unescapeXml(matcherHsps.group(i)));
              }
              // This list is ready to add; now duplicate for each
              // additional (identical)
              // accession from another species
              // Set<Long> taxidsFound = new HashSet<Long>(); //
              // only for old protein schema
              // Set<Integer> taxidsFound = new
              // HashSet<Integer>();
              boolean firstRow = true;
              // UNCOMMENT THREE LINES BELOW TO STICK TO ORIGINAL
              // PARSING
              // while (dupAccessions.size() > 1){
              // dupAccessions.remove(1);
              // }
              // UNCOMMENT THREE LINES ABOVE TO STICK TO ORIGINAL
              // PARSING

              for (String dupAccession : dupAccessions) {
                if (!reachedIdentity || !taxidsSeenSoFar.contains(dupPairs.get(dupAccession))) {
                  if (reachedIdentity) {
                    taxidsSeenSoFar.add(dupPairs.get(dupAccession));
                  }
                  // }
                  // if
                  // (!taxidsSeenSoFar.contains(dupPairs.get(dupAccession)))
                  // { // KEEP

                  @SuppressWarnings("unchecked")
                  ArrayList<String> hitRowToAdd = (ArrayList<String>) hitRowTemplate.clone();
                  hitRowToAdd.set(1, dupAccession);
                  hitRowToAdd.set(2, dupPairs.get(dupAccession).toString());
                  if (firstRow) {
                    firstRow = false;
                  } else {
                    hitRowToAdd.set(hitDefIndex, ""); // SAVES
                    // A
                    // LOT
                    // OF
                    // SPACE,
                    // MAY
                    // NOT
                    // NEED
                    // THE
                    // FIELD
                    // AT
                    // ALL
                    hitRowToAdd.set(3, "not run");
                    hitRowToAdd.set(4, "not run");
                  }
                  hitRowToAdd.set(2, dupPairs.get(dupAccession).toString());
                  hitRowToAdd.set(5, dupAccessions.get(0));
                  Integer nearClassTaxid = (Integer) jdbcTemplate.queryForObject("SELECT TAXID_at_rank_for_taxid(?,?);",
                      Integer.class, "class", dupPairs.get(dupAccession));
                  hitRowToAdd.set(6, nearClassTaxid.toString());
                  if (dupAccession.equals(queryAccessionString)) {
                    identityQueryIndex = hitRows.size();
                    hitRowToAdd.set(3, "queued");
                    hitRowToAdd.set(4, "Y");
                  }
                  if (!reachedIdentity) {
                    logger.debug("AccID#" + accessionRunId + ": Hit above identity: " + hitRowToAdd);
//										System.out.println("Row: " + dupAccession + " : " + dupPairs.get(dupAccession));
//
//										System.out.println("      " + hitRows.size() + ". canonicalAccessionTaxid: "
//												+ canonicalAccessionTaxid + " -> queryAccessionTaxid: "
//												+ queryAccessionTaxid);
//										System.out.println("      " + hitRows.size() + ". identityCanonicalIndex: "
//												+ identityCanonicalIndex + " -> identityQueryIndex: "
//												+ identityQueryIndex);
                  }
                  // System.out.println("adding: " +
                  // hitRowToAdd.get(1) + " to row: " +
                  // hitRows.size());
                  hitRows.add(hitRowToAdd);
                }
              }

              if (!reachedIdentity && queryAccessionTaxid > -1 && canonicalAccessionTaxid > -1
                  && identityCanonicalIndex > -1 && identityQueryIndex > -1) {
                logger.debug("AccID#" + accessionRunId + ": Reached identity and parsed all duplicates at row: "
                    + hitRows.size());
                // System.out.println("Reached identity and parsed all duplicates at row: " + hitRows.size());

                List<Integer> rowsToRemove = new ArrayList<Integer>();
                taxidsSeenSoFar.add(queryAccessionTaxid);
                reachedIdentity = true;

                int identityStartPoint = identityCanonicalIndex;
                if (queryAccessionTaxid == canonicalAccessionTaxid
                    && !(queryAccessionString.equals(canonicalAccessionString))) {
                  // Unusual case in which we need to remove
                  // canonical row, but tag accession row
                  // to "queued" for RBH and RPS
                  logger.info("AccID#" + accessionRunId + ": *special* flagging for removal row "
                      + identityCanonicalIndex + " for accession: " + canonicalAccessionString + " because taxid: "
                      + canonicalAccessionTaxid + " is same as (non-canonical) query accession");
//                  System.out.println("*special* flagging for removal row " + identityCanonicalIndex + " for accession: "
//                      + canonicalAccessionString + " because taxid: " + canonicalAccessionTaxid
//                      + " is same as (non-canonical) query accession");

                  rowsToRemove.add(identityCanonicalIndex);
                  // hitRows.get(identityQueryIndex).set(3,
                  // "queued");
                  // hitRows.get(identityQueryIndex).set(4,
                  // "queued");
                  // hitRows.get(identityQueryIndex).set(hitDefIndex,
                  // hitRows.get(identityCanonicalIndex).get(hitDefIndex));

                  identityStartPoint++;
                } else {
                  taxidsSeenSoFar.add(canonicalAccessionTaxid);
                }
                // String lastHitDef = null;
                /*
                 * First go through and note the taxids in the identity sequence
                 */
                for (int i = identityStartPoint; i < hitRows.size(); i++) {
                  ArrayList<String> hitRowToConsider = hitRows.get(i);
                  String queryAccession = hitRowToConsider.get(1);
                  Integer taxid = Integer.parseInt(hitRowToConsider.get(2));

                  if (taxidsSeenSoFar.contains(taxid) && !queryAccession.equals(queryAccessionString)
                      && !queryAccession.equals(canonicalAccessionString)) {
                    rowsToRemove.add(i);
                    logger.info("AccID#" + accessionRunId + ": Flagging for removal row " + i + " for accession: "
                        + queryAccession + " because taxid: " + taxid + " is already found within the identity set");
//                    System.out.println("Flagging for removal row " + i + " for accession: " + queryAccession
//                        + " because taxid: " + taxid + " is already found within the identity set");
                    // if (taxid == queryAccessionTaxid) {
                    // queryTaxidAccessionsAboveIdentity.add(queryAccession);
                    // }
                  } else {
                    taxidsSeenSoFar.add(taxid);
                  }
                }
                /*
                 * Now go through rows with bitscore higher than that of identity and remove rows with duplicate taxids
                 */

                for (int i = 0; i < identityCanonicalIndex; i++) {
                  ArrayList<String> hitRowToConsider = hitRows.get(i);
                  String queryAccession = hitRowToConsider.get(1);
                  Integer taxid = Integer.parseInt(hitRowToConsider.get(2));
                  if (taxidsSeenSoFar.contains(taxid)) {
                    rowsToRemove.add(i);
                    logger.info("AccID#" + accessionRunId + ": Flagging for removal row " + i + " for accession: "
                        + queryAccession + " because taxid: " + taxid + " is already found");
//                    System.out.println("Flagging for removal row " + i + " for accession: " + queryAccession
//                        + " because taxid: " + taxid + " is already found");
                    if (taxid == queryAccessionTaxid) {
                      queryTaxidAccessionsAboveIdentity.add(queryAccession);
                    }
                  } else {
                    taxidsSeenSoFar.add(taxid);
                  }
                }
                for (int i = hitRows.size() - 1; i >= 0; i--) {
                  if (rowsToRemove.contains(i)) {
                    if (i < hitRows.size() - 1) {
                      ArrayList<String> hitRowToRemove = hitRows.get(i);
                      ArrayList<String> nextHitRowInList = hitRows.get(i + 1);
                      String queryToRemove = hitRowToRemove.get(1);
                      String canonicalToRemove = hitRowToRemove.get(5);
                      String nextQuery = nextHitRowInList.get(1);
                      String nextCanonical = nextHitRowInList.get(5);

                      if (queryToRemove.equals(canonicalToRemove) && queryToRemove.equals(nextCanonical)) {
                        nextHitRowInList.set(3, "queued");
                        if (nextQuery.equals(queryAccessionString)) {
                          nextHitRowInList.set(4, "Y");

                        } else {
                          nextHitRowInList.set(4, "queued");
                        }
                        nextHitRowInList.set(hitDefIndex, hitRowToRemove.get(hitDefIndex));
                      }
                    }

                    hitRows.remove(i);
                    logger.info("AccID#" + accessionRunId + ": Actually removed row: " + i);
                    // System.out.println("Actually removed row: " + i);
                  }
                }
              }
            }
          }
          if (hitRows.size() > 800) { // SPLIT INTO MANAGEABLE CHUNKS.
//						System.out.println("hitRows has "+ hitRows.size() + " at line 616");
            if (!reachedIdentity) {
              logger.warn("AccID#" + accessionRunId
                  + ": Whoa!  How did we get to 800 rows without getting to the identical accession id to the one submitted?!?");
//              System.out.println(
//                  "Whoa!  How did we get to 800 rows without getting to the identical accession id to the one submitted?!?");
            }
            int numerOfBatches = 1 + (hitRows.size() / 500);
            for (int batch = 0; batch < numerOfBatches; batch++) {
              int toIndex = Math.min((batch + 1) * 500, hitRows.size());
              List<ArrayList<String>> subList = hitRows.subList(batch * 500, toIndex);
              if (subList.size() > 0) {
//								System.out.println("subList has "+ subList.size() + " at line 626");

                if (batch == 0) {
//									System.out.println("nameList: " + nameList);
//									System.out.println("subList: " + subList);
                }
//								System.out.println("hitRows has "+ hitRows.size() + " at line 632");
                blastTools.saveAccessionHitInOneBatch(nameList, subList);
              }
            }
            hitRows.clear();
          } else if (hitRows.size() > 400) {
//						System.out.println("hitRows has "+ hitRows.size() + " at line 638");
//						System.out.println("nameList: " + nameList);
//						System.out.println("subList: " + hitRows);

            blastTools.saveAccessionHitInOneBatch(nameList, hitRows);
            logger.info("AccID#" + accessionRunId + ": Finished saveAccessionHitinOneBatch");
            // System.out.println("Finished saveAccessionHitinOneBatch L642");
            hitRows.clear();
            logger.debug("AccID#" + accessionRunId + ": Finished hitRows.clear");
            // System.out.println("Finished hitRows.clear");
          }

        }
//				System.out.println("bottom of for loop");
      }
      logger.debug("AccID#" + accessionRunId + ": after for loop");
      // System.out.println("after for loop");
      if (hitRows.size() > 0) {
//			  System.out.println("hitRows has "+ hitRows.size() + " at line 648");
//			System.out.println("nameList: " + nameList);
//			System.out.println("subList: " + hitRows);

        blastTools.saveAccessionHitInOneBatch(nameList, hitRows);
        // Don't forget to save the last few!
      }
      stream.close();

    } catch (

    IOException e) {
      blastTools2.updateAccessionRunStatus(accessionRunId, "failed");
      logger.error("AccID#" + accessionRunId + ": Could not open output file and get results\nException info: " + e);
      // System.out.println("Files.lines tool failed");
      // e.printStackTrace();
      return null;
    }
    logger.info("AccID#" + accessionRunId + ": Finished parsing results. Hit count: " + hitRows.size());
    // System.out.println("try / catch finished at line 702, hitRows has " + hitRows.size() + " at line 683");

    if (deleteFile(pathToTempOut)) {
      logger.debug("AccID#" + accessionRunId + ": Deleted BLASTp temp file: " + pathToTempOut);
      // System.out.println("Deleted BLASTp temp file: " + pathToTempOut);
    }
    accessionRun.setQueryTaxidAccessionsAboveIdentity(queryTaxidAccessionsAboveIdentity);

//		System.out.println("Debug 1");

    // int allHits = blastTools.setBLASTpHitTaxids(accessionRunId);
    // int hitCount = blastTools.getHitCount(accessionRunId);

    query = "SELECT COUNT(*) FROM accession_hit WHERE accession_run_id = ?";
    int hitCount = jdbcTemplate.queryForObject(query, int.class, accessionRunId);
//		System.out.println("Debug 2 with: " + hitCount);

    if (hitCount == 0) {
      logger.warn("AccID#" + accessionRunId + ": No hits!");

      // System.out.println("No hits!");
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
      logger.warn("AccID#" + accessionRunId + ": Hit count < 3: " + hitCount);
      // There was only one hit, so there is no possible cutoff plot and it makes no sense to continue
      // System.out.println("Hit count = " + hitCount);

      blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
//			System.out.println("Debug a");

      blastTools.updateBlastpStatus("too few hits", accessionRunId);
//			System.out.println("Debug b");

      blastTools2.setAccessionRunCompletion(accessionRunId);
//			System.out.println("Debug c");

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
    blastTools.addRBHRows(accessionRunId);
    blastTools.updateAccessionHitRbhStatus(accessionRunId);

    logger.info("AccID#" + accessionRunId + ": Now getRBHnRPSinfo");

    // System.out.println("Now getRBHnRPSinfo... ");
    // Map<String, List> results = blastTools.getRBHnRPSinfo(accessionRunId,
    // ncbi_version_id);
    Map<String, List> results = blastTools.getRBHnRPSinfoFull(accessionRunId);
    // System.out.println("... with row count: " + results.size()); // results.size() is always 4. Did we mean hitCount?
    logger.info("AccID#" + accessionRunId + ": RPS count: " + results.get("rpsAccs").size());
    logger.info("AccID#" + accessionRunId + ": RBH count: " + results.get("rbhAccs").size());

    blastTools.updateBlastpStatus("complete", accessionRunId);
    return results;
  }

  private boolean deleteFile(String fullPath) {
    try {
      File listFile = new File(fullPath);
      if (listFile.delete()) {
        return true;
      }
    } catch (Exception e) {
      // System.out.println("Failed to delete file with message: " + e.getMessage());
      logger.error("Failed to delete file with message: {}", e.getMessage());
    }
    // System.out.println("Failed to delete file: " + fullPath);
    logger.error("Failed to delete file: {}", fullPath);

    return false;
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
