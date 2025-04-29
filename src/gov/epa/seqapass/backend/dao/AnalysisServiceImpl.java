package gov.epa.seqapass.backend.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
//import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.util.SerializationUtils;
import org.springframework.util.concurrent.ListenableFuture;

import com.google.common.collect.Lists;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.epa.seqapass.backend.domain.FutureCustomTask;
import gov.epa.seqapass.backend.domain.Job;
import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.PriorityTaskExecutor;
import gov.epa.seqapass.backend.domain.Task;
import gov.epa.seqapass.backend.domain.TaskGroup;
import gov.epa.seqapass.backend.domain.ThreadPool;
import gov.epa.seqapass.backend.serviceThread.AccessionRun;
import gov.epa.seqapass.backend.serviceThread.BLASTTools;
import gov.epa.seqapass.backend.serviceThread.RunLevel1;
import gov.epa.seqapass.backend.serviceThread.RunLevel2;
import gov.epa.seqapass.backend.serviceThread.RunLevel3;
import gov.epa.seqapass.backend.serviceThread.RunLevel4;
import gov.epa.seqapass.backend.serviceThread.RunSomeRBHBLAST;
import gov.epa.seqapass.backend.serviceThread.RunSomeRPSBLAST;
import gov.epa.seqapass.backend.serviceThread.UserRun;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
//import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.LevelOneRequestable;
import gov.epa.seqapass.common.LevelThreeRequestableRow;
import gov.epa.seqapass.common.LevelTwoRequestableRow;

public class AnalysisServiceImpl implements AnalysisService {
	
	private static Logger logger = LogManager.getLogger(AnalysisServiceImpl.class);

	public AnalysisServiceImpl(JdbcTemplate template, NCBIKeeper keeper, BLASTTools blastTools,
			ThreadPoolMonitorService poolService) {

		this.levelOnePriorityTaskExecutor = new PriorityTaskExecutor();
		levelOnePriorityTaskExecutor.setCorePoolSize(3);
		levelOnePriorityTaskExecutor.setMaxPoolSize(3);
		levelOnePriorityTaskExecutor.setQueueCapacity(1500);
		levelOnePriorityTaskExecutor.initialize();
		
		this.rbhPriorityTaskExecutor = new PriorityTaskExecutor();
		rbhPriorityTaskExecutor.setCorePoolSize(10); // FIXME - This probably gets increased a lot since HPC can handle big queue
		rbhPriorityTaskExecutor.setMaxPoolSize(10);
		rbhPriorityTaskExecutor.setQueueCapacity(1500);
		rbhPriorityTaskExecutor.initialize();
		
		this.rpsPriorityTaskExecutor = new PriorityTaskExecutor();
		rpsPriorityTaskExecutor.setCorePoolSize(15);
		rpsPriorityTaskExecutor.setMaxPoolSize(15);
		rpsPriorityTaskExecutor.setQueueCapacity(1500);
		rpsPriorityTaskExecutor.initialize();
		
		this.levelTwoPriorityTaskExecutor = new PriorityTaskExecutor();
		levelTwoPriorityTaskExecutor.setCorePoolSize(8);
		levelTwoPriorityTaskExecutor.setMaxPoolSize(8);
		levelTwoPriorityTaskExecutor.setQueueCapacity(1500);
		levelTwoPriorityTaskExecutor.initialize();
		
		this.levelThreePriorityTaskExecutor = new PriorityTaskExecutor();
		levelThreePriorityTaskExecutor.setCorePoolSize(8);
		levelThreePriorityTaskExecutor.setMaxPoolSize(8);
		levelThreePriorityTaskExecutor.setQueueCapacity(1500);
		levelThreePriorityTaskExecutor.initialize();
		
		this.levelFourFastaPriorityTaskExecutor = new PriorityTaskExecutor();
		levelFourFastaPriorityTaskExecutor.setCorePoolSize(8);
		levelFourFastaPriorityTaskExecutor.setMaxPoolSize(8);
		levelFourFastaPriorityTaskExecutor.setQueueCapacity(1500);
		levelFourFastaPriorityTaskExecutor.initialize();
		
		this.levelFourItasserPriorityTaskExecutor= new PriorityTaskExecutor();
		levelFourItasserPriorityTaskExecutor.setCorePoolSize(2);
		levelFourItasserPriorityTaskExecutor.setMaxPoolSize(2);
		levelFourItasserPriorityTaskExecutor.setQueueCapacity(1500);
		levelFourItasserPriorityTaskExecutor.initialize();
		
		this.levelFourTMAlignPriorityTaskExecutor= new PriorityTaskExecutor();
		levelFourTMAlignPriorityTaskExecutor.setCorePoolSize(8);
		levelFourTMAlignPriorityTaskExecutor.setMaxPoolSize(8);
		levelFourTMAlignPriorityTaskExecutor.setQueueCapacity(1500);
		levelFourTMAlignPriorityTaskExecutor.initialize();
		
		this.levelOneToxcastPriorityTaskExecutor = new PriorityTaskExecutor();
		levelOneToxcastPriorityTaskExecutor.setCorePoolSize(2);
		levelOneToxcastPriorityTaskExecutor.setMaxPoolSize(2);
		levelOneToxcastPriorityTaskExecutor.setQueueCapacity(1500);
		levelOneToxcastPriorityTaskExecutor.initialize();
		
		this.levelTwoToxcastPriorityTaskExecutor = new PriorityTaskExecutor();
		levelTwoToxcastPriorityTaskExecutor.setCorePoolSize(8);
		levelTwoToxcastPriorityTaskExecutor.setMaxPoolSize(8);
		levelTwoToxcastPriorityTaskExecutor.setQueueCapacity(5000);
		levelTwoToxcastPriorityTaskExecutor.initialize();
		
		this.setPoolService(poolService);
		this.jdbcTemplate = template;
		this.ncbiKeeper = keeper;
		this.blastTools = blastTools;
		//this.rService = null;
	}

	private int totalJobsToRun = 20;

	private PriorityTaskExecutor levelOnePriorityTaskExecutor;
	private PriorityTaskExecutor rbhPriorityTaskExecutor;
	private PriorityTaskExecutor rpsPriorityTaskExecutor;
	private PriorityTaskExecutor levelTwoPriorityTaskExecutor;
	private PriorityTaskExecutor levelThreePriorityTaskExecutor;
	private PriorityTaskExecutor levelFourFastaPriorityTaskExecutor;
	private PriorityTaskExecutor levelFourItasserPriorityTaskExecutor;
	private PriorityTaskExecutor levelFourTMAlignPriorityTaskExecutor;
	private PriorityTaskExecutor levelOneToxcastPriorityTaskExecutor;
	private PriorityTaskExecutor levelTwoToxcastPriorityTaskExecutor;

