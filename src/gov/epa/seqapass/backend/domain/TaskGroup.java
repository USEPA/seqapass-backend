package gov.epa.seqapass.backend.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.concurrent.ListenableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaskGroup {
	
	private static Logger logger = LogManager.getLogger(TaskGroup.class);
	
	public TaskGroup(String name, List<ListenableFuture<Object>> futures) {
		this.taskName = name;
		this.taskCount = futures.size();
		this.completedTaskCount = 0;
		this.queuedTaskCount = futures.size();
		this.activeTaskCount = 0;
		this.failedTaskCount = 0;
		this.taskFutureList = futures;
	}
	
	public TaskGroup(String name, ListenableFuture<Object> future) {
		this.taskName = name;
		this.taskCount = 1;
		this.completedTaskCount = 0;
		this.queuedTaskCount = 1;
		this.activeTaskCount = 0;
		this.failedTaskCount = 0;
		this.taskFutureList = new ArrayList<ListenableFuture<Object>>();
		this.taskFutureList.add(future);
	}

	private String taskName;
	private int taskCount;
	private int completedTaskCount;
	private int queuedTaskCount;
	private int activeTaskCount;
	private int failedTaskCount;
	
	List<ListenableFuture<Object>> taskFutureList;
	
	public void getTaskInfo(){
		//System.out.println(String.format("[monitor] Name: %s, Tasks: %d, Completed: %d, Queued: %d, Active: %d, Failed: %d",
		//		this.getTaskName(), this.getTaskCount(), this.getCompletedTaskCount(), this.getQueuedTaskCount(),
		//		this.getActiveTaskCount(), this.getFailedTaskCount()));
		logger.info(String.format("[monitor] Name: %s, Tasks: %d, Completed: %d, Queued: %d, Active: %d, Failed: %d",
				this.getTaskName(), this.getTaskCount(), this.getCompletedTaskCount(), this.getQueuedTaskCount(),
				this.getActiveTaskCount(), this.getFailedTaskCount()));
//		System.out.println("Task Count = " + getTaskCount());
//		System.out.println("Completed Count = " + getCompletedTaskCount());
//		System.out.println("Queued Count = " + getQueuedTaskCount());
//		System.out.println("Active Count = " + getActiveTaskCount());
//		System.out.println("Failed Count = " + getFailedTaskCount());
	}
	
	public void setCallbackList(List<ListenableFuture<Object>> futures){
		for(ListenableFuture<Object> future : futures){
			future.addCallback(new CustomListenableFutureCallback<Object>(this));
		}
	}
	
	public void setCallbackList(ListenableFuture<Object> future){
			future.addCallback(new CustomListenableFutureCallback<Object>(this));
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}

	public int getTaskCount() {
		return taskCount;
	}

	public void setTaskCount(int taskCount) {
		this.taskCount = taskCount;
	}

	public int getCompletedTaskCount() {
		return completedTaskCount;
	}

	public void setCompletedTaskCount(int completedTaskCount) {
		this.completedTaskCount = completedTaskCount;
	}

	public int getQueuedTaskCount() {
		return queuedTaskCount;
	}

	public void setQueuedTaskCount(int queuedTaskCount) {
		this.queuedTaskCount = queuedTaskCount;
	}

	public int getActiveTaskCount() {
		return activeTaskCount;
	}

	public void setActiveTaskCount(int runningTaskCount) {
		this.activeTaskCount = runningTaskCount;
	}

	public int getFailedTaskCount() {
		return failedTaskCount;
	}

	public void setFailedTaskCount(int failedTaskCount) {
		this.failedTaskCount = failedTaskCount;
	}

	public List<ListenableFuture<Object>> getTaskFutureList() {
		return taskFutureList;
	}

	public void setTaskFutureList(List<ListenableFuture<Object>> taskFutureList) {
		this.taskFutureList = taskFutureList;
	}

}
