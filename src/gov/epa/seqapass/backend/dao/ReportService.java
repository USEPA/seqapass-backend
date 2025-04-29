package gov.epa.seqapass.backend.dao;


import gov.epa.seqapass.common.AminoAcid;
import gov.epa.seqapass.common.Chemical;
//import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.DensityRow;
import gov.epa.seqapass.common.HistogramRow;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.LevelFourResultRow;
import gov.epa.seqapass.common.LevelFourStatusRow;
import gov.epa.seqapass.common.LevelOneReportRow;
import gov.epa.seqapass.common.LevelOneStatusRow;
import gov.epa.seqapass.common.LevelThreeReportRow;
import gov.epa.seqapass.common.LevelThreeRequestableRow;
import gov.epa.seqapass.common.LevelThreeStatusRow;
import gov.epa.seqapass.common.LevelThreeViewRequest;
import gov.epa.seqapass.common.LevelTwoReportRow;
import gov.epa.seqapass.common.LevelTwoRequestableRow;
import gov.epa.seqapass.common.LevelTwoStatusRow;
import gov.epa.seqapass.common.Link;
import gov.epa.seqapass.common.ReportInfo;
import gov.epa.seqapass.common.ReportRow;
import gov.epa.seqapass.common.ReportTypeEnum;
import gov.epa.seqapass.common.SpeciesTaxGrouping;
import gov.epa.seqapass.common.TaxEcos;
import gov.epa.seqapass.common.UniprotMap;
import gov.epa.seqapass.common.ZipRequestable;

import java.util.List;
import java.util.Map;

public interface ReportService {

	/**
	 * This method returns information about all Level 1 runs for a given user
	 * 
	 * @param userId
	 *            - the id in the user table
	 * @return A List of LevelOneStatusRow objects to be displayed on the "SeqAPASS Run Status" tab
	 */
	public List<LevelOneStatusRow> getLevelOneStatusForUser(int userId);

	/**
	 * 
	 * @param userId
	 *            - the id in the user table
	 * @return A list of ReportRow objects to be displayed on the "View SeqAPASS Reports" main tab for a given user
	 */
	public List<ReportRow> getMainReportForUser(int userId);

	/**
	 * 
	 * @param accessionRunId
	 *            - the id in the accessionRun table
	 * @return A list of LevelOneReportRow objects to be displayed on the "View SeqAPASS Reports" tab for a given level one job
	 */
	public List<LevelOneReportRow> getLevelOneReport(int accessionRunId);

//	/**
//	 * 
//	 * @param accessionRunId
//	 *            - the id in the accessionRun table
//	 * @param cutoff
//	 *            - the cutoff to be used for susceptibility determination.
//	 * @return A list of LevelOneReportRow objects to be displayed on the "View SeqAPASS Reports" tab for a given level one job
//	 */
//	public List<LevelOneReportRow> getLevelOneReport(int accessionRunId, double cutoff);

	public List<HistogramRow> getLevelOneCutoffHistogram(int accessionRunId, int binCount);

	/**
	 * 
	 * @param accessionRunId
	 *            - the accession run id in the accession_run table
	 * @param domain
	 *            - the domain id (ex. number from gnl|cdd|number) in the rps_result table
	 * @return A list of LevelTwoReportRow objects to be displayed on the "View SeqAPASS Reports" tab for a given level two job
	 */
	public List<LevelTwoReportRow> getLevelTwoReport(int accessionRunId, int domain);

	/**
	 * 
	 * @param userId
	 *            - the id in the user table
	 * @return A List of LevelTwoStatusRow objects to be displayed on the "SeqAPASS Run Status" tab
	 */
	public List<LevelTwoStatusRow> getLevelTwoStatusForUser(int userId);

	/**
	 * 
	 * @param userId
	 *            - the id in the user table
	 * @return A List of LevelThreeStatusRow objects to be displayed on the "SeqAPASS Run Status" tab
	 */
	List<LevelThreeStatusRow> getLevelThreeStatusForUser(int userId);

//	/**
//	 * 
//	 * @param accessionRunId
//	 *            - the accession run id in the accession_run table
//	 * @return A CutoffData object to be used to generate the primary level one density graph
//	 */
//	public CutoffData getLevelOnePrimaryCutoff(int accessionRunId);
//	
//	/**
//	 * 
//	 * @param accessionRunId
//	 *            - the accession run id in the accession_run table
//	 * @return A CutoffData object to be used to generate the full level one density graph
//	 */
//	public CutoffData getLevelOneFullCutoff(int accessionRunId);
//	
//	/**
//	 * 
//	 * @param domainRunId
//	 *            - the level 2 run id in the ??? table
//	 * @return A Cutoff object to be used to generate the primary level two density graph
//	 */
//	public CutoffData getLevelTwoPrimaryCutoff(int domainRunId);
//	
//	/**
//	 * 
//	 * @param domainRunId
//	 *            - the level 2 run id in the ??? table
//	 * @return A Cutoff object to be used to generate the full level two density graph
//	 */
//	public CutoffData getLevelTwoFullCutoff(int domainRunId);

//	/**
//	 * This method returns a list of rows with the CDDs matched in rpsBLAST for the given accessionRunId
//	 * 
//	 * @param accessionIdName - the accession id (string) which will be ambiguous if
//	 * the same protein has been run with more than one update version
//	 * @param userId - the user's id to determine which runs they have already run
//	 * @return A list of LevelTwoRequestableRows
//	 */
//	public List<LevelTwoRequestableRow> getLevelTwoRequestables(String accessionIdName, int userId);
	
