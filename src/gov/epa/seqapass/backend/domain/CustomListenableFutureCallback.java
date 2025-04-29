package gov.epa.seqapass.backend.domain;

import org.springframework.util.concurrent.ListenableFutureCallback;
import gov.epa.seqapass.backend.domain.TaskGroup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("hiding")
public class CustomListenableFutureCallback<Object> implements ListenableFutureCallback<Object> {
	
	private static Logger logger = LogManager.getLogger(CustomListenableFutureCallback.class);

	public CustomListenableFutureCallback(TaskGroup taskGroup) {
		this.taskGroup = taskGroup;
	}

	private TaskGroup taskGroup;

	@Override
	public void onSuccess(Object arg0) {
		// System.out.println("Task " + taskGroup.getTaskName() + " completed with result: " + arg0.toString());
		logger.debug("Task {} completed with result: {}", 
				taskGroup.getTaskName(), arg0.toString());
		this.taskGroup.setCompletedTaskCount(taskGroup.getCompletedTaskCount() + 1);
		this.taskGroup.setQueuedTaskCount(taskGroup.getQueuedTaskCount() - 1);
	}

	@Override
	public void onFailure(Throwable arg0) {
		//System.out.println("Task " + taskGroup.getTaskName() + " failed with message: " + arg0.getMessage() + "\n toString() of "
		//		+ arg0.toString() + "\n getCause() " + arg0.getCause());
		logger.error("Task {} failed with message: {} \n toString() of {} \n getCause() {}", 
				taskGroup.getTaskName(), arg0.getMessage(), arg0.toString(), arg0.getCause());
		this.taskGroup.setFailedTaskCount(taskGroup.getFailedTaskCount() + 1);
		this.taskGroup.setQueuedTaskCount(taskGroup.getQueuedTaskCount() - 1);
	}

	public TaskGroup getTaskGroup() {
		return taskGroup;
	}

	public void setTaskGroup(TaskGroup taskGroup) {
		this.taskGroup = taskGroup;
	}

}
