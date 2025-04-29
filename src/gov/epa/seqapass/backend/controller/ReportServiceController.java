package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.ReportService;
import gov.epa.seqapass.backend.dao.ReportServiceImpl;
import gov.epa.seqapass.common.AminoAcid;
import gov.epa.seqapass.common.Chemical;
import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.DensityRow;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.LevelFourResultRow;
import gov.epa.seqapass.common.LevelFourStatusRow;
//import gov.epa.seqapass.common.HistogramRow;
import gov.epa.seqapass.common.LevelOneReportRow;
import gov.epa.seqapass.common.LevelOneStatusRow;
import gov.epa.seqapass.common.LevelThreeReportRow;
import gov.epa.seqapass.common.LevelThreeRequestableRow;
import gov.epa.seqapass.common.LevelThreeStatusRow;
import gov.epa.seqapass.common.LevelThreeViewRequest;
import gov.epa.seqapass.common.LevelTwoReportRow;
import gov.epa.seqapass.common.LevelTwoRequestableRow;
import gov.epa.seqapass.common.LevelTwoStatusRow;
import gov.epa.seqapass.common.ReportInfo;
import gov.epa.seqapass.common.ReportRow;
import gov.epa.seqapass.common.ReportTypeEnum;
import gov.epa.seqapass.common.SpeciesTaxGrouping;
import gov.epa.seqapass.common.TaxEcos;
import gov.epa.seqapass.common.UniprotMap;
import gov.epa.seqapass.common.ZipRequestable;

import java.util.Arrays;
import java.util.List;
//import java.util.zip.ZipOutputStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/protected/service/report/")
public class ReportServiceController {

	private static Logger logger = LogManager.getLogger(ReportServiceController.class);

	ReportService reportService;

	public ReportServiceController(ReportService service) {
		this.reportService = service;
	}

	// get main report list for given userid
	@RequestMapping(value = "/mainReport", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
	public @ResponseBody List<ReportRow> getMainReportForUser(@RequestParam("userid") int userid) {
		List<ReportRow> reportRows = reportService.getMainReportForUser(userid);
		return reportRows;
	}

	// get level 1 report list for requested accessionRunId
	@RequestMapping(value = "/levelOneReport", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId" })
	public @ResponseBody List<LevelOneReportRow> getLevelOneReport(@RequestParam("accessionRunId") int accessionRunId) {
		List<LevelOneReportRow> reportRows = reportService.getLevelOneReport(accessionRunId);
		return reportRows;
	}

	// create level 1 reports for given accession
	@RequestMapping(value = "/createLevelOneReportCSV", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "destination" })
	public @ResponseBody boolean createLevelOneReports(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("destination") int destination) {
		reportService.createLevelOneReports(accessionRunId, -1, destination);
		return true;
	}

	@RequestMapping(value = "/createLevelTwoReportCSV", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "lev2RunId", "userId", "destination" })
	public @ResponseBody boolean createLevelTwoReports(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("lev2RunId") int lev2RunId, @RequestParam("userId") int userId,
			@RequestParam("destination") int destination) {
		reportService.createLevelTwoReports(accessionRunId, lev2RunId, userId, destination);
		return true;
	}

