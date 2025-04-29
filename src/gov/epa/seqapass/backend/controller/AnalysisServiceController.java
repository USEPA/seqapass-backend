package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.AnalysisService;
import gov.epa.seqapass.backend.dao.ReportServiceImpl;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.LevelOneRequestable;
import gov.epa.seqapass.common.LevelThreeRequestableRow;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/protected/service/analysis/")
public class AnalysisServiceController {

	private static Logger logger = LogManager.getLogger(AnalysisServiceController.class);

	AnalysisService analysisService;

	public AnalysisServiceController(AnalysisService service) {
		this.analysisService = service;
	}

	// request level 1 run
	@RequestMapping(value = "/levelOneRun", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<String> requestRun(@RequestBody LevelOneRequestable requestObject) {
		return analysisService.requestLevelOneRun(requestObject);
	}

//	// debug level 1 run
//	@RequestMapping(value = "/debugRun", method = RequestMethod.GET, headers = "Accept=application/json")
//	public @ResponseBody List<String> debugRun() {
//		return analysisService.debugLevelOneRun();
//	}

	@RequestMapping(value = "/cancelAllThreads", method = RequestMethod.GET, headers = "Accept=application/json")
	public void cancelAllThreads() {
		analysisService.killAllThreads();
	}

	// request level 2 run
	@RequestMapping(value = "/levelTwoRun", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"accessionRunId", "key", "startPosition", "userId" })
	public @ResponseBody String requestLevelTwoRun(@RequestParam("accessionRunId") int accessionRunId,
			@RequestParam("key") String key, @RequestParam("startPosition") int startPosition,
			@RequestParam("userId") int userId) {
		return analysisService.requestLevelTwoRun(accessionRunId, key, startPosition, userId);
	}

	// request level 3 run
	@RequestMapping(value = "/levelThreeRun", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody String requestLevelThree(@RequestBody LevelThreeRequestableRow request) {
		// System.out.println("Got a level 3 request !!");
		logger.info("Got a level 3 request !!");
		return analysisService.requestLevelThreeRun(request);
	}

	// request level 4 run
	@RequestMapping(value = "/levelFourRun", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody String createLevelFour(@RequestBody LevelFourRequestableRow request) {
		// System.out.println("Got a level 3 request !!");
		logger.info("Got a level 4 request !!");
		return analysisService.createLevelFourRun(request);
	}

	// request level 4 run FASTA creation
	@RequestMapping(value = "/levelFourFASTAs", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody String requestLevelFourFASTAs(@RequestBody LevelFourRequestableRow request) {
		// System.out.println("Got a level 3 request !!");
		logger.info("Getting level 4 FASTAs !!");
		return analysisService.requestLevelFourFASTAs(request);
	}

	// request level 4 I-TASSER run
	@RequestMapping(value = "/levelFourItasser", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody String requestLevelFourITasser(@RequestBody LevelFourRequestableRow request) {
		// System.out.println("Got a level 3 request !!");
		logger.info("Requesting level 4 I-Tasser run !!");
		return analysisService.requestLevelFourItasser(request);
	}
	
	// request level 4 TM-Align run
		@RequestMapping(value = "/levelFourTMAlign", method = RequestMethod.POST, headers = "Accept=application/json")
		public @ResponseBody String requestLevelFourTMAlign(@RequestBody List<LevelFourRequestableRow> requests) {
			// System.out.println("Got a level 3 request !!");
			logger.info("Requesting level 4 TM-align run !!");
			return analysisService.requestLevelFourTMAlign(requests);
		}

	// request associated jobs count
	@RequestMapping(value = "/levelOneJobsCount", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<String> requestLevelOneJobsCount(@RequestBody LevelOneRequestable requestObject) {
		return analysisService.levelOneJobsCount(requestObject);
	}

	// remove all associated jobs with each accession id
	@RequestMapping(value = "/levelOneJobsRemove", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<String> levelOneJobsDelete(@RequestBody LevelOneRequestable requestObject) {
		return analysisService.levelOneJobsDelete(requestObject);
	}

	// set taskexecutor pool size
	@RequestMapping(value = "/setThreadPoolSize", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"name", "size" })
	public @ResponseBody String setThreadPoolSize(@RequestParam("name") String name, @RequestParam("size") int size) {
		return analysisService.setPoolSize(name, size);
	}
	
	// request UniProt Mapping
	@RequestMapping(value = "/mapUniprot", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"acc"})
	public @ResponseBody String mapUniprot(@RequestParam("acc") String acc) {
		logger.info("Getting Uniprot mapping !!");
		return analysisService.findUniProtId(acc);
	}
	
	// request UniProt Mapping and update db
	@RequestMapping(value = "/runUniprotUpdate", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"runId"})
	public @ResponseBody String updateUniprot(@RequestParam("runId") int accRunId) {
		logger.info("Getting Uniprot mapping !!");
		return analysisService.updateUniprot(accRunId);
	}
	
	// request UniProt Mapping and update db
	@RequestMapping(value = "/runLevel4Update", method = RequestMethod.GET, headers = "Accept=application/json", params = {
			"runId"})
	public @ResponseBody String updateLevel4(@RequestParam("runId") int level4RunId) {
		logger.info("Getting Uniprot mapping !!");
		return analysisService.updateLevel4(level4RunId);
	}

	// ----NEW
//	@RequestMapping(value = "/RBHHPC", method = RequestMethod.GET, headers = "Accept=application/json")
//	public @ResponseBody String RBHHPC() {
//		 analysisService.RBHHPC();
//		 return "Successful";
//	}
	// ----
}
