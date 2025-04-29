package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.AnalysisService;
import gov.epa.seqapass.backend.dao.ReportService;
import gov.epa.seqapass.backend.dao.ReportServiceImpl;
import gov.epa.seqapass.backend.dao.UserService;
import gov.epa.seqapass.backend.serviceThread.RunSomeRBHBLAST;
import gov.epa.seqapass.common.LevelTwoRequestableRow;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/admin/")
public class AdminController {
	
	private static Logger logger = LogManager.getLogger(AdminController.class);
	
	UserService userService;
	ReportService reportService;
	AnalysisService analysisService;

	public AdminController(UserService userService, ReportService reportService, AnalysisService analysisService) {
		this.userService = userService;
		this.reportService = reportService;
		this.analysisService = analysisService;
	}


	// addUser GET method
	@RequestMapping(value = "/addUser", method = RequestMethod.GET, headers = "Accept=application/json", params = { "email", "isAdmin", "isItasser" })
	public @ResponseBody int addUser(@RequestParam("email") String email, @RequestParam("isAdmin") String isAdmin, @RequestParam("isItasser") String isItasser) {
		logger.info("/addUser called with email = {}, isAdmin = {}, and isItasser = {}", email, isAdmin, isItasser);
		return userService.addUserOld(email, isAdmin, isItasser);
	}
	
	@RequestMapping(value = "/updateNCBIVersion", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody int updateNCBIVersion() {
		int largestLocalId = userService.updateNCBIVersion();
		logger.info("/updateNCBIVersion called and NCBIVersion Id is now: {}", largestLocalId);
		return largestLocalId;
	}
	
	
	@RequestMapping(value = "/repeatToxCastLevelTwoRuns", method = RequestMethod.GET, headers = "Accept=application/json", params = { "start", "end" })
	public @ResponseBody String repeatToxCastLevelTwoRuns(@RequestParam("start") int start, @RequestParam("end") int end) {
		// TODO add query to find level 2 run info needed to repeat runs
		//  using start/end as range of accessionRunId for latest data version that need level 2 runs
		//  will probably use methods in report service
		//TEMP - use table with info from staging
		logger.info("/repeatToxCastLevelTwoRuns with (ignored) values: start = {} and end = {}", start, end);
		int toxCastUserId = reportService.getToxCastUserId();
		List<LevelTwoRequestableRow> lev2List = reportService.getToxCastLevelTwoRunInfo();
		
		//submit each level 2 request in list
		for(int i=0; i<lev2List.size(); i++){
			LevelTwoRequestableRow req = lev2List.get(i);
			String status = analysisService.requestLevelTwoRun(req.getRunId(), req.getKey(), req.getStartPosition(), toxCastUserId);
			// System.out.println("Requested lev2Run with accRunId:" + req.getRunId() + ", key:" + req.getKey() + ", pos:" + req.getStartPosition() + ", userId:"+toxCastUserId);
			logger.info("Requested lev2Run with accRunId: {}, key: {}, pos: {}, userId: {}", 
					req.getRunId(), req.getKey(), req.getStartPosition(), toxCastUserId);
			// System.out.println("    status: " + status);
			logger.info("    status: {}", status);
		}
		return "completed";
	}
	
	@RequestMapping(value = "/requestToxcastLevelOne", method = RequestMethod.GET, headers = "Accept=application/json", params = { "dataVersion"})
	public @ResponseBody String requestToxcastLevelOne(@RequestParam("dataVersion") int dataVersion) {
		return analysisService.requestToxcastLevelOne(dataVersion);
	}
	
	@RequestMapping(value = "/requestToxcastLevelOne", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody String requestToxcastLevelOne() {
		return analysisService.requestToxcastLevelOne();
	}
	
	@RequestMapping(value = "/requestToxcastLevelTwo", method = RequestMethod.GET, headers = "Accept=application/json", params = { "dataVersion", "batchSize"})
	public @ResponseBody String requestToxcastLevelTwo(@RequestParam("dataVersion") int dataVersion, @RequestParam("batchSize") int batchSize) {
		return analysisService.requestToxcastLevelTwo(dataVersion, batchSize);
	}
	
	
	@RequestMapping(value = "/requestToxcastLevelTwo", method = RequestMethod.GET, headers = "Accept=application/json", params = { "dataVersion"})
	public @ResponseBody String requestToxcastLevelTwo(@RequestParam("dataVersion") int dataVersion) {
		return analysisService.requestToxcastLevelTwo(dataVersion);
	}
	
	@RequestMapping(value = "/requestToxcastLevelTwo", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody String requestToxcastLevelTwo() {
		return analysisService.requestToxcastLevelTwo();
	}
	
}