	@RequestMapping(value = "/createLevelThreeReportCSV", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "lev3RunId", "userId", "destination" })
	public @ResponseBody boolean createLevelThreeReports(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("lev3RunId") int lev3RunId, @RequestParam("userId") int userId,
			@RequestParam("destination") int destination) {
		reportService.createLevelThreeReport(accessionRunId, lev3RunId, userId, destination);
		return true;
	}

	@RequestMapping(value = "/createAllReportsForRunCSV", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId" })
	public @ResponseBody boolean createAllReportsForRun(@RequestParam("accessionRunId") int accessionRunId) {
		reportService.createAllReportsForRun(accessionRunId, true, true, true);
		return true;
	}

	@RequestMapping(value = "/createAllReportsCSV", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody boolean createAllReports() {
		reportService.createAllReports(true, true, true);
		return true;
	}

	@RequestMapping(value = "/createAllLevelOneReportsCSV", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody boolean createAllLevelOneReports() {
		reportService.createAllReports(true, false, false);
		return true;
	}

	@RequestMapping(value = "/createAllLevelTwoReportsCSV", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody boolean createAllLevelTwoReports() {
		reportService.createAllReports(false, true, false);
		return true;
	}

	@RequestMapping(value = "/createAllLevelThreeReportsCSV", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody boolean createAllLevelThreeReports() {
		reportService.createAllReports(false, false, true);
		return true;
	}

	@RequestMapping(value = "/createAllReportsCSVForRange", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"start", "end" })
	public @ResponseBody boolean createAllReportsForRange(@RequestParam("start") int start,
			@RequestParam("end") int end) {
		reportService.createAllReportsForRange(start, end, true, true, true);
		return true;
	}

	@RequestMapping(value = "/createAllLevelOneReportsCSVForRange", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"start", "end" })
	public @ResponseBody boolean createAllLevelOneReportsForRange(@RequestParam("start") int start,
			@RequestParam("end") int end) {
		reportService.createAllReportsForRange(start, end, true, false, false);
		return true;
	}

	@RequestMapping(value = "/createAllLevelTwoReportsCSVForRange", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"start", "end" })
	public @ResponseBody boolean createAllLevelTwoReportsForRange(@RequestParam("start") int start,
			@RequestParam("end") int end) {
		reportService.createAllReportsForRange(start, end, false, true, false);
		return true;
	}

	@RequestMapping(value = "/createAllLevelThreeReportsCSVForRange", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"start", "end" })
	public @ResponseBody boolean createAllLevelThreeReportsForRange(@RequestParam("start") int start,
			@RequestParam("end") int end) {
		reportService.createAllReportsForRange(start, end, false, false, true);
		return true;
	}

	@RequestMapping(value = "/requestZippedReports" /*
													 * , method = RequestMethod.POST, headers = "Accept=application/zip"
													 */)
	public ResponseEntity<byte[]> getZippedReports(@RequestBody List<ZipRequestable> zipRequests) {
		byte[] resultZip = reportService.requestZippedReports(zipRequests);
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
		responseHeaders.setContentLength(resultZip.length);
		return new ResponseEntity<byte[]>(resultZip, responseHeaders, HttpStatus.CREATED);
	}

	// get level 1 status list for given userid
	@RequestMapping(value = "/levelOneStatus", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
	public @ResponseBody List<LevelOneStatusRow> getLevelOneStatusForUser(@RequestParam("userid") int userid) {
		List<LevelOneStatusRow> reportRows = reportService.getLevelOneStatusForUser(userid);
		return reportRows;
	}

	// get level 2 report list for requested accession and domain
	@RequestMapping(value = "/levelTwoReport", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "lev2Id" })
	public @ResponseBody List<LevelTwoReportRow> getLevelTwoReport(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("lev2Id") int lev2Id) {
		List<LevelTwoReportRow> reportRows = reportService.getLevelTwoReport(accessionRunId, lev2Id);
		return reportRows;
	}

	// get level 2 status list for given userid
	@RequestMapping(value = "/levelTwoStatus", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
	public @ResponseBody List<LevelTwoStatusRow> getLevelTwoStatusForUser(@RequestParam("userid") int userid) {
		List<LevelTwoStatusRow> reportRows = reportService.getLevelTwoStatusForUser(userid);
		return reportRows;
	}

	// get level 3 status list for given userid
	@RequestMapping(value = "/levelThreeStatus", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
	public @ResponseBody List<LevelThreeStatusRow> getLevelThreeStatusForUser(@RequestParam("userid") int userid) {
		List<LevelThreeStatusRow> reportRows = reportService.getLevelThreeStatusForUser(userid);
		return reportRows;
	}

	// get level 4 status list for given userid
	@RequestMapping(value = "/levelFourStatus", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
	public @ResponseBody List<LevelFourStatusRow> getLevelFourStatusForUser(@RequestParam("userid") int userid) {
		List<LevelFourStatusRow> reportRows = reportService.getLevelFourStatusForUser(userid);
		return reportRows;
	}
	
	// get TM-Align status list for given userid
		@RequestMapping(value = "/tmalignStatus", method = RequestMethod.GET, headers = "Accept=application/json", params = "userid")
		public @ResponseBody List<LevelFourStatusRow> getTMAlignStatusForUser(@RequestParam("userid") int userid) {
			List<LevelFourStatusRow> reportRows = reportService.getTMAlignStatusForUser(userid);
			return reportRows;
		}

//	// get level 2 available domains
//	@RequestMapping(value = "/requestDomains", method = RequestMethod.GET, headers = "Accept=application/json", params = {
//			"accessionIdName", "userId" })
//	public @ResponseBody List<LevelTwoRequestableRow> requestLevelTwoDomains(
//			@RequestParam("accessionIdName") String accessionIdName, @RequestParam("userId") int userId) {
//		List<LevelTwoRequestableRow> domainRows = reportService.getLevelTwoRequestables(accessionIdName, userId);
//		return domainRows;
//	}

	// get level 2 available domains new
	@RequestMapping(value = "/requestDomainsNew", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "userId" })
	public @ResponseBody List<LevelTwoRequestableRow> requestLevelTwoDomainsNew(
			@RequestParam("accessionRunId") int accessionRunId, @RequestParam("userId") int userId) {
		List<LevelTwoRequestableRow> domainRows = reportService.getLevelTwoRequestablesNew(accessionRunId, userId);
		return domainRows;
	}

	// get level 3 report list for requested accession and level3runId
	@RequestMapping(value = "/levelThreeReport", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<LevelThreeReportRow> getLevelThreeReport(@RequestBody LevelThreeViewRequest request) {
		List<LevelThreeReportRow> reportRows = reportService.getLevelThreeReport(request);
		return reportRows;
	}

	// get level 3 completed runs
	@RequestMapping(value = "/requestCompleteLevelThree", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "userId" })
	public @ResponseBody List<LevelThreeRequestableRow> requestLevelThreeDomains(
			@RequestParam("accessionRunId") int accessionRunId, @RequestParam("userId") int userId) {
		List<LevelThreeRequestableRow> levelThreeRuns = reportService.getLevelThreeCompleted(accessionRunId, userId);
		return levelThreeRuns;
	}

	// get level 4 completed runs
	@RequestMapping(value = "/requestStartedLevelFour", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "userId" })
	public @ResponseBody List<LevelFourRequestableRow> requestLevelFourCompletedRuns(
			@RequestParam("accessionRunId") int accessionRunId, @RequestParam("userId") int userId) {
		List<LevelFourRequestableRow> levelFourRuns = reportService.getLevelFourStarted(accessionRunId, userId);
		return levelFourRuns;
	}

	// get level 3 template sequence
	@RequestMapping(value = "/levelThreeSequence", method = RequestMethod.GET, headers = "Accept=application/json", params = "levelThreeRunId")
	public @ResponseBody String getLevelThreeSequence(@RequestParam("levelThreeRunId") int levelThreeRunId) {
		String sequence = reportService.getLevelThreeSequence(levelThreeRunId);
		return sequence;
	}

	// get level 4 created runs
	@RequestMapping(value = "/requestCreatedLevelFour", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "userId" })
	public @ResponseBody List<LevelFourRequestableRow> requestCreatedLevelFour(
			@RequestParam("accessionRunId") int accessionRunId, @RequestParam("userId") int userId) {
		List<LevelFourRequestableRow> levelFourRuns = reportService.getLevelFourCreated(accessionRunId, userId);
		return levelFourRuns;
	}

	// get level 4 data count
	@RequestMapping(value = "/getLevelFourDataCount", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"level4RunId" })
	public @ResponseBody Integer getLevelFourDataCount(@RequestParam("level4RunId") int level4RunId) {
		Integer level4DataCount = reportService.getLevelFourDataCount(level4RunId);
		return level4DataCount;
	}

	// get level 4 data rows
	@RequestMapping(value = "/getLevelFourDataRows", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"level4RunId" })
	public @ResponseBody List<LevelFourAccessionRow> getLevelFourDataRows(
			@RequestParam("level4RunId") int level4RunId) {
		List<LevelFourAccessionRow> levelFourDataRows = reportService.getLevelFourDataRows(level4RunId);
		return levelFourDataRows;
	}

	// get level 4 tmalign available reports
	@RequestMapping(value = "/getAvailableTmalignReports", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"userId", "level4RunIds" })
	public @ResponseBody List<LevelFourRequestableRow> getAvailableTmalignReports(@RequestParam("userId") int userId,
			@RequestParam("level4RunIds") List<Integer> level4RunIds) {

//		    for (int i=0; i<level4RunIds.size(); i++) {
//		    	System.out.println("id:" + level4RunIds.get(i));
//		    }

		List<LevelFourRequestableRow> reqRows = reportService.getAvailableTmalignReports(userId, level4RunIds);
		
		//use reportIds (tmAlign_run_ids to get LevelFourRequestableRows)
		
		
		
		return reqRows;
//		    return null;
	}

	// get level 4 tmalign available reports
	@RequestMapping(value = "/getTmalignReport", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"userId", "tmAlignRunId" })
	public @ResponseBody List<LevelFourResultRow> getAvailableTmalignReports(@RequestParam("userId") int userId,
			@RequestParam("tmAlignRunId") int tmAlignRunId) {

//			    for (int i=0; i<level4RunIds.size(); i++) {
//			    	System.out.println("id:" + level4RunIds.get(i));
//			    }

		List<LevelFourResultRow> levelFourResultRows = reportService.getTmalignReport(userId, tmAlignRunId);
		return levelFourResultRows;
//			    return null;
	}

	// get level 4 results
	@RequestMapping(value = "/getLevelFourResultRows", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"level4RunId" })
	public @ResponseBody List<LevelFourResultRow> getLevelFourResultRows(@RequestParam("level4RunId") int level4RunId) {
		List<LevelFourResultRow> levelFourResultRows = reportService.getLevelFourResultRows(level4RunId);
		return levelFourResultRows;
	}
	