	/**
	 * This method returns a list of rows with the CDDs matched in rpsBLAST for the given accessionRunId
	 * 
	 * @param accessionRunId - the accession_run id to check
	 * @param userId - the user's id to determine which runs they have already run
	 * @return A list of LevelTwoRequestableRows
	 */
	public List<LevelTwoRequestableRow> getLevelTwoRequestablesNew(int accessionRunId, int userId);
	
	/**
	 * 
	 * @param accessionRunId
	 * @param levelThreeRunId
	 * @return A list of LevelThreeReportRows
	 */
	public List<LevelThreeReportRow> getLevelThreeReport(LevelThreeViewRequest request);
	
	/**
	 * 
	 * @param accessionRunId
	 * @param levelThreeRunId
	 * @return A list of LevelThreeReportRows
	 */
	public List<LevelThreeReportRow> getLevelThreeReportComplete(LevelThreeViewRequest request);

	/**
	 * 
	 * @param accessionIdName
	 * @param userId
	 * @return A list of completed Level Three runs
	 */
	public List<LevelThreeRequestableRow> getLevelThreeCompleted(int accessionRunId, int userId);

	/**
	 * 
	 * @param accessionIdName
	 * @param userId
	 * @return A list of completed Level Four runs
	 */
	public List<LevelFourRequestableRow> getLevelFourStarted(int accessionRunId, int userId);
	
	/**
	 * 
	 * @param levelThreeRunId
	 * @return a String containing the level three template sequence
	 */
	public String getLevelThreeSequence(int levelThreeRunId);
	
	/**
	 * 
	 * @param accessionIdName
	 * @param userId
	 * @return A list of created Level Four runs
	 */
	public List<LevelFourRequestableRow> getLevelFourCreated(int accessionRunId, int userId);
	
	
	/**
	 * 
	 * @param accessionRunId
	 *            - the id in the accessionRun table
	 * @return A list of LevelOneReportRow objects to be displayed on the "View SeqAPASS Reports" tab for a given level one job
	 */
	public int getLevelOneDefaultOrthologCount(int accessionRunId, boolean isEukaryote);
	
	public List<DensityRow> getDensityData(int level, ReportTypeEnum reportType, int runId, double eValue, int commonDomains, boolean eukaryotesOnly);
	
	/**
	 * 
	 * @return a Date object containing the NCBI date
	 */
	public Long getNcbiDate();

	public ReportInfo getReportInfo(int accessionRunId, int lev2Id, int lev3Id, int lev4Id);

	public String getBackendInfo();

	public List<ReportInfo> getUpdateInfo();

//	public void convertLevelOneCutoff(int accessionRunId);
//
//	void convertLevelTwoCutoff(int domainRunId);
//
//	public void convertAllCutoffs();

	public SpeciesTaxGrouping getTaxGroups(SpeciesTaxGrouping taxIds);

	public void createLevelOneReports(int accessionRunId, int userId, int destination);
	
	public void createLevelTwoReports(int accessionRunId, int lev2Id, int userId, int destination);
	
	public void createLevelThreeReport(int accessionRunId, int lev3RunId, int userId, int destination);

	public LevelTwoRequestableRow getLevelTwoInfoByRunId(int lev2Id);

	public LevelThreeRequestableRow getLevelThreeInfoByRunId(int lev3Id);

	public void createAllReports(boolean createLevelOne, boolean createLevelTwo, boolean createLevelThree);

	public void createAllReportsForRange(int start, int end, boolean createLevelOne, boolean createLevelTwo, boolean createLevelThree);

	public byte[] requestZippedReports(List<ZipRequestable> zipRequests);

	public int getToxCastUserId();

	public void createReportsAndLinksForRun(int accessionRunId, int userId, boolean createLevelOne, boolean createLevelTwo, boolean createLevelThree);

	public void createAllReportsForRun(int accessionRunId, boolean createLevelOne, boolean createLevelTwo, boolean createLevelThree);
	
	public List<Integer> getBadTaxGroupIds();

	public String getSciNameAtRank(String accessionId, String rank);

	public List<TaxEcos> getEndangered();

	public List<Integer> getModelOrganisms();

	public boolean isQueryEukaryoteFromAccessionRunId(int accessionRunId);
	
	public boolean isQueryEukaryoteFromLevelTwoRunId(int levelTwoRunId);
	
	public List<AminoAcid> getAminoAcidInfo();
	
	
	public List<LevelTwoRequestableRow> getToxCastLevelTwoRunInfo();

	public List<TaxEcos> getThreatened();

	public List<Link> getLinksForGroup(String group);

	public boolean isUserAdmin(int userId);
	
	public int jobsWithinDay(int userId);
	
	public List<Chemical> getChemicalSearch(String search);

	public Integer getLevelFourDataCount(int level4RunId);

	public List<LevelFourAccessionRow> getLevelFourDataRows(int level4RunId);
	
	/**
	 * 
	 * @param userId
	 *            - the id in the user table
	 * @return A List of LevelFourStatusRow objects to be displayed on the "SeqAPASS Run Status" tab
	 */
	public List<LevelFourStatusRow> getLevelFourStatusForUser(int userId);
	
	public List<LevelFourStatusRow> getTMAlignStatusForUser(int userId);

	public List<LevelFourResultRow> getLevelFourResultRows(int level4RunId);

	public List<LevelFourRequestableRow> getAvailableTmalignReports(int userId, List<Integer> level4RunIds);

	public List<LevelFourResultRow> getTmalignReport(int userId, int tmAlignRunId);

	public List<UniprotMap> getUniprotMaps(List<String> accs);
	public List<UniprotMap> getUniprotMapsByAccId(int accRunId, boolean euksOnly);

	public List<UniprotMap> getUniprotMapsDebug();
	
	public int getPreviousDataVersion();
	
//	public List<Integer> getNCBIVersions(int dataVersion);
	public int getNCBIVersions(int dataVersion);



}
