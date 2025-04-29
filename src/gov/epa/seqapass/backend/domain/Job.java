package gov.epa.seqapass.backend.domain;

import java.sql.Timestamp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Job {
	
	private static Logger logger = LogManager.getLogger(Job.class);

	private String jobName;
	private Integer jobPriority;
	private Timestamp submitTime;
	
	public Job(){
		
	}
	
	public Job(String jobName, Integer jobPriority){
		this.jobName = jobName;
		this.jobPriority = jobPriority;
		this.submitTime = new Timestamp(System.currentTimeMillis());
	}
	
	
	public String runJob(){
		// System.out.println("Job " + jobName + " is active");
		logger.info("Job {} is active", jobName);
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			logger.error("Failed to suspend execution of current thread: {}", e);
			e.printStackTrace();
		}
		// System.out.println("Finished " + jobName + " with Priority " + jobPriority);
		logger.debug("Finished {} with Priority {}", jobName, jobPriority);
		return "Finished " + jobName + " with Priority " + jobPriority;
	}
	
	

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public Integer getJobPriority() {
		return jobPriority;
	}

	public void setJobPriority(Integer jobPriority) {
		this.jobPriority = jobPriority;
	}

	public Timestamp getSubmitTime() {
		return submitTime;
	}

	public void setSubmitTime(Timestamp submitTime) {
		this.submitTime = submitTime;
	}
}