//	// get UniProt mappings
	@RequestMapping(value = "/getUniprotMaps", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<UniprotMap> getUniprotMaps(@RequestBody List<String> accs) {
		
		List<UniprotMap> mappings = reportService.getUniprotMaps(accs);
		
		return mappings;
	}
	// get UniProt mappings
	@RequestMapping(value = "/getUniprotMapsByAccId", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId" })
	public @ResponseBody List<UniprotMap> getUniprotMaps(@RequestParam("accessionRunId") int accRunId, 
			@RequestParam("euksOnly") boolean eukaryotesOnly) {
		
		List<UniprotMap> mappings = reportService.getUniprotMapsByAccId(accRunId, eukaryotesOnly);
		
		return mappings;
	}
	
	// get UniProt mappings
	@RequestMapping(value = "/getUniprotMapsDebug", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<UniprotMap> getUniprotMapsDebug() {
		
		List<UniprotMap> mappings = reportService.getUniprotMapsDebug();
		
		return mappings;
	}
	
	// get ncbi date
	@RequestMapping(value = "/ncbiDate", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody Long getNcbiDate() {
		return reportService.getNcbiDate();
	}

	// get all ncbi data info
	@RequestMapping(value = "/reportInfo", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "lev2Id", "lev3Id", "lev4Id" })
	public @ResponseBody ResponseEntity<ReportInfo> getReportInfo(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("lev2Id") int lev2Id, @RequestParam("lev3Id") int lev3Id,
			@RequestParam("lev4Id") int lev4Id) {
		ReportInfo reportInfo = reportService.getReportInfo(accessionRunId, lev2Id, lev3Id, lev4Id);
		if (reportInfo == null) {
			return new ResponseEntity<ReportInfo>(HttpStatus.NO_CONTENT);
		} else {
			return new ResponseEntity<ReportInfo>(reportInfo, HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/backendInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody String getBackendInfo() {
		return reportService.getBackendInfo();
	}

	// get tax groups for requested rank
	@RequestMapping(value = "/getTaxGroups", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody SpeciesTaxGrouping getTaxGroups(@RequestBody SpeciesTaxGrouping speciesTaxGrp) {
		// System.out.println("Received /getTaxGroups in ReportServiceController with "
		// + speciesTaxGrp.getGroupMap().size() + " entries");
		logger.info("Received /getTaxGroups in ReportServiceController with {} entries",
				speciesTaxGrp.getGroupMap().size());
		SpeciesTaxGrouping resultGrp = reportService.getTaxGroups(speciesTaxGrp);
		return resultGrp;
	}

	// get list of taxIds to skip when determining across-species susceptibility
	@RequestMapping(value = "/getBadTaxGroupIds", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<Integer> getBadTaxGroupIds() {
		List<Integer> badTaxIds = reportService.getBadTaxGroupIds();
		return badTaxIds;
	}

	// get density data using report settings
	@RequestMapping(value = "/getDensityData", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<DensityRow> getDensityData(@RequestParam("level") int level,
			@RequestParam("reportType") ReportTypeEnum reportType, @RequestParam("runId") int runId,
			@RequestParam("eValue") double eValue, @RequestParam("commonDomains") int commonDomains,
			@RequestParam("euksOnly") boolean euksOnly) {
		List<DensityRow> rows = reportService.getDensityData(level, reportType, runId, eValue, commonDomains, euksOnly);
		return rows;
	}

	// get cutoff data using report settings -- currently just used for
	// debugging
	@RequestMapping(value = "/getCutoffData", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody CutoffData getCutoffData(@RequestParam("level") int level,
			@RequestParam("reportType") ReportTypeEnum reportType, @RequestParam("runId") int runId,
			@RequestParam("eValue") double eValue, @RequestParam("commonDomains") int commonDomains,
			@RequestParam("euksOnly") boolean euksOnly) {
		List<DensityRow> rows = reportService.getDensityData(level, reportType, runId, eValue, commonDomains, euksOnly);
		CutoffData theData = CutoffData.newInstance(rows, level); // new
		// CutoffData(rows);
		return theData;
	}

	// get scientific name at specified rank for accession
	@RequestMapping(value = "/getSciNameAtRank", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody String getSciNameAtRank(@RequestParam("accessionId") String accessionId,
			@RequestParam("rank") String rank) {
		String sciName = reportService.getSciNameAtRank(accessionId, rank);
		return sciName;
	}

	// get Endangered species list
	@RequestMapping(value = "/getEndangered", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<TaxEcos> getEndangered() {
		List<TaxEcos> theEndangereds = reportService.getEndangered();
		return theEndangereds;
	}

	// get Threatened species list
	@RequestMapping(value = "/getThreatened", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<TaxEcos> getThreatened() {
		List<TaxEcos> theThreateneds = reportService.getThreatened();
		return theThreateneds;
	}

	// get Model Organisms list
	@RequestMapping(value = "/getModelOrganisms", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<Integer> getModelOrganisms() {
		List<Integer> theModels = reportService.getModelOrganisms();
		return theModels;
	}

	// get Amino Acid Information
	@RequestMapping(value = "/getAminoAcidInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<AminoAcid> getAminoAcidInfo() {
		List<AminoAcid> theInfo = reportService.getAminoAcidInfo();
		return theInfo;
	}

	// get Chemical search info
	@RequestMapping(value = "/getChemicalSearch", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<Chemical> getChemicalSearch(@RequestParam("search") String search) {
		List<Chemical> chemicals = reportService.getChemicalSearch(search);
		return chemicals;
	}

}
