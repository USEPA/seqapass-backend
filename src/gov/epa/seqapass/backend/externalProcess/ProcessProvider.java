package gov.epa.seqapass.backend.externalProcess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.epa.seqapass.backend.hpcAccess.Polling;

public class ProcessProvider {
	
	private static Logger logger = LogManager.getLogger(ProcessProvider.class);
	
	private String id;
	private int userID = -1;
	private int jobID = -1;
	private ProcessTypeProvider processType;
	private String execPath;
	private List<String> argVals;

	public ProcessProvider() {
	};

	private List<String> buildCommand() {
		List<String> command = new ArrayList<String>();
		if (processType == null || argVals == null || userID < 0 || jobID < 0 || processType.getArgNames().size() < argVals.size()) {
			return null;
		}
		command.add(execPath + "/" + processType.getCommand());
		for (int i = 0; i < processType.getArgNames().size(); i++) {
			command.add(processType.getArgNames().get(i));
			if (!processType.getArgNames().get(i).equals("-target_only")) {
				command.add(argVals.get(i));
			}
		}
		// System.out.println("command = " + command);
		logger.info("command = {}", command);
		return command;
	}

	public String[] run() throws InterruptedException, IOException {
		List<String> command = buildCommand();
		if (command == null) {
			return new String[] { "", "command is null" };
		}
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();

		final Process process = processBuilder.start();

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
		String[] result = new String[2];
		result[0] = stdOutStringBuilder.toString();
		result[1] = stdErrStringBuilder.toString();

		// System.out.println("Program terminated!");
		logger.error("Program terminated!");

		return result;
	}

	public String[] timeRun() throws InterruptedException, IOException {
		List<String> command = buildCommand();
		if (command == null) {
			return new String[] { "", "command is null" };
		}
		command.add(0, "time");
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		final StringBuilder stdOutStringBuilder = new StringBuilder();
		final StringBuilder stdErrStringBuilder = new StringBuilder();

		// System.out.println("Executable starting with timeRun() with command " + command);
		logger.debug("Executable starting with timeRun() with command {}", command);
		final Process process = processBuilder.start();

		String[] result = new String[2];

		InputStream stdOutStream = process.getInputStream();
		InputStreamReader stdOutStreamReader = new InputStreamReader(stdOutStream);
		BufferedReader stdOutBufferedReader = new BufferedReader(stdOutStreamReader);

		String stdOutLine;
		int lineCount = 0;
		while ((stdOutLine = stdOutBufferedReader.readLine()) != null) {
			stdOutStringBuilder.append(stdOutLine);
			lineCount++;
		}
		result[0] = stdOutStringBuilder.toString();
		// System.out.println("Executable finished with timeRun() with command " + command);
		// System.out.println("Stdout line count is: " + lineCount);
		logger.debug("Executable finished with timeRun() with command {}", command);
		logger.info("Stdout line count is: {}", lineCount);
		InputStream stdErrStream = process.getErrorStream();
		InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
		BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
		String stdErrLine;

		lineCount = 0;
		while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
			stdErrStringBuilder.append(stdErrLine);
			lineCount++;
		}
		// System.out.println("Stderr line count is: " + lineCount);
		logger.info("Stderr line count is: {}", lineCount);

		result[1] = stdErrStringBuilder.toString();

		// System.out.println("Program terminated with command: " + command);
		logger.warn("Program terminated with command: {}", command);

		return result;
	}

	public String timeRunToFile() throws InterruptedException, IOException {
		List<String> command = buildCommand();
		if (command == null) {
			return "command is null";
		}
		command.add(0, "/usr/bin/time");
		ProcessBuilder processBuilder = new ProcessBuilder(command);

		// System.out.println("Executable starting with timeRunToFile() with command " + command);
		logger.debug("Executable starting with timeRunToFile() with command {}", command);
		final Process process = processBuilder.start();
		// TODO - check to see if it is necessary to read stdOut so that GC can process the ProcessBuilder
		InputStream stdErrStream = process.getErrorStream();
		InputStreamReader stdErrStreamReader = new InputStreamReader(stdErrStream);
		BufferedReader stdErrBufferedReader = new BufferedReader(stdErrStreamReader);
		final StringBuilder stdErrStringBuilder = new StringBuilder();
		String stdErrLine = null;

		while ((stdErrLine = stdErrBufferedReader.readLine()) != null) {
			stdErrStringBuilder.append(stdErrLine);
		}

		// System.out.println("Executable terminated with timeRunToFile() with command: " + command);
		logger.debug("Executable terminated with timeRunToFile() with command: {}", command);
		return stdErrStringBuilder.toString();
	}

	public String runStdOutOnly() throws InterruptedException, IOException {
		List<String> command = new ArrayList<String>();
		if (processType == null || argVals == null || userID < 0 || jobID < 0 || processType.getArgNames().size() != argVals.size()) {
			return null;
		}
		command.add(execPath + "/" + processType.getCommand());
		for (int i = 0; i < processType.getArgNames().size(); i++) {
			command.add(processType.getArgNames().get(i));
			command.add(argVals.get(i));
		}

		ProcessBuilder builder = new ProcessBuilder(command);
		// Map<String, String> environ = builder.environment();
		final StringBuilder b = new StringBuilder();
		final Process process = builder.start();
		InputStream is = process.getInputStream();
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;
		while ((line = br.readLine()) != null) {
			b.append(line);
			// System.out.println(line);
		}
		// System.out.println("Program terminated!");
		logger.warn("Program terminated!");

		return b.toString();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getUserID() {
		return userID;
	}

	public void setUserID(int userID) {
		this.userID = userID;
	}

	public int getJobID() {
		return jobID;
	}

	public void setJobID(int jobID) {
		this.jobID = jobID;
	}

	public ProcessTypeProvider getProcessType() {
		return processType;
	}

	public void setProcessType(ProcessTypeProvider processType) {
		this.processType = processType;
	}

	public String getExecPath() {
		return execPath;
	}

	public void setExecPath(String execPath) {
		this.execPath = execPath;
	}

	public List<String> getArgVals() {
		return argVals;
	}

	public void setArgVals(List<String> argVals) {
		this.argVals = argVals;
	}

}
