package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.HPCService;
import gov.epa.seqapass.backend.dao.ReportService;
import gov.epa.seqapass.backend.dao.UserService;
//import gov.epa.seqapass.common.LevelThreeReportRow;
//import gov.epa.seqapass.common.LevelThreeViewRequest;
import gov.epa.seqapass.common.Link;
import gov.epa.seqapass.common.Protein;
import gov.epa.seqapass.common.ReportInfo;
import gov.epa.seqapass.common.UserMessage;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebController {
	
	ReportService reportService;
	UserService userService;
	HPCService hpcService;

	public WebController(ReportService reportService, UserService userService, HPCService hpcService) {
		this.reportService = reportService;
		this.userService = userService;
		this.hpcService = hpcService;
	}
	
//	@RequestMapping(value = "/index", method = RequestMethod.GET)
//	public String index(){
//		return "index";
//	}
	
//	@RequestMapping(value = "/staticPage", method = RequestMethod.GET)
//	public String redirect() {
//		return "redirect:/static/final.htm";
//	}
//	
	@RequestMapping(value = "/updateInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody List<ReportInfo> getUpdateInfo(){
		return reportService.getUpdateInfo();
	}
	
	@RequestMapping(value = "/getUserMessages", method = RequestMethod.GET, headers = "Accept=application/json")
	public @ResponseBody UserMessage getUserMessages(){
		return userService.getUserMessages();
	}
	
	// request list of links
	@RequestMapping(value = "/getLinks", method = RequestMethod.GET, headers = "Accept=application/json", params = "group")
	public @ResponseBody List<Link> getLinksForGroup(@RequestParam("group") String group){
		return reportService.getLinksForGroup(group);
	}
	
	// taxid from accession_id
	@RequestMapping(value = "/getTaxids", method = RequestMethod.POST, headers = "Accept=application/json")
	public @ResponseBody List<Protein> HPCService(@RequestBody List<Protein> proteinsToLookup){
		return hpcService.getTaxidsFromAccessions(proteinsToLookup);
	}
}
