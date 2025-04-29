package gov.epa.seqapass.backend.serviceThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Joiner;
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;

import gov.epa.seqapass.backend.dao.ReportService;
//import gov.epa.seqapass.backend.dao.RService;
import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessProvider;
import gov.epa.seqapass.backend.externalProcess.ProcessTypeKeeper;
import gov.epa.seqapass.backend.hpcAccess.SBatchObject;
//import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelTwoRequestableRow;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@EnableRetry
public class BLASTTools {

  private static Logger logger = LogManager.getLogger(BLASTTools.class);

  public BLASTTools(JdbcTemplate jdbcTemplate, NCBIKeeper ncbiKeeper, BLASTTools2 blastTools2) {
    init();
    this.jdbcTemplate = jdbcTemplate;
    this.ncbiKeeper = ncbiKeeper;
    this.blastTools2 = blastTools2;
  }

  private JdbcTemplate jdbcTemplate;
  private NCBIKeeper ncbiKeeper;
  private BLASTTools2 blastTools2;

  public BLASTTools2 getBlastTools2() {
    return blastTools2;
  }

  public void setBlastTools2(BLASTTools2 blastTools2) {
    this.blastTools2 = blastTools2;
  }

  // private BLASTTools2Injected instance = null;
  /**
   * This LinkedHashMap lists the xml data types important in the output of BLASTp in order. The value of each key is a
   * string representing the datatype (in Java) for each tag.
   */
  private final Map<String, String> blastPxmlTypes = new LinkedHashMap<String, String>();

  public Map<String, String> getBlastPxmlTypes() {
    return blastPxmlTypes;
  }

  /**
   * This LinkedHashMap lists the xml data types important in the output of rpsBLAST in order. The value of each key is
   * a string representing the datatype (in Java) for each tag.
   */
  private final Map<String, String> rpsBLASTxmlTypes = new LinkedHashMap<String, String>();

  /**
   * This LinkedHashMap lists the xml data types important in the output of rbh BLAST (BLASTp with two inputs) in order.
   * The value of each key is a string representing the datatype (in Java) for each tag.
   */
  private final Map<String, String> rbhBLASTxmlTypes = new LinkedHashMap<String, String>();

  private void init() {
    // System.out.println("!!!!!!!!! BLASTtools is getting initiallized !!!!!!!!!");
    blastPxmlTypes.put("Hit_num", "int");
    blastPxmlTypes.put("Hit_id", "String");
    blastPxmlTypes.put("Hit_def", "String");
    blastPxmlTypes.put("Hit_accession", "String");
    blastPxmlTypes.put("Hit_len", "int");
    // NOTE: Each "Hit" may have one or more "Hit_hsps" each of which is an
    // "Hsp" with the following:
    blastPxmlTypes.put("Hsp_num", "int");
    blastPxmlTypes.put("Hsp_bit-score", "double");
    blastPxmlTypes.put("Hsp_score", "int");
    blastPxmlTypes.put("Hsp_evalue", "double");
    blastPxmlTypes.put("Hsp_query-from", "int");
    blastPxmlTypes.put("Hsp_query-to", "int");
    blastPxmlTypes.put("Hsp_hit-from", "int");
    blastPxmlTypes.put("Hsp_hit-to", "int");
    blastPxmlTypes.put("Hsp_query-frame", "int");
    blastPxmlTypes.put("Hsp_hit-frame", "int");
    blastPxmlTypes.put("Hsp_identity", "int");
    blastPxmlTypes.put("Hsp_positive", "int");
    blastPxmlTypes.put("Hsp_gaps", "int");
    blastPxmlTypes.put("Hsp_align-len", "int");
    blastPxmlTypes.put("Hsp_qseq", "String");
    blastPxmlTypes.put("Hsp_hseq", "String");
    blastPxmlTypes.put("Hsp_midline", "String");

    // BLASTp results
    rpsBLASTxmlTypes.put("Hit_num", "int");
    // NOTE:
    rpsBLASTxmlTypes.put("Hsp_num", "int");

    // rbhBLAST results
    rbhBLASTxmlTypes.put("Hit_num", "int");
    rbhBLASTxmlTypes.put("Hit_id", "String");
    rbhBLASTxmlTypes.put("Hit_def", "String");
    rbhBLASTxmlTypes.put("Hit_accession", "String");
    rbhBLASTxmlTypes.put("Hit_len", "int");
    // NOTE: Each "Hit" may have one or more "Hit_hsps" each of which is an
    // "Hsp" with the following:
    rbhBLASTxmlTypes.put("Hsp_num", "int");
    rbhBLASTxmlTypes.put("Hsp_bit-score", "double");
    rbhBLASTxmlTypes.put("Hsp_score", "int");
    rbhBLASTxmlTypes.put("Hsp_evalue", "double");
    rbhBLASTxmlTypes.put("Hsp_query-from", "int");
    rbhBLASTxmlTypes.put("Hsp_query-to", "int");
    rbhBLASTxmlTypes.put("Hsp_hit-from", "int");
    rbhBLASTxmlTypes.put("Hsp_hit-to", "int");
    rbhBLASTxmlTypes.put("Hsp_query-frame", "int");
    rbhBLASTxmlTypes.put("Hsp_hit-frame", "int");
    rbhBLASTxmlTypes.put("Hsp_identity", "int");
    rbhBLASTxmlTypes.put("Hsp_positive", "int");
    rbhBLASTxmlTypes.put("Hsp_gaps", "int");
    rbhBLASTxmlTypes.put("Hsp_align-len", "int");
    rbhBLASTxmlTypes.put("Hsp_qseq", "String");
    rbhBLASTxmlTypes.put("Hsp_hseq", "String");
    rbhBLASTxmlTypes.put("Hsp_midline", "String");
  }

  public List<String> getBLASTpXMLTypeNames() {
    List<String> result = new ArrayList<String>();
    for (String name : blastPxmlTypes.keySet()) {
      result.add(name);
    }
    return result;
  }

  public LinkedList<String> getRbhBLASTxmlTypeNames() {
    LinkedList<String> result = new LinkedList<String>();
    for (String name : rbhBLASTxmlTypes.keySet()) {
      result.add(name);
    }
    return result;
  }

  @SuppressWarnings("unused")
  private void INITIALIZATION_SPECIFIC_METHODS() { // ======================================================
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class,
      RuntimeException.class }, backoff = @Backoff(delay = 200, maxDelay = 2000, multiplier = 1.1, random = true))
  public void logJobTime(final String jobType, final int accessionRunId, final int jobFragmentNumber,
      final int componentCount, final String stdErr) {
    //System.out.println("Attempting method: logJobTime with accessionRunId = " + accessionRunId);
    logger.debug("Attempting method: logJobTime with accessionRunId = {}", accessionRunId);
    Pattern timeMessage = Pattern
        .compile("^(.*?)([0-9]+\\.[0-9]{2})user\\s*(.*?)system\\s*(.*?)elapsed\\s*(.*?)%CPU (.*)$");
    // 1503.66user 41.95system 1:47.65elapsed 1435%CPU (0avgtext+0avgdata 103057476maxresident)k0inputs+61944outputs
    // (0major+27022322minor)pagefaults 0swaps

    Matcher matcher = timeMessage.matcher(stdErr);
    if (matcher.find()) {
      String preTime = matcher.group(1);
      double userTime = Double.parseDouble(matcher.group(2));
      double systemTime = Double.parseDouble(matcher.group(3));
      String elapsedTime = matcher.group(4);
      double percentCPU = Double.parseDouble(matcher.group(5));
      String otherInfo = matcher.group(6);
      StringBuilder b = new StringBuilder();
      b.append("INSERT INTO job_stats ");
      b.append(
          "(job_type, accession_run_id, job_fragement_number, component_count, user_time_seconds, system_time_seconds, elapsed_time, percent_cpu, other_stderr_info, failure_notes) ");
      b.append("VALUES (?, ?, ?, ?, ?, ? ,?, ?, ?, ?)");
      String updateQuery = b.toString();
      jdbcTemplate.update(updateQuery, jobType, accessionRunId, jobFragmentNumber, componentCount, userTime, systemTime,
          elapsedTime, percentCPU, otherInfo, preTime);
    } else {
      //System.out.println("Std error didn't parse as expected.  Here's the string:\n" + stdErr);
      logger.warn("Std error didn't parse as expected.  Here's the string: {} \n", stdErr);
      StringBuilder b = new StringBuilder();
      b.append("INSERT INTO job_stats ");
      b.append("(job_type, accession_run_id, job_fragement_number, component_count, failure_notes) ");
      b.append("VALUES (?, ?, ?, ?, ?)");
      String updateQuery = b.toString();
      jdbcTemplate.update(updateQuery, jobType, accessionRunId, jobFragmentNumber, componentCount, stdErr);
    }
    //System.out.println("Completed method: logJobTime with accessionRunId = " + accessionRunId);
    logger.debug("Completed method: logJobTime with accessionRunId = {}", accessionRunId);
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class,
      RuntimeException.class }, backoff = @Backoff(delay = 200, maxDelay = 2000, multiplier = 1.1, random = true))
  public void logJobStats(final String jobType, final int accessionRunId, final int jobFragmentNumber,
      final int numOfProteinsInBatch, final double userTime, final String elapsedTime, final double percentCPU,
      final String otherInfo, final String failureNotes) {

    //System.out.println("Attempting method: logJobTime with accessionRunId = " + accessionRunId);
    logger.debug("Attempting method: logJobTime with accessionRunId = {}", accessionRunId);
    StringBuilder b = new StringBuilder();
    b.append("INSERT INTO job_stats ");
    b.append(
        "(job_type, accession_run_id, job_fragement_number, component_count, user_time_seconds, elapsed_time, percent_cpu, other_stderr_info, failure_notes) ");
    b.append("VALUES (?, ?, ?, ?, ?, ? ,?, ?, ?)");
    String updateQuery = b.toString();
    jdbcTemplate.update(updateQuery, jobType, accessionRunId, jobFragmentNumber, numOfProteinsInBatch, userTime,
        elapsedTime, percentCPU, otherInfo, failureNotes);

    //System.out.println("Completed method: logJobTime with accessionRunId = " + accessionRunId);
    logger.debug("Completed method: logJobTime with accessionRunId = {}", accessionRunId);
  }

  public double calculateMaxBitScore(String accession) {
    if (!createFastaFileIfNecessary(accession)) {
      return -1;
    }
    String fullPath = getFastaPath(accession);
    ProcessProvider selfAlign = new ProcessProvider();
    selfAlign.setUserID(0);
    selfAlign.setJobID(0);
    selfAlign.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.selfAlignProcess));
    selfAlign.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    List<String> argVals = new ArrayList<String>();
    argVals.add(fullPath);
    argVals.add(fullPath);
    argVals.add(20000 + "");
    argVals.add(5 + "");
    selfAlign.setArgVals(argVals);
    double maxBitScore = -1;
    try {
      String[] runResults = selfAlign.timeRun();
      String stdOut = runResults[0];
      String pattern = "<Hsp_bit-score>([0-9.]*)<\\/Hsp_bit-score>";
      Pattern bitScorePattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
      Matcher matcher = bitScorePattern.matcher(stdOut);
      if (matcher.find()) {
        String value = matcher.group(1);
        maxBitScore = Double.parseDouble(value);
      }
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return maxBitScore;
    }
    return maxBitScore;
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void setMaxBitScore(int accessionRunId, double maxBitScore) {
    String updateQuery = "UPDATE accession_run SET max_bit_score = ? WHERE id = ?";
    jdbcTemplate.update(updateQuery, maxBitScore, accessionRunId);
  }

  /**
   * This method looks up the proper current accession id (with current version if available) for a given accession id
   * (with incorrect or missing version). It can be used to validate the presence of an accession id by returning null
   * if not present.
   * 
   * @param accession id with correct, incorrect, or missing version number
   * @return The corresponding accession with corrected version if needed or null if not found
   */
  public String getCorrectAccessionFromAccessionIdName(String accessionIdName) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    int ncbiVersion = ncbiProvider.getUpdateVersion();
    if (accessionIdName.trim().equals("")) {
      return null;
    }
    try {
      return jdbcTemplate.queryForObject(
          "SELECT accession_id FROM protein WHERE accession_id = ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
          String.class, accessionIdName, ncbiVersion);
    } catch (DataAccessException e) {
      // ONE MORE POSSIBILITY (STARTS WITH)
    }
    try {
      String versionlessAccessionIdName = accessionIdName.replaceFirst("\\.\\d+$", "");
      List<Map<String, Object>> hits = jdbcTemplate.queryForList(
          "SELECT accession_id FROM protein WHERE accession_id LIKE ? AND FIND_IN_SET(?, ncbi_valid_versions)",
          versionlessAccessionIdName + "%", ncbiVersion);
      for (Map<String, Object> item : hits) {
        String hit = (String) item.get("accession_id");
        //System.out.println("hit= " + hit);
        logger.info("hit = {}", hit);
        if (hit.matches("^" + versionlessAccessionIdName + ".\\d+$")) {
          return hit;
        }
      }
      return null;
    } catch (DataAccessException e) {
      return null;
    }
  }

  /**
   * This method looks up the taxid for a given accession id. It can be used to validate the presence of an accession id
   * by returning -1 if not present.
   * 
   * @param accessionIdName
   * @return the taxid of the protein or -1 if not found
   */
  public int getTaxidFromAccessionIdName(String accessionIdName) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    int ncbiVersion = ncbiProvider.getUpdateVersion();

    try {
      return jdbcTemplate.queryForObject(
          "SELECT taxid FROM protein WHERE accession_id = ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1", int.class,
          accessionIdName, ncbiVersion);
    } catch (DataAccessException e) {
      // ONE MORE POSSIBILITY (STARTS WITH)
    }
    try {
      String versionlessAccessionIdName = accessionIdName.replaceFirst("\\.\\d+$", "");
      return jdbcTemplate.queryForObject(
          "SELECT taxid FROM protein WHERE accession_id LIKE ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
          int.class, versionlessAccessionIdName + "%", ncbiVersion);
    } catch (DataAccessException e) {
      return -1;
    }
  }
  
  
  /**
   * This method looks up the taxid and title for a given accession id. It can be used to validate the presence of an accession id
   * by returning -1 if not present.
   * 
   * @param accessionIdName
   * @return the taxid (or -1 if not found) and title (or empty string if not found) of the protein 
   */
  public List<Object> getTaxidAndTitleFromAccessionIdName(String accessionIdName) {
	int taxId = -1;
	String title = "";
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    int ncbiVersion = ncbiProvider.getUpdateVersion();

    try {
    	Map<String, Object> data = jdbcTemplate.queryForMap(
          "SELECT taxid, title FROM protein WHERE accession_id = ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
          accessionIdName, ncbiVersion);
    	taxId = (int) data.get("taxid");
    	title = (String) data.get("title");
    } catch (DataAccessException e) {
      // ONE MORE POSSIBILITY (STARTS WITH)
    	try {
    		String versionlessAccessionIdName = accessionIdName.replaceFirst("\\.\\d+$", "");
    	    Map<String, Object> data = jdbcTemplate.queryForMap(
    	    	"SELECT taxid, title FROM protein WHERE accession_id LIKE ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
    	    	versionlessAccessionIdName + "%", ncbiVersion);
    	    taxId = (int) data.get("taxid");
        	title = (String) data.get("title");
    	} catch (DataAccessException ex) {
    		return Arrays.asList(taxId, title);
    		//return;
    	}
    }
    return Arrays.asList(taxId, title);
    
  }

  /**
   * Quick method to take an accession_id with or without version info and return current protein with version info (if
   * available)
   * 
   * @param putativeAccessionId
   * @return valid accession id in current update version or null if not found
   */
  public String resolveAccessionVersion(String putativeAccessionId) {
    String versionlessAccessionIdName = putativeAccessionId.replaceFirst("\\.\\d+$", "");

    if (versionlessAccessionIdName.trim().equals("")) {
      return null;
    }

    String mysqlRegexpString = "^" + putativeAccessionId + "$|^" + versionlessAccessionIdName + "\\.[0-9]+$";
    // Example: ^P00567$|^P00567\.[0-9]$
    try {
      return jdbcTemplate.queryForObject(
          "SELECT accession_id FROM protein WHERE accession_id LIKE ? AND FIND_IN_SET(?, ncbi_valid_versions) AND accession_id REGEXP ? LIMIT 1",
          String.class, versionlessAccessionIdName + "%", ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion(),
          mysqlRegexpString);
    } catch (DataAccessException e) {
      return null;
    }
  }

  /**
   * This method uses blastdbcmd to find the "canonical" accession id for a given protein accession id. This is the
   * first result in the fasta definition. The method returns null if not found.
   * 
   * @param accessionIdName
   * @return the canonical accession id
   */
  public String findCanonicalAccessionString(String accessionIdName) {
    String versionlessAccessionIdName = accessionIdName.replaceFirst("\\.\\d+$", "");
    ProcessProvider getCanonical = new ProcessProvider();
    getCanonical.setUserID(0);
    getCanonical.setJobID(0);
    getCanonical
        .setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.getCanonicalAccessionProcess));
    getCanonical.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    //System.out.println("Just set execPath to: " + ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    logger.info("Just set execPath to: {}", ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());

    List<String> argVals = new ArrayList<String>();
    argVals.add(ncbiKeeper.getPreferredNCBIProvider().getPathNrData());
    argVals.add("prot");
    argVals.add(versionlessAccessionIdName);
    argVals.add("%a ");
    getCanonical.setArgVals(argVals);
    try {
      String[] outErr = getCanonical.run();
      String parts = outErr[0];
      String stdErr = outErr[1];
      //System.out.println("parts = " + parts);
      //System.out.println("stdErr = " + stdErr);
      logger.info("parts = {}", parts);
      logger.info("stdErr = {}", stdErr);
      if (parts.length() < 2 || parts.startsWith("Error:")) {
        return null;
      }
      return parts.substring(0, parts.indexOf(" "));
    } catch (InterruptedException | IOException e) {
      return null;
    }
  }

  /**
   * This method uses blastdbcmd to find the "canonical" accession id for a given protein accession id. This is the
   * first result in the fasta definition. It also finds all accession ids identical to the query protein from the same
   * taxid. The method returns null if not found.
   * 
   * @param accessionIdName
   * @return the canonical accession id followed by the sequence of identical proteins from same taxid
   */
  public List<String> findCanonicalAccessionPlusIdenticalsFromTaxidString(String accessionIdName) {
    String versionlessAccessionIdName = accessionIdName.replaceFirst("\\.\\d+$", "");
    ProcessProvider getCanonical = new ProcessProvider();
    getCanonical.setUserID(0);
    getCanonical.setJobID(0);
    getCanonical.setProcessType(
        ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.getCanonicalAccessionAndTaxidsProcess));
    getCanonical.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    //System.out.println("Just set execPath to: " + ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    logger.info("Just set execPath to: {}", ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());

    List<String> argVals = new ArrayList<String>();
    argVals.add(ncbiKeeper.getPreferredNCBIProvider().getPathNrData());
    argVals.add("prot");
    argVals.add(versionlessAccessionIdName);
    argVals.add("%a %T ");
    getCanonical.setArgVals(argVals);
    String parts = null;
    try {
      String[] outErr = getCanonical.run();
      parts = outErr[0];
      String stdErr = outErr[1];
      //System.out.println("parts = " + parts);
      //System.out.println("stdErr = " + stdErr);
      logger.info("parts = {}", parts);
      logger.info("stdErr = {}", stdErr);
      if (parts.length() < 2 || parts.startsWith("Error:")) {
        return null;
      }

    } catch (InterruptedException | IOException e) {
      return null;
    }

    /*
     * Now have to parse the canonical and if canonical is not the same as the query, all identical proteins from same
     * taxid as query
     */

    List<String> results = new ArrayList<String>();
    String canonical = parts.substring(0, parts.indexOf(" "));
    results.add(canonical);

    if (canonical.equals(versionlessAccessionIdName)
        || canonical.matches("^" + versionlessAccessionIdName + ".\\d+$")) {
      return results;
    }

    String queryAccession = null;
    String querySpecies = null;

    String accessionPatternString = "(\\S+) (\\d+) ";
    Pattern accessionPattern = Pattern.compile(accessionPatternString);

    HashMap<String, String> identicals = new HashMap<String, String>();
    Matcher matcher1 = accessionPattern.matcher(parts);
    while (matcher1.find()) {
      String accession = matcher1.group(1);
//			if(accession.startsWith("pir||") || accession.startsWith("prf||")){
//				accession = accession.substring(5);
//			}

      String species = matcher1.group(2);
//			System.out.println("Found accession: " + accession + " and species: " + species);
      if (accession.equals(versionlessAccessionIdName)
          || accession.matches("^" + versionlessAccessionIdName + ".\\d+$")) {
        queryAccession = accession;
        querySpecies = species;
        //System.out.println("Matched query: " + versionlessAccessionIdName);
        logger.info("Matched query: {}", versionlessAccessionIdName);
      }
      identicals.put(accession, species);
    }
    for (String key : identicals.keySet()) {
      if (identicals.get(key).equals(querySpecies) && !(key.equals(queryAccession))) {
        results.add(key);
      }
    }
    if (identicals.size() < 2) {
      //System.out.println("Bummer! " + parts + " didn't match the matcher.");
      logger.warn("Bummer! {} didn't match the matcher.", parts);
    }
    return results;
  }
  
  

  /**
   * This method checks to see whether the given accession_id string has already been created using the specifications
   * of the ncbiKeeper (preferred NCBIProvider). If so, it returns true quickly. Otherwise, the method creates the file
   * with the proper name and returns true. If the method returns false, the file is not available or has size zero for
   * some reason. It may not be a valid accession_id in the current database, for example
   * 
   * @param accession
   * @param ncbiKeeper
   * @return boolean indicating whether the fasta file is not available for use
   */
  public boolean createFastaFileIfNecessary(String accession) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    String fullPath = getFastaPath(accession);
    File fastaFile = new File(fullPath);
    if (fastaFile.length() > 0) {
      return true;
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
    argVals.add(fullPath);
    createFasta.setArgVals(argVals);
    try {
      String[] fastaRunResults = createFasta.run();
      if (fastaRunResults[1].length() > 3) {
        //System.out.println("Got an error creating a fasta: " + fastaRunResults[1]);
        logger.error("Got an error creating a fasta: {}", fastaRunResults[1]);
      }
    } catch (InterruptedException | IOException e) {
      return false;
    }

    if (fastaFile.length() > 0) {
      return true;
    }
    return false;
  }

  /**
   * This simply gets a fasta file from an accession id. It first removes the version to maximize the chances of a
   * match. It returns the fasta in two parts of a String[2] object, the first is the canonical accession id (without
   * the leading >) and the second is the sequence
   * 
   * @param accession id with or without version number
   * @return
   *         <ul>
   *         <li>String[2] with canonical accession id and sequence or</li>
   *         <li>null if failure</li>
   *         </ul>
   */
  public String[] getFastaPartsFromAccession(String accession) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    String trimmedAccession = accession.replaceFirst("\\.\\d+$", ""); // Remove
    // the
    // version
    // if
    // present

    ProcessProvider getFasta = new ProcessProvider();
    getFasta.setUserID(0);
    getFasta.setJobID(0);
    getFasta.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.getOneFastaProcess));
    getFasta.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    List<String> argVals = new ArrayList<String>();
    argVals.add(ncbiProvider.getPathNrData());
    argVals.add("prot");
    argVals.add(trimmedAccession);
    argVals.add(">%a>%s");
    getFasta.setArgVals(argVals);
    String resultingFasta = null;
    try {
      String[] getFastaResults = getFasta.run();
      resultingFasta = getFastaResults[0];
//			System.out.println("Resulting fasta file size = " + resultingFasta.length());
      String stdErr = getFastaResults[1];
//			System.out.println("Resulting fasta err = " + stdErr);
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return null;
    }
    if (resultingFasta == null || resultingFasta.length() < 10) {
      return null;
    }

    String[] fastaParts = resultingFasta.split(">");
    String[] resultArray = new String[2];
    resultArray[0] = fastaParts[1].trim();
    resultArray[1] = fastaParts[2].trim();