	private ThreadPoolMonitorService poolService;
	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private BLASTTools blastTools;
	//private RService rService;

	// This calls the cancel method on all futures in each task in all threadpools
	// This does not interrupt the processes if they are already running
	// (parameter=false)
	public void killAllThreads() {
		for (Map.Entry<String, ThreadPool> entry : poolService.getThreadPools().entrySet()) {
			for (TaskGroup group : entry.getValue().getTaskGroupList()) {
				for (ListenableFuture<Object> future : group.getTaskFutureList()) {
					future.cancel(false);
				}
			}
		}
	}
	
	public void pause(Long millis){
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public String setPoolSize(String poolName, int poolSize){
		PriorityTaskExecutor taskExecutor = null;
		switch (poolName) {
		case "one":
			taskExecutor = levelOnePriorityTaskExecutor;
			break;
		case "rps":
			taskExecutor = rpsPriorityTaskExecutor;
			break;
		case "rbh":
			taskExecutor = rbhPriorityTaskExecutor;
			break;
		case "two":
			taskExecutor = levelTwoPriorityTaskExecutor;
			break;
		case "three":
			taskExecutor = levelThreePriorityTaskExecutor;
			break;
		case "fasta":
			taskExecutor = levelFourFastaPriorityTaskExecutor;
			break;
		case "itasser":
			taskExecutor = levelFourItasserPriorityTaskExecutor;
			break;
		case "toxcast":
			taskExecutor = levelOneToxcastPriorityTaskExecutor;
			break;
		default:
			return "Threadpool not found";
		}
		taskExecutor.setCorePoolSize(poolSize);
		taskExecutor.setMaxPoolSize(poolSize);
		
		return "Threadpool " + poolName + " set to size " + poolSize;
		
	}
	
	
	public Integer determinePriority(int userId){
		//lower priority number denotes higher priority job
		//admin has priority 0
		//normal user priority begins at 1
		
		int maxAllowedJobs = 10;
		int binSize = maxAllowedJobs/2; //integer division
		
		Integer priority = null;
		
		boolean isAdmin = blastTools.getBlastTools2().getReportService().isUserAdmin(userId);
		
		boolean isToxcast = (blastTools.getBlastTools2().getToxCastUserId() == userId);
		
		// set initial priority based on regular user or admin
//		if (isAdmin){
//			priority = 0;
//		} else {
//			priority = 1;
//		}
		
		//ALTERNATIVE
		// admin user has higher priority than normal user
		// lower priority after N=maxAllowedJobs jobs submitted in 24 hour period.
		if (isAdmin || isToxcast){
			priority = 0;
		} else {
			priority = 1;
			//check number of jobs submitted by user in 24 hour period and deprioritize as needed.
			int numJobs = blastTools.getBlastTools2().getReportService().jobsWithinDay(userId);		
			if (numJobs > maxAllowedJobs){
				int offset = numJobs / binSize; 
				if (offset > 1){
					priority = offset;
				}
			}
		}
		
		return priority;
	}
	
	public Integer determinePriorityDebug(int userId, int jobCount){
		//lower priority number denotes higher priority job
		//admin has priority 0
		//normal user priority begins at 1
		
		int maxAllowedJobs = 10;
		int binSize = maxAllowedJobs/2; //integer division
		
		Integer priority = null;
		
		boolean isAdmin = blastTools.getBlastTools2().getReportService().isUserAdmin(userId);
		
		// set initial priority based on regular user or admin
//		if (isAdmin){
//			priority = 0;
//		} else {
//			priority = 1;
//		}
		
		//ALTERNATIVE
		// admin user has higher priority than normal user
		// lower priority after N=maxAllowedJobs jobs submitted in 24 hour period.
		if (isAdmin){
			priority = 0;
		} else {
			priority = 1;
			//check number of jobs submitted by user in 24 hour period and deprioritize as needed.
//			int numJobs = blastTools.getBlastTools2().getReportService().jobsWithinDay(userId);		
			int numJobs = jobCount;
			if (numJobs > maxAllowedJobs){
				int offset = numJobs / binSize; 
				if (offset > 1){
					priority = offset;
				}
			}
		}
		
		return priority;
	}

	@Override
	public List<String> requestLevelOneRun(LevelOneRequestable requestObject) {
		List<String> queryAccessionStringList = requestObject.getAccessionList();
		int userId = requestObject.getUserID();
		if (queryAccessionStringList.size() < 1) {
			List<String> empty = new ArrayList<String>();
			empty.add("No runs");
			return empty;
		}

		UserRun userRun = new UserRun(blastTools, jdbcTemplate, ncbiKeeper, userId);
		userRun.pushQueryAccessionStringList(queryAccessionStringList); // All work associated with creating and
																		// populating AccessionRuns

		int toxcastUserId = blastTools.getBlastTools2().getToxCastUserId();
		
		List<String> accessionRunStatuses = new ArrayList<String>();
		Set<Integer> uniqueNewIds = new HashSet<Integer>();
		for (AccessionRun accessionRun : userRun.getAccessionRuns()) {
			String submittedQueryAccessionString = accessionRun.getSubmittedQueryAccessionString();
			// String queryAccessionString = accessionRun.getQueryAccessionString();
			String status = accessionRun.getStatusNoDB();
			accessionRunStatuses.add(submittedQueryAccessionString + ": " + status);
			Integer accessionRunId = accessionRun.getAccessionRunId();
			//System.out.println("This accession has a queryString of: " + submittedQueryAccessionString
			//		+ " and an accessionRunId of: " + accessionRunId + " . And a status of: " + status);
			logger.info("This accession has a queryString of: {} and an accessionRunId of: {} ."
					+ " And a status of: {}",  
					submittedQueryAccessionString, accessionRunId, status);
			if (status != null && status.startsWith("submitted") && !uniqueNewIds.contains(accessionRunId)) {
				uniqueNewIds.add(accessionRunId);

				Integer priority = determinePriority(userId);
				LevelOneJob levOneJob = new LevelOneJob(accessionRun,priority);		
				Task task = new Task(Job::runJob, levOneJob);
				// System.out.println("submitting " + accessionRun.getQueryAccessionString() + " with Priority " + priority);
				logger.info("submitting {} with Priority {}", 
						accessionRun.getQueryAccessionString(), priority);
				if (userId == toxcastUserId) {
					//idea is that this gets added back to the toxcast queue
					//with priority 0.  All initial requests form requestToxcastLevelOne
					//are with priority 1.  This should prioritize this active LevelOneRun over
					//the other queued toxcast runs.  The determinePriority should place toxcast user
					//as higher priority than other users (same priority as admin)
					levelOneToxcastPriorityTaskExecutor.execute(new FutureCustomTask(task));
				} else {
					levelOnePriorityTaskExecutor.execute(new FutureCustomTask(task));
				}

			} else if (status != null && status.startsWith("existing")){
				//write Toxcast report if needed
				blastTools.createToxCastReport(accessionRunId, userId);
			}
		}

		return accessionRunStatuses;
	}

	@Override
	public String requestLevelTwoRun(int accessionRunId, String key, int startPosition, int userId) {
		//System.out.println("requestLevelTwoRun with: accessionRunId, key, startPosition, userId of: " + accessionRunId
		//		+ " - " + key + " - " + startPosition + " - " + userId);
		logger.info("requestLevelTwoRun with: accessionRunId, key, startPosition, userId of: {} - {} - {} - {}", 
				accessionRunId, key, startPosition, userId);
		int cddId = Integer.parseInt(key.split(":", 2)[0]);
//		RunLevel2 run1Level2 = new RunLevel2(blastTools, jdbcTemplate, ncbiKeeper, rService, accessionRunId, userId);
		RunLevel2 run1Level2 = new RunLevel2(blastTools, jdbcTemplate, ncbiKeeper, accessionRunId, userId);
		run1Level2.setLevel2CddId(cddId);
		run1Level2.setLevel2startPosition(startPosition);
		// System.out.println("User id for level 2 run is " + userId);
		logger.info("User id for level 2 run is {}", userId);
		int level2RunId = run1Level2.createAddAndGetNewLevel2RunId();
		if (level2RunId < 0) {
			// NOTE: the line below should only come up if user clicks the submit button
			// twice.
			return "Already submitted";
		}
		String status = run1Level2.getLevel2StatusFromDB();
		if (!status.equals("queued")) {
			return status;
		}
		String statusBeforeStart = run1Level2.getLevel2StatusFromDB();
		String taskName = "level2run-" + level2RunId;

		Integer priority = determinePriority(userId);
		LevelTwoJob levTwoJob = new LevelTwoJob(run1Level2, priority);
		Task task = new Task(Job::runJob, levTwoJob);
		// System.out.println("submitting level 2 run: " + run1Level2.getLevel2RunId() + " with Priority " + priority);
		logger.info("submitting level 2 run: {} with Priority {}", 
				run1Level2.getLevel2RunId(), priority);
		
		int toxcastUserId = blastTools.getBlastTools2().getToxCastUserId();
		if (userId == toxcastUserId) {
			//idea is that this gets added back to the toxcast queue
			//with priority 0.  All initial requests form requestToxcastLevelOne
			//are with priority 1.  This should prioritize this active LevelOneRun over
			//the other queued toxcast runs.  The determinePriority should place toxcast user
			//as higher priority than other users (same priority as admin)
			levelTwoToxcastPriorityTaskExecutor.execute(new FutureCustomTask(task));
		} else {
			levelTwoPriorityTaskExecutor.execute(new FutureCustomTask(task));
		}

		return statusBeforeStart;
	}

	@Override
	public String requestLevelThreeRun(LevelThreeRequestableRow levelThreeRequestableRow) {
		RunLevel3 runLevel3 = new RunLevel3(blastTools, jdbcTemplate, ncbiKeeper, levelThreeRequestableRow);


		String possibleFailure = runLevel3.setupAndReportFailure();
		if (possibleFailure != null) {
			// System.out.println("Level 3 failed with this message: " + possibleFailure);
			logger.error("Level 3 failed with this message: {}", possibleFailure);
			return possibleFailure;
		}
		String status = runLevel3.getStatus();
		if (!status.equals("queued")) {
			return status;
		}
		String taskName = "level3run-" + runLevel3.getLevel3RunId();

		Integer priority = determinePriority(levelThreeRequestableRow.getUserId());
		LevelThreeJob levThreeJob = new LevelThreeJob(runLevel3, priority);
		Task task = new Task(Job::runJob, levThreeJob);
		// System.out.println("submitting level 3 run: " + runLevel3.getLevel3RunId() + " with Priority " + priority);
		logger.info("submitting level 3 run: {} with Priority {}", 
				runLevel3.getLevel3RunId(), priority);
		levelThreePriorityTaskExecutor.execute(new FutureCustomTask(task));
		
		return status;
	}
	
	@Override
	public String createLevelFourRun(LevelFourRequestableRow levelFourRequestableRow) {
		RunLevel4 runLevel4 = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, levelFourRequestableRow);
		
		String possibleFailure = runLevel4.setupAndReportFailure();
		System.out.println("CreateLevelFourRun: possibleFailure = " + possibleFailure);
		if (possibleFailure != null) {
			// System.out.println("Level 3 failed with this message: " + possibleFailure);
			logger.error("Level 4 failed with this message: {}", possibleFailure);
			return possibleFailure;
		}
		
		blastTools.getBlastTools2().updateLevel4Status(runLevel4.getLevel4RunId(), "Analysis Created");
		
		String status = "Created new Level Four Run: " + levelFourRequestableRow.getJobName();
		return status;
	}
	
