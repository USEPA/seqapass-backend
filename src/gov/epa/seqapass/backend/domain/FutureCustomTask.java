package gov.epa.seqapass.backend.domain;

import java.sql.Timestamp;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class FutureCustomTask extends FutureTask<FutureCustomTask> implements Comparable<FutureCustomTask>{

	private Task task;
	
	public FutureCustomTask(Task task) {
		super(task, null);
		this.task = task;
	}

//	@Override
//	public int compareTo(FutureCustomTask o) {
//		//First check for highest priority
//		int result = task.getJob().getJobPriority().compareTo(o.task.getJob().getJobPriority());
//		
//		//If all same priority, check for earliest submit time
//		if (result == 0) {
//			result = task.getJob().getSubmitTime().compareTo(o.task.getJob().getSubmitTime());
//		}
//
//		return result;
//	}
	
	@Override
	public int compareTo(FutureCustomTask o) {
		Timestamp nowTime = new Timestamp(System.currentTimeMillis());
		
		//current job
		Integer jobPriority = task.getJob().getJobPriority();
		Timestamp submitTime = task.getJob().getSubmitTime();
		//adjust priority of current job
		int days = (int)TimeUnit.MILLISECONDS.toDays(nowTime.getTime() - submitTime.getTime());
		if (jobPriority > 1) { //don't want to supersede priority 0 jobs
			jobPriority = Math.max(1, jobPriority - days);
		}
		
		//comparison job
		Integer oJobPriority = o.task.getJob().getJobPriority();
		Timestamp oSubmitTime = o.task.getJob().getSubmitTime();
		//adjust priority of comparison job
		days = (int)TimeUnit.MILLISECONDS.toDays(nowTime.getTime() - o.task.getJob().getSubmitTime().getTime());
		if (oJobPriority > 1){
			oJobPriority = Math.max(1,  oJobPriority - days);
		}
		
		//now compare by job priority first, submit time second
		int result = jobPriority.compareTo(oJobPriority);
		if (result == 0){
			result = submitTime.compareTo(oSubmitTime);
		}
		return result;
	}
	
//	public int DevcompareTo(FutureCustomTask o) {
//		Timestamp nowTime = new Timestamp(System.currentTimeMillis());
//		
//		long interval = TimeUnit.DAYS.toMillis(5); // 5 days
//		
//		Integer jobPriority = task.getJob().getJobPriority();
//		Timestamp submitTime = task.getJob().getSubmitTime();
//		// check if submitTime is more than X time ago (ex. 5 days ago)
//		// if so jobPriority -= 1; (with check that it is not negative)
//		if ((nowTime.getTime() - submitTime.getTime()) > interval){
//			if (jobPriority > 1){ //don't want to supersede priority 0 jobs 
//				jobPriority -=1;
//			}
//		}
//		
//		Integer oJobPriority = o.task.getJob().getJobPriority();
//		Timestamp oSubmitTime = o.task.getJob().getSubmitTime();
//		// check if submitTime is more than X time ago (ex. 5 days ago)
//		// if so jobPriority -= 1; (with check that it is not negative)
//		if ((nowTime.getTime() - oSubmitTime.getTime()) > interval){
//			if (oJobPriority > 1){ //don't want to supersede priority 0 jobs
//				oJobPriority -=1;
//			}
//		}
//		
//		//now compare by job priority, submit time
//		
//		int result = jobPriority.compareTo(oJobPriority);
//		if (result == 0){
//			result = submitTime.compareTo(oSubmitTime);
//		}
//		return result;
//	}

}
