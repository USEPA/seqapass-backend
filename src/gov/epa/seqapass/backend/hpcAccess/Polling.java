package gov.epa.seqapass.backend.hpcAccess;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import gov.epa.seqapass.backend.serviceThread.BLASTTools;
import gov.epa.seqapass.backend.serviceThread.RunLevel1;
import gov.epa.seqapass.backend.serviceThread.RunLevel4;
import gov.epa.seqapass.backend.serviceThread.RunSomeRBHBLAST;
import gov.epa.seqapass.backend.serviceThread.RunSomeRPSBLAST;
import gov.epa.seqapass.backend.utility.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Polling implements Runnable {
	
	private static Logger logger = LogManager.getLogger(Polling.class);

	private HPCConnector hpcConnector;
	private BLASTTools blastTools;
	private ArrayList<SBatchObject> toRemove = new ArrayList<>();
	private ArrayList<SBatchObject> sBatchObjectsList = new ArrayList<>();
	private RunSomeRBHBLAST runSomeRBHBLAST;
	private RunSomeRPSBLAST runSomeRPSBLAST;
//	private RunLevel1 runLevel1;
	private RunLevel4 runLevel4;
	private Timer timer;

	LinkedBlockingQueue<SBatchObject> linkedBlockingQueuePolling; // Holds the polling jobs
	LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate; // Holds the completed jobs that are ready to get updated

	public Polling(LinkedBlockingQueue<SBatchObject> linkedBlockingQueuePolling,
			LinkedBlockingQueue<SBatchObject> linkedBlockingQueueUpdate, HPCConnector hpcConnector,
			BLASTTools blastTools, Timer timer, RunSomeRBHBLAST runSomeRBHBLAST, RunSomeRPSBLAST runSomeRPSBLAST, RunLevel1 runLevel1, RunLevel4 runLevel4) {
		this.linkedBlockingQueuePolling = linkedBlockingQueuePolling;
		this.linkedBlockingQueueUpdate = linkedBlockingQueueUpdate;
		this.hpcConnector = hpcConnector;
		this.blastTools = blastTools;
		this.timer = timer;
		this.runSomeRBHBLAST = runSomeRBHBLAST;
		this.runSomeRPSBLAST = runSomeRPSBLAST;
		//	this.runLevel1 = runLevel1;
		this.runLevel4 = runLevel4;
	}

	@Override
	public void run() {

		boolean shouldRun = true;
		while (shouldRun) {
			try {
				SBatchObject s = linkedBlockingQueuePolling.poll(5000, TimeUnit.MILLISECONDS); // Pop the first sbatch object from the queue
				if (s != null) {
					sBatchObjectsList.add(s);
				}
				for (SBatchObject sBatchObject : sBatchObjectsList) {
					if (sBatchObject.getOutputFilePath().compareTo("stop") != 0) { // If not the stop job
						printPollingLogStatement();
						if (checkStatus(sBatchObject)) { // If the job status = complete OR the job = failed (send to updating anyway)
							toRemove.add(sBatchObject); // add the job to the list of removals
							linkedBlockingQueueUpdate.add(sBatchObject); // add the job to the updating queue
						}
					}
				}

				for (SBatchObject sBatchObject : toRemove) {
					sBatchObjectsList.remove(sBatchObject); // remove the jobs that have been completed or failed
				}
				toRemove.clear();

				if (sBatchObjectsList.size() == 1 && sBatchObjectsList.get(0).getOutputFilePath().compareTo("stop") == 0 && linkedBlockingQueuePolling.size() == 0) { // If the stop job is the only job left
					linkedBlockingQueueUpdate.add(sBatchObjectsList.get(0));
					shouldRun = false; // Done polling for job completeness
				}
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (InterruptedException | ParseException e) {
				logger.error("Error happened while polling: {}", e);
			}
		}
	}
	
	private void printPollingLogStatement() {
		double timeElapsed = timer.getCurrentTimeElasped();
		if ((timeElapsed > 0.0) && (timeElapsed % 20) == 0) { //Every 20 minutes
			if (runSomeRBHBLAST != null) {
				logger.debug("RBH polling for " + sBatchObjectsList.size() + " jobs");
			} else if (runSomeRPSBLAST != null) {
				logger.debug("RPS polling for " + sBatchObjectsList.size() + " jobs");
			} else if (runLevel4 != null) {
				if(sBatchObjectsList.size() > 0)
					logger.debug("Level4ID#" + sBatchObjectsList.get(0).getLevel4RunId() + ". Polling for" + sBatchObjectsList.size() + " jobs");
			}
		}
	}

	private boolean checkStatus(SBatchObject sBatchObject) throws ParseException {
		if(sBatchObject != null) {
			String status = hpcConnector.getJobStatus(sBatchObject.getJobId()); // Get the job status
			if(status != null) {
				
				if(runLevel4 != null) { //Is Level4
					sBatchObject.getLevelFourAccessionRow().setStatus(status.toLowerCase()); //Set L4 accession row status to slurm status
					logger.debug("Level4 Run Id# " + sBatchObject.getLevel4RunId() + ". SLURM Job Id finished with a status of " + status + " : {}", sBatchObject.getJobId());
				} else if (runSomeRBHBLAST != null) { //Is RBH
					logger.debug("RBH Acc Run Id# " + sBatchObject.getAccessionRunId() + ". SLURM Job Id finished with a status of " + status + " : {}", sBatchObject.getJobId());
				} else if (runSomeRPSBLAST != null) { //Is RPS
					logger.debug("RPS Acc Run Id# " + sBatchObject.getAccessionRunId() + ". SLURM Job Id finished with a status of " + status + " : {}", sBatchObject.getJobId());
				}
				
				if (status.equals("COMPLETED")) {
					File file = new File(sBatchObject.getErrorFilePath()); // Check for content in error file for completed jobs
					if (file.length() > 0) debugError(true, sBatchObject, status);
					recordJobStats(sBatchObject);
					return true;
				} else if (status.equals("FAILED")) {
					debugError(false, sBatchObject, status);
					return true; 
				} else if (status.equals("TIMEOUT")) { 
					toRemove.add(sBatchObject);
					SBatchObject newSBatchObject = null;
					if (runSomeRBHBLAST != null) {
						newSBatchObject = runSomeRBHBLAST.resubmitJob(sBatchObject);
					} else if (runSomeRPSBLAST != null) {
						newSBatchObject = runSomeRPSBLAST.resubmitJob(sBatchObject);
//					} else if(runLevel1 != null) {
//						newSBatchObject = runLevel1.resubmitJob(sBatchObject);
					} else if (runLevel4 != null) {
						newSBatchObject = runLevel4.resubmitJob(sBatchObject);
					}
					if (newSBatchObject != null) linkedBlockingQueuePolling.add(newSBatchObject);
					return false;
				} 
				else {
					boolean isProductive = hpcConnector.isSlurmJobProductive(status);
					if(isProductive) {
						double timeElapsed = timer.getCurrentTimeElasped();
						if ((timeElapsed > 0.0) && (timeElapsed % 30) == 0) {
							logger.warn("It has been {} minutes. Jobs are still either pending or running or completing or configuring", timeElapsed);
						}
						logger.debug("SLURM Job Id has a status of : {}, {}", sBatchObject.getJobId(), status);
						return false;
					} else {
						debugError(false, sBatchObject, status);
						return true;
					}
				}
			} else {
				logger.error("checkStatus: status is null.");
				return true;
			}
		} else {
			logger.error("checkStatus: sBatchObject is null. This should never happen.");
			return true;
		}
	}

	private void recordJobStats(SBatchObject sBatchObject) throws ParseException {

		String scontrolInfoString = hpcConnector.scontrol(sBatchObject.getJobId());
		if (scontrolInfoString != null) {
			String jobType= "";
			if (runSomeRBHBLAST != null) {
				jobType = "rbhBLAST";
			} else if (runSomeRPSBLAST != null) {
				jobType = "rpsBLAST";
//			} else if (runLevel1 != null){
//				jobType = "BLASTp";
			} else if( runLevel4 != null) {
				jobType ="I-TASSER"; //TODO: Check formatting here
			}
			
			Map<String, String> sControlInfoMap = hpcConnector.parseScontrolString(scontrolInfoString);

			String submitInfo = sControlInfoMap.get("SubmitTime");
			String[] s1 = submitInfo.split("T");
			String unformattedSubmitDate = s1[0];
			String formattedSubmitDate = formatDate(unformattedSubmitDate);
			String submitTime = s1[1];

			
			String endInfo = sControlInfoMap.get("EndTime");
			String[] s2 = endInfo.split("T");
			String unformattedEndDate = s2[0];
			String formattedEndDate = formatDate(unformattedEndDate);
			String endTime = s2[1];

			String startInfo = sControlInfoMap.get("StartTime");
			String [] s3 = startInfo.split("T");
			String unformattedStartDate = s3[0];
			String formattedStartDate = formatDate(unformattedStartDate);
			String startTime = s3[1];
			
			SimpleDateFormat fullFormat = new SimpleDateFormat("dd/M/yyyy HH:mm:ss");
			Date submitDate = fullFormat.parse(formattedSubmitDate + " " + submitTime);
			Date startDate = fullFormat.parse(formattedStartDate + " " + startTime);
			Date endDate = fullFormat.parse(formattedEndDate + " " + endTime);

			long secondsInMilli = 1000;
			long different1 = endDate.getTime() - submitDate.getTime();
			long differenceSeconds = different1 / secondsInMilli;
			
			double userTime = Long.valueOf(differenceSeconds).doubleValue();

			long different2 = endDate.getTime() - startDate.getTime();
			long minutesInMilli = secondsInMilli * 60;
			long hoursInMilli = minutesInMilli * 60;
			long daysInMilli = hoursInMilli * 24;

			long elapsedDays = different2 / daysInMilli;
			different2 = different2 % daysInMilli;
			
			long elapsedHours = different2 / hoursInMilli;
			different2 = different2 % hoursInMilli;
			
			long elapsedMinutes = different2 / minutesInMilli;
			different2 = different2 % minutesInMilli;
			
			long elapsedSeconds = different2 / secondsInMilli;
			
			SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
			Date reference = dateFormat.parse("00:00:00");
			elapsedHours = elapsedHours + (elapsedDays * 24);
			Date time = dateFormat.parse(elapsedHours + ":" + elapsedMinutes + ":" + elapsedSeconds);
			int numCPUs = Integer.parseInt(sControlInfoMap.get("NumCPUs"));
			long seconds = (time.getTime() - reference.getTime()) / 1000L;
			double percentCPU = ((numCPUs * seconds) * 100);
					
			String unFormattedElapsedTime = sControlInfoMap.get("RunTime");
			String elapsedTime = formatElapsedTime(unFormattedElapsedTime);
			
			
			StringBuilder otherInfoStringBuilder = new StringBuilder();
			otherInfoStringBuilder.append("JodId = " + sControlInfoMap.get("JobId"));
			otherInfoStringBuilder.append(" TimeLimit = " + sControlInfoMap.get("TimeLimit"));
			otherInfoStringBuilder.append(" NumNodes = " + sControlInfoMap.get("NumNodes"));
			otherInfoStringBuilder.append(" NumCPU = " + sControlInfoMap.get("NumCPUs"));
			otherInfoStringBuilder.append(" NumTasks = " + sControlInfoMap.get("NumTasks"));
			String otherInfo = otherInfoStringBuilder.toString();

			StringBuilder failureNotesStringBuilder = new StringBuilder();
			failureNotesStringBuilder.append("Requeue = " + sControlInfoMap.get("Requeue"));
			failureNotesStringBuilder.append(" Restarts = " + sControlInfoMap.get("Restarts"));
			failureNotesStringBuilder.append(" ExitCode = " + sControlInfoMap.get("ExitCode"));
			String failureNotes = failureNotesStringBuilder.toString();

			blastTools.logJobStats(jobType, sBatchObject.getAccessionRunId(),
					sBatchObject.getJobFragmentNumber(), sBatchObject.getNumberOfProteinsInFasta(), userTime,
					elapsedTime, percentCPU, otherInfo, failureNotes);
		} else {
			System.out.println("Could not send job stats for jobID " + sBatchObject.getJobId() + " because scontrol returned null");
			logger.error("Could not send job stats for jobID {} because scontrol returned null.", sBatchObject.getJobId());
		}
	}
	
	private String formatElapsedTime(String elapsedTime) {
		String[] eStrings = elapsedTime.split("-");
		if (eStrings.length > 1) {
			int days = Integer.parseInt(eStrings[0]);
			String time = eStrings[1];
			String[] splitStrings = time.split(":");
			int hours = Integer.parseInt(splitStrings[0]);
			int min = Integer.parseInt(splitStrings[1]);
			int sec = Integer.parseInt(splitStrings[2]);

			hours = hours + (days * 24);
			return hours + ":" + min + ":" + sec;

		} else {
			return elapsedTime;
		}
	}
	
	private String formatDate(String unformattedDate) {
		//converts yyyy-mm-dd TO mm/dd/yyyy
		String [] splitStrings = unformattedDate.split("-");
		String year = splitStrings[0];
		String month = splitStrings[1];
		String day = splitStrings[2];
		
		return day + "/" + month + "/" + year;
	}

	private void debugError(boolean completed, SBatchObject sBatchObject, String slurmStatus) {
		if (completed) {
			logger.warn("Job status for {} was COMPLETED, but it has an error in its slurm file.", sBatchObject.getJobId());
		} else {
			logger.error("Job status was {} for job {}", slurmStatus, sBatchObject.getJobId());
		}

		File errorFile = new File(sBatchObject.getErrorFilePath());
		try {
			Scanner scanner = new Scanner(errorFile);
			logger.error("Error for job {}", sBatchObject.getJobId());
			int lineNum =0;
			while (scanner.hasNext()) {
				logger.error("Line number {},  {}", lineNum, scanner.nextLine());
				lineNum++;
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			logger.error("Could not find error file for job {}", sBatchObject.getJobId());
			e.printStackTrace();
		}
	}

}