	@Override
	public String requestLevelFourFASTAs(LevelFourRequestableRow requestObject) {
		
		int userId = requestObject.getUserId();

		if (requestObject.getAccessionData().size() == 0) {
			return "No accession data";
		}
		
//		RunLevel4 runLevel4 = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, requestObject);
		RunLevel4 levelFourRun = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, requestObject);
		String possibleFailure = levelFourRun.setupAndReportFailure();
		System.out.println("RequestLevelFourFASTAs: possibleFailure = " + possibleFailure);
		if (possibleFailure != null) {
			// System.out.println("Level 3 failed with this message: " + possibleFailure);
			logger.error("Level 4 FASTA failed with this message: {}", possibleFailure);
			return possibleFailure;
		}
		
		//FASTAs are already set for source level 2
		if (requestObject.getSourceLevel() == 1) {
			Integer priority = determinePriority(userId);
//		RunLevel4 levelFourRun = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, requestObject);
			LevelFourFastaJob levFourJob = new LevelFourFastaJob(levelFourRun, priority);
			Task task = new Task(Job::runJob, levFourJob);
			// System.out.println("submitting " + accessionRun.getQueryAccessionString() + "
			// with Priority " + priority);
			logger.info("submitting {} with Priority {}", requestObject.getJobName(), priority);
			levelFourFastaPriorityTaskExecutor.execute(new FutureCustomTask(task));

//		} else if (status != null && status.startsWith("existing")){
//			//write Toxcast report if needed
//			blastTools.createToxCastReport(accessionRunId, userId);
//		}
		} else {
//			Map<String, String> fastaLookupMap = levelFourRun.buildFastaLookup(requestObject.getAccessionData());
			levelFourRun.loadLevelTwoFASTAs(requestObject);
		}
		
