package gov.epa.seqapass.backend.dao;

import java.util.Map;

import gov.epa.seqapass.backend.domain.ThreadPool;

public interface ThreadPoolMonitorService {

	// public Map<String, Object> getThreadPoolInfo(String poolName);

	// public Map<String, Object> getAllThreadPoolInfo();

	public ThreadPool getLevelOnePool();

	public ThreadPool getRBHPool();

	public ThreadPool getRPSPool();

	public ThreadPool getLevelTwoPool();

	public ThreadPool getLevelThreePool();
	
	public ThreadPool getLevelOneToxcastPool();
	
	public ThreadPool getLevelTwoToxcastPool();
	
	public ThreadPool getLevelFourFastaPool();
	
	public ThreadPool getLevelFourITasserPool();
	
	public ThreadPool getLevelFourTMAlignPool();

	public Map<String, ThreadPool> getThreadPools();

}
