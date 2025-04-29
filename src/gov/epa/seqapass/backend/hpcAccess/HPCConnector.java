package gov.epa.seqapass.backend.hpcAccess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HPCConnector implements hpcConnectorIF {
	
	private static Logger logger = LogManager.getLogger(HPCConnector.class);
	
	private String sudoCommandFullPath;
	private String sbatchCommandFullPath;
	private String remoteIP;
	private String commonUserLocal;
	private String commonUserRemote;
	
	/**
	 * This will be used for reading output
	 */
	private String commonRootLocal = "/work/SEQAPASS";
	/**
	 * This will be used for writing output
	 */
	private String commonRootRemote = "/work/SEQAPASS";

	/**
	 * This is the directory name in the commonRoot[either] which can be accessed by both the local and HPC computers.
	 */
	private String commonDirectory;
	
	/**
	 * NOTE: Tom wonders if the constructor should always have all of these parts.
	 * This class should be reusable with minimal changes needed to set up another
	 * SLURM-based system
	 * 
	 * @param sudoCommandFullPath
	 * @param commongUserRemote
	 * @param commonDirectoyRemote
	 */
	public HPCConnector(String sudoCommandFullPath,String commonUserRemote, String commonDirectory) {
		this.sudoCommandFullPath = sudoCommandFullPath;
		this.commonUserRemote = commonUserRemote;
		this.commonDirectory = commonDirectory;
	}

	public String[] sinfo() {
		return null;
	}

	public String scontrol(int jobId) {
		String jobIdString = Integer.toString(jobId);
		String[] command = { "/usr/local/bin/scontrol", "show", "job", jobIdString };

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();
		Process process;
		try {
			process = processBuilder.start();

			InputStream stdOutStream = process.getInputStream();
			InputStreamReader stdOutStreamReader = new InputStreamReader(stdOutStream);
			BufferedReader stdOutBufferedReader = new BufferedReader(stdOutStreamReader);
			String stdOutLine;
			while ((stdOutLine = stdOutBufferedReader.readLine()) != null) {
				stdOutStringBuilder.append(stdOutLine);
			}

			InputStream stdErrStream = process.getErrorStream();
			InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
			BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
			String stdErrLine;
			while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
				stdErrStringBuilder.append(stdErrLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		String stdOut = stdOutStringBuilder.toString(); // stdOut = a string containing many features of a single job
		if (stdOut.equals("slurm_load_jobs error: Invalid job id specified")) {
			return null;
		} else {
			return stdOut;
		}
	}
	
	public Map<String, String> parseScontrolString(String scontrolString) {
		Map<String, String> map = new HashMap<String, String>();
		String formattedSControlString = scontrolString.replaceAll("\\s+", " ");
		String[] scontrolProperties = formattedSControlString.split(" "); //Should consider more than 1 "="
		for (int i = 0; i < scontrolProperties.length; i++) {
			String oneProperty = scontrolProperties[i];
			String[] onePropertyInfo = oneProperty.split("=");
			if (i == (scontrolProperties.length - 1)) {
				break;
			} else {
				if(onePropertyInfo.length == 2) {
					map.put(onePropertyInfo[0], onePropertyInfo[1]);
				}
			}
		}
		return map;
	}
	
	public String getJobStatus(int jobId) {
		String jobIdString = Integer.toString(jobId);
		String [] command = {"/usr/local/bin/scontrol", "show", "job", jobIdString}; 
	
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();
		
		Process process;
		try {
			process = processBuilder.start();

			InputStream stdOutStream = process.getInputStream();
			InputStreamReader stdOutStreamReader = new InputStreamReader(stdOutStream);
			BufferedReader stdOutBufferedReader = new BufferedReader(stdOutStreamReader);
		
			String stdOutLine;
			while ((stdOutLine = stdOutBufferedReader.readLine()) != null) {
				stdOutStringBuilder.append(stdOutLine);
			}
			
			InputStream stdErrStream = process.getErrorStream();
			InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
			BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
			String stdErrLine;
			while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
				stdErrStringBuilder.append(stdErrLine);
			}
			
		} catch (IOException e) {
			logger.error("Error getting job status in HPC Connector: {}", e);
		}
		
		
		String stdOut = stdOutStringBuilder.toString(); //stdOut = a string containing many features of a single job
		String [] strings = stdOut.split(" ");
		String jobStateLong = null;
		for(int i =0; i < strings.length;i++) {
			if(strings[i].contains("JobState")) { //Specifically looking for JobState status
				jobStateLong = strings[i];
			}
		}
		
		String stderr = stdErrStringBuilder.toString();
		// Not sure what might come out of stderr, but don't expect anything
		
		if (jobStateLong != null) {
			String[] strings2 = jobStateLong.split("=");
			return strings2[1];
		} else {
			return"UNKOWN";
		}
	}
	
	/**
	 * Used to determine whether the job is still processing or has terminated.
	 * @param jobStatus
	 * @return
	 */
	public boolean isSlurmJobProductive(String jobStatus) {
		switch (jobStatus.toUpperCase()) {
			case "BOOT_FAIL":
				return false;
			case "CANCELLED":
				return false;
			case "COMPLETED":
				return false;
			case "DEADLINE":
				return false;
			case "FAILED":
				return false;
			case "NODE_FAIL":
				return false;
			case "OUT_OF_MEMORY":
				return false;
			case "PENDING":
				return true;
			case "PREEMPTED":
				return false;
			case "RUNNING":
				return true;
			case "SUSPENDED":
				return true;
			case "TIMEOUT":
				return false;
			case "COMPLETING":
				return true;
			case "CONFIGURING":
				return true;
			case "LAUNCH_FAILED":
				return true;
			case "POWER_UP_NODE":
				return true;
			case "RECONFIG_FAIL":
				return false;
			case "REQUEUED":
				return true;
			case "REQUEUE_FED":
				return true;
			case "REQUEUE_HOLD":
				return true;
			case "RESIZING":
				return true;
			case "RESV_DEL_HOLD":
				return true;
			case "REVOKED":
				return false;
			case "SIGNALING":
				return true;
			case "SPECIAL_EXIT":
				return true;
			case "STAGE_OUT":
				return true;
			case "STOPPED":
				return false;
			case "UPDATE_DB":
				return true;
		}
		return true;
	}

	public int sbatch(String scriptName) {
		String[] command = { sudoCommandFullPath, "-u", commonUserRemote, sbatchCommandFullPath, commonRootRemote + "/" + commonDirectory + "/" + scriptName };
		logger.info("sbatch command script = {}", command[4]);

//		String command = "sudo -u seqapass /usr/local/bin/sbatch "+ System.getProperty("catalina.base")+"/webresults/sbatch_testing/test4.sh";
		int resultingJobId = -1;
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();

		Process process;
		try {
			process = processBuilder.start();

			InputStream stdOutStream = process.getInputStream();
			InputStreamReader stdOutStreamReader = new InputStreamReader(stdOutStream);
			BufferedReader stdOutBufferedReader = new BufferedReader(stdOutStreamReader);
			String stdOutLine;
			while ((stdOutLine = stdOutBufferedReader.readLine()) != null) {
				stdOutStringBuilder.append(stdOutLine);
			}

			InputStream stdErrStream = process.getErrorStream();
			InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
			BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
			String stdErrLine;
			while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
				stdOutStringBuilder.append(stdErrLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String stdout = stdOutStringBuilder.toString();
		// Expect something like:'Submitted batch job 807098'

		if (stdout.startsWith("Submitted batch job ")) {
			String jobId = stdout.substring(20);
			try {
				resultingJobId = Integer.parseInt(jobId);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		String stderr = stdErrStringBuilder.toString();
		// Not sure what might come out of stderr, but don't expect anything
		if ((stderr != "") && (!stderr.isEmpty()) && (stderr != null)) {
			logger.error("stderr from submitting sbatch: {}", stderr);
		}
		return resultingJobId;
	}
	
	public String getSudoCommandFullPath() {
		return sudoCommandFullPath;
	}

	public void setSudoCommandFullPath(String sudoCommandFullPath) {
		this.sudoCommandFullPath = sudoCommandFullPath;
	}

	public String getSbatchCommandFullPath() {
		return sbatchCommandFullPath;
	}

	public void setSbatchCommandFullPath(String sbatchCommandFullPath) {
		this.sbatchCommandFullPath = sbatchCommandFullPath;
	}

	public String getRemoteIP() {
		return remoteIP;
	}

	public void setRemoteIP(String remoteIP) {
		this.remoteIP = remoteIP;
	}

	public String getCommonUserLocal() {
		return commonUserLocal;
	}

	public void setCommonUserLocal(String commonUserLocal) {
		this.commonUserLocal = commonUserLocal;
	}

	public String getCommonUserRemote() {
		return commonUserRemote;
	}

	public void setCommonUserRemote(String commonUserRemote) {
		this.commonUserRemote = commonUserRemote;
	}

	public String getCommonDirectory() {
		return commonDirectory;
	}

	public void setCommonDirectory(String commonDirectory) {
		this.commonDirectory = commonDirectory;
	}

	public String getCommonRootRemote() {
		return commonRootRemote;
	}

	public void setCommonRootRemote(String commonRootRemote) {
		this.commonRootRemote = commonRootRemote;
	}

	public String getCommonRootLocal() {
		return commonRootLocal;
	}

	public void setCommonRootLocal(String commonRootLocal) {
		this.commonRootLocal = commonRootLocal;
	}
}