		return "Creating FASTAs and updating priorities";
	}
	
	public String requestLevelFourItasser(LevelFourRequestableRow requestObject) {
		int userId = requestObject.getUserId();
		
		if (requestObject.getAccessionData().size() == 0) {
			return "No accession data";
		}
		
		RunLevel4 levelFourRun = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, requestObject);		

		Integer priority = determinePriority(userId);
		
		LevelFourItasserJob levFourJob = new LevelFourItasserJob(levelFourRun,priority);
		Task task = new Task(Job::runJob, levFourJob);
		
		logger.info("Level 4 I-TASSER Requested with job name = {} and priority = {}", requestObject.getJobName(), priority);
		levelFourItasserPriorityTaskExecutor.execute(new FutureCustomTask(task));
		
		return "Submitted";
	}
	
	
	public String requestLevelFourTMAlign(List<LevelFourRequestableRow> requestObjects) {
		
		//Validation of request objects
		boolean passQueryValidation = true;
		for (LevelFourRequestableRow request : requestObjects) {
			String queryAcc = request.getQueryAccessionData().getNcbiAccession();
			String queryJobName = request.getQueryJobName();
			if (queryAcc == null || queryAcc.trim().isEmpty()) {
				passQueryValidation = false;
			} else if (queryJobName == null || queryJobName.trim().isEmpty()) {
				passQueryValidation = false;
			} else if (request.getQueryAccessionData().getLevel4RunId() < 0) {
				passQueryValidation = false;
			}
			if (!passQueryValidation) return "Failed validation of query information";
			
			if (request.getAccessionData().size() == 0) {
				return "Not enough accession data provided";
			}
		}
		
		//Insert new level4_tmalign_run entry
		LevelFourRequestableRow firstReq = requestObjects.get(0);
		int tmAlignRunId = -9999;
		
		//Submit all jobs
//		for (LevelFourRequestableRow request : requestObjects) {
		for (int i=0; i<requestObjects.size(); i++) {
			LevelFourRequestableRow request = requestObjects.get(i);
			int queryAccessionLevel4RunId = request.getQueryAccessionData().getLevel4RunId();
			
			//should insert and auto-increment id (tmAlignRunId) for first object
			//after that it reuses id (tmAlignRunId)
			tmAlignRunId = blastTools.getBlastTools2().insertLevel4TMAlignRun(tmAlignRunId, queryAccessionLevel4RunId, request);

			RunLevel4 levelFourRun = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper, request, tmAlignRunId);
			int userId = request.getUserId();
			Integer priority = determinePriority(userId);
			
			LevelFourTMAlignJob levFourJob = new LevelFourTMAlignJob(levelFourRun, priority);
			Task task = new Task(Job::runJob, levFourJob);
			

			logger.info("submitting {} with Priority {}", 
					request.getQueryDisplayName(), priority);
			levelFourTMAlignPriorityTaskExecutor.execute(new FutureCustomTask(task));
		}
		
		return "Submitted";
		
	}
	
	@Override
	public String requestToxcastLevelOne(){
		//first get data version to be used as a reference
		ReportService reportService = blastTools.getBlastTools2().getReportService();
		int dataVersion = reportService.getPreviousDataVersion();
		
		return requestToxcastLevelOne(dataVersion);
	}
	
	@Override
	public String requestToxcastLevelOne(int dataVersion){
		
		List<String> queryAccIds = new ArrayList<String>();
		queryAccIds = blastTools.getToxcastAccIdsForDataVersion(dataVersion);
		int toxCastUserId = blastTools.getBlastTools2().getReportService().getToxCastUserId(); 
		
		//Do I need to break these up in subsets of 10.
		//If not will the one leveloneRequestable get submitted and immediately run with ~500 accessions
		//Instead we should have multiple levelOneRequestables, each with 10 or so.
		int batchSize = 10;
		List<List<String>> batch = Lists.partition(queryAccIds, batchSize);
		
		for (List<String> group : batch) {
			LevelOneRequestable requestObject = new LevelOneRequestable(group, toxCastUserId);
			//List<String> statuses = requestLevelOneRun(requestObject);
			//Send to toxcast queue
			LevelOneToxcastJob levOneJob = new LevelOneToxcastJob(requestObject);
			Task task = new Task(Job::runJob, levOneJob);
			levelOneToxcastPriorityTaskExecutor.execute(new FutureCustomTask(task));
		}
		
		return batch.size() + " groups of 10 accessions submitted";
		
//		LevelOneRequestable requestObject = new LevelOneRequestable(queryAccIds, toxCastUserId);
//		
//		List<String> statuses = requestLevelOneRun(requestObject);
		
//		return statuses.size() + " ToxCast accessions submitted";
	}
	
	@Override
	public String requestToxcastLevelTwo() {
		//first get data version to be used as a reference
		ReportService reportService = blastTools.getBlastTools2().getReportService();
		int dataVersion = reportService.getPreviousDataVersion();
		
		return requestToxcastLevelTwo(dataVersion);
	}
	
	@Override
	public String requestToxcastLevelTwo(int dataVersion) {
//		List<String> queryAccIds = new ArrayList<String>();
////		queryAccIds = blastTools.getToxcastAccIdsForDataVersion(dataVersion);
//		int toxCastUserId = blastTools.getBlastTools2().getReportService().getToxCastUserId();
//		
//		
//
//		List<LevelTwoRequestableRow> lev2Req = blastTools.getToxcastLevelTwoRequestablesForDataVersion(dataVersion);
//		
//		for(LevelTwoRequestableRow req: lev2Req) {
//			
//		   String status = requestLevelTwoRun(req.getRunId(), req.getKey(), req.getStartPosition(), toxCastUserId);
//		}
//		
//		
//		return "Submitting " + lev2Req.size() + " level 2 toxcast runs";
		
		int defaultBatchSize = 20;
		return requestToxcastLevelTwo(dataVersion, defaultBatchSize);
		
	}
	
	@Override
	public String requestToxcastLevelTwo(int dataVersion, int batchSize) {
		List<String> queryAccIds = new ArrayList<String>();
		int toxCastUserId = blastTools.getBlastTools2().getReportService().getToxCastUserId();

		List<LevelTwoRequestableRow> lev2Req = blastTools.getToxcastLevelTwoRequestablesForDataVersion(dataVersion);
		
		//check to make sure batchsize isn't too large
		if (batchSize > lev2Req.size()) {
			batchSize = lev2Req.size();
		}
		List<LevelTwoRequestableRow> batchReqs = lev2Req.subList(0,  batchSize);
		
		for(LevelTwoRequestableRow req: batchReqs) {
			
		   String status = requestLevelTwoRun(req.getRunId(), req.getKey(), req.getStartPosition(), toxCastUserId);
		}
		
		
		return "Submitting " + batchSize + " of " + lev2Req.size() + " remaining level 2 toxcast runs";
		
	}
	
	public void beginDebug(String accessionRun, int priority) throws InterruptedException{
		// System.out.println("Starting debug: " + accessionRun);
		logger.debug("Starting debug: {}", accessionRun);
		Thread.sleep(10000);
		// System.out.println("Ending debug: " + accessionRun);
		logger.debug("Ending debug: {}", accessionRun);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void beginLevelOne(AccessionRun accessionRun) {
		// System.out.println("canonicalAccessionString: " + accessionRun.getCanonicalAccessionString());
		// System.out.println("queryAccessionString: " + accessionRun.getQueryAccessionString());
		// System.out.println("accessionRunId: " + accessionRun.getAccessionRunId());
		
		logger.info("canonicalAccessionString: {}", accessionRun.getCanonicalAccessionString());
		logger.info("queryAccessionString: {}", accessionRun.getQueryAccessionString());
		logger.info("accessionRunId: {}", accessionRun.getAccessionRunId());

		// == THROTTLING CONTROL ==
		// If there is a back log, hold this job up
//		System.out.println("rpsPool.getCurrentPoolSize()" + rpsPool.getCurrentPoolSize());
//		System.out.println("rpsPool.getAvailableQueues()" + rpsPool.getAvailableQueues());
//		System.out.println("rpsPool.getThreadPoolInfo().get(\"Queues Available\") "
//				+ rpsPool.getThreadPoolInfo().get("Queues Available"));
//		System.out.println("rpsPool.getExecutor().getPoolSize()" + rpsPool.getExecutor().getPoolSize());
//		System.out.println("rpsPool.getExecutor().getActiveCount()" + rpsPool.getExecutor().getActiveCount());
		
//		System.out.println("rpsPriorityTaskExecutor.getCorePoolSize():" + rpsPriorityTaskExecutor.getCorePoolSize());
//		System.out.println("rpsPriorityTaskExecutor.getActiveCount():" + rpsPriorityTaskExecutor.getActiveCount());
//		System.out.println("rpsPriorityTaskExecutor.getPoolSize():" + rpsPriorityTaskExecutor.getPoolSize());
//		System.out.println("rpsPriorityTaskExecutor.getMaxPoolSize():" + rpsPriorityTaskExecutor.getMaxPoolSize());
//		System.out.println("Queue remaining capacity:" + rpsPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
//		System.out.println("Queue size:" + rpsPriorityTaskExecutor.getThreadPoolExecutor().getQueue().size());
		
//		System.out.println("RPS Queue remaining capacity:" + rpsPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
//		System.out.println("RBH Queue remaining capacity:" + rbhPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
		

		// Consider blocking new submit jobs here, too.
		// int blockNewSubmits = 1000;
		// if (rpsPool.getCurrentPoolSize() > 16 || rbhPool.getCurrentPoolSize() > 6) {)
		//
		// }
//		int minAvailableQueues = 60;
//		int rpsQueues = rpsPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
//		int rbhQueues = rbhPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
//		int rpsQueues = Integer.parseInt((String) rpsPool.getThreadPoolInfo().get("Queues Available"));
//		int rbhQueues = Integer.parseInt((String) rbhPool.getThreadPoolInfo().get("Queues Available"));

//		while (rpsQueues < minAvailableQueues || rbhQueues < minAvailableQueues) {
//			try {
//				System.out.println("Must wait 5 minutes because, rps: " + rpsQueues + " .  rbh: " + rbhQueues);
//				Thread.sleep(300000);
////				rpsQueues = Integer.parseInt((String) rpsPool.getThreadPoolInfo().get("Queues Available"));
////				rbhQueues = Integer.parseInt((String) rbhPool.getThreadPoolInfo().get("Queues Available"));
//				rpsQueues = rpsPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
//				rbhQueues = rbhPriorityTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		System.out.println("Queues are clear; rps: " + rpsQueues + " .  rbh: " + rbhQueues);

		// Now things are clear enough to submit
		RunLevel1 runLevel1 = new RunLevel1(blastTools, jdbcTemplate, ncbiKeeper, accessionRun);

		// THE NEXT LINE DOES THE WORK AND RETURNS RBH AND RPS JOBS TO BE RUN
		Map<String, List> rbhAndRpsLists = runLevel1.run();

		// String topHitAccessionString = runLevel1.getTopHitAccessionString();
		// System.out.println("topHitAccessionString: " + topHitAccessionString);

		/* Now work out resources to be dedicated to each part */
		List<Integer> rbhIdsToRun = rbhAndRpsLists.get("rbhIds");
		List<String> rbhAccessionsToRun = rbhAndRpsLists.get("rbhAccs");
		List<Integer> rpsIdsToRun = rbhAndRpsLists.get("rpsIds");
		List<String> rpsAccessionsToRun = rbhAndRpsLists.get("rpsAccs");
		//System.out.println("There are " + rbhAccessionsToRun.size() + " rbh job(s) and " + rpsAccessionsToRun.size()
		//		+ " rps job(s).");
		logger.info("There are {} rbh job(s) and {} rps job(s).", 
				rbhAccessionsToRun.size(), rpsAccessionsToRun.size());
		int totalToRun = rbhAccessionsToRun.size() + rpsAccessionsToRun.size();
		if (totalToRun == 0) {
			blastTools.updateAccessionRunRbhCompleteness(accessionRun.getAccessionRunId(), 100);
			blastTools.updateAccessionRunRpsCompleteness(accessionRun.getAccessionRunId(), 100);
			blastTools.updateRpsCDDCountsWTopHit(accessionRun.getAccessionRunId());
			blastTools.beginAnalysis(true, true, accessionRun.getAccessionRunId(),accessionRun.getUserId());
			return;
		}
		if (rbhIdsToRun.size() == 0) {
			blastTools.updateAccessionRunRbhCompleteness(accessionRun.getAccessionRunId(), 100);
		}
		if (rpsIdsToRun.size() == 0) {
			blastTools.updateAccessionRunRpsCompleteness(accessionRun.getAccessionRunId(), 100);
			blastTools.updateRpsCDDCountsWTopHit(accessionRun.getAccessionRunId());
		}
		int numberConcurrentRbh = (rbhAccessionsToRun.size() * totalJobsToRun) / (2 * totalToRun);
		if (numberConcurrentRbh == 0 && rbhAccessionsToRun.size() > 0) {
			numberConcurrentRbh = 1;
		}
		int numberConcurrentRps = totalJobsToRun - numberConcurrentRbh;
//		System.out.println("Assigning " + numberConcurrentRbh + " core(s) to rbh jobs " + numberConcurrentRps
//				+ " core(s) to rps job(s).");
//		Core assignments above are not relevant any more - all jobs go to HPC.

		if (rbhAccessionsToRun.size() > 0) { // RUN ONLY ONE JOB
			RunSomeRBHBLAST rbhJob = new RunSomeRBHBLAST(jdbcTemplate, ncbiKeeper, blastTools, accessionRun);
			rbhJob.setPreferredNcbiProviderId(ncbiKeeper.getPreferredNCBIProviderID());
			rbhJob.setAccession_run_id(accessionRun.getAccessionRunId());
//			rbhJob.setQueryAccessionIdName(accessionRun.getQueryAccessionString());
			rbhJob.setQueryAccessionString(accessionRun.getQueryAccessionString());
			rbhJob.setQueryTaxidAccessionsAboveIdentity(runLevel1.getQueryTaxidAccessionsAboveIdentity());
//			if (rbhJob.lookupTopHitIdString() == null) {
//				System.out.println("Could not look up the top hit accession id for RBH run!!");
//				return;
//			}
			rbhJob.setJobFragmentNumber(0);
			rbhJob.setTotalNumberToRun(rbhAccessionsToRun.size());
			rbhJob.setAccessionHitIds(rbhIdsToRun);
			rbhJob.setAccessionIdNames(rbhAccessionsToRun);
			
//			Integer priority = determinePriority(accessionRun.getUserId());
			Integer priority = 0;  //run first in first out
			RBHJob thisRBHJob = new RBHJob(rbhJob,priority);		
			Task task = new Task(Job::runJob, thisRBHJob);
			rbhPriorityTaskExecutor.execute(new FutureCustomTask(task));
		} 
		
		if (rpsIdsToRun.size() > 0) { //RUN ONLY ONE JOB
			RunSomeRPSBLAST rpsJob = new RunSomeRPSBLAST(blastTools, jdbcTemplate, ncbiKeeper, accessionRun);
			rpsJob.setPreferredNcbiProviderId(ncbiKeeper.getPreferredNCBIProviderID());
			rpsJob.setAccession_run_id(accessionRun.getAccessionRunId());
			rpsJob.setQueryAccessionString(accessionRun.getQueryAccessionString());
			rpsJob.setJobFragmentNumber(0);
			rpsJob.setTotalNumberToRun(rpsIdsToRun.size());
			rpsJob.setAccessionHitIds(rpsIdsToRun);
			rpsJob.setAccessionIdNames(rpsAccessionsToRun);
			
//			Integer priority = determinePriority(accessionRun.getUserId());
			Integer priority = 0;  //run first in first out
			RPSJob thisRPSJob = new RPSJob(rpsJob,priority);		
			Task task = new Task(Job::runJob, thisRPSJob);
			rpsPriorityTaskExecutor.execute(new FutureCustomTask(task));
		} 

		// System.out.println("Finished beginLevelOne");
		logger.info("Finished beginLevelOne");
	}

	@Override
	public List<String> levelOneJobsCount(LevelOneRequestable requestObject) {
		List<String> queryAccessionStringList = requestObject.getAccessionList();
		// int userId = requestObject.getUserID();
		// if (userId > 5) {
		// List<String> oneResponse = new ArrayList<String>();
		// oneResponse.add("Only specific admin users may access job counts");
		// return oneResponse;
		// }

		if (queryAccessionStringList.size() < 1) {
			List<String> empty = new ArrayList<String>();
			empty.add("No runs");
			return empty;
		}
		List<String> accessionRunJobCounts = new ArrayList<String>();

		StringBuilder b = new StringBuilder();
		b.append("SELECT COUNT(DISTINCT a.user_run_id, a.query_accession_id) AS `level1_count`, ");
		b.append("       COUNT(DISTINCT b.id) AS `level2_count`, ");
		b.append("       COUNT(DISTINCT c.id) AS `level3_count` ");
		b.append("  FROM user_run_accession_run a ");
		b.append("         JOIN accession_run e ON e.id = a.accession_run_id, ");
		b.append("       user_run_accession_run d ");
		b.append("         LEFT JOIN level2_run b ON b.accession_run_id = d.accession_run_id ");
		b.append("         LEFT JOIN level3_run c ON c.accession_run_id = d.accession_run_id ");
		b.append(" WHERE d.query_accession_id = ? ");
		b.append("   AND a.accession_run_id = d.accession_run_id ");
		b.append("   AND a.user_run_id = ?; ");
		String query = b.toString();
		// System.out.println("Query = " + query);
		logger.info("Query = {}", query);
		for (String queryString : queryAccessionStringList) {
			String queryAccessionId = null;
			int userRunId = -1;
			try {
				// Parse accession and data version
				if (queryString.contains(":")) {
					String[] parts = queryString.split(":");
					try {
						userRunId = Integer.parseInt(parts[0]);
					} catch (Exception e) {
						userRunId = -1;
					}

					queryAccessionId = parts[1];
				}

				if (userRunId != -1) {

					// System.out.println("Trying: " + queryAccessionId + " for version " + userRunId);
					logger.info("Trying: {} for version {}", queryAccessionId, userRunId);

					Map<String, Object> jobCounts = jdbcTemplate.queryForMap(query, queryAccessionId, userRunId);
					// for (String key : jobCounts.keySet()) {
					// System.out.println("name: " + key + " . has value: " + jobCounts.get(key));
					// System.out.println(" and: " + jobCounts.get(key) + " . is object:
					// "+jobCounts.get(key).getClass());
					// }
					Long level1 = (Long) jobCounts.get("level1_count");
					Long level2 = (Long) jobCounts.get("level2_count");
					Long level3 = (Long) jobCounts.get("level3_count");
					if (level1 != null && level1 == 0) {
						accessionRunJobCounts.add(queryAccessionId + ": not found");
						// System.out.println("levels 1, 2, and 3: " + level1 + level2 + level3);
						logger.info("levels 1, 2, and 3: {}, {}, {}", level1, level2, level3);
					} else {
						accessionRunJobCounts.add("SeqAPASS Run Id " + userRunId + " " + queryAccessionId + ": " + level1 + " , "
								+ level2 + " , " + level3);
						// System.out.println("levels 1, 2, and 3: " + level1 + level2 + level3);
						logger.info("levels 1, 2, and 3: {}, {}, {}", level1, level2, level3);
					}
				} else {
					accessionRunJobCounts.add(queryString + ": incorrect format (version:accession)");
				}
			} catch (DataAccessException e) {
				accessionRunJobCounts.add(queryAccessionId + ": no results found");
			}
		}

		return accessionRunJobCounts;
	}

	@Override
	public List<String> levelOneJobsDelete(LevelOneRequestable requestObject) {
		List<String> queryAccessionStringList = requestObject.getAccessionList();
		// int userId = requestObject.getUserID();
		// System.out.println("Attempting to delete with requestObject: " + requestObject);
		logger.info("Attempting to delete with requestObject: {}", requestObject);

		// if (userId < 5) {
		// List<String> oneResponse = new ArrayList<String>();
		// oneResponse.add("Only specific admin users may access job counts");
		// return oneResponse;
		// }
		if (queryAccessionStringList.size() < 1) {
			List<String> empty = new ArrayList<String>();
			empty.add("No runs");
			return empty;
		}
		List<String> accessionRunJobCounts = new ArrayList<String>();
		for (String queryString : queryAccessionStringList) {

			String queryAccessionId = null;
			int userRunId = -1;
			// Parse accession and data version
			if (queryString.contains(":")) {
				String[] parts = queryString.split(":");
				try {
					userRunId = Integer.parseInt(parts[0]);
				} catch (Exception e) {
					userRunId = -1;
				}

				queryAccessionId = parts[1];
			}

			if (userRunId != -1) {
				// System.out.println("Attempting to delete: " + queryAccessionId + " for seqapass run id " + userRunId);
				logger.warn("Attempting to delete: {} for seqapass run id {}", 
						queryAccessionId, userRunId);
				String removalStatus = blastTools.deleteAccessionRun(queryAccessionId, userRunId);
				accessionRunJobCounts.add(removalStatus);
			}
		}
		return accessionRunJobCounts;
	}


	public ThreadPoolMonitorService getPoolService() {
		return poolService;
	}

	public void setPoolService(ThreadPoolMonitorService poolService) {
		this.poolService = poolService;
	}

//	@Override
//	public Boolean rerunLevelTwoCutoff(int level2RunId) {
//
//		CutoffData oldPrimaryCutoff = (CutoffData) SerializationUtils.deserialize(jdbcTemplate.queryForObject(
//				"SELECT density_plot_object FROM domain_run_density_plot WHERE domain_run_id = ? ", byte[].class,
//				level2RunId));
//		CutoffData oldFullCutoff = (CutoffData) SerializationUtils.deserialize(jdbcTemplate.queryForObject(
//				"SELECT full_density_plot_object FROM domain_run_density_plot WHERE domain_run_id = ? ", byte[].class,
//				level2RunId));
//
//		CutoffData primaryCutoffData = blastTools.getBlastTools2().generateLevelTwoPrimaryCutoff(rService, level2RunId);
//		CutoffData fullCutoffData = blastTools.getBlastTools2().generateLevelTwoFullCutoff(rService, level2RunId);
//
//		System.out.println("Updating old primary cutoff values: " + oldPrimaryCutoff.getCutoffValues().toString());
//		System.out.println("      to new primary cutoff values: " + primaryCutoffData.getCutoffValues().toString());
//
//		System.out.println("Updating old full cutoff values: " + oldFullCutoff.getCutoffValues().toString());
//		System.out.println("      to new full cutoff values: " + fullCutoffData.getCutoffValues().toString());
//		// System.out.println("Level 2 still going 4");
//
//		// blastTools2.insertLevelTwoCutoff(level2RunId, primaryCutoffData,
//		// fullCutoffData);
//		System.out.println("Attempting to overwrite levelTwoCutoff for lev2RunId: " + level2RunId);
//
//		StringBuilder b4 = new StringBuilder();
//		b4.append("INSERT INTO");
//		b4.append("    domain_run_density_plot (domain_run_id, density_plot_object, full_density_plot_object) ");
//		b4.append("    VALUES(?,?,?) ");
//		b4.append("    ON DUPLICATE KEY UPDATE ");
//		b4.append("    density_plot_object = VALUES(density_plot_object), ");
//		b4.append("    full_density_plot_object = VALUES(full_density_plot_object) ");
//
//		byte[] serializedPrimaryData = SerializationUtils.serialize(primaryCutoffData);
//		byte[] serializedFullData = SerializationUtils.serialize(fullCutoffData);
//		jdbcTemplate.update(b4.toString(), level2RunId, serializedPrimaryData, serializedFullData);
//
//		System.out.println("Finished overwriting levelTwoCutoff for lev2RunId: " + level2RunId);
//
//		return true;
//	}

//	@Override
//	public Boolean rerunLevelTwoCutoffForRange(int start, int end) {
//
//		StringBuilder b = new StringBuilder();
//		b.append(" SELECT DISTINCT(id) FROM level2_run ");
//		b.append(" WHERE id >= ? AND id <= ? ORDER BY id ASC");
//		List<Map<String, Object>> lev2Runs = jdbcTemplate.queryForList(b.toString(), start, end);
//
//		for (int i = 0; i < lev2Runs.size(); i++) {
//			Map<String, Object> row = lev2Runs.get(i);
//			int lev2RunId = (int) row.get("id");
//			System.out.println("lev2Id: " + lev2RunId);
//			rerunLevelTwoCutoff(lev2RunId);
//		}
//
//		return null;
//	}

	
	public class DebugLevOneJob extends Job{
		private AccessionRun accessionRun;
		
		public DebugLevOneJob(AccessionRun accessionRun, Integer jobPriority){
			super(accessionRun.getQueryAccessionString(), jobPriority);
			this.accessionRun = accessionRun;
		}
		
		@Override
		public String runJob(){
			
			// System.out.println("Job " + super.getJobName() + " is active");
			logger.info("Job {} is active", super.getJobName());
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// System.out.println("Finished " + super.getJobName() + " with Priority " + super.getJobPriority());
			logger.info("Finished {} with Priority {}", 
					super.getJobName(), super.getJobPriority());
			
			return "Finished " + super.getJobName() + " with Priority " + super.getJobPriority();
		}

	}
	
	public class LevelOneJob extends Job{
		private AccessionRun accessionRun;
		
		public LevelOneJob(AccessionRun accessionRun, Integer jobPriority){
			super(accessionRun.getQueryAccessionString(), jobPriority);
			this.accessionRun = accessionRun;
		}
		
		@Override
		public String runJob(){
			
			beginLevelOne(accessionRun);
			return "complete";
			
		}

	}
	
	public class LevelOneToxcastJob extends Job{
		private LevelOneRequestable request;
		
		public LevelOneToxcastJob(LevelOneRequestable requestObject){
			super(requestObject.getAccessionList().get(0), 1);
			this.request = requestObject;
		}
		
		@Override
		public String runJob(){
			
			requestLevelOneRun(request);
			return "complete";
			
		}

	}
	
	public class RBHJob extends Job{
		private RunSomeRBHBLAST rbhJob;
		
		public RBHJob(RunSomeRBHBLAST rbhJob, Integer jobPriority){
			super("RBHjob:"+String.valueOf(rbhJob.getSubjectTaxid()), jobPriority);
			this.rbhJob = rbhJob;
		}
		
		@Override
		public String runJob(){
			
			int runStatus = rbhJob.runAll();
			return "rbh job completed with run status:" + runStatus;
			//return "rbh job completed " + numRun + " entries";
		
		}

	}
	
	
	public class RPSJob extends Job{
		private RunSomeRPSBLAST rpsJob;
		
		public RPSJob(RunSomeRPSBLAST rpsJob, Integer jobPriority){
			super("RPSjob:"+String.valueOf(rpsJob.getAccession_run_id()), jobPriority);
			this.rpsJob = rpsJob;
		}
		
		@Override
		public String runJob(){
			int numRun = rpsJob.runAll();
			return "rps job completed " + numRun + " entries";
			
		}

	}
	
	public class LevelTwoJob extends Job{
		private RunLevel2 runLevel2;
		
		public LevelTwoJob(RunLevel2 runLevel2, Integer jobPriority){
			super("level2run-" + runLevel2.getLevel2RunId(), jobPriority);
			this.runLevel2 = runLevel2;
		}
		
		@Override
		public String runJob(){
			int numRun = runLevel2.runLevelTwo();
			return "level 2 job completed " + numRun + " entries";
			
		}

	}
	
	public class LevelThreeJob extends Job{
		private RunLevel3 runLevel3;
		
		public LevelThreeJob(RunLevel3 runLevel3, Integer jobPriority){
			super("level3run-" + runLevel3.getLevel3RunId(), jobPriority);
			this.runLevel3 = runLevel3;
		}
		
		@Override
		public String runJob(){
			int numRun = runLevel3.run();
			return "level 3 job completed. If good this is a zero: " + numRun;
			
		}

	}
	
	public class LevelFourFastaJob extends Job{
		private RunLevel4 runLevel4;
		
		public LevelFourFastaJob(RunLevel4 runLevel4, Integer jobPriority){
			super("level4 FASTAs-" + runLevel4.getLevel4RunId(), jobPriority);
			this.runLevel4 = runLevel4;
		}
		
		@Override
		public String runJob(){
			int numRun = runLevel4.runFASTAs();
			return "level 4 FASTAs completed. If good this is a zero: " + numRun;
			
		}

	}
	
	public class LevelFourItasserJob extends Job{
		private RunLevel4 runLevel4;
		
		public LevelFourItasserJob(RunLevel4 runLevel4, Integer jobPriority){
			super("level4 I-Tasser-" + runLevel4.getLevel4RunId(), jobPriority);
			this.runLevel4 = runLevel4;
		}
		
		@Override
		public String runJob(){
			int numRun = runLevel4.runItasser();
			return "level 4 I-TASSER submitted with " + numRun + " accessions";
			
		}

	}
	
	
	public class LevelFourTMAlignJob extends Job{
		private RunLevel4 runLevel4;
		
		public LevelFourTMAlignJob(RunLevel4 runLevel4, Integer jobPriority){
			super("level4 TM-Align-" + runLevel4.getLevel4RunId(), jobPriority);
			this.runLevel4 = runLevel4;
		}
		
		@Override
		public String runJob(){
			System.out.println("inside L4 runJob");
			int numRun = runLevel4.runTMAlign();
			return "level 4 TM-Align submitted with " + numRun + " accessions";
			
		}

	}
	
	public String findUniProtId(String inputAcc) {
		List<String> accList = blastTools.findCanonicalAccessionPlusIdenticalsFromTaxidString(inputAcc);
		//search list for first listed UniProtId
//		System.out.println("Acclist: ");
//		System.out.println(accList);
		
		String pattern = "[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}";
	    Pattern uniProtPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	    for (String acc : accList) {
	    	Matcher matcher = uniProtPattern.matcher(acc);
	    	if (matcher.find()) {
		        return acc;
		    }
	    }	
//	    System.out.println("No Swiss Prot match found");
	    return null;
		
	}
	
	public String updateUniprot(int accRunId) {
//		RunLevel4 runLevel4 = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper);
//		runLevel4.handleAllLevel1UniprotAccessions(accRunId);
//
//	    return "completed";
	    
	    return "deprecated";
		
	}
	
//	private HashMap<String, String> findUniProtIds(List<String> inputAccs){
//		
//		HashMap<String, String> ncbiUniProtMap = new HashMap<String, String>();
//		for(String inputAcc : inputAccs) {
//			String foundAcc = findUniProtId(inputAcc);
//			if (foundAcc != null) {
//				ncbiUniProtMap.put(inputAcc, foundAcc);
//			}
//		}
//		return ncbiUniProtMap;
//	}
	
	@Override
	public String updateLevel4(int level4RunId) {
		RunLevel4 runLevel4 = new RunLevel4(blastTools, jdbcTemplate, ncbiKeeper);
		runLevel4.runLevel4Update(level4RunId);
		return "Completed";
	}
//----NEW
//	@Override
//	public String RBHHPC() {
//		// TODO Auto-generated method stub
//		AccessionRun accessionRun = new AccessionRun(blastTools, jdbcTemplate, ncbiKeeper, "null", totalJobsToRun);
//		RunSomeRBHBLAST rbhBlast = new RunSomeRBHBLAST(jdbcTemplate, ncbiKeeper, blastTools, accessionRun);
//		rbhBlast.parseRBHHPC();
//		return null;
//	}
//----

	

	
}
