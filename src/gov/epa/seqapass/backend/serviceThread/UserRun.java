package gov.epa.seqapass.backend.serviceThread;

import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.common.ReportInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

public class UserRun {
	
	private static Logger logger = LogManager.getLogger(UserRun.class);
	
	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private int userRunId = -1;
	private int userId = -1;
	private List<AccessionRun> accessionRuns = new LinkedList<AccessionRun>();
	private BLASTTools blastTools;

	/**
	 * Create and insert a new user_run entry.
	 * 
	 * @param template
	 * @param keeper
	 * @param userId
	 */
	public UserRun(BLASTTools blastTools, JdbcTemplate template, NCBIKeeper keeper, int userId) {
		this.jdbcTemplate = template;
		this.ncbiKeeper = keeper;
		this.blastTools = blastTools;
		this.userId = userId;
	}

	private int getUserRunId() {
		if (userRunId == -1) {
			setNewUserRunId();
		}
		return userRunId;
	}

	private void setNewUserRunId() {
		if (userId < 0) {
			// System.out.println("No user id!");
			logger.error("No user id!");
			return;
		}

		try {
			userRunId = blastTools.insertUserRunReturnId(userId);
		} catch (SQLException e) {
			// System.out.println("Could not get new user_run id!");
			logger.error("Could not get new user_run id! - {}", e);
		}
	}

	public void pushQueryAccessionStringList(List<String> queryAccessionStringList) {
		accessionRuns.clear();
		for (String submittedQueryAccessionString : queryAccessionStringList) {
			// System.out.println("Working on accessionRun with queryId: " + submittedQueryAccessionString);
			logger.info("Working on accessionRun with queryId: {}", submittedQueryAccessionString);
			AccessionRun accessionRun = new AccessionRun(blastTools, jdbcTemplate, ncbiKeeper, submittedQueryAccessionString, userId);
		
			if (accessionRun.blastDBConfirm() && accessionRun.getCanonicalAccessionString() != null && accessionRun.getQueryAccessionString() != null) {
				String correctedQueryAccessionString = accessionRun.getQueryAccessionString();
				// System.out.println("Canonical name found: " + accessionRun.getCanonicalAccessionString());
				logger.info("Canonical name found: {}", accessionRun.getCanonicalAccessionString());

				int accessionRunId = accessionRun.getAccessionRunId(); // Now does much more

				// System.out.println("Accession_run id = " + accessionRunId + " for queryId: " + correctedQueryAccessionString);
				logger.info("Accession_run id = {} for queryId: {}", accessionRunId, correctedQueryAccessionString);
				if (accessionRunId > -1) {
					getUserRunId(); // Putting this after above if means a new user_run is only created if
									// necessary.
					// System.out.println("Newly set userRunId = " + userRunId);
					logger.info("Newly set userRunId = {}", userRunId);
					if (accessionRun.getStatusNoDB().equals("new")) {
						accessionRun.setMaxBitScore();
						if (accessionRun.getQueryAccessionString()
								.equals(accessionRun.getSubmittedQueryAccessionString())) {
							accessionRun.setStatusNoDB("submitted");
						} else {
							accessionRun.setStatusNoDB("submitted: " + accessionRun.getQueryAccessionString());
						}
					} else {
						accessionRun.get3DBValuesSet3LocalValues(userRunId);
						//System.out.println("For already submitted job: " + accessionRunId + " status is now: "
						//		+ accessionRun.getStatusNoDB());
						logger.info("For already submitted job: {} status is now: {}", 
								accessionRunId, accessionRun.getStatusNoDB());
					}
					blastTools.insertUserRunAccessionRunRow(userRunId, accessionRunId, correctedQueryAccessionString);
				}
			} else {
				accessionRun.setStatusNoDB("not in database");
			}
			accessionRuns.add(accessionRun);
		}
	}

	public List<AccessionRun> getAccessionRuns() {
		return accessionRuns;
	}
}
