package gov.epa.seqapass.backend.hpcAccess;

public interface hpcConnectorIF {
	/**
	 * An id number specifies the job. From Linux man pages:<br/>
	 * view information about Slurm nodes and partitions
	 * 
	 * @return String[] - each string is a delimited row containing information
	 */
	String[] sinfo();

	/**
	 * Input parameters are TBD. From Linux man pages: <br/>
	 * Used view and modify Slurm configuration and state
	 * 
	 * @return String[] - each string is a delimited row containing information
	 *         Slurm nodes and partitions
	 */
	String scontrol(int jobId);

	/**
	 * Input parameters are TBD, but the values that must be set one way or another
	 * include
	 * <ul>
	 * <li>the full path to the sudo command</li>
	 * <li>the user present on the local and remote machine</li>
	 * <li>the full path to the sbatch command</li>
	 * <li>the full path the command to execute</li>
	 * </ul>
	 * From Linux man pages: <br/>
	 * Submit a batch script to Slurm
	 * 
	 * @return int - the job id or -1 if failure
	 */
	int sbatch( String scriptName);

	/*
	 * Others that may not be needed
	 * 
	 * String sacct();
	 * 
	 * String salloc();
	 * 
	 * String sattach();
	 * 
	 * String sbatch();
	 * 
	 * String sbcast();
	 * 
	 * String smap();
	 * 
	 * String squeue();
	 * 
	 * String srun();
	 * 
	 * String strigger();
	 * 
	 * String sview();
	 * 
	 */
}
