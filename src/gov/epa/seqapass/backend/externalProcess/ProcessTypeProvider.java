package gov.epa.seqapass.backend.externalProcess;

import java.util.List;

public class ProcessTypeProvider {
	private String name;
	private String command;
//	private String path;
	private List<String> argNames;
	ProcessTypeProvider(){}
//	ProcessTypeProvider(String name, String command, String path){
	ProcessTypeProvider(String name, String command){
		this.name = name;
		this.command = command;
//		this.path = path;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCommand() {
		return command;
	}
	public void setCommand(String command) {
		this.command = command;
	}
//	public String getPath() {
//		return path;
//	}
//	public void setPath(String path) {
//		this.path = path;
//	}
	public List<String> getArgNames() {
		return argNames;
	}
	public void setArgNames(List<String> argNames) {
		this.argNames = argNames;
	}
}
