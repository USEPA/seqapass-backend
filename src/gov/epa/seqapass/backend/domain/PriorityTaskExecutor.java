package gov.epa.seqapass.backend.domain;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PriorityTaskExecutor extends ThreadPoolTaskExecutor{
	/**
	 * 
	 */
	
	private static Logger logger = LogManager.getLogger(PriorityTaskExecutor.class);
	
	private static final long serialVersionUID = 7398767667714165047L;

	private ThreadPoolTaskExecutor executor;
	
	public PriorityTaskExecutor(){
		
	}
	
	@Override
	protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
		// System.out.println("queueCapacity is " + queueCapacity);
		logger.info("queueCapacity is {}", queueCapacity);
        return new PriorityBlockingQueue<Runnable>(queueCapacity);
    }
	

}
