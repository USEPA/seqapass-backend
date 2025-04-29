package gov.epa.seqapass.backend.serviceThread;

import gov.epa.seqapass.backend.domain.NCBIKeeper;

//import gov.epa.seqapass.common.ReportInfo;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class AccessionRun {
	
	private static Logger logger = LogManager.getLogger(AccessionRun.class);
	
	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private String submittedQueryAccessionString;
	private String queryAccessionString;
	private List<String> identicalsFromQuerySpecies;
	private int userId = -1;
	private int queryTaxid = -1;
	private String canonicalAccessionString;
	private String status = null;
	private double maxBitScore = -1;
	private int accessionRunId = -1;
	private BLASTTools blastTools;
	private Set<String> queryTaxidAccessionsAboveIdentity = null;

	public AccessionRun(BLASTTools blastTools, JdbcTemplate jdbcTemplate, NCBIKeeper ncbiKeeper,
			String queryAccessionString, int userId) {
		this.userId = userId;
		this.jdbcTemplate = jdbcTemplate;
		this.ncbiKeeper = ncbiKeeper;
		this.submittedQueryAccessionString = queryAccessionString;
		this.queryAccessionString = blastTools.resolveAccessionVersion(queryAccessionString);
		this.blastTools = blastTools;
	}

	public boolean blastDBConfirm() {
		if (this.queryAccessionString != null) {
			List<String> list = blastTools.findCanonicalAccessionPlusIdenticalsFromTaxidString(queryAccessionString);
			if(list == null) {
				return false;
			}
			setCanonicalAccessionString(list.get(0));
			if (list.size() > 1) {
				setIdenticalsFromQuerySpecies(list);
				//System.out.println("The list of identicals with same taxid as query has " + list.size() + " items");
				logger.info("The list of identicals with same taxid as query has {} items", list.size());
			} else {
				setIdenticalsFromQuerySpecies(new ArrayList<String>());
			}
			setQueryTaxid(blastTools.getTaxidFromAccessionIdName(queryAccessionString));
		}
		return true;
	}
	
	public int getAccessionRunId() {
		if (accessionRunId < 0) {
			if (queryAccessionString == null || canonicalAccessionString == null) {
				return -1;
			}
			createAndSetAccessionRunId();
		}
		return accessionRunId;
	}

	private void createAndSetAccessionRunId() {
		String query = "SELECT id FROM accession_run WHERE top_hit_accession_id = ? AND ncbi_version_id = ? LIMIT 1";
		//System.out.println("Looking for run with top_hit_accession_id (query accession): " + queryAccessionString
		//		+ " And ncbiVersion: " + ncbiKeeper.getPreferredNCBIProviderID());
		logger.info("Looking for run with top_hit_accession_id (query accession): {} And ncbiVersion: {}", 
				queryAccessionString, ncbiKeeper.getPreferredNCBIProviderID());
		try {
			Integer value = jdbcTemplate.queryForObject(query, Integer.class, queryAccessionString,
					ncbiKeeper.getPreferredNCBIProviderID());
			//System.out.println("Already present with accesssion_run id = " + value);
			logger.info("Already present with accesssion_run id = {}", value);

			if (value != null && value > -1) {
				accessionRunId = value;
				if (queryAccessionString.equals(submittedQueryAccessionString)) {
					setStatusNoDB("existing");
				} else {
					setStatusNoDB("existing: " + queryAccessionString);
				}
				return;
			}
		} catch (DataAccessException e) {
			//System.out.println("There is not one, yet, so we'll create one.");
			logger.warn("There is not one, yet, so we'll create one.");
			// No problem, just didn't exist already
		}

		String title = "";
		query = "SELECT SUBSTRING_INDEX(title,' [',1) as `protein_name` FROM protein WHERE accession_id = ? LIMIT 1";
		try {
			title = jdbcTemplate.queryForObject(query, String.class, queryAccessionString);
		} catch (DataAccessException e1) {
			//System.out.println("Apparently no title for protein: " + queryAccessionString);
			logger.error("Apparently no title for protein: {}", queryAccessionString);
			e1.printStackTrace();
		}

		try {

			accessionRunId = blastTools.insertAccessionRunReturnId(canonicalAccessionString, queryAccessionString,
					queryTaxid, title);

		} catch (SQLException e) {
			//System.out.println("... failed to create new accession_run id");
			logger.error("... failed to create new accession_run id");
			setStatusNoDB("failed");
			return;
		}
		// System.out.println("... now with accession_run id = " + accessionRunId);
		setStatusNoDB("new");
		return;
	}

	public String getSubmittedQueryAccessionString() {
		return submittedQueryAccessionString;
	}

	public void setSubmittedQueryAccessionString(String submittedQueryAccessionString) {
		this.submittedQueryAccessionString = submittedQueryAccessionString;
	}

	public String getQueryAccessionString() {
		return queryAccessionString;
	}

	public void setQueryAccessionString(String queryAccessionString) {
		this.queryAccessionString = queryAccessionString;
	}

	public List<String> getIdenticalsFromQuerySpecies() {
		return identicalsFromQuerySpecies;
	}

	public void setIdenticalsFromQuerySpecies(List<String> identicalsFromQuerySpecies) {
		this.identicalsFromQuerySpecies = identicalsFromQuerySpecies;
	}

	public int getQueryTaxid() {
		return queryTaxid;
	}

	public void setQueryTaxid(Integer queryTaxid) {
		if (queryTaxid != null) {
			this.queryTaxid = queryTaxid;
		}
	}

	public String getCanonicalAccessionString() {
		return canonicalAccessionString;
	}

	public void setCanonicalAccessionString(String canonicalAccessionString) {
		this.canonicalAccessionString = canonicalAccessionString;
	}

	public String getStatusNoDB() {
		return status;
	}

	public String getDBStatusSetLocalStatus() {
		if (status == null && accessionRunId > -1) {
			String query = "SELECT status FROM accession_run WHERE id = ? LIMIT 1";
			try {
				setStatusNoDB(jdbcTemplate.queryForObject(query, String.class, accessionRunId));
			} catch (DataAccessException e) {
				//System.out.println("Could not get status for accession run with id: " + accessionRunId);
				logger.error("Could not get status for accession run with id: {}", accessionRunId);
			}
		}
		return status;
	}

	// public boolean get4DBValuesSet4LocalValues() {
	// if (accessionRunId > -1) {
	// String query = "SELECT b.query_accession_id, a.accession_id, a.max_bit_score,
	// a.status "
	// + "FROM accession_run a, user_run_accession_run b WHERE a.id = ? AND
	// b.accession_run_id = a.id LIMIT 1";
	// try {
	// Map<String, Object> values = jdbcTemplate.queryForMap(query, accessionRunId);
	// setQueryAccessionString((String) values.get("query_accession_id"));
	// setCanonicalAccessionString((String) values.get("accession_id"));
	// Double maxBitScoreObject = (Double) values.get("maxBitScore");
	// if (maxBitScoreObject != null) {
	// maxBitScore = maxBitScoreObject;
	// }
	// setStatusNoDB((String) values.get("status"));
	//
	// return true;
	// } catch (DataAccessException e) {
	// System.out.println("Could not get 4 values for accession run with id: " +
	// accessionRunId);
	// }
	// }
	// return false;
	// }

	public boolean get3DBValuesSet3LocalValues(int userRunId) {
		if (accessionRunId > -1) {
			String query = "SELECT b.query_accession_id, a.canonical_accession_id, a.max_bit_score "
					+ "FROM accession_run a, user_run_accession_run b, user_run c WHERE a.id = ? AND c.id = ? AND b.accession_run_id = a.id AND c.id = b.user_run_id";
			try {
				Map<String, Object> values = jdbcTemplate.queryForMap(query, accessionRunId, userRunId);
				queryAccessionString = (String) values.get("query_accession_id");
				canonicalAccessionString = (String) values.get("canonical_accession_id");
				Double maxBitScoreObject = (Double) values.get("maxBitScore");
				if (maxBitScoreObject != null) {
					maxBitScore = maxBitScoreObject;
				}

				return true;
			} catch (DataAccessException e) {
				//System.out.println("Could not get 3 values for accession run with id: " + accessionRunId);
				logger.error("Could not get 3 values for accession run with id: {}", accessionRunId);
			}
		}
		return false;
	}

	public void setMaxBitScore() {
		if (maxBitScore == -1 && accessionRunId > -1 && canonicalAccessionString != null) {
			maxBitScore = blastTools.calculateMaxBitScore(canonicalAccessionString);
			blastTools.setMaxBitScore(accessionRunId, maxBitScore);
		}
	}

	public Set<String> getQueryTaxidAccessionsAboveIdentity() {
		return queryTaxidAccessionsAboveIdentity;
	}

	public void setQueryTaxidAccessionsAboveIdentity(Set<String> queryTaxidAccessionsAboveIdentity) {
		this.queryTaxidAccessionsAboveIdentity = queryTaxidAccessionsAboveIdentity;
	}

	public double getMaxBitScoreNoDB() {
		return maxBitScore;
	}

	public void setStatusNoDB(String status) {
		this.status = status;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}
}
