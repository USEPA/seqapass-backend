package gov.epa.seqapass.backend.dao;

import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import gov.epa.seqapass.backend.domain.ThreadPool;

public class ThreadPoolMonitorServiceImpl implements ThreadPoolMonitorService {

	public static final String RBHPool = "RBH_ThreadPool";
	public static final String RPSPool = "RPS_ThreadPool";
	public static final String LevelOnePool = "LevelOne_ThreadPool";
	public static final String LevelTwoPool = "LevelTwo_ThreadPool";
	public static final String LevelThreePool = "LevelThree_ThreadPool";
	public static final String LevelOneToxcastPool = "LevelOne_Toxcast_ThreadPool";
	public static final String LevelTwoToxcastPool = "LevelTwo_Toxcast_ThreadPool";
	public static final String LevelFourFastaPool = "LevelFourFasta_ThreadPool";
	public static final String LevelFourITasserPool = "LevelFourITasser_ThreadPool";
	public static final String LevelFourTMAlignPool = "LevelFourTMAlign_ThreadPool";

	public ThreadPoolMonitorServiceImpl(ThreadPoolTaskExecutor levelOneTaskExecutor, ThreadPoolTaskExecutor rbhTaskExecutor,
			ThreadPoolTaskExecutor rpsTaskExecutor, ThreadPoolTaskExecutor levelTwoTaskExecutor,
			ThreadPoolTaskExecutor levelThreeTaskExecutor, ThreadPoolTaskExecutor levelOneToxcastTaskExecutor, ThreadPoolTaskExecutor levelTwoToxcastTaskExecutor,
			ThreadPoolTaskExecutor levelFourFastaTaskExecutor, ThreadPoolTaskExecutor levelFourItasserTaskExecutor,
			ThreadPoolTaskExecutor levelFourTMAlignTaskExecutor) {
		ThreadPool levelOnePool = new ThreadPool(levelOneTaskExecutor, LevelOnePool);
		threadPools.put(LevelOnePool, levelOnePool);
		ThreadPool rbhPool = new ThreadPool(rbhTaskExecutor, RBHPool);
		threadPools.put(RBHPool, rbhPool);
		ThreadPool rpsPool = new ThreadPool(rpsTaskExecutor, RPSPool);
		threadPools.put(RPSPool, rpsPool);
		ThreadPool levelTwoPool = new ThreadPool(levelTwoTaskExecutor, LevelTwoPool);
		threadPools.put(LevelTwoPool, levelTwoPool);
		ThreadPool levelThreePool = new ThreadPool(levelThreeTaskExecutor, LevelThreePool);
		threadPools.put(LevelThreePool, levelThreePool);
		ThreadPool levelOneToxcastPool = new ThreadPool(levelOneToxcastTaskExecutor, LevelOneToxcastPool);
		threadPools.put(LevelOneToxcastPool, levelOneToxcastPool);
		ThreadPool levelTwoToxcastPool = new ThreadPool(levelTwoToxcastTaskExecutor, LevelTwoToxcastPool);
		threadPools.put(LevelOneToxcastPool, levelTwoToxcastPool);
		ThreadPool levelFourFastaPool = new ThreadPool(levelFourFastaTaskExecutor, LevelFourFastaPool);
		threadPools.put(LevelFourFastaPool, levelFourFastaPool);
		ThreadPool levelFourITasserPool = new ThreadPool(levelFourItasserTaskExecutor, LevelFourITasserPool);
		threadPools.put(LevelFourITasserPool, levelFourITasserPool);
		ThreadPool levelFourTMAlignPool = new ThreadPool(levelFourTMAlignTaskExecutor, LevelFourTMAlignPool);
		threadPools.put(LevelFourTMAlignPool, levelFourTMAlignPool);
		
		
	}

	private Map<String, ThreadPool> threadPools = new HashMap<String, ThreadPool>();

	// @Override
	// public Map<String, Object> getThreadPoolInfo(String poolName) {
	// Map<String, Object> result = new LinkedHashMap<String, Object>();
	// // ThreadPool pool = threadPools.get(poolName);
	// // ThreadPoolTaskExecutor poolExec = pool.getExecutor();
	// // result.put("Pool Name", poolName);
	// // result.put("Current Pool Size", Integer.toString(poolExec.getPoolSize()));
	// // result.put("Core Pool Size", Integer.toString(poolExec.getCorePoolSize()));
	// // result.put("Max Pool Size", Integer.toString(poolExec.getMaxPoolSize()));
	// // result.put("Max Queue Size", Integer.toString(poolExec.getThreadPoolExecutor().getQueue().size() +
	// // poolExec.getThreadPoolExecutor().getQueue().remainingCapacity()));
	// // result.put("Queues Available", Integer.toString(poolExec.getThreadPoolExecutor().getQueue().remainingCapacity()));
	// //
	// // int count = 1;
	// // for(TaskGroup group:pool.getTaskGroupList()){
	// // result.put("Group" + count, group.getTaskName());
	// // result.put("Total Task Count" + count, Integer.toString(group.getTaskCount()));
	// // result.put("Queued Task Count" + count, Integer.toString(group.getQueuedTaskCount()));
	// // result.put("Completed Task Count" + count, Integer.toString(group.getCompletedTaskCount()));
	// // result.put("\tFailed Task Count" + count, Integer.toString(group.getFailedTaskCount()));
	// // count++;
	// // }
	//
	// return result;
	// }

	public ThreadPool getLevelOnePool() {
		return threadPools.get(LevelOnePool);
	}

	public ThreadPool getRBHPool() {
		return threadPools.get(RBHPool);
	}

	public ThreadPool getRPSPool() {
		return threadPools.get(RPSPool);
	}

	public ThreadPool getLevelTwoPool() {
		return threadPools.get(LevelTwoPool);
	}

	public ThreadPool getLevelThreePool() {
		return threadPools.get(LevelThreePool);
	}
	
	public ThreadPool getLevelOneToxcastPool() {
		return threadPools.get(LevelOneToxcastPool);
	}
	
	public ThreadPool getLevelTwoToxcastPool() {
		return threadPools.get(LevelTwoToxcastPool);
	}
	
	public ThreadPool getLevelFourFastaPool() {
		return threadPools.get(LevelFourFastaPool);
	}
	
	public ThreadPool getLevelFourITasserPool() {
		return threadPools.get(LevelFourITasserPool);
	}
	
	public ThreadPool getLevelFourTMAlignPool() {
		return threadPools.get(LevelFourTMAlignPool);
	}

	// @Override
	// public Map<String, Object> getAllThreadPoolInfo() {
	// Map<String, Object> allResults = new LinkedHashMap<String, Object>();
	// allResults.putAll(getThreadPoolInfo(LevelOnePool));
	// // allResults.putAll(getThreadPoolInfo(RBHPool));
	// return allResults;
	// }

	public Map<String, ThreadPool> getThreadPools() {
		return threadPools;
	}

	public void setThreadPools(Map<String, ThreadPool> threadPools) {
		this.threadPools = threadPools;
	}


}
