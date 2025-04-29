package gov.epa.seqapass.backend.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.epa.seqapass.common.ReportInfo;

public class NCBIKeeper {
	
	private static Logger logger = LogManager.getLogger(NCBIKeeper.class);
	
	private int preferredNCBIProviderID = -1;
	private Map<Integer, NCBIProvider> ncbiProviders = new HashMap<Integer, NCBIProvider>();

	private JdbcTemplate jdbcTemplate;
	private BackEndProvider backEnd;

	public NCBIKeeper(JdbcTemplate jdbcTemplate, BackEndProvider backEnd) {
		this.jdbcTemplate = jdbcTemplate;
		this.backEnd = backEnd;
		updateNCBIProviderMap();
	}

	public int getPreferredNCBIProviderID() {
		return preferredNCBIProviderID;
	}

	public NCBIProvider getPreferredNCBIProvider() {
		return ncbiProviders.get(preferredNCBIProviderID);
	}

	public Map<Integer, NCBIProvider> getNcbiProviders() {
		return ncbiProviders;
	}

	public void updateNCBIProviderMap() {
		// System.out.println("Updating NCBI provider map");
		// System.out.println("... with backEnd: "+backEnd);
		// System.out.println("... with backEnd.getDomainName: "+backEnd.getDomainName());
		// System.out.println("... with backEnd.getId: "+backEnd.getId());
		// System.out.println("... with backEnd.getPath: "+backEnd.getPath());
		// System.out.println("... with backEnd.getPort: "+backEnd.getPort());
		// System.out.println("... with backEnd.getURL: "+backEnd.getURL());
		
		logger.info("Updating NCBI provider map");
		logger.info("... with backEnd: {}", backEnd);
		logger.info("... with backEnd.getDomainName: {}", backEnd.getDomainName());
		logger.info("... with backEnd.getId: {}", backEnd.getId());
		logger.info("... with backEnd.getPath: {}", backEnd.getPath());
		logger.info("... with backEnd.getPort: {}", backEnd.getPort());
		logger.info("... with backEnd.getURL: {}", backEnd.getURL());


		String localHostName = backEnd.getDomainName();
		// System.out.println("localHostName: "+localHostName);
		logger.info("localHostName: {}", localHostName);
		
		List<NCBIProvider> listNCBIProvider = new ArrayList<NCBIProvider>();
		try {
			listNCBIProvider = jdbcTemplate.query("SELECT * FROM version WHERE backend_host = ? ORDER BY id DESC",
					new NCBIMapper(), localHostName);
		} catch (DataAccessException e) {
			// System.out.println("Query failed: ");
			logger.error("Query failed: {}", e);
			e.printStackTrace();
		}
		ncbiProviders = new HashMap<Integer, NCBIProvider>();
		if (listNCBIProvider.size() == 0) {
			// System.out.println("COULD NOT RETRIEVE version ROWS FROM DATABASE for backend_host: "+localHostName);
			logger.error("COULD NOT RETRIEVE version ROWS FROM DATABASE for backend_host: {}",
					localHostName);
		}
		for (NCBIProvider ncbiProvider : listNCBIProvider) {
			int id = ncbiProvider.getId();
			ncbiProviders.put(id, ncbiProvider);
		}
		preferredNCBIProviderID = listNCBIProvider.get(0).getId();
		logger.info("preferredNCBIProviderID: {}", preferredNCBIProviderID);
	}
}

class NCBIMapper implements RowMapper<NCBIProvider> {
	
	private static Logger logger = LogManager.getLogger(NCBIMapper.class);
	
	public NCBIProvider mapRow(ResultSet rs, int rowNumber) throws SQLException {
		NCBIProvider ncbiProvider = new NCBIProvider();

		ncbiProvider.setId(rs.getInt("id"));
		ncbiProvider.setUpdateVersion(rs.getInt("update_version"));
		ncbiProvider.setSeqVersion(rs.getString("seqapass_version"));
		ncbiProvider.setInstallDate(rs.getDate("install_date"));
		ncbiProvider.setHost(rs.getString("backend_host"));
		ncbiProvider.setCobaltExecDate(rs.getDate("cobalt_exec_date"));
		ncbiProvider.setCobaltExecVersion(rs.getString("cobalt_exec_version"));
		ncbiProvider.setCobaltDataDate(rs.getDate("cobalt_data_date"));
		ncbiProvider.setCobaltDataVersion(rs.getString("cobalt_data_version"));
		ncbiProvider.setCddDataFtpDate(rs.getDate("cdd_data_ftp_date"));
		ncbiProvider.setCddDataDate(rs.getDate("cdd_data_date"));
		ncbiProvider.setBlastExecFtpDate(rs.getDate("blast_exec_ftp_date"));
		ncbiProvider.setBlastExecDate(rs.getDate("blast_exec_date"));
		ncbiProvider.setBlastExecVersion(rs.getString("blast_exec_version"));
		ncbiProvider.setTaxonomyProteinDate(rs.getDate("taxonomy_protein_date"));
		ncbiProvider.setProteinRecordCount(rs.getInt("protein_record_count"));
		ncbiProvider.setTaxidRecordCount(rs.getInt("taxid_record_count"));
		ncbiProvider.setJavaVersion(rs.getString("java_version"));
		ncbiProvider.setPrimefacesVersion(rs.getString("primefaces_version"));
		ncbiProvider.setTomcatVersion(rs.getString("tomcat_version"));
		ncbiProvider.setDbServerVersion(rs.getString("db_server_version"));
		ncbiProvider.setrVersion(rs.getString("r_version"));
		ncbiProvider.setNotes(rs.getString("notes"));
		
		ncbiProvider.setWebdataPath(System.getProperty("catalina.base")+"/webdata");
		ncbiProvider.setWebresultsPath(System.getProperty("catalina.base")+"/webresults");

		ncbiProvider.setCobaltDataPath(ncbiProvider.getWebdataPath()+"/cdd_clique_db/cdd_clique_"+ncbiProvider.getCobaltDataVersion());
		ncbiProvider.setCobaltExecPath(ncbiProvider.getWebdataPath()+"/ncbi-cobalt-" + ncbiProvider.getCobaltExecVersion()+"/bin");
		ncbiProvider.setBlastExecPath(ncbiProvider.getWebdataPath()+"/ncbi-blast-" + ncbiProvider.getBlastExecVersion()+"+/bin");
		// System.out.println("webdata path: " + ncbiProvider.getWebdataPath());
		// System.out.println("webresults path: " + ncbiProvider.getWebresultsPath());
		// System.out.println("cobaltdata path: " + ncbiProvider.getCobaltDataPath());
		// System.out.println("cobaltexec path: " + ncbiProvider.getCobaltExecPath());
		// System.out.println("blastexec path: " + ncbiProvider.getBlastExecPath());
		
		logger.info("webdata path: {}", ncbiProvider.getWebdataPath());
		logger.info("webresults path: {}", ncbiProvider.getWebresultsPath());
		logger.info("cobaltdata path: {}", ncbiProvider.getCobaltDataPath());
		logger.info("cobaltexec path: {}", ncbiProvider.getCobaltExecPath());
		logger.info("blastexec path: {}", ncbiProvider.getBlastExecPath());
		
		
		return ncbiProvider;
	}
}