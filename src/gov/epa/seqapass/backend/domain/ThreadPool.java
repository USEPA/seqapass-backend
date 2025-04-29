package gov.epa.seqapass.backend.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThreadPool {
	
	private static Logger logger = LogManager.getLogger(ThreadPool.class);

	public ThreadPool(ThreadPoolTaskExecutor executor, String poolName) {
		this.executor = executor;
		this.poolName = poolName;
		this.CorePoolSize = executor.getCorePoolSize();
		this.maxPoolSize = executor.getMaxPoolSize();
		this.maxQueueSize = executor.getThreadPoolExecutor().getQueue().size()
				+ executor.getThreadPoolExecutor().getQueue().remainingCapacity();
		this.currentPoolSize = executor.getActiveCount();
		this.availableQueues = executor.getThreadPoolExecutor().getQueue().remainingCapacity();
	}

	private ThreadPoolTaskExecutor executor;
	private String poolName;
	private int currentPoolSize;
	private int CorePoolSize;
	private int maxPoolSize;
	private int maxQueueSize;
	private int availableQueues;

	private List<TaskGroup> taskGroupList = new ArrayList<TaskGroup>();

	public void addTaskGroup(TaskGroup taskGroup) {
		// System.out.println("Added group");
		logger.info("Added group");
		taskGroupList.add(taskGroup);
	}

	public void resetTaskGroups() {
		taskGroupList.clear();
	}

	public Map<String, Object> getThreadPoolInfo() {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("Pool Name", getPoolName());
		result.put("Current Pool Size", Integer.toString(executor.getActiveCount()));
		result.put("Core Pool Size", Integer.toString(getCorePoolSize()));
		result.put("Max Pool Size", Integer.toString(getMaxPoolSize()));
		result.put(
				"Max Queue Size",
				Integer.toString(executor.getThreadPoolExecutor().getQueue().size()
						+ executor.getThreadPoolExecutor().getQueue().remainingCapacity()));
		result.put("Queues Available", Integer.toString(executor.getThreadPoolExecutor().getQueue().remainingCapacity()));

		int count = 1;
		for (TaskGroup group : getTaskGroupList()) {
			if (group.getCompletedTaskCount() == 0) {
				result.put("Group" + count, group.getTaskName());
				result.put("Group" + count + ": " + "Total Task Count" + count, Integer.toString(group.getTaskCount()));
				result.put("Group" + count + ": " + "Queued Task Count" + count, Integer.toString(group.getQueuedTaskCount()));
				result.put("Group" + count + ": " + "Completed Task Count" + count, Integer.toString(group.getCompletedTaskCount()));
				result.put("Group" + count + ": " + "Failed Task Count" + count, Integer.toString(group.getFailedTaskCount()));
				count++;
			}
		}

		return result;
	}

	public List<TaskGroup> getTaskGroupList() {
		return taskGroupList;
	}

	public void setTaskGroupList(List<TaskGroup> taskGroupList) {
		this.taskGroupList = taskGroupList;
	}

	public ThreadPoolTaskExecutor getExecutor() {
		return executor;
	}

	public void setExecutor(ThreadPoolTaskExecutor executor) {
		this.executor = executor;
	}

	public String getPoolName() {
		return poolName;
	}

	public int getCurrentPoolSize() {
		return currentPoolSize;
	}

	public int getCorePoolSize() {
		return CorePoolSize;
	}

	public int getMaxPoolSize() {
		return maxPoolSize;
	}

	public int getMaxQueueSize() {
		return maxQueueSize;
	}

	public int getAvailableQueues() {
		return availableQueues;
	}

}