//		System.out.println("resultArray contents: " + resultArray.toString());
    return resultArray;
    // return ">" + fastaParts[0] + "\n" + fastaParts[fastaParts.length -
    // 1];
  }

  /**
   * Returns the userRun ID for the newly inserted run
   * 
   * @param jdbcTemplate
   * @param userId
   * @return newly created userRun id
   * @throws SQLException
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int insertUserRunReturnId(int userId) throws SQLException {
    String updateQuery = "INSERT INTO user_run (user_id) VALUES (?)";
    Connection connection = null;
    int userRunId = -1;

    try {
      connection = jdbcTemplate.getDataSource().getConnection();
      PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, Statement.RETURN_GENERATED_KEYS);
      preparedStatement.setInt(1, userId);
      preparedStatement.executeUpdate();
      ResultSet keys = preparedStatement.getGeneratedKeys();
      if (keys.next()) {
        userRunId = keys.getInt(1);
      }
    } catch (SQLException e) {
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
    return userRunId;
  }

  // /**
  // * Returns the userRun ID for the newly inserted run
  // *
  // * @param jdbcTemplate
  // * @param userId
  // * @return newly created userRun id
  // * @throws SQLException
  // */
  // @Transactional
  // @Retryable(maxAttempts = 50, value = {
  // MySQLTransactionRollbackException.class, DataAccessException.class,
  // BatchUpdateException.class, RuntimeException.class }, backoff =
  // @Backoff(delay = 500, multiplier = 2))
  // public int insertAccessionRunReturnId(String accessionStringId) throws
  // SQLException {
  // String updateQuery = "INSERT INTO accession_run (accession_id,
  // ncbi_version_id) VALUES (?, ?)";
  // Connection connection = null;
  // int accessionRunId = -1;
  // try {
  // connection = jdbcTemplate.getDataSource().getConnection();
  //
  // PreparedStatement preparedStatement =
  // connection.prepareStatement(updateQuery,
  // Statement.RETURN_GENERATED_KEYS);
  // preparedStatement.setString(1, accessionStringId);
  // preparedStatement.setInt(2, ncbiKeeper.getPreferredNCBIProviderID());
  //
  // preparedStatement.executeUpdate();
  // ResultSet keys = preparedStatement.getGeneratedKeys();
  // if (keys.next()) {
  // accessionRunId = keys.getInt(1);
  // }
  // } catch (SQLException e) {
  // } finally {
  // if (connection != null) {
  // connection.close();
  // }
  // }
  // return accessionRunId;
  // }

  /**
   * Returns the userRun ID for the newly inserted run
   * 
   * @param jdbcTemplate
   * @param userId
   * @return newly created userRun id
   * @throws SQLException
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int insertAccessionRunReturnId(String canonicalAccessionString, String queryAccessionString, int taxid,
      String title) throws SQLException {
    String updateQuery = "INSERT INTO accession_run (canonical_accession_id, top_hit_accession_id, query_taxid, query_title, ncbi_version_id) VALUES (?, ?, ?, ?, ?)";
    Connection connection = null;
    int accessionRunId = -1;
    try {
      connection = jdbcTemplate.getDataSource().getConnection();

      PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, Statement.RETURN_GENERATED_KEYS);
      preparedStatement.setString(1, canonicalAccessionString);
      preparedStatement.setString(2, queryAccessionString);
      preparedStatement.setInt(3, taxid);
      preparedStatement.setString(4, title);
      preparedStatement.setInt(5, ncbiKeeper.getPreferredNCBIProviderID());

      preparedStatement.executeUpdate();
      ResultSet keys = preparedStatement.getGeneratedKeys();
      if (keys.next()) {
        accessionRunId = keys.getInt(1);
      }
    } catch (SQLException e) {
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
    return accessionRunId;
  }

  // @Transactional
  // @Retryable(maxAttempts = 50, value = {
  // MySQLTransactionRollbackException.class, DataAccessException.class,
  // BatchUpdateException.class, RuntimeException.class }, backoff =
  // @Backoff(delay = 500, multiplier = 2))
  // public String setProteinNameForAccessionRun(int accessionRunId, String
  // canonicalAccessionString) {
  // String getProteinNameQuery = "SELECT taxid, SUBSTRING_INDEX(title,' [',1) AS
  // `protein_name` FROM protein WHERE accession_id = ? LIMIT 1";
  // String proteinTitle = "(protein name not found)";
  // Integer taxid = null;
  // System.out.println("Trying to get data from protein table using: " +
  // canonicalAccessionString);
  // System.out.println("Query is: " + getProteinNameQuery + " with accession id =
  // " + accessionRunId);
  // try {
  // List<Map<String, Object>> rows =
  // jdbcTemplate.queryForList(getProteinNameQuery, canonicalAccessionString);
  // if (rows.size() == 0) {
  // System.out
  // .println("No rows from query: " + getProteinNameQuery + " using: " +
  // canonicalAccessionString);
  // // return;
  // }
  // Map<String, Object> row = rows.get(0);
  // taxid = (Integer) row.get("taxid");
  // if (taxid == 0) {
  // return "Protein has no taxonomy info";
  // }
  // proteinTitle = (String) row.get("protein_name");
  // } catch (DataAccessException e) {
  // System.out.println("Didn't get data from protein table using: " +
  // canonicalAccessionString
  // + "\nTrying a 'like' query...");
  // // NOTE: the above condition is not likely since blastdbcmd found
  // // this hit. So, it should be in the protein table
  // getProteinNameQuery = "SELECT taxid, SUBSTRING_INDEX(title,' [',1) AS
  // `protein_name` FROM protein WHERE accession_id LIKE ? LIMIT 1";
  // try {
  // List<Map<String, Object>> rows =
  // jdbcTemplate.queryForList(getProteinNameQuery,
  // canonicalAccessionString + "%");
  // if (rows.size() == 0) {
  // System.out.println("Still nothing");
  // return "Protein not found";
  // }
  // Map<String, Object> row = rows.get(0);
  // taxid = (Integer) row.get("taxid");
  // if (taxid == 0) {
  // return "Protein has no taxonomy info";
  // }
  // proteinTitle = (String) row.get("protein_name");
  // } catch (DataAccessException e2) {
  // System.out.println("Still nothing");
  // return "Could not get protein info";
  // }
  // System.out.println("Got data from protein table using: " + taxid + ", and " +
  // proteinTitle);
  //
  // }
  // System.out.println("Got data from protein table using: " + taxid + ", and " +
  // proteinTitle);
  //
  // String updateQuery = "UPDATE accession_run SET accession_taxid = ?,
  // protein_title = ? WHERE id = ?";
  // jdbcTemplate.update(updateQuery, taxid, proteinTitle, accessionRunId);
  // return null;
  // }
  
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public String selectQueryAccessionFromUserRunAccessionRun(int accessionRunId) {
		logger.debug("Attempting method: selectQueryAccessionFromUserRunAccessionRun with accessionRunId = {}"	+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class", accessionRunId);

		String query = "SELECT query_accession_id FROM user_run_accession_run WHERE accession_run_id = ? LIMIT 1";
		try {
			return jdbcTemplate.queryForObject(query, String.class, accessionRunId);
		} catch (DataAccessException e) {
			return null;
		}
	}
  

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void insertUserRunAccessionRunRow(int userRunId, int accessionRunId, String queryAccessionString) {
    // NOW DO THE queryAccessionString
    String getExactProteinNameQuery = "SELECT SUBSTRING_INDEX(title,' [',1) as `protein_name` FROM protein WHERE accession_id = ? LIMIT 1";
    String getLikeProteinNameQuery = "SELECT SUBSTRING_INDEX(title,' [',1) as `protein_name` FROM protein WHERE accession_id LIKE ? LIMIT 1";

    String proteinTitle = "(protein name not found)";
    //System.out.println("Trying to get data from protein table using: " + queryAccessionString);
    logger.info("Trying to get data from protein table using: {}", queryAccessionString);

    try {
      proteinTitle = jdbcTemplate.queryForObject(getExactProteinNameQuery, String.class, queryAccessionString);
    } catch (DataAccessException e) {
      //System.out.println("Didn't get data from protein table using exact: " + queryAccessionString
      //    + "\nTrying a 'LIKE' query with % at end... ");
      logger.error("Didn't get data from protein table using exact: {} \nTrying a 'LIKE' query with % at end... ", 
    		  queryAccessionString);
      String versionlessAccessionString = queryAccessionString.replaceFirst("\\.\\d+$", "");
      try {
        proteinTitle = jdbcTemplate.queryForObject(getLikeProteinNameQuery, String.class,
            versionlessAccessionString + "%");

      } catch (DataAccessException e2) {
        //System.out.println("Still nothing...  ");
        logger.error("Still nothing...  ");
      }
    }

    //System.out.println("Got the title: " + proteinTitle + " Adding to user_run_accession_run with these other vals: "
    //  + userRunId + ", " + accessionRunId + ", " + queryAccessionString);
    logger.info("Got the title: {}. Adding to user_run_accession_run with these other vals: {}, {}, {}", 
    		proteinTitle, userRunId, accessionRunId, queryAccessionString);

    String updateQuery = "INSERT IGNORE INTO user_run_accession_run (user_run_id, accession_run_id, query_accession_id, protein_title) values (? , ? , ? , ?)";

    jdbcTemplate.update(updateQuery, userRunId, accessionRunId, queryAccessionString, proteinTitle);
  }

  /**
   * Returns the level2Run ID for the newly inserted run
   * 
   * @param accessionRunId (int) The accession run id, associated with level one analysis.
   * @param userId         (int) the user's id number
   * @param level2CddId    (int) the accession id associated with the cdd found in rps blast
   * @param startPosition  (int) the starting amino acid position for the domain match
   * 
   * @return newly created level2Run id OR -1 if the run has already been requested
   * @throws SQLException
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int insertLevel2RunReturnId(int accessionRunId, int userId, int level2CddId, int startPosition)
      throws SQLException {
    String updateQuery = "INSERT INTO level2_run (accession_run_id, user_id, cdd_accession_num, start_position) VALUES (?,?,?,?)";

    Connection connection = null;
    int level2RunId = -1;

    connection = jdbcTemplate.getDataSource().getConnection();

    PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, Statement.RETURN_GENERATED_KEYS);
    preparedStatement.setInt(1, accessionRunId);
    preparedStatement.setInt(2, userId);
    preparedStatement.setInt(3, level2CddId);
    preparedStatement.setInt(4, startPosition);

    preparedStatement.executeUpdate();
    ResultSet keys = preparedStatement.getGeneratedKeys();
    if (keys.next()) {
      level2RunId = keys.getInt(1);
    }

    if (connection != null) {
      connection.close();
    }
    return level2RunId;
  }

  /**
   * Returns the level3Run ID for the newly inserted run
   * 
   * @param accessionRunId
   * @param userId
   * @param jobName
   * @return newly created level3Run id
   * @throws SQLException
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int insertLevel3RunReturnId(int accessionRunId, int userId, String jobName, String templateName)
      throws SQLException {
    String updateQuery = "INSERT INTO level3_run (accession_run_id, user_id, job_name, template_name) VALUES (?,?,?,?)";
    Connection connection = null;
    int level3RunId = -1;
    // TODO - CHECK HERE AND OTHER PLACES TO SEE IF THIS SHOULD BE IN A TRY
    // CATCH OR THROW AND EXCEPTION WHICH WOULD BE CAUGHT BY
    // RETRYABLE
    try {
      connection = jdbcTemplate.getDataSource().getConnection();

      PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, Statement.RETURN_GENERATED_KEYS);
      preparedStatement.setInt(1, accessionRunId);
      preparedStatement.setInt(2, userId);
      preparedStatement.setString(3, jobName);
      preparedStatement.setString(4, templateName);
      preparedStatement.executeUpdate();
      ResultSet keys = preparedStatement.getGeneratedKeys();
      if (keys.next()) {
        level3RunId = keys.getInt(1);
      }
    } catch (SQLException e) {
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
    return level3RunId;
  }

  /**
   * Returns the level4Run ID for the newly inserted run
   * 
   * @param accessionRunId
   * @param userId
   * @param jobName
   * @return newly created level4Run id
   * @throws SQLException
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int insertLevel4RunReturnId(int accessionRunId, int userId, String jobName, int sourceLevel, int level2RunId)
      throws SQLException {
	  
	  String updateQuery;
	  if (sourceLevel == 1) {
		  updateQuery = "INSERT INTO level4_run (accession_run_id, user_id, job_name, source_level) VALUES (?,?,?,?)";
	  } else if (sourceLevel ==2) {
		  updateQuery = "INSERT INTO level4_run (accession_run_id, user_id, job_name, source_level, level2_run_id) VALUES (?,?,?,?,?)";
	  } else {
		  logger.error("insertLevel4RunReturnId - Invalid source level: {}", sourceLevel);
		  return -1;
	  }
    Connection connection = null;
    int level4RunId = -1;
    // TODO - CHECK HERE AND OTHER PLACES TO SEE IF THIS SHOULD BE IN A TRY
    // CATCH OR THROW AND EXCEPTION WHICH WOULD BE CAUGHT BY
    // RETRYABLE
    try {
      connection = jdbcTemplate.getDataSource().getConnection();

      PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, Statement.RETURN_GENERATED_KEYS);
      preparedStatement.setInt(1, accessionRunId);
      preparedStatement.setInt(2, userId);
      preparedStatement.setString(3, jobName);
      preparedStatement.setInt(4,  sourceLevel);
      if (sourceLevel == 2) {
    	  preparedStatement.setInt(5, level2RunId);
      }
//      preparedStatement.setString(4, templateName);
      preparedStatement.executeUpdate();
      ResultSet keys = preparedStatement.getGeneratedKeys();
      if (keys.next()) {
    	  level4RunId = keys.getInt(1);
      }
    } catch (SQLException e) {
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
    return level4RunId;
  }
  
  /**
   * /** This method returns the file system path name for a given fasta file given its accession code. It is
   * centralized to allow easy update of the path, although the path is collected from the database.
   * 
   * @param accession  the accession code
   * @param ncbiKeeper the ncbiKeeper object being used
   * @return The server file system path
   */
  public String getFastaPath(String accession) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    return ncbiProvider.getPathFasta() + "/" + accession + ".fsa";
  }

  public String getDBTopHitAccessionString(int accessionRunId) {
    String query = "SELECT top_hit_accession_id FROM accession_run WHERE accession_run_id = ? LIMIT 1";
    try {
      return jdbcTemplate.queryForObject(query, String.class, accessionRunId);
    } catch (DataAccessException e) {
      return null;
    }
  }

  /**
   * This method is intended to remove all level 1, level 2, and level 3 runs associated with the specified
   * query_accesison_id . It returns a string stating the action taken.
   * 
   * @param queryAccessionId
   * @return String: the action taken
   */

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public String deleteAccessionRun(String queryAccessionId, int userRunId) {
    //System.out.println("About to delete this accession: " + queryAccessionId);
    logger.warn("About to delete this accession: {}", queryAccessionId);

    StringBuilder b = new StringBuilder();
    b.append(" SELECT ");
    b.append("    user_run_id, accession_run_id ");
    b.append("FROM ");
    b.append("    user_run_accession_run a ");
    b.append("        JOIN accession_run b ON b.id = a.accession_run_id ");
    b.append("    JOIN version c ON c.id = b.ncbi_version_id ");
    b.append("WHERE query_accession_id = ? ");
    b.append("        AND a.user_run_id = ? ");
    String firstQuery = b.toString();

    // int userRunId = -1;
    List<Integer> userRunIds = new ArrayList<Integer>();
    int accessionRunId = -1;
    //System.out.println("Testing query: " + firstQuery + " \n with queryAccessionId: '" + queryAccessionId + "'");
    logger.info("Testing query: {}  \n with queryAccessionId: '{}'", firstQuery , queryAccessionId);
    // try {
    // System.out.println("Testing query: " + firstQuery +
    // " \n with queryAccessionId: '" + queryAccessionId + "'");

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(firstQuery, queryAccessionId, userRunId);
    //System.out.println("Query OK.  Getting data from:  " + rows);
    logger.info("Query OK.  Getting data from:  {}", rows);
    if (rows.size() == 0) {
      return queryAccessionId + ": not found";
    }
    for (Map<String, Object> row : rows) {
      userRunIds.add((int) row.get("user_run_id"));
      accessionRunId = (int) row.get("accession_run_id");
    }
    //System.out.println("This accession: " + queryAccessionId + " has first userRunId: " + userRunIds.get(0)
    //    + " and accessionRunId: " + accessionRunId);
    logger.info("This accession: {} has first userRunId: {} and accessionRunId: {}", 
    		queryAccessionId, userRunIds.get(0), accessionRunId);

    if (accessionRunId == -1) {
      return queryAccessionId + ": not found";
    }
    try {
      String secondQuery = "SELECT COUNT(*) FROM user_run_accession_run WHERE user_run_id = ?";
      for (Integer id : userRunIds) {
        if (jdbcTemplate.queryForObject(secondQuery, Integer.class, id) == 1) {
          //System.out.println("Delete Start");
          logger.info("Delete Start");
          jdbcTemplate.update("DELETE FROM user_run WHERE id = ?", id);
          //System.out.println("Deleted entire run");
          logger.info("Deleted entire run");
        }
      }
      jdbcTemplate.update("DELETE FROM accession_hit WHERE accession_run_id = ? ", accessionRunId);
      //System.out.println("Delete 3 - (no accession_run_cutoff)");
      logger.info("Delete 3 - (no accession_run_cutoff)");

      // jdbcTemplate.update("DELETE FROM accession_run_cutoff WHERE
      // accession_run_id
      // = ? ",
      // accessionRunId);
      //System.out.println("Delete 4 - (no accession_run_density_plot)");
      logger.info("Delete 4 - (no accession_run_density_plot)");
      //jdbcTemplate.update("DELETE FROM accession_run_density_plot WHERE accession_run_id = ? ", accessionRunId);
      //System.out.println("Delete 5");
      logger.info("Delete 5");
      jdbcTemplate.update(
          "DELETE FROM level2_result WHERE level2_run_id IN (SELECT id FROM level2_run WHERE accession_run_id = ? ) ",
          accessionRunId);
      //System.out.println("Delete 6 - (not used)");
      logger.info("Delete 6 - (not used)");
      // jdbcTemplate.update(
      // "DELETE FROM domain_run_cutoff WHERE domain_run_id IN (SELECT id
      // FROM
      // level2_run WHERE accession_run_id = ? ) ",
      // accessionRunId);
      //System.out.println("Delete 7 - (no domain_run_density_plot) ");
      logger.info("Delete 7 - (no domain_run_density_plot) ");
//      jdbcTemplate.update(
//          "DELETE FROM domain_run_density_plot WHERE domain_run_id IN (SELECT id FROM level2_run WHERE accession_run_id = ? ) ",
//          accessionRunId);
      //System.out.println("Delete 8");
      logger.info("Delete 8");
      jdbcTemplate.update("DELETE FROM level2_run WHERE accession_run_id = ? ", accessionRunId);
      //System.out.println("Delete 9");
      logger.info("Delete 9");
      jdbcTemplate.update(
          "DELETE FROM level3_result WHERE level3_run_id IN (SELECT id FROM level3_run WHERE accession_run_id = ? ) ",
          accessionRunId);
      //System.out.println("Delete 10");
      logger.info("Delete 10");
      jdbcTemplate.update("DELETE FROM level3_run WHERE accession_run_id = ? ", accessionRunId);
      //System.out.println("Delete 11");
      logger.info("Delete 11");
      jdbcTemplate.update("DELETE FROM user_run_accession_run WHERE accession_run_id = ? ", accessionRunId);
      //System.out.println("Delete 12");
      logger.info("Delete 12");
      jdbcTemplate.update("DELETE FROM accession_run WHERE id = ? ", accessionRunId);
      //System.out.println("Delete 13");
      logger.info("Delete 13");
    } catch (DataAccessException e) {
      //System.out.println("No Delete");
      logger.info("No Delete");
      return queryAccessionId + ": not fully deleted";
    }
    return queryAccessionId + ": deleted";
  }

  @SuppressWarnings("unused")
  private void BLASTp_SPECIFIC_METHODS() { // ======================================================
  }

  // /**
  // * This method is intended to be generally applicable to inserting a set
  // of values into the accession_hit table. It takes a single
  // list
  // * of Names, and a List of Lists of Values. This method assumes the set
  // coming be be unmanageable, so only does a maximum of 500 rows
  // * per insert statement.
  // *
  // * @param nameList
  // * @param valuesList
  // */
  // @Transactional
  // @Retryable(maxAttempts = 50, value = {
  // MySQLTransactionRollbackException.class, DataAccessException.class,
  // BatchUpdateException.class,
  // RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier =
  // 2))
  // public void saveAccessionHitBatch(final LinkedList<String> nameList,
  // final List<LinkedList<String>> valuesList) {
  // System.out.println("Attempting method: saveAccessionHitBatch with value
  // start
  // = "
  // + valuesList.get(0).get(0));
  // final int nameSize = nameList.size();
  // StringBuilder b = new StringBuilder();
  // b.append("INSERT IGNORE INTO accession_hit (");
  // StringBuilder qMarks = new StringBuilder();
  // for (String name : nameList) {
  // if (name.equals("accession_run_id") || name.equals("hit_accession_id")) {
  // b.append("`" + name + "`,");
  // } else {
  // b.append("`xml_" + name + "`,");
  // }
  // qMarks.append("?,");
  // }
  // b.deleteCharAt(b.length() - 1);
  // qMarks.deleteCharAt(qMarks.length() - 1);
  // b.append(") VALUES (");
  // b.append(qMarks.toString());
  // b.append(")");
  // final String insertQuery = b.toString();
  // final int batchSize = 500;
  // for (int index = 0; index < valuesList.size(); index += batchSize) {
  // // long start = new Date().getTime();
  // int last = Math.min(index + batchSize, valuesList.size());
  // final List<LinkedList<String>> batch = valuesList.subList(index, last);
  // jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter()
  // {
  // @Override
  // public void setValues(java.sql.PreparedStatement ps, int i) throws
  // SQLException {
  // LinkedList<String> values = batch.get(i);
  // if (values.size() == nameSize) {
  // for (int j = 1; j < nameList.size() + 1; j++) {
  // String name = nameList.get(j - 1);
  // String value = values.get(j - 1);
  // if (name.equals("accession_run_id")) {
  // ps.setInt(j, Integer.parseInt(value));
  // } else if (name.equals("hit_accession_id")) {
  // ps.setString(j, value);
  // } else if (blastPxmlTypes.get(name).equals("String")) {
  // ps.setString(j, value);
  // } else if (blastPxmlTypes.get(name).equals("double")) {
  // ps.setDouble(j, Double.parseDouble(value));
  // } else if (blastPxmlTypes.get(name).equals("int")) {
  // ps.setInt(j, Integer.parseInt(value));
  // } else {
  // ps.setString(j, value);
  // }
  // }
  // }
  // }
  //
  // @Override
  // public int getBatchSize() {
  // return batch.size();
  // }
  // });
  // }
  // // Busy.getInstance().restoreAccessionHit();
  //
  // System.out.println("Completed method: saveAccessionHitBatch with value
  // start
  // = "
  // + valuesList.get(0).get(0));
  // // + valuesList.get(valuesList.size() - 1)
  // // +
  // " and retryable value(s) = DataAccessException.class,
  // BatchUpdateException.class, RuntimeException.class");
  //
  // }

  /**
   * This method takes the string following a > (greater than symbol) and returns the correct protein accession ID
   * 
   * @param postGTString: The string following the greater than sign: >
   * @return proteinAccessionID: Protein Accession which may include prf|| or pir| as of data version 5
   */

  public String parseAccession(String postGTString) {
    // boolean randomPrint = false;
    // if(Math.random()<0.01) {randomPrint = true;}
    // In all cases, nothing important comes after a space character, so remove from the first space onward
    String[] spaceSplits = postGTString.split(" ");
    String firstPart = spaceSplits[0];
    if (firstPart.startsWith("pdb|")) {
      // e.g. parsing from this--> ...>pdb|1R5K|B Chain B, Estrogen Receptor [Homo sapiens] >...
      // postGTString would be --> pdb|1R5K|B
      // Algorithm - take from character 4 until but not including space.
      // Replace pipe with underscore
      // if (randomPrint) {System.out.println("PDB: "+firstPart+" => "+ firstPart.substring(4).replaceAll("\\|", "_"));}
      return firstPart.substring(4).replaceAll("\\|", "_");
    } else if (firstPart.startsWith("prf||")) {
      // e.g. parsing from this--> ...>prf||1402310B cryptic steroid hormone receptor 2 [Homo sapiens] >...
      // postGTString would be --> prf||1402310B
      // Algorithm - take from character 0 until but not including space.
      // if (randomPrint) {System.out.println("PRF: "+firstPart+" => "+ firstPart);}
      return firstPart;
    } else if (firstPart.startsWith("pir|")) {
      // e.g. parsing from this--> ...>pir|S11513| usp protein - fruit fly (Drosophila sp.) [Drosophila sp. (in:
      // Insecta)] >...
      // postGTString would be --> pir|S11513|
      // Algorithm - take from character 0 until but not including pipe-then-space.
      // if (randomPrint) {System.out.println("PIR: "+firstPart+" => "+ firstPart);}
      return firstPart;
    } else {
      // e.g. parsing from this--> ...>ref|NP_476781.1| ultraspiracle, isoform A [Drosophila melanogaster] >...
      // postGTString would be --> ref|NP_476781.1|
      // Algorithm - take content between first and second pipe
      // Also, capture sources (content before first pipe) and Print a message if it is not in this set:
      // dbj , emb , gb , ref , sp , tpd
      List<String> knownSources = new ArrayList<String>(
          Arrays.asList("dbj", "emb", "gb", "ref", "sp", "tpg", "tpd", "tpe"));
      String[] pipeSplits = firstPart.split("\\|");
      if (pipeSplits.length == 1) {
        return null;
      }
      if (!knownSources.contains(pipeSplits[0])) {
        //System.out.println("Unknown source: " + pipeSplits[0] + " for accession: " + pipeSplits[1]);
        logger.warn("Unknown source: {} for accession: {}", pipeSplits[0], pipeSplits[1]);
      }
      // if (randomPrint) {System.out.println(pipeSplits[0]+": "+firstPart+" => "+ pipeSplits[1]);}
      return pipeSplits[1];
    }
  }

  /**
   * This method is intended to be generally applicable to inserting a set of values into the accession_hit table. It
   * takes a single list of Names, and a List of Lists of Values. This method assumes the set coming in is manageable,
   * so only does one insert statement.
   * 
   * @param nameList
   * @param valuesList
   * @param jdbcTemplate
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void saveAccessionHitInOneBatch(final List<String> nameList, final List<ArrayList<String>> valuesList) {
    //System.out.println("Attempting method: saveAccessionHitInOneBatch a single batch of size = " + valuesList.size());
    logger.info("Attempting method: saveAccessionHitInOneBatch a single batch of size = {}", 
    		valuesList.size());
    final int nameSize = nameList.size();
    StringBuilder b = new StringBuilder();
    b.append("INSERT IGNORE INTO accession_hit (");
    StringBuilder qMarks = new StringBuilder();
    for (String name : nameList) {
      if (name.equals("accession_run_id") || name.equals("hit_accession_id") || name.equals("hit_taxid")
          || name.equals("rps_status") || name.equals("rbh_status") || name.equals("hit_canonical_id")
          || name.equals("near_class_taxid")) {
        b.append("`" + name + "`,");
      } else {
        b.append("`xml_" + name + "`,");
      }
      qMarks.append("?,");
    }
    b.deleteCharAt(b.length() - 1);
    qMarks.deleteCharAt(qMarks.length() - 1);
    b.append(") VALUES (");
    b.append(qMarks.toString());
    b.append(")");
    final String insertQuery = b.toString();
//		System.out.println("insertQuery: " + insertQuery);

    jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
        ArrayList<String> values = valuesList.get(i);
        if (values.size() != nameSize) {
          //System.out.println("The number of fields and the number of values are different!");
          logger.warn("The number of fields and the number of values are different!");
        } else {
          for (int j = 1; j < nameList.size() + 1; j++) {
            String name = nameList.get(j - 1);
            String value = values.get(j - 1);
            if (name.equals("accession_run_id")) {
              ps.setInt(j, Integer.parseInt(value));
            } else if (name.equals("hit_taxid")) {
              ps.setInt(j, Integer.parseInt(value));
            } else if (name.equals("hit_accession_id")) {
              ps.setString(j, value);
            } else if (name.equals("hit_canonical_id")) {
              ps.setString(j, value);
            } else if (name.equals("near_class_taxid")) {
              ps.setInt(j, Integer.parseInt(value));
            } else if (name.equals("rps_status")) {
              ps.setString(j, value);
            } else if (name.equals("rbh_status")) {
              ps.setString(j, value);
            } else if (blastPxmlTypes.get(name).equals("String")) {
              ps.setString(j, value);
            } else if (blastPxmlTypes.get(name).equals("double")) {
              ps.setDouble(j, Double.parseDouble(value));
            } else if (blastPxmlTypes.get(name).equals("int")) {
              ps.setInt(j, Integer.parseInt(value));
            } else {
              //System.out.println("Type not seen for index: " + j + " and value: " + value);
              logger.error("Type not seen for index: {} and value {}", j, value);
              ps.setString(j, value);
            }
          }
//					System.out.println("prepared statement is: "+ps);
        }
      }

      @Override
      public int getBatchSize() {
        return valuesList.size();
      }
    });
    //System.out.println("Completed method: saveAccessionHitInOneBatch with value start = " + valuesList.get(0).get(0));
    logger.debug("Completed method: saveAccessionHitInOneBatch with value start = {}", 
    		valuesList.get(0).get(0));
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void updateBlastpStatus(final String status, final int accession_run_id) {
    String statusLower = status.toLowerCase();
    //System.out.println("Attempting method: updateBlastpStatus with accession_run_id = " + accession_run_id);
    logger.debug("Attempting method: updateBlastpStatus with accession_run_id = {}", accession_run_id);
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
    String updateQuery = "UPDATE accession_run SET blastp_status = ? WHERE id = ? AND blastp_status != 'no hits found' ";
    if (!statusLower.equals("queued") && !statusLower.equals("started") && !statusLower.equals("analyzing")
        && !statusLower.equals("complete") && !statusLower.equals("no hits found")
        && !statusLower.equals("too few hits")) {
      //System.out.println("Aborting method: updateBlastpStatus with accession_run_id = " + accession_run_id
      //    + " because status: '" + statusLower + "' is not in the enum!");
      logger.debug("Aborting method: updateBlastpStatus with accession_run_id = {} because status: ' {} ' "
      		+ "is not in the enum!", 
      		accession_run_id, statusLower);
      return;
    }
    try {
      jdbcTemplate.update(updateQuery, statusLower, accession_run_id);
    } catch (DataAccessException e) {
      // Failure here causes a retry
    }
    //System.out.println("Completed method: updateBlastpStatus with accession_run_id = " + accession_run_id);
    logger.debug("Completed method: updateBlastpStatus with accession_run_id = {}", accession_run_id);
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void sendQueryRBH(String subjectTopCanonicalId, Double bitscore, Double evalue, String canonicalAccessionId,
      Integer subjectTaxid, Integer ncbiProviderId, SBatchObject sBatchObject) {
    //System.out.println("Attempting method: sendQueryRBH with accession_run_id = " + sBatchObject.getAccessionRunId()
    //    + " job fragment# = " + sBatchObject.getJobFragmentNumber());
    logger.debug("Attempting method: sendQueryRBH with accession_run_id = {} job fragment# = {}", 
    		sBatchObject.getAccessionRunId(), sBatchObject.getJobFragmentNumber());

    String query = "UPDATE rbh_nova SET subject_top_canonical_id = ?, " + "bitscore = ?, " + "evalue = ? "
        + "WHERE ncbi_version_id = ? " + "AND canonical_accession_id = ? " + "AND  subject_taxid = ? ";

    try {
      jdbcTemplate.update(query, new PreparedStatementSetter() {
        @Override
        public void setValues(java.sql.PreparedStatement ps) throws SQLException {
          ps.setString(1, subjectTopCanonicalId);
          ps.setDouble(2, bitscore);
          ps.setDouble(3, evalue);
          ps.setInt(4, ncbiProviderId);
          ps.setString(5, canonicalAccessionId);
          ps.setInt(6, subjectTaxid);
        }
      });

    } catch (DataAccessException e) {
      // Failure here causes a retry
    }
    //System.out.println("Completed method: sendQueryRBH with accession_run_id = " + sBatchObject.getAccessionRunId()
    //    + " job fragment# = " + sBatchObject.getJobFragmentNumber());
    logger.debug("Completed method: sendQueryRBH with accession_run_id = {} job fragment# = {}", 
    		sBatchObject.getAccessionRunId(), sBatchObject.getJobFragmentNumber());
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void sendQueryRPS(List<String> nameList, List<ArrayList<String>> allValuesList, SBatchObject sBatchObject) {
    //System.out.println("Attempting method: sendQueryRPS with accession_run_id = " + sBatchObject.getAccessionRunId()
    //    + " job fragment# = " + sBatchObject.getJobFragmentNumber());
    logger.debug("Attempting method: sendQueryRPS with accession_run_id = {} job fragment# = {}", 
    		sBatchObject.getAccessionRunId(), sBatchObject.getJobFragmentNumber());

    try {
      final int nameSize = nameList.size();
      StringBuilder b = new StringBuilder();
      b.append("INSERT IGNORE INTO rps_result (");
      StringBuilder qMarks = new StringBuilder();
      for (String name : nameList) {
        if (name.equals("accession_id") || name.equals("ncbi_version_id")) {
          b.append("`" + name + "`,");
        } else {
          b.append("`xml_" + name + "`,");
        }
        qMarks.append("?,");
      }
      b.deleteCharAt(b.length() - 1);
      qMarks.deleteCharAt(qMarks.length() - 1);
      b.append(") VALUES (");
      b.append(qMarks.toString());
      b.append(")");
      final String insertQuery = b.toString();
      logger.info("AccID:" + sBatchObject.getAccessionRunId() + "." + sBatchObject.getJobFragmentNumber()
          + ": Ready to insert " + allValuesList.size() + " rows into rpsResult.");
//      logger.debug("AccID:" + sBatchObject.getAccessionRunId() + "." + sBatchObject.getJobFragmentNumber()
//          + ": nameList: " + nameList);
//      logger.debug("AccID:" + sBatchObject.getAccessionRunId() + "." + sBatchObject.getJobFragmentNumber()
//          + ": allValuesList: " + allValuesList);
      jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
          ArrayList<String> values = allValuesList.get(i);
          if (values.size() != nameSize) {
            logger.error("AccID:" + sBatchObject.getAccessionRunId() + "." + sBatchObject.getJobFragmentNumber()
                + ": The # of fields (" + nameSize + ") != # of values (" + values.size() + ")!");
            // System.out.println("The number of fields and the number of values are different!");
          } else {
//						int count = 1;
            for (int j = 1; j < nameList.size() + 1; j++) {
              String value = values.get(j - 1);
              switch (j) {
              case 2:
              case 4:
              case 5:
              case 22:
              case 23:
              case 24:
                ps.setString(j, value);
                break;
              case 9:
              case 11:
                ps.setDouble(j, Double.parseDouble(value));
                break;
              default:
                ps.setInt(j, Integer.parseInt(value));
                break;
              }
            }
          }
        }

        @Override
        public int getBatchSize() {
          return allValuesList.size();
        }
      });

    } catch (DataAccessException e) {
      logger.warn("AccID:" + sBatchObject.getAccessionRunId() + "." + sBatchObject.getJobFragmentNumber()
          + ": Retrying because could not insert into rpsResult. Exception:\n" + e);
      // Failure here causes a retry
    }
    //System.out.println("Completed method: sendQueryRPS with accession_run_id = " + sBatchObject.getAccessionRunId()
    //    + " job fragment# = " + sBatchObject.getJobFragmentNumber());
    logger.debug("Completed method: sendQueryRPS with accession_run_id = {} job fragment# = {}",
    		sBatchObject.getAccessionRunId(), sBatchObject.getJobFragmentNumber());
  }

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public int markBLASTpHitResults(final int accessionRunId, final String topHitAccessionId) {
//		System.out.println("Attempting method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		long start = new Date().getTime();
//
//		SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("UPDATE_rbh_rps");
//		Map<String, Object> inputParameterMap = new HashMap<String, Object>();
//		inputParameterMap.put("in_accrunid", accessionRunId);
//		inputParameterMap.put("in_ncbiversionid", ncbiKeeper.getPreferredNCBIProviderID());
//
//		SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
//		Map<String, Object> simpleJdbcCallResult = simpleJdbcCall.execute(sqlParameterSource);
//		if (simpleJdbcCallResult == null) {
//			return -1;
//		}
//		// System.out.println("simpleJdbcCallResult = " + simpleJdbcCallResult);
//
//		long speciesCount = -1;
//		try {
//			for (String key : simpleJdbcCallResult.keySet()) {
//				// System.out.println("simpleJdbcCallResult key = " + key);
//				Object value = simpleJdbcCallResult.get(key);
//				// System.out.println("simpleJdbcCallResult value = " + value);
//				if (key.equals("#result-set-1")) {
//					if (value.getClass().equals(ArrayList.class)) {
//						@SuppressWarnings("unchecked")
//						ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) value;
//						Map<String, Object> firstEntry = arrayList.get(0);
//						speciesCount = (Long) firstEntry.get("out_species_count");
//					}
//				}
//			}
//			System.out.println("Unique species count = " + speciesCount);
//		} catch (Exception e) {
//			System.out.println("Could not get species count from stored procedure: UPDATE_rbh_rps");
//			return -1;
//		}
//
//		long end = new Date().getTime();
//		long timeTook = end - start;
//		System.out.println("Milliseconds: " + timeTook + " for stored procedure UPDATE_rbh_rps.");
//
//		String query = "SELECT id FROM accession_hit WHERE accession_run_id = ? AND rbh_status = 'finished'";
//
//		List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, accessionRunId);
//		List<Integer> markedRbhHits = new ArrayList<Integer>();
//		System.out.println("Saved " + rows.size() + " RBH jobs");
//		for (int i = 0; i < rows.size(); i++) {
//			Map<String, Object> row = rows.get(i);
//			int id = (int) row.get("id");
//			markedRbhHits.add(id);
//		}
//		if (markedRbhHits.size() > 0) {
//			blastTools2.updateAccessionHit_rbh_status(ncbiKeeper.getPreferredNCBIProviderID(), accessionRunId,
//					topHitAccessionId, markedRbhHits);
//		}
//		System.out.println("Completed method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		return (int) speciesCount;
//	}

  // @Transactional
  // @Retryable(maxAttempts = 50, value = {
  // MySQLTransactionRollbackException.class, DataAccessException.class,
  // BatchUpdateException.class, RuntimeException.class }, backoff =
  // @Backoff(delay = 500, multiplier = 2))
  // public void markBLASTpHitResultsFull3(final int accessionRunId, final String
  // canonicalAccessionId,
  // final String queryAccessionId, final Set<String>
  // queryTaxidAccessionsAboveIdentity,
  // final List<String> identicalsFromQuerySpecies) {
  // System.out.println("Attempting method: markBLASTpHitResults with
  // accession_run_id = " + accessionRunId);
  //
  // SimpleJdbcCall simpleJdbcCall = new
  // SimpleJdbcCall(jdbcTemplate).withProcedureName("UPDATE_rbh_rps_full_parse");
  // Map<String, Object> inputParameterMap = new HashMap<String, Object>();
  // inputParameterMap.put("in_accrunid", accessionRunId);
  // inputParameterMap.put("in_ncbiversionid",
  // ncbiKeeper.getPreferredNCBIProviderID());
  //
  // SqlParameterSource sqlParameterSource = new
  // MapSqlParameterSource(inputParameterMap);
  // simpleJdbcCall.execute(sqlParameterSource);
  //
  // blastTools2.updateAccessionHit_rbh_status_full3(ncbiKeeper.getPreferredNCBIProviderID(),
  // accessionRunId,
  // canonicalAccessionId, queryAccessionId, queryTaxidAccessionsAboveIdentity,
  // identicalsFromQuerySpecies);
  //
  // System.out.println("Completed method: markBLASTpHitResults with
  // accession_run_id = " + accessionRunId);
  // return;
  // }

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void markBLASTpHitResultsFull2(final int accessionRunId, final String canonicalAccessionId,
//			final Set<String> queryTaxidAccessionsAboveIdentity) {
//		System.out.println("Attempting method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		// long start = new Date().getTime();
//
//		SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("UPDATE_rbh_rps_full_parse");
//		Map<String, Object> inputParameterMap = new HashMap<String, Object>();
//		inputParameterMap.put("in_accrunid", accessionRunId);
//		inputParameterMap.put("in_ncbiversionid", ncbiKeeper.getPreferredNCBIProviderID());
//
//		SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
//		// Map<String, Object> simpleJdbcCallResult =
//		// simpleJdbcCall.execute(sqlParameterSource);
//		simpleJdbcCall.execute(sqlParameterSource);
//		// if (simpleJdbcCallResult == null) {
//		// return;
//		// }
//		// // System.out.println("simpleJdbcCallResult = " +
//		// simpleJdbcCallResult);
//		//
//		// long speciesCount = -1;
//		// try {
//		// for (String key : simpleJdbcCallResult.keySet()) {
//		// // System.out.println("simpleJdbcCallResult key = " + key);
//		// Object value = simpleJdbcCallResult.get(key);
//		// // System.out.println("simpleJdbcCallResult value = " + value);
//		// if (key.equals("#result-set-1")) {
//		// if (value.getClass().equals(ArrayList.class)) {
//		// @SuppressWarnings("unchecked")
//		// ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String,
//		// Object>>) value;
//		// Map<String, Object> firstEntry = arrayList.get(0);
//		// speciesCount = (Long) firstEntry.get("out_species_count");
//		// }
//		// }
//		// }
//		// System.out.println("Unique species count = " + speciesCount);
//		// } catch (Exception e) {
//		// System.out.println("Could not get species count from stored
//		// procedure:
//		// UPDATE_rbh_rps");
//		// return -1;
//		// }
//
//		// blastTools2.updateAccessionHit_rbh_status_full2(ncbiKeeper.getPreferredNCBIProviderID(),
//		// accessionRunId,
//		// canonicalAccessionId, queryTaxidAccessionsAboveIdentity);
//
//		// long end = new Date().getTime();
//		// long timeTook = end - start;
//		// System.out.println("Milliseconds: " + timeTook + " for stored
//		// procedure
//		// UPDATE_rbh_rps.");
//		// System.out.println("Waiting 0...");
//		// try {
//		// Thread.sleep(15000);
//		// } catch (InterruptedException e) {
//		// // TODO Auto-generated catch block
//		// e.printStackTrace();
//		// }
//		// blastTools2.updateAccessionHit_rbh_status_full2a(ncbiKeeper.getPreferredNCBIProviderID(),
//		// accessionRunId,
//		// canonicalAccessionId, queryTaxidAccessionsAboveIdentity);
//		// System.out.println("Waiting 1...");
//		// try {
//		// Thread.sleep(15000);
//		// } catch (InterruptedException e) {
//		// // TODO Auto-generated catch block
//		// e.printStackTrace();
//		// }
//		// blastTools2.updateAccessionHit_rbh_status_full2b(ncbiKeeper.getPreferredNCBIProviderID(),
//		// accessionRunId,
//		// canonicalAccessionId, queryTaxidAccessionsAboveIdentity);
//		// System.out.println("Waiting 2...");
//		// try {
//		// Thread.sleep(15000);
//		// } catch (InterruptedException e) {
//		// // TODO Auto-generated catch block
//		// e.printStackTrace();
//		// }
//		// blastTools2.updateAccessionHit_rbh_status_full2c(ncbiKeeper.getPreferredNCBIProviderID(),
//		// accessionRunId,
//		// canonicalAccessionId, queryTaxidAccessionsAboveIdentity);
//
//		System.out.println("Completed method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		return;
//	}

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void markBLASTpHitResultsFull4a(final int accessionRunId) {
    SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("UPDATE_rbh_rps_full_parse");
    Map<String, Object> inputParameterMap = new HashMap<String, Object>();
    inputParameterMap.put("in_accrunid", accessionRunId);
    inputParameterMap.put("in_ncbiversionid", ncbiKeeper.getPreferredNCBIProviderID());

    SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
    simpleJdbcCall.execute(sqlParameterSource);
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void addRBHRows(final int accessionRunId) {
    SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("ADD_rbh_nova_rows");
    Map<String, Object> inputParameterMap = new HashMap<String, Object>();
    inputParameterMap.put("in_accrunid", accessionRunId);

    SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
    simpleJdbcCall.execute(sqlParameterSource);
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void updateAccessionHitRbhStatus(final int accessionRunId) {
    SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate)
        .withProcedureName("UPDATE_accession_hit_rbh_status");
    Map<String, Object> inputParameterMap = new HashMap<String, Object>();
    inputParameterMap.put("in_accrunid", accessionRunId);

    SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
    simpleJdbcCall.execute(sqlParameterSource);
  }

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void markBLASTpHitResultsFull4b(final int accessionRunId, final String canonicalAccessionId,
//			final String queryAccessionId, final Set<String> queryTaxidAccessionsAboveIdentity) {
//
//		blastTools2.updateAccessionHit_rbh_status_full4b(ncbiKeeper.getPreferredNCBIProviderID(), accessionRunId,
//				queryAccessionId, queryTaxidAccessionsAboveIdentity);
//		return;
//	}

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void markBLASTpHitResultsFull4c(final int accessionRunId, final String queryAccessionId) {
//
//		blastTools2.updateAccessionHit_rbh_status_full4c(ncbiKeeper.getPreferredNCBIProviderID(), accessionRunId,
//				queryAccessionId);
//		return;
//	}
//
//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void markBLASTpHitResultsFull4d(final int accessionRunId) {
//
//		blastTools2.updateAccessionHit_rbh_status_full4d(ncbiKeeper.getPreferredNCBIProviderID(), accessionRunId);
//		return;
//	}

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void markBLASTpHitResultsFull(final int accessionRunId, final String topHitAccessionId) {
//		System.out.println("Attempting method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		long start = new Date().getTime();
//
//		SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("UPDATE_rbh_rps_full_parse");
//		Map<String, Object> inputParameterMap = new HashMap<String, Object>();
//		inputParameterMap.put("in_accrunid", accessionRunId);
//		inputParameterMap.put("in_ncbiversionid", ncbiKeeper.getPreferredNCBIProviderID());
//
//		SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
//		// Map<String, Object> simpleJdbcCallResult =
//		// simpleJdbcCall.execute(sqlParameterSource);
//		simpleJdbcCall.execute(sqlParameterSource);
//		// if (simpleJdbcCallResult == null) {
//		// return;
//		// }
//		// // System.out.println("simpleJdbcCallResult = " +
//		// simpleJdbcCallResult);
//		//
//		// long speciesCount = -1;
//		// try {
//		// for (String key : simpleJdbcCallResult.keySet()) {
//		// // System.out.println("simpleJdbcCallResult key = " + key);
//		// Object value = simpleJdbcCallResult.get(key);
//		// // System.out.println("simpleJdbcCallResult value = " + value);
//		// if (key.equals("#result-set-1")) {
//		// if (value.getClass().equals(ArrayList.class)) {
//		// @SuppressWarnings("unchecked")
//		// ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String,
//		// Object>>) value;
//		// Map<String, Object> firstEntry = arrayList.get(0);
//		// speciesCount = (Long) firstEntry.get("out_species_count");
//		// }
//		// }
//		// }
//		// System.out.println("Unique species count = " + speciesCount);
//		// } catch (Exception e) {
//		// System.out.println("Could not get species count from stored
//		// procedure:
//		// UPDATE_rbh_rps");
//		// return -1;
//		// }
//
//		long end = new Date().getTime();
//		long timeTook = end - start;
//		System.out.println("Milliseconds: " + timeTook + " for stored procedure UPDATE_rbh_rps.");
//
//		blastTools2.updateAccessionHit_rbh_status_full(ncbiKeeper.getPreferredNCBIProviderID(), accessionRunId,
//				topHitAccessionId);
//
//		System.out.println("Completed method: markBLASTpHitResults with accession_run_id = " + accessionRunId);
//		return;
//	}
  
  @Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void insertUniprotIds(HashMap<String, String> accMap) {
		
	  try {
		String insertQuery = "INSERT INTO uniprot_ids(ncbi_accession, uniprot_accession) VALUES(?, ?)";
		PreparedStatement ps = jdbcTemplate.getDataSource().getConnection().prepareStatement(insertQuery);
		
		for(String key: accMap.keySet()) {
			ps.setString(1, key);
			String val = accMap.get(key);
			if (val == null) {
				ps.setNull(2,  Types.VARCHAR);
			} else {
				ps.setString(2, accMap.get(key));
			}
			ps.addBatch();
		}
		ps.executeBatch();
		ps.close();
		
	  } catch(SQLException ex) {
		  logger.error("Could not insert into uniprot_ids table");
	  }
	}
	
  
  @SuppressWarnings("rawtypes")
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public  ArrayList<String> getNCBIAccessionsFromUniProtTable() {
    ArrayList<String> results = new ArrayList<String>();
    String getIdsQuery = "SELECT ncbi_accession FROM uniprot_ids";
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(getIdsQuery);
    if(rows!= null) {
    	for (int i = 0; i < rows.size(); i++) {
    		Map<String, Object> row = rows.get(i);
    		String accession_id = (String) row.get("ncbi_accession");
    		results.add(accession_id);
    	}
    }
    return results;
  }
  
  @SuppressWarnings("rawtypes")
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public  ArrayList<String> getNCBIAccessionsFromAccessionRunId(final int accessionRunId) {
    ArrayList<String> results = new ArrayList<String>();
    String getIdsQuery = "SELECT hit_accession_id FROM accession_hit WHERE accession_run_id = ?";
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(getIdsQuery, accessionRunId);
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> row = rows.get(i);
      String hit_accession_id = (String) row.get("hit_accession_id");
      results.add(hit_accession_id);
    }
    return results;
  }

  @SuppressWarnings("rawtypes")
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public Map<String, List> getRBHnRPSinfo(final int accessionRunId, final int ncbiVersion) {
    //System.out.println("Attempting method: getRBHnRPSinfo with accession_run_id = " + accessionRunId);
    logger.debug("Attempting method: getRBHnRPSinfo with accession_run_id = {}", accessionRunId);
    Map<String, List> results = new HashMap<String, List>();
    List<Integer> rbhToRunIds = new ArrayList<Integer>();
    List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

    List<Integer> rpsToRunIds = new ArrayList<Integer>();
    List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
    long start = new Date().getTime();

    String getIdsQuery = "SELECT id, hit_accession_id, rbh_status, rps_status FROM accession_hit  WHERE accession_run_id = ? AND (rbh_status = 'queued' OR rps_status = 'queued')";
    //System.out.println("Query: " + getIdsQuery);
    logger.info("Query: {}", getIdsQuery);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(getIdsQuery, accessionRunId);
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> row = rows.get(i);
      int id = (int) row.get("id");
      String hit_accession_id = (String) row.get("hit_accession_id");

      String rbh_status = (String) row.get("rbh_status");
      String rps_status = (String) row.get("rps_status");
      if (rbh_status.equals("queued")) {
        rbhToRunIds.add(id);
        rbhAccessionIdsToRunIds.add(hit_accession_id);
      }
      if (rps_status.equals("queued")) {
        rpsToRunIds.add(id);
        rpsAccessionIdsToRunIds.add(hit_accession_id);
      }
    }
    long end = new Date().getTime();
    long timeTook = end - start;
    //System.out.println("Milliseconds: " + timeTook
    //    + " for COLLECT THE accession_hit.id's OF RBH AND RPS ROWS with row count: " + rows.size());
    logger.info("Milliseconds: {} for COLLECT THE accession_hit.id's OF RBH AND RPS ROWS with row count: {}", timeTook, rows.size());

    results.put("rbhIds", rbhToRunIds);
    results.put("rpsIds", rpsToRunIds);
    results.put("rbhAccs", rbhAccessionIdsToRunIds);
    results.put("rpsAccs", rpsAccessionIdsToRunIds);
    //System.out.println("Completed method: getRBHnRPSinfo with accession_run_id = " + accessionRunId);
    logger.debug("Completed method: getRBHnRPSinfo with accession_run_id = {}", accessionRunId);
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
    return results;
  }

  @SuppressWarnings("rawtypes")
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public Map<String, List> getRBHnRPSinfoFull(final int accessionRunId) {
    //System.out.println("Attempting method: getRBHnRPSinfo with accession_run_id = " + accessionRunId);
    logger.debug("Attempting method: getRBHnRPSinfo with accession_run_id = {}", accessionRunId);
    Map<String, List> results = new HashMap<String, List>();
    List<Integer> rbhToRunIds = new ArrayList<Integer>();
    List<String> rbhAccessionIdsToRunIds = new ArrayList<String>();

    List<Integer> rpsToRunIds = new ArrayList<Integer>();
    List<String> rpsAccessionIdsToRunIds = new ArrayList<String>();
    long start = new Date().getTime();

    String getIdsQuery = "SELECT id, hit_accession_id, rbh_status, rps_status FROM accession_hit  WHERE accession_run_id = ? AND (rbh_status = 'queued' OR rps_status = 'queued')";
    //System.out.println("Query: " + getIdsQuery);
    logger.info("Query: {}", getIdsQuery);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(getIdsQuery, accessionRunId);
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> row = rows.get(i);
      int id = (int) row.get("id");
      String hit_accession_id = (String) row.get("hit_accession_id");

      String rbh_status = (String) row.get("rbh_status");
      String rps_status = (String) row.get("rps_status");
      if (rbh_status.equals("queued")) {
        rbhToRunIds.add(id);
        rbhAccessionIdsToRunIds.add(hit_accession_id);
      }
      if (rps_status.equals("queued")) {
        rpsToRunIds.add(id);
        rpsAccessionIdsToRunIds.add(hit_accession_id);
      }
    }
    long end = new Date().getTime();
    long timeTook = end - start;
    //System.out.println("Milliseconds: " + timeTook
    //    + " for COLLECT THE accession_hit.id's OF RBH AND RPS ROWS with row count: " + rows.size());
    logger.info("Milliseconds: {} for COLLECT THE accession_hit.id's OF RBH AND RPS ROWS with row count: {}", 
    		timeTook, rows.size());
    
    results.put("rbhIds", rbhToRunIds);
    results.put("rpsIds", rpsToRunIds);
    results.put("rbhAccs", rbhAccessionIdsToRunIds);
    results.put("rpsAccs", rpsAccessionIdsToRunIds);
    //System.out.println("Completed method: getRBHnRPSinfo with accession_run_id = " + accessionRunId);
    logger.debug("Completed method: getRBHnRPSinfo with accession_run_id = {}", accessionRunId);
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
    return results;
  }

  @SuppressWarnings("unused")
  private void RBH_SPECIFIC_METHODS() { // ======================================================
  }

  private boolean createAccListFromTaxid(int taxid) {
    if (taxid < 1 || ncbiKeeper == null || jdbcTemplate == null) {
      return false;
    }
    String query = "SELECT COUNT(*) FROM protein WHERE taxid = ? and ncbi_valid_versions&?>0";
    int rowCount = jdbcTemplate.queryForObject(query, Integer.class, taxid,
        Math.pow(2, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion() - 1));
    //System.out.println("Taxid: " + taxid + " has " + rowCount + " accessions");
    logger.info("Taxid: {} has {} accessions", taxid, rowCount);
    
    String fullPath = getAccListPath(taxid);
    try {
      PrintWriter acclist = new PrintWriter(fullPath + "_proto");

      int batchSize = 100000;
      for (int batchStart = 0; batchStart < rowCount; batchStart += batchSize) {
        int batchCount = Math.min(rowCount - batchStart, batchSize);
        //System.out.println("Getting rows from " + batchStart);
        logger.info("Getting rows from {}",  batchStart);

        query = "SELECT accession_id FROM protein WHERE taxid = ? and ncbi_valid_versions&?>0 LIMIT ?, ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, taxid,
            Math.pow(2, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion() - 1), batchStart, batchCount);
        //System.out.println("Got that batch!");
        logger.info("Got that batch!");

        for (Map<String, Object> row : rows) {

          Object value = row.get("accession_id");
          if (value != null) {
            acclist.print(value + "\n");
            // acclist.println(value);
          }
        }
        acclist.flush();
      }
      acclist.close();
    } catch (FileNotFoundException e) {
      return false;
    }
    return fixAccList(taxid); // Don't forget to fix it!
  }

  private boolean fixAccList(int taxid) {
    String fullPathIn = getAccListPath(taxid) + "_proto";
    String fullPathOut = getAccListPath(taxid);

    File acclistFileProto = new File(fullPathIn);

    if (!acclistFileProto.exists() || acclistFileProto.length() == 0) {
      acclistFileProto.delete();
      return false;
    }

    ProcessProvider fixAcc = new ProcessProvider();
    fixAcc.setUserID(0);
    fixAcc.setJobID(0);
    fixAcc.setProcessType(ProcessTypeKeeper.getProcessTypeProviderByName(ProcessTypeKeeper.fixAccListProcess));
    fixAcc.setExecPath(ncbiKeeper.getPreferredNCBIProvider().getBlastExecPath());
    List<String> argVals = new ArrayList<String>();
    argVals.add(fullPathIn);
    argVals.add(fullPathOut);
    fixAcc.setArgVals(argVals);
    try {
      String[] runResults = fixAcc.run();
      String stdOut = runResults[0];
      String stdErr = runResults[1];
      //System.out.println("Fix acc out: " + stdOut);
      //System.out.println("Fix acc err: " + stdErr);
      logger.info("Fix acc out: {}", stdOut);
      logger.info("Fix acc err: {}", stdErr);

    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * /** This method returns the file system path name for a given acclist file given its taxid code. It is centralized
   * to allow easy update of the path, although the path is collected from the database.
   * 
   * @param taxid        the taxid for the species whose accession id will be collected
   * @param ncbiKeeperFS the ncbiKeeper object being used
   * @return The server file system path
   */
  public String getAccListPath(int taxid) {
    NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
    return ncbiProvider.getPathAcclist() + "/" + taxid + ".acclist";
  }

  public String createAccListIfNecessary(int taxid) {
    String fullPath = getAccListPath(taxid);
    File acclistFile = new File(fullPath);
    if (acclistFile.exists() && acclistFile.length() > 0) {
//			rsyncAccLists();
      return fullPath;
    }
    String fullPathProto = getAccListPath(taxid) + "_proto";
    File acclistFileProto = new File(fullPathProto);

    if (acclistFileProto.exists() && acclistFileProto.length() > 0) {
//			rsyncAccLists();
      if (fixAccList(taxid)) {
        return fullPath;
      }
      acclistFileProto.delete();
    }

    acclistFile.delete();

    if (!createAccListFromTaxid(taxid)) {
      return null;
    }
    acclistFile = new File(fullPath);
    if (acclistFile.exists() && acclistFile.length() > 0) {
//			rsyncAccLists();
      return fullPath;
    }

    return null;
  }

  public boolean isProd() {
    String query = "SELECT database()";
    String result = jdbcTemplate.queryForObject(query, String.class);
    //System.out.println("The database you are using is: " + result);
    logger.info("The database you are using is: {}", result);
    if (result.toLowerCase().equals("prod_seqapass")) {
      return true;
    }
    return false;
  }

