package gov.epa.seqapass.backend.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.epa.seqapass.backend.externalProcess.ProcessProvider;

public class BackEndProvider {
	
	private static Logger logger = LogManager.getLogger(BackEndProvider.class);
	
	private String id;
	private String protocol;
	private String domainName;
	private int port;
	private String path;
	// private DatabaseProvider databaseProvider;
	private String notes;

	public BackEndProvider() {
		InetAddress ip = null;
		try {
			//FIXME - THIS NEEDS TO BE SIMPLER, GETTING MOST INFO FROM DB
			ip = InetAddress.getLocalHost();
			// System.out.println("ip is "+ip);
			logger.info("ip is {}", ip);
			
			String canHostName = ip.getCanonicalHostName();
			// System.out.println("canHostName is "+canHostName);
			logger.info("canHostName is {}", canHostName);

			if (canHostName.equals("crystal.hesc.epa.gov")){
				// System.out.println("Setting crystal");
				logger.info("Setting crystal");
				this.setId("crystal");
				this.setProtocol("https");
				this.setDomainName("crystal.hesc.epa.gov");
				this.setPort(443);
			} else if (canHostName.equals("seqapass.hesc.epa.gov")){
				// System.out.println("Setting 2018-08 NCC hardware");
				logger.info("Setting 2018-08 NCC hardware");
				this.setId("seqapass_hesc");
				this.setProtocol("https");
				this.setDomainName("seqapass.hesc.epa.gov");
				this.setPort(8443);
				System.out.println("Hey, Brad -- we got the right host: "+ canHostName);
			} else if (canHostName.equals("v2626umcth029.rtord.epa.gov")){
				// System.out.println("Setting nickel");
				logger.info("Setting nickel");
				this.setId("nickel");
				this.setProtocol("https");
				this.setDomainName("seqapass.ni.epa.gov");
				this.setPort(443);
			} else if (canHostName.equals("v2626umcth028.rtord.epa.gov")){
				// System.out.println("Setting aluminum");
				logger.info("Setting aluminum");
				this.setId("aluminum");
				this.setProtocol("https");
				this.setDomainName("seqapass.al.epa.gov");
				this.setPort(443);	
			}else if (canHostName.equals("v2626umcth021.rtord.epa.gov")){
				// System.out.println("Setting silver");
				logger.info("Setting silver");
				this.setId("silver");
				this.setProtocol("http");
				this.setDomainName("ag.epa.gov");
				this.setPort(8526);
			}else if (canHostName.equals("LZ66CSIMMO02.aa.ad.epa.gov")) {
				// System.out.println("Setting codyDev");
				logger.info("Setting codyDev");
				this.setId("codyDev");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8080);
			}else if (canHostName.equals("LZ66AWILKE06.aa.ad.epa.gov")) {
				// System.out.println("Setting audreyDev");
				logger.info("Setting audreyDev");
				this.setId("audreyDev");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8080);
			}else if (canHostName.equals("LZ18H1NTTRANSUE.aa.ad.epa.gov")) {
				// System.out.println("Setting TomDev");
				logger.info("Setting TomDev");
				this.setId("tomDev");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8080);
			}else if (canHostName.equals("dogwood.rtpnc.epa.gov")) {
				// System.out.println("Setting Tom's mac");
				logger.info("Setting Tom's mac");
				this.setId("tomDev");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8080);
			}else if (canHostName.equals("iris2.rtpnc.epa.gov")) {
				// System.out.println("Setting Iris2");
				logger.info("Setting Iris2");
				this.setId("iris2");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8083);
			}else if (canHostName.equals("fred.rtpnc.epa.gov")) {
				// System.out.println("Setting Cody's mac");
				logger.info("Setting Cody's mac");
				this.setId("codyMac");
				this.setProtocol("http");
				this.setDomainName("localhost");
				this.setPort(8083);
			}else {
				this.setId("Not Found.  Using default.");
				this.setId("default");
				this.setProtocol("http");
				this.setDomainName(canHostName);
				this.setPort(8080);
			}
			this.setPath("SeqAPASS-BE");

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			// System.out.println("Failed to get server info");
			logger.error("Failed to get server info");
			e.printStackTrace();
		}
		
	};

	public String getURL() {
		return protocol + "://" + domainName + ":" + port + "/" + path + "/";
	}

	public Map<String, String> getNameValuePairs() {
		Map<String, String> result = new HashMap<String, String>();
		result.put("id", id + "");
		result.put("protocol", protocol);
		result.put("domainName", domainName);
		result.put("port", port + "");
		result.put("path", path);
		result.put("notes", notes);
		return result;
	}
	
//	public BackEndProvider getPreferredBackEndProvider() {
//		InetAddress ip = null;
//		try {
//
//			ip = InetAddress.getLocalHost();
//			
//			BackEndProvider backEnd = new BackEndProvider();
//			String canHostName = ip.getCanonicalHostName();
//			if (canHostName.equals("v2626umcth029.rtord.epa.gov")){
//				System.out.println("Setting nickel");
//				backEnd.setId("nickel");
//				backEnd.setProtocol("https");
//				backEnd.setDomainName("seqapass.ni.epa.gov");
//				backEnd.setPort(443);
//			} else if (canHostName.equals("v2626umcth028.rtord.epa.gov")){
//				System.out.println("Setting aluminum");
//				backEnd.setId("aluminum");
//				backEnd.setProtocol("https");
//				backEnd.setDomainName("seqapass.al.epa.gov");
//				backEnd.setPort(443);	
//			}else if (canHostName.equals("v2626umcth021.rtord.epa.gov")){
//				System.out.println("Setting silver");
//				backEnd.setId("silver");
//				backEnd.setProtocol("http");
//				backEnd.setDomainName("ag.epa.gov");
//				backEnd.setPort(8515);	
//			}else if (canHostName.equals("D18H1Ncsimmo021.aa.ad.epa.gov")) {
//				System.out.println("Setting codyDev");
//				backEnd.setId("codyDev");
//				backEnd.setProtocol("http");
//				backEnd.setDomainName("localhost");
//				backEnd.setPort(8526);
//			} else {
//				backEnd.setId("Not Found");
//			}
//			backEnd.setPath("SeqAPASS-BE");
//			
//			return backEnd;
//
//		} catch (UnknownHostException e) {
//			// TODO Auto-generated catch block
//			System.out.println("Failed to get server info");
//			e.printStackTrace();
//			return null;
//		}
//
//	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getDomainName() {
		if (domainName == null) {
			return "failed";
		}
		return domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	// public DatabaseProvider getDatabaseProvider() {
	// return databaseProvider;
	// }
	//
	// public void setDatabaseProvider(DatabaseProvider databaseProvider) {
	// this.databaseProvider = databaseProvider;
	// }

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}
}
