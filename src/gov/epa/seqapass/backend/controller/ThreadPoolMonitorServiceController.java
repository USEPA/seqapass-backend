package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.ThreadPoolMonitorService;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/protected/service/threadPoolMonitor/")
public class ThreadPoolMonitorServiceController {

	ThreadPoolMonitorService threadPoolMonitorService;

	public ThreadPoolMonitorServiceController(ThreadPoolMonitorService service) {
		this.threadPoolMonitorService = service;
	}

	@RequestMapping(value = "/getLevelOneThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelOneThreadInfo() {
		return threadPoolMonitorService.getLevelOnePool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelTwoThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelTwoThreadInfo() {
		return threadPoolMonitorService.getLevelTwoPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelThreeThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelThreeThreadInfo() {
		return threadPoolMonitorService.getLevelThreePool().getThreadPoolInfo();
	}

	@RequestMapping(value = "/getRBHThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getRBHThreadInfo() {
		return threadPoolMonitorService.getRBHPool().getThreadPoolInfo();
	}

	@RequestMapping(value = "/getRPSThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getRPSThreadInfo() {
		return threadPoolMonitorService.getRPSPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelOneToxcastThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelOneToxcastThreadInfo() {
		return threadPoolMonitorService.getLevelOneToxcastPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelTwoToxcastThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelTwoToxcastThreadInfo() {
		return threadPoolMonitorService.getLevelTwoToxcastPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelFourFastaThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelFourFastaThreadInfo() {
		return threadPoolMonitorService.getLevelFourFastaPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelFourITasserThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelFourITasserThreadInfo() {
		return threadPoolMonitorService.getLevelFourITasserPool().getThreadPoolInfo();
	}
	
	@RequestMapping(value = "/getLevelFourTMAlignThreadInfo", method = RequestMethod.GET, headers = "Accept=application/json")
	public Map<String, Object> getLevelFourTMAlignThreadInfo() {
		return threadPoolMonitorService.getLevelFourTMAlignPool().getThreadPoolInfo();
	}
}