//	public boolean rsyncAccLists() {
//		NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
//		String pathToSync = ncbiProvider.getPathAcclist();
//		String [] rsyncDirectoryCommand = {"/bin/rsync", "-a", pathToSync, "/work/SEQAPASS/sheg_mirror" };
//		ProcessBuilder processBuilder = new ProcessBuilder(rsyncDirectoryCommand);
//		Process process = null;
//		try {
//			process = processBuilder.start();
//			System.out.println("Sync of acclist directory was successful");
//			return true;
//			
//		}
//		catch(Exception e) {
//			System.out.println("Could not rsync acclists directory" + e);
//			return false;
//		}
//		
//	}

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public boolean setRbhCurrentAccessionIds(final List<Integer> accessionHitIds, final int accessionRunId,
      final String status) {
    //System.out.println("Attempting method: setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
    logger.debug("Attempting method: setRbhCurrentAccessionIds with accession_run_id = {}", accessionRunId);
    if (accessionHitIds.isEmpty()) {
      //System.out.println("Completed (nothing to do): setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
      logger.debug("Completed (nothing to do): setRbhCurrentAccessionIds with accession_run_id = {}", accessionRunId);
      return true;
    }
    if (!status.equals("queued") && !status.equals("started") && !status.equals("finished")
        && !status.equals("not run")) {
      //System.out.println("Completed method: setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
      logger.debug("Completed method: setRbhCurrentAccessionIds with accession_run_id = {}", accessionRunId);
      return false;
    }
    if (accessionHitIds.size() < 0 || accessionRunId < 0) {
      //System.out.println("Completed method: setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
      logger.debug("Completed method: setRbhCurrentAccessionIds with accession_run_id = {}", accessionRunId);
      return false;
    }
    if (accessionHitIds.size() == 0) {
      //System.out.println("Completed method: setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
      return true;
    }
    int batchSize = 500;
    String updateQuery = "UPDATE accession_hit SET rbh_status = '" + status + "' WHERE id in (";
    for (int batch = 0; batch <= accessionHitIds.size(); batch += batchSize) {
      // TODO - double check this
      int end = Math.min(batch + batchSize, accessionHitIds.size());
      if (end > batch) {
        String batchString = Joiner.on(",").join(accessionHitIds.subList(batch, end));
        jdbcTemplate.update(updateQuery + batchString + ");");
      }
    }
    //System.out.println("Completed method: setRbhCurrentAccessionIds with accession_run_id = " + accessionRunId);
    logger.debug("Completed method: setRbhCurrentAccessionIds with accession_run_id = {}", accessionRunId);
    return true;
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void updateAccessionRunRbhCompleteness(final int accessionRunId, int done) {
    //System.out.println("Attempting method: updateAccessionRunRbhCompleteness with accession_run_id = " + accessionRunId);
    logger.debug("Attempting method: updateAccessionRunRbhCompleteness with accession_run_id = {}", accessionRunId);
    // + " and percent done = " + done
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
    if (done > 100) {
      done = 100;
    }
    if (done < 0) {
      done = 0;
    }
    String updateQuery = "UPDATE accession_run SET rbh_completeness = ? WHERE id = ?";
    jdbcTemplate.update(updateQuery, done, accessionRunId);
    //System.out.println("Completed method: updateAccessionRunRbhCompleteness with accession_run_id = " + accessionRunId);
    logger.debug("Completed method: updateAccessionRunRbhCompleteness with accession_run_id = {}", accessionRunId);
    // + " and percent done = " + done
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void updateAccessionRunRpsCompleteness(final int accessionRunId, int done) {
    //System.out.println("Attempting method: updateAccessionRunRpsCompleteness with accession_run_id = " + accessionRunId);
    logger.debug("Attempting method: updateAccessionRunRpsCompleteness with accession_run_id = {}", accessionRunId);
    // + " and percent done = " + done
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
    if (done > 100) {
      done = 100;
    }
    if (done < 0) {
      done = 0;
    }
    String updateQuery = "UPDATE accession_run SET rps_completeness = ? WHERE id = ?";
    jdbcTemplate.update(updateQuery, done, accessionRunId);
    //System.out.println("Completed method: updateAccessionRunRpsCompleteness with accession_run_id = " + accessionRunId);
    logger.debug("Completed method: updateAccessionRunRpsCompleteness with accession_run_id = {}", accessionRunId);
    // + " and percent done = " + done
    // +
    // " and retryable value(s) = DataAccessException.class,
    // BatchUpdateException.class, RuntimeException.class");
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public boolean setRpsCurrentAccessionIds(String status, List<Integer> accessionHitIds, int accessionRunId) {
    if (!status.equals("queued") && !status.equals("started") && !status.equals("finished")
        && !status.equals("not run")) {
      return false;
    }
    if (accessionHitIds.size() < 0 || accessionRunId < 0) {
      return false;
    }
    if (accessionHitIds.size() == 0) {
      return true;
    }
    int batchSize = 1000;
    String updateQuery = "UPDATE accession_hit SET rps_status = '" + status + "' WHERE id in (";
    for (int batch = 0; batch <= accessionHitIds.size(); batch += batchSize) {
      // TODO - double check this
      int end = Math.min(batch + batchSize, accessionHitIds.size());
      String batchString = Joiner.on(",").join(accessionHitIds.subList(batch, end));
      if (!batchString.equals("")) {
        jdbcTemplate.update(updateQuery + batchString + ");");
      }
    }
    return true;
  }

  // @Transactional
  // @Retryable(maxAttempts = 50, value = {
  // MySQLTransactionRollbackException.class, DataAccessException.class,
  // BatchUpdateException.class, RuntimeException.class }, backoff =
  // @Backoff(delay = 500, multiplier = 2))
  // public int updateRpsCDDCounts(final int accessionRunId, final String
  // accessionId) {
  // System.out.println("Attempting method: updateRpsCDDCounts with AccessionRunId
  // = " + accessionRunId);
  // int ncbiId = ncbiKeeper.getPreferredNCBIProviderID();
  // long start = new Date().getTime();
  //
  // // FIRST GET id AND hit_accession_id FROM ALL RPS COMPLETE accession_hit
  // // ROWS
  // Map<Integer, String> accessionsToUpdate = new HashMap<Integer, String>();
  // List<Map<String, Object>> idAndAccessionIds = jdbcTemplate.queryForList(
  // "SELECT id, hit_canonical_id FROM accession_hit WHERE accession_run_id = ?
  // AND rps_status = 'finished'",
  // accessionRunId);
  // for (Map<String, Object> pair : idAndAccessionIds) {
  // Integer id = (Integer) pair.get("id");
  // String hitCanonicalId = (String) pair.get("hit_canonical_id");
  // accessionsToUpdate.put(id, hitCanonicalId);
  // }
  //
  // String getDomainsQuery = "SELECT xml_Hit_accession, `xml_Hsp_hit-from`,
  // `xml_Hsp_hit-to` FROM rps_result WHERE accession_id = ? AND ncbi_version_id =
  // ? ORDER BY xml_Hit_accession";
  // // Get the domains for the query protein
  // List<Map<String, Object>> queryDomains =
  // jdbcTemplate.queryForList(getDomainsQuery, accessionId, ncbiId);
  // int queryDomainCount = queryDomains.size();
  // String updateQuery = "UPDATE accession_hit SET cdd_count = ? WHERE id = ?";
  //
  // for (Integer id : accessionsToUpdate.keySet()) {
  // String hitCanonicalId = accessionsToUpdate.get(id);
  // List<Map<String, Object>> hitDomains =
  // jdbcTemplate.queryForList(getDomainsQuery, hitCanonicalId, ncbiId);
  // int commonDomains = countCommonDomains(queryDomains, hitDomains);
  // jdbcTemplate.update(updateQuery, commonDomains, id);
  // }
  //
  // // NOTE: added above limit at 100 on 2016-03-15 so as to speed up long
  // // running MySQL queries.
  // long end = new Date().getTime();
  // long time = end - start;
  // int totalCount = idAndAccessionIds.size();
  // System.out.println(totalCount + " cdd counts with up to " + queryDomainCount
  // + " conserved domains updated in "
  // + time + " milliseconds for rpsBLAST with accession_run.id = " +
  // accessionRunId);
  // System.out.println("Completed method: updateRpsCDDCounts with AccessionRunId
  // = " + accessionRunId);
  // return totalCount;
  // }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public int updateRpsCDDCountsWTopHit(final int accessionRunId) {
    //System.out.println("Attempting method: updateRpsCDDCounts with AccessionRunId = " + accessionRunId);
    logger.debug("Attempting method: updateRpsCDDCounts with AccessionRunId = {}", accessionRunId);
    int ncbiId = ncbiKeeper.getPreferredNCBIProviderID();
    long start = new Date().getTime();

    // FIRST GET id AND hit_accession_id FROM ALL RPS COMPLETE accession_hit
    // ROWS
    Map<Integer, String> accessionsToUpdate = new HashMap<Integer, String>();
    List<Map<String, Object>> idAndAccessionIds = null;

    try {
      idAndAccessionIds = jdbcTemplate.queryForList(
          "SELECT id, hit_canonical_id FROM accession_hit WHERE accession_run_id = ? AND rps_status = 'finished' ORDER BY id",
          accessionRunId);
    } catch (DataAccessException e) {
      e.printStackTrace();
      return 0;
    }
    if (idAndAccessionIds == null || idAndAccessionIds.size() == 0) {
      return 0;
    }
    String topHitCanonical = (String) idAndAccessionIds.get(0).get("hit_canonical_id");

    for (Map<String, Object> pair : idAndAccessionIds) {

      Integer id = (Integer) pair.get("id");
      String hitCanonicalId = (String) pair.get("hit_canonical_id");
      accessionsToUpdate.put(id, hitCanonicalId);
    }

    String getDomainsQuery = "SELECT xml_Hit_accession, `xml_Hsp_hit-from`, `xml_Hsp_hit-to` FROM rps_result WHERE accession_id = ? AND ncbi_version_id = ? ORDER BY xml_Hit_accession";
    // Get the domains for the query protein
    List<Map<String, Object>> queryDomains = jdbcTemplate.queryForList(getDomainsQuery, topHitCanonical, ncbiId);
    int queryDomainCount = queryDomains.size();
    String updateQuery = "UPDATE accession_hit SET cdd_count = ? WHERE id = ?";

    for (Integer id : accessionsToUpdate.keySet()) {
      String hitCanonicalId = accessionsToUpdate.get(id);
      List<Map<String, Object>> hitDomains = jdbcTemplate.queryForList(getDomainsQuery, hitCanonicalId, ncbiId);
      int commonDomains = countCommonDomains(queryDomains, hitDomains);
      jdbcTemplate.update(updateQuery, commonDomains, id);
    }

    // NOTE: added above limit at 100 on 2016-03-15 so as to speed up long
    // running MySQL queries.
    long end = new Date().getTime();
    long time = end - start;
    int totalCount = idAndAccessionIds.size();
    //System.out.println(totalCount + " cdd counts with up to " + queryDomainCount + " conserved domains updated in "
    //    + time + " milliseconds for rpsBLAST with accession_run.id = " + accessionRunId);
    //System.out.println("Completed method: updateRpsCDDCounts with AccessionRunId = " + accessionRunId);
    logger.info("{} cdd counts with up to {} conserved domains updated in {} milliseconds for rpsBLAST with accession_run.id = {}", 
    		totalCount, queryDomainCount, time, accessionRunId);
    logger.debug("Completed method: updateRpsCDDCounts with AccessionRunId = {}",  accessionRunId);
    return totalCount;
  }

  private int countCommonDomains(List<Map<String, Object>> queryDomains, List<Map<String, Object>> hitDomains) {
    Set<Integer> hits = new HashSet<Integer>();
    int hitDomainStartIndex = 0;
    int nextHitDomain = -1;
    for (Map<String, Object> queryDomain : queryDomains) {
      int queryDomainId = (int) queryDomain.get("xml_Hit_accession");
      if (hits.contains(queryDomainId)) {
        continue;
      }
      if (queryDomainId < nextHitDomain) {
        continue;
      }
      int queryFrom = (int) queryDomain.get("xml_Hsp_hit-from");
      int queryTo = (int) queryDomain.get("xml_Hsp_hit-to");
      for (int index = hitDomainStartIndex; index < hitDomains.size(); index++) {
        Map<String, Object> hitDomain = hitDomains.get(index);
        nextHitDomain = (int) hitDomain.get("xml_Hit_accession");
        if (queryDomainId < nextHitDomain) {
          hitDomainStartIndex = index;
          break;
        }
        if (queryDomainId == nextHitDomain) {
          int hitFrom = (int) hitDomain.get("xml_Hsp_hit-from");
          int hitTo = (int) hitDomain.get("xml_Hsp_hit-to");
          if (queryFrom >= hitFrom && queryTo <= hitTo) {
            hits.add(queryDomainId);
            hitDomainStartIndex = index + 1;
            break;
          }
        }
      }
    }
    return hits.size();
  }

  @SuppressWarnings("unused")
  private void LEVEL2_SPECIFIC_METHODS() { // ======================================================
  }

  // /**
  // * This method creates a List of LinkedLists for one "Hit" entry from the
  // XML output from BLASTp. It requires a zero or greater
  // * accession_run_id whcih is prepended to the start of all results lists.
  // *
  // * @param accession_run_id
  // * @param oneHit
  // * A String containing the XML content of one "Hit" result from BLASTp
  // * @return
  // */
  // public List<ArrayList<String>> processOneLevel2Hit(int level2RunId,
  // String oneHit) {
  // if (level2RunId < 0) {
  // return null;
  // }
  // List<ArrayList<String>> results = new ArrayList<ArrayList<String>>();
  //
  // StringBuilder pattern1Builder = new StringBuilder();
  // StringBuilder pattern2Builder = new StringBuilder();
  //
  // for (String name : blastPxmlTypes.keySet()) {
  // if (name.startsWith("Hit_")) {
  // pattern1Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
  // } else if (name.startsWith("Hsp_")) {
  // pattern2Builder.append("<" + name + ">(.*?)</" + name + ">.*?");
  // }
  // }
  // Pattern pattern1 = Pattern.compile(pattern1Builder.toString());
  // Pattern pattern2 = Pattern.compile(pattern2Builder.toString());
  //
  // // String pattern3String =
  // "gi\\|\\d+\\|([^\\|]+)\\|([^\\|]+)\\|([^\\|]+)$";
  // // Pattern pattern3 = Pattern.compile(pattern3String);
  //
  // LinkedList<String> baseValues = new LinkedList<String>();
  // baseValues.add(level2RunId + "");
  // Matcher matcher1 = pattern1.matcher(oneHit);
  // if (matcher1.find()) {
  // String parsedAccession = null;
  // for (int i = 1; i < matcher1.groupCount() + 1; i++) {
  // String value = StringEscapeUtils.unescapeXml(matcher1.group(i));
  // // The second entry is "Hit_id", but it is always of form "Subject_##"
  // // The fourth entry is "Hit_accession", but it is same as "Hit_id"
  // // The third entry is "Hit_def", and so we must use this to get the
  // accession_id
  // if (i == 3) {
  // parsedAccession = value;
  // }
  // baseValues.add(value);
  // }
  // // Now, the parsedAccession must be added as the second item (after
  // accession_run_id);
  // baseValues.add(1, parsedAccession);
  // } else {
  // return null;
  // }
  //
  // Matcher matcher2 = pattern2.matcher(oneHit);
  // while (matcher2.find()) {
  // ArrayList<String> allValues = new ArrayList<String>();
  // allValues.addAll(baseValues);
  // for (int i = 1; i < matcher2.groupCount() + 1; i++) {
  // allValues.add(StringEscapeUtils.unescapeXml(matcher2.group(i)));
  // }
  // results.add(allValues);
  // }
  // if (results.get(0).size() == baseValues.size()) {
  // return null;
  // }
  // return results;
  // }

  /**
   * This method is intended to be generally applicable to inserting a set of values into the accession_hit table. It
   * takes a single list of Names, and a List of Lists of Values.
   * 
   * @param nameList
   * @param valuesList
   * @param jdbcTemplate
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void saveLevel2Batch(final List<String> nameList, final List<ArrayList<String>> valuesList) {
    //System.out.println("Attempting method: saveLevel2Batch with " + valuesList.size() + " values level2_run_id = "
    //    + valuesList.get(0).get(0));
    logger.debug("Attempting method: saveLevel2Batch with {} values level2_run_id = {}", 
    		valuesList.size(), valuesList.get(0).get(0));
    final int nameSize = nameList.size();
    StringBuilder b = new StringBuilder();
    b.append("INSERT IGNORE INTO level2_result (");
    StringBuilder qMarks = new StringBuilder();
    for (String name : nameList) {
      if (name.equals("level2_run_id") || name.equals("hit_accession_id")) {
        b.append("`" + name + "`,");
      } else {
        b.append("`xml_" + name + "`,");
      }
      qMarks.append("?,");
    }
    b.deleteCharAt(b.length() - 1);
    qMarks.deleteCharAt(qMarks.length() - 1);
    b.append(") VALUES (");
    b.append(qMarks.toString());
    b.append(")");

    final String insertQuery = b.toString();
    final int batchSize = 500;
    for (int index = 0; index < valuesList.size(); index += batchSize) {
      int last = Math.min(index + batchSize, valuesList.size());
      final List<ArrayList<String>> batch = valuesList.subList(index, last);

      jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
          ArrayList<String> values = batch.get(i);
          if (values.size() == nameSize) {
            for (int j = 1; j < nameList.size() + 1; j++) {
              String name = nameList.get(j - 1);
              String value = values.get(j - 1);
              if (name.equals("level2_run_id")) {
                ps.setInt(j, Integer.parseInt(value));
              } else if (name.equals("hit_accession_id")) {
                ps.setString(j, value);
              } else if (blastPxmlTypes.get(name).equals("String")) {
                ps.setString(j, value);
              } else if (blastPxmlTypes.get(name).equals("double")) {
                ps.setDouble(j, Double.parseDouble(value));
              } else if (blastPxmlTypes.get(name).equals("int")) {
                ps.setInt(j, Integer.parseInt(value));
              } else {
                ps.setString(j, value);
              }
            }
          }
        }

        @Override
        public int getBatchSize() {
          return batch.size();
        }
      });
      //System.out.println("Completed method: saveLevel2Batch with " + valuesList.size() + " values accession_run_id = "
      //    + valuesList.get(0).get(0));
      logger.debug("Completed method: saveLevel2Batch with {} values level2_run_id = {}", 
      		valuesList.size(), valuesList.get(0).get(0));
    }
  }

  @SuppressWarnings("unused")
  private void COBALT_SPECIFIC_METHODS() { // ======================================================
  }

  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public void updateLevel3TemplateName(int level3RunId, String templateName) {
    String updateQuery = "UPDATE level3_run SET template_name = ? WHERE id = ?";
    jdbcTemplate.update(updateQuery, templateName, level3RunId);
  }

  // /**
  // * This method takes either a fasta sequence or an accession ID and
  // returns both an accessionIdString and the path to the fasta file.
  // If
  // * the input string is not valid for either, it returns null.
  // *
  // * @param template
  // * - a String containing either a fasta sequence (first line >protein
  // info, subsequent lines are AA sequence in all caps)
  // * @return a String[2] with the accession id followed by the path to the
  // fasta file
  // */
  // public String[] validateTemplate(int accessionRunId, int userId, String
  // jobName, String template) {
  // if (jobName.matches(".*[^a-zA-Z0-9_-].*")) {
  // return null;
  // }
  // String[] results = new String[2];
  // if (template.startsWith(">")) {
  // String[] parts = template.split("[\\x00\\n\\r]", 2);
  // if (parts[0].matches("[^ -~]")) { // REJECT NON-ASCII CHARACTERS AND <CR>
  // <TAB> <LF> TYPE CHARS
  // String[] failure = new String[1];
  // failure[0] = "First line of fasta contains unacceptable characters";
  // return failure;
  // }
  // if (parts[1].matches("[^ACDEFGHIKLMNPQRSTVWYX\\x00\\n\\r-]")) {
  // String[] failure = new String[1];
  // failure[0] = "Fasta sequence contains unacceptable characters";
  // return failure;
  // }
  // String root = ncbiKeeper.getPreferredNCBIProvider().getPathFasta();
  // String path = root + "level3-" + accessionRunId + "-" + userId +
  // "jobName" + ".fsa";
  // // File file = new File(path);
  //
  // try {
  // PrintWriter fastaFile = new PrintWriter(path);
  // fastaFile.print(parts[1]);
  // fastaFile.flush();
  // fastaFile.close();
  // } catch (Exception e) {
  // System.out.println("Write custom fasta failed for filename: " + path);
  // String[] failure = new String[1];
  // failure[0] = "Could not write fasta file";
  // return failure;
  // }
  // results[0] = parts[0].substring(1);
  // results[1] = path;
  // return results;
  // } else {
  // if (getTaxidFromAccessionIdName(template) < 0) {
  // String[] failure = new String[1];
  // failure[0] = "Accession ID not found";
  // return failure;
  // }
  // if (!createFastaFileIfNecessary(template)) {
  // String[] failure = new String[1];
  // failure[0] = "Could not create fasta file";
  // return failure;
  // }
  // }
  // results[0] = template;
  // results[1] = getFastaPath(template);
  // return results;
  // }

  /** 
   * This method uses the targetDefs for the <i>sequence_def</i> field and
   * parses the textToParse (output of Cobalt) to get the <i>seq</i> (sequences).
   * The textToParse is split on &gt; characters, then the sequence_def length is removed,
   * although we could likely use the &lt;CR&gt; characters to split.
   * 
   * @param int level3RunId
   * @param List<String> targetDefs
   * @param String textToParse
   * @return boolean (success)
   * 
   */
  @Transactional
  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
  public boolean saveCobaltResults(int level3RunId, List<String> targetDefs, String textToParse) {
    if (level3RunId < 0 || targetDefs.size() < 2 || textToParse == null) {
      return false;
    }
    logger.debug("Attempting saveCobaltResults for level3_run_id: {}, and {} targetDefs, and a textToParse with length {}\n",level3RunId,targetDefs.size(),textToParse.length());

    //System.out.println("Testing this string for Level3 results: " + textToParse + "\n");
    //logger.info("Testing this string for Level3 results: {} \n", textToParse);
    // Note that the textToParse averaged 116KB for 700 or so Level 3 runs.  No need to put it in the logs
    String trimmedFirstGreaterThan = textToParse.replaceFirst("^>>", ">");
    String[] cobaltResultHits = trimmedFirstGreaterThan.split(">");
    int result_count = cobaltResultHits.length - 1;
    if (result_count != targetDefs.size()) {
      //System.out.println(" Results of COBALT should be same as sequences input count; however, result count = "
      //    + result_count + " ,and target count =  " + targetDefs.size());
      logger.warn(" Results of COBALT should be same as sequences input count; however, result count = {} and target count = {}", 
    		  result_count, targetDefs.size());
    }
    int i = -1;
    for (i = 0; i < targetDefs.size(); i++) {
      String sequenceDef = targetDefs.get(i);
      String allASCII = sequenceDef.replaceAll("[^ -~]", "?"); // Non-ASCII characters will each be replaced with a ?
      if (!allASCII.equals(sequenceDef)) {
    	sequenceDef = allASCII;
    	logger.warn("Converted at least one non-ASCII character to a ? .  Byte count changed from {} to {}", sequenceDef.getBytes().length, allASCII.getBytes().length);
      }
      String defPlusSeq = cobaltResultHits[i + 1];

      int seqDefLength = sequenceDef.length(); // Number of characters, so doesn't matter if non-ASCII replaced
      // if (sequenceDef.startsWith("(user defined) ") && i > 0) {
      // headerLength -= 15;
      // }
      String sequence = defPlusSeq.substring(seqDefLength); // Cut off the def at the beginning to leave the seq

      String insertQuery = "INSERT IGNORE INTO level3_result (level3_run_id, sequence_def, seq)" + " VALUES(?, ?, ?)";

      int oneIfNewRow = 0;
      try {
        oneIfNewRow = jdbcTemplate.update(insertQuery, level3RunId, sequenceDef, sequence);
      } catch (DataAccessException e) {
        logger.catching(e);
        e.printStackTrace();
      }
      
      String additionalCompQuery = "UPDATE level3_result a SET a.taxid = ?, a.title = ? WHERE level3_run_id = ? and sequence_def = ?";
      int taxId = -1;
      String title = "";
      //retrieve taxId and protein title for additional comparison (or template)
      
      if (i == 0 && oneIfNewRow == 1) {
    	  //this is the template accession
    	  List<Object> res = getTaxidAndTitleFromAccessionIdName(sequenceDef);
    	  taxId = (int) res.get(0);
    	  title = (String) res.get(1);
    	  int retVal = jdbcTemplate.update(additionalCompQuery, taxId, title, level3RunId, sequenceDef);
    	  logger.info("Template accession updated: {} row(s)",retVal);
      }
      if (i > 0 && oneIfNewRow == 1) {

        String updateQuery = "UPDATE level3_result a, level3_run b, accession_hit c SET a.level1_bitscore = c.`xml_Hsp_bit-score` "
            + "WHERE a.sequence_def = ? AND c.hit_accession_id = ? AND a.level3_run_id = ? AND b.id = ? AND b.accession_run_id = c.accession_run_id";
        try {
//					System.out.println(updateQuery + " - " + sequenceDef + " - " + level3RunId);

          int recordsUpdated = jdbcTemplate.update(updateQuery, sequenceDef, sequenceDef, level3RunId, level3RunId);
					//System.out.println("After recordsUpdated: " + recordsUpdated);
					logger.info("After recordsUpdated: {}", recordsUpdated);
          
          if (recordsUpdated == 0) {
        	  //since the record was not updated, this is an additional comparison
        	  List<Object> res = getTaxidAndTitleFromAccessionIdName(sequenceDef);
        	  taxId = (int) res.get(0);
        	  title = (String) res.get(1);
        	  int retVal = jdbcTemplate.update(additionalCompQuery, taxId, title, level3RunId, sequenceDef);
        	  logger.info("Additional comparision accession updated: {} row(s)",retVal);
          }
        } catch (DataAccessException e) {
          logger.catching(e);
          e.printStackTrace();
        }
      }
    }
    logger.debug("Completed saveCobaltResults for level3_run_id: {}, and {} targetDefs, and a textToParse with length {}\n",level3RunId,targetDefs.size(),textToParse.length());
    return true;
  }

  @SuppressWarnings("unused")
  private void FINAL_ANALYSIS_SPECIFIC_METHODS() { // ======================================================
  }

  public void beginAnalysis(boolean rbhJobsConfirmedFinished, boolean rpsJobsConfirmedFinished, int accessionRunId,
      int userId) {

//		int accessionRunId = accessionRun.getAccessionRunId();

    if (!rbhJobsConfirmedFinished) {
      String query = "SELECT count(*) FROM accession_hit WHERE accession_run_id = ? AND rbh_status IN ('queued','started')";
      if (jdbcTemplate.queryForObject(query, int.class, accessionRunId) == 0) {
        rbhJobsConfirmedFinished = true;
      }
    }
    if (!rpsJobsConfirmedFinished) {
      String query = "SELECT count(*) FROM accession_hit WHERE accession_run_id = ? AND rps_status IN ('queued','started')";
      if (jdbcTemplate.queryForObject(query, int.class, accessionRunId) == 0) {
        rpsJobsConfirmedFinished = true;
      }
    }
    if (rbhJobsConfirmedFinished && rpsJobsConfirmedFinished) {
      blastTools2.copyResultsToDups(accessionRunId);
      blastTools2.updateAccessionRunStatus(accessionRunId, "analyzing");
//			CutoffData primaryCutoffData = blastTools2.generateLevelOnePrimaryCutoff(accessionRunId);
//			CutoffData fullCutoffData = blastTools2.generateLevelOneFullCutoff(accessionRunId);
//			blastTools2.insertLevelOneCutoff(accessionRunId, primaryCutoffData, fullCutoffData);
      blastTools2.setAccessionRunCompletion(accessionRunId); // TODO -
      // consider
      // not
      // setting
      // this
      // until
      // analysis
      // is
      // complete.
      // Calculate default ortholog count for main report
      boolean isEukaryote = blastTools2.checkIfEukaryote(accessionRunId);
      int orthologCnt = blastTools2.getDefaultOrthologCount(accessionRunId, isEukaryote);
      // insert ortholog count into accession_run table
      blastTools2.setOrthologCount(accessionRunId, orthologCnt);

      //System.out.println("Attempting createLevelOneReports for seqapass_reports");
      logger.debug("Attempting createLevelOneReports for seqapass_reports");
      blastTools2.createLevelOneReports(accessionRunId, -1, 1); // creates
      // for
      // seqapass_reports
      // destination

      createToxCastReport(accessionRunId, userId);
      // int userId =
      // blastTools2.getUserIdFromAccessionRunId(accessionRunId);
//			int toxCastUserID = blastTools2.getToxCastUserId();
//
//			if (userId == toxCastUserID) {
//				System.out.println("Attempting createLevelOneReports for public_reports");
//				blastTools2.createLevelOneReports(accessionRunId, toxCastUserID, 2); // creates for public_reports
//																						// destination
//			}
      //System.out.println("Almost done.  Updating status with accessionRunId: " + accessionRunId);
      logger.info("Almost done.  Updating status with accessionRunId: {}", accessionRunId);
      blastTools2.updateAccessionRunStatus(accessionRunId, "complete");
    }
  }

  public void createToxCastReport(int accessionRunId, int userId) {
    int toxCastUserID = blastTools2.getToxCastUserId();

    //System.out.println("userId = " + userId);
    //System.out.println("taxCastUserId = " + toxCastUserID);
    logger.info("userId = {}", userId);
    logger.info("taxCastUserId = {}", toxCastUserID);

    if (userId == toxCastUserID) {
      //System.out.println("Attempting createLevelOneReports for public_reports");
      logger.debug("Attempting createLevelOneReports for public_reports");
      blastTools2.createLevelOneReports(accessionRunId, toxCastUserID, 2); // creates for public_reports
      // destination
    }
  }
  
  public List<String> getToxcastAccIdsForDataVersion(int dataVersion) {
	  
	//get all ncbiVersionIds for supplied dataVersion
	ReportService reportService = blastTools2.getReportService(); 
	int ncbiVersionId = reportService.getNCBIVersions(dataVersion);
		
	int toxCastUserId = reportService.getToxCastUserId();
	
	//get List of toxCast level one runs in supplied dataVersion
	StringBuilder sb = new StringBuilder();
	sb.append("SELECT distinct query_accession_id ");
	sb.append("FROM user_run u ");
	sb.append("JOIN user_run_accession_run ua ON u.id = ua.user_run_id ");
	sb.append("JOIN accession_run a ON a.id = ua.accession_run_id ");
	sb.append("WHERE user_id = :toxcastId ");
	sb.append("AND a.ncbi_version_id = :ncbiVersionId ");
	sb.append("ORDER BY u.id DESC ");
	
	MapSqlParameterSource  params = new MapSqlParameterSource();
	params.addValue("toxcastId", toxCastUserId);
	params.addValue("ncbiVersionId", ncbiVersionId);
	NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	List<Map<String, Object>> oldVersionRuns = namedJdbcTemplate.queryForList(sb.toString(), params);
	
	//now get list of toxcast level one runs in current data version
	//in case any have already been run or toxcast runs have been restarted
	ncbiVersionId = ncbiKeeper.getPreferredNCBIProviderID();
	params.addValue("ncbiVersionId", ncbiVersionId);
	
	List<String> existingQueryAccs = new ArrayList<String>();
	List<Map<String, Object>> currentVersionRuns = namedJdbcTemplate.queryForList(sb.toString(), params);
	for (Map<String, Object> entry: currentVersionRuns) {
		String queryAcc = (String)entry.get("query_accession_id");
		existingQueryAccs.add(queryAcc);
	}
	
	List<String> queryAccs = new ArrayList<String>();
	for (Map<String, Object> entry: oldVersionRuns) {
		String queryAcc = (String)entry.get("query_accession_id");
		if(!existingQueryAccs.contains(queryAcc)) {
			queryAccs.add(queryAcc);
		}
	}
	
	return queryAccs;
	  
	  
  }
  
  public List<LevelTwoRequestableRow> getToxcastLevelTwoRequestablesForDataVersion(int dataVersion) {
	//get all ncbiVersionId for supplied dataVersion
	  //should be one ncbiVersionId for each update version,
	  //but can cover multiple seqapass versions
		ReportService reportService = blastTools2.getReportService(); 
		int ncbiVersionId = reportService.getNCBIVersions(dataVersion);
			
		int toxCastUserId = reportService.getToxCastUserId();
		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT a.accession_run_id, "); 
		sb.append("a.cdd_accession_num, a.start_position, ");
		sb.append("d.query_accession_id ");
		sb.append("FROM level2_run a JOIN accession_run b ");
		sb.append("ON a.accession_run_id = b.id ");
		sb.append("JOIN version c ON b.ncbi_version_id = c.id ");
		sb.append("JOIN user_run_accession_run d ON d.accession_run_id = b.id ");
		sb.append("where ncbi_version_id = :ncbiVersionId ");
		sb.append("AND user_id = :toxcastId");
		
		MapSqlParameterSource  params = new MapSqlParameterSource();
		params.addValue("ncbiVersionId", ncbiVersionId);
		params.addValue("toxcastId", toxCastUserId);
		NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		List<Map<String, Object>> tmpMap = namedJdbcTemplate.queryForList(sb.toString(), params);
		
		
		List<LevelTwoRequestableRow> reqs = new ArrayList<LevelTwoRequestableRow>();
		List<String> queryAccs = new ArrayList<String>();
		for (Map<String,Object> entry: tmpMap) {
			//int accRunId = (int)entry.get("accession_run_id");
			int accRunId = -1;  //Set this as marker until accRunId from latest data version can be found
			String queryAcc = (String)entry.get("query_accession_id");
			int domainNumber = (int)entry.get("cdd_accession_num");
			int startPos = (int)entry.get("start_position");
			String key = Integer.toString(domainNumber) + ":" + Integer.toString(startPos);
			LevelTwoRequestableRow req = new LevelTwoRequestableRow(accRunId, queryAcc, domainNumber, key, startPos, "");
			reqs.add(req);
//			String[] result = queryAcc.split("\\.");
			String versionLessQueryAcc = queryAcc.split("\\.")[0];
			queryAccs.add(versionLessQueryAcc);
		}
		
		int currentNcbiVersionId = ncbiKeeper.getPreferredNCBIProviderID();
		List<String> queryAccsUnique = new ArrayList<String>(new HashSet<>(queryAccs));
//		for (String acc: queryAccsUnique) {
//			acc = "'" + acc + "'";	
//		}
//		for (int i=0; i<queryAccsUnique.size(); i++) {
//			String acc = queryAccsUnique.get(i);
////			acc = "'" + acc + "'";
//			queryAccsUnique.set(i, "'" + acc + "'");
//		}

		String queryList = String.join(",", queryAccsUnique);
		
		//look up queryAcc in current data version
		sb.setLength(0);
		sb.append("SELECT query_accession_id, accession_run_id ");
	    sb.append("FROM user_run_accession_run ua ");
	    sb.append("JOIN user_run u ON u.id = ua.user_run_id ");
	    sb.append("JOIN accession_run a ON a.id = ua.accession_run_id ");
	    sb.append("WHERE user_id = :toxcastId ");
	    sb.append("AND ncbi_version_id = :ncbiVerId ");
	    //sb.append("AND query_accession_id in (:accList) ");
		
	    params = new MapSqlParameterSource();
//		params.addValue("accList", queryList);
//	    params.addValue("accList", queryAccsUnique);
		params.addValue("toxcastId", toxCastUserId);
		params.addValue("ncbiVerId", currentNcbiVersionId);
		
		List<Map<String, Object>> tmp2 = namedJdbcTemplate.queryForList(sb.toString(),  params);
		Map<String, Integer> tmpMap2 = new HashMap<String, Integer>();
		Map<String, String> versionLessAccMap = new HashMap<String, String>();
		for (Map<String,Object> entry: tmp2) {
			String queryAcc = (String)entry.get("query_accession_id");
			String versionlessQueryAcc = queryAcc.split("\\.")[0];
			tmpMap2.put(versionlessQueryAcc, (Integer)entry.get("accession_run_id"));
			versionLessAccMap.put(queryAcc.split("\\.")[0], queryAcc);
		}
	    
		List<String> missingQueries = new ArrayList<String>();
		List<LevelTwoRequestableRow> allReqs = new ArrayList<LevelTwoRequestableRow>();
		//use mapped results from query to set accession_run_id for latest data version
		for (LevelTwoRequestableRow req: reqs) {
			String versionLessReq = req.getAccession().split("\\.")[0];
			Integer accRunId = tmpMap2.get(versionLessReq);
			String currentVersionAcc = versionLessAccMap.get(versionLessReq);
			if (accRunId != null) {
				//if accRunId exists then currentVersionAcc should exist also
				if (currentVersionAcc != null) {
					req.setAccession(currentVersionAcc);
					req.setRunId(accRunId);
					allReqs.add(req);
				} else {
					missingQueries.add(req.getAccession());
				}
				
			} else {
				missingQueries.add(req.getAccession());
			}
		}
		
		if(missingQueries.size() > 0) {
			logger.error("In: getToxcastLevelTwoRequestablesForDataVersion - could not find accessionRunIds for queryAccs: {}",
				missingQueries.toString());
		}
		
		//check to see what level2 toxcast runs have already been run for this dataversion
		sb.setLength(0);
		sb.append("SELECT ");
		sb.append("l2.id, l2.accession_run_id, l2.user_id, l2.cdd_accession_num, ");
		sb.append("l2.start_position, a.ncbi_version_id, ua.query_accession_id ");
		sb.append(" FROM level2_run l2 ");
		sb.append("JOIN accession_run a ");
		sb.append("ON l2.accession_run_id = a.id ");
		sb.append("JOIN user_run_accession_run ua ");
		sb.append("ON ua.accession_run_id = a.id ");
		sb.append("WHERE a.ncbi_version_id = :ncbiVersionId ");
		sb.append("AND l2.user_id = :toxcastId ");
		
		params = new MapSqlParameterSource();
		params.addValue("ncbiVersionId", currentNcbiVersionId);
		params.addValue("toxcastId", toxCastUserId);
		List<Map<String, Object>> tmpMap3 = namedJdbcTemplate.queryForList(sb.toString(), params);
		
		List<LevelTwoRequestableRow> completedRuns = new ArrayList<LevelTwoRequestableRow>();
		for (Map<String,Object> entry: tmpMap3) {
			//int accRunId = (int)entry.get("accession_run_id");
			int accRunId = (int)entry.get("accession_run_id");
			String queryAcc = (String)entry.get("query_accession_id");
			int domainNumber = (int)entry.get("cdd_accession_num");
			int startPos = (int)entry.get("start_position");
			String key = Integer.toString(domainNumber) + ":" + Integer.toString(startPos);
			LevelTwoRequestableRow req = new LevelTwoRequestableRow(accRunId, queryAcc, domainNumber, key, startPos, "");
			completedRuns.add(req);
		}
		
		allReqs.removeAll(completedRuns);
		
		return allReqs;
		
  }
  
  public List<LevelTwoRequestableRow> getToxcastLevelTwoReqsForQueryAcc(int accRunId){
	  return null;
  }

  // ======================== THINGS BELOW DO NOT USE DATABASE
  // ============================

}
