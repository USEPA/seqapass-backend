package gov.epa.seqapass.backend.serviceThread;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SerializationUtils;

import com.google.common.base.Joiner;
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;

//import gov.epa.seqapass.backend.dao.RService;
import gov.epa.seqapass.backend.dao.ReportService;
import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.DensityRow;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.ReportInfo;
import gov.epa.seqapass.common.ReportTypeEnum;

@Service
@EnableRetry
public class BLASTTools2 {

	private static Logger logger = LogManager.getLogger(BLASTTools2.class);

	public BLASTTools2(JdbcTemplate jdbcTemplate, ReportService reportService) {
		this.jdbcTemplate = jdbcTemplate;
		this.reportService = reportService;
	}

	private JdbcTemplate jdbcTemplate;

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public ReportService getReportService() {
		return reportService;
	}

	private ReportService reportService;

	private double defaultLevelOneEvalue = 0.01;
	private double defaultLevelTwoEvalue = 10;
	private int defaultCommonDomains = 1;

	// private BLASTTools2Injected instance = null;

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateAccessionRunStatus(final int accessionRunId, final String status) {
		// System.out.println("Attempting method: updateAccessionRunStatus
		// accessionRunId = " + accessionRunId
		// + " and status = {}" + status
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug(
				"Attempting method: updateAccessionRunStatus accessionRunId = {} and status = {} and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId, status);
		String updateQuery = "UPDATE accession_run SET status = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, status, accessionRunId);
		// System.out.println("Completed method: updateAccessionRunStatus accessionRunId
		// = " + accessionRunId
		// + " and status = " + status
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug(
				"Completed method: updateAccessionRunStatus accessionRunId = {} and status = {} and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId, status);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setAccessionRunCompletion(final int accessionRunId) {
		// System.out.println("Attempting method: setAccessionRunCompletion
		// accessionRunId = " + accessionRunId
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug(
				"Atempting method: setAccessionRunCompletion accessionRunId = {} and retryable value(s) = "
						+ "DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId);
		String updateQuery = "UPDATE accession_run SET completion_date = NOW() WHERE id = ?";
		jdbcTemplate.update(updateQuery, accessionRunId);
		// System.out.println("Completed method: setAccessionRunCompletion
		// accessionRunId = " + accessionRunId
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug(
				"Completed method: setAccessionRunCompletion accessionRunId = {} and retryable value(s) = "
						+ "DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4DataStatusAndDate(final int level4RunId, String accession, String stage, String status) {
		logger.debug(
				"Attempting method: setLevel4DataCompletion level4RunId = {} and accession = {} and status {} and retryable value(s) = "
						+ "DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
						level4RunId, accession, status);
		String updateQuery = null;
		if (stage.toLowerCase().equals("itasser")) {
			if (status.toLowerCase().equals("running")) {
				updateQuery = "UPDATE level4_data SET itasser_start = NOW(), status = 'I-TASSER running' WHERE level4_run_id = ? AND accession = ?";
			} else if (status.toLowerCase().equals("completed")) {
				updateQuery = "UPDATE level4_data SET itasser_end = NOW(), status = 'I-TASSER complete' WHERE level4_run_id = ? AND accession = ?";
			} else if (status.toLowerCase().equals("failed")) {
				updateQuery = "UPDATE level4_data SET itasser_end = NOW(), status = 'I-TASSER failed' WHERE level4_run_id = ? AND accession = ?";
			} else {
				logger.error("Failed method with unknown status: setLevel4DataStartEnd itasser level4RunId = {}, accession = {}, stage = {}, status = {} and retryable value(s) = " + "DataAccessException.class, BatchUpdateException.class, RuntimeException.class", level4RunId, accession, stage, status);
			}
		} else if(stage.toLowerCase().equals("tmalign")) {
			if (status.toLowerCase().equals("running")) {
				updateQuery = "UPDATE level4_data SET tmalign_start = NOW(), status = 'TM-Align running' WHERE level4_run_id = ? AND accession = ?";
			} else if (status.toLowerCase().equals("complete")) {
					updateQuery = "UPDATE level4_data SET tmalign_end = NOW(), status = 'TM-Align complete' WHERE level4_run_id = ? AND accession = ?";
			} else {
				logger.error("Failed method: setLevel4DataStartEnd tmalign level4RunId = {}, accession = {}, stage = {}, status = {} and retryable value(s) = "
						+ "DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
						level4RunId, accession, stage, status);
			}
		}
		
		if (updateQuery != null) {
			jdbcTemplate.update(updateQuery, level4RunId, accession);
		} else {
			logger.error("Failed method: setLevel4DataStartEnd itasser level4RunId = {}, accession = {}, stage = {}, status = {}. updateQuery is null",level4RunId, accession, stage, status);
		}
		
		logger.debug("Completed method: setLevel4DataCompletion level4RunId = {} and accession = {}, stage = {}, status = {} and retryable value(s) = " + "DataAccessException.class, BatchUpdateException.class, RuntimeException.class", level4RunId, accession, stage, status);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4RunDate(final int level4RunId, String status) {

		logger.debug("Attempting method: setLevel4RunComplete level4RunId = {}", level4RunId);
		
		String updateQuery = null;
		if (status.toLowerCase().equals("running")) {
			//start
			updateQuery = "UPDATE level4_run SET start = NOW() WHERE id = ?";
		} else if (status.toLowerCase().equals("complete")) {
			//end
			updateQuery = "UPDATE level4_run SET end = NOW() WHERE id = ?";
		} else {
			logger.error("Failed method: updateLevel4RunDate level4RunId = {}, status = {} and retryable value(s) = "
					+ "DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
					level4RunId, status);
		}
		
		jdbcTemplate.update(updateQuery, level4RunId);

		logger.debug("Completed method: setLevel4RunComplete level4RunId = {}", level4RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateTMAlignRunDate(final int tmalignRunId) {

		logger.debug("Attempting method: updateTMAlignRunDate level4RunId = {}", tmalignRunId);
		
		String updateQuery = "UPDATE level4_tmalign_run SET end = NOW() WHERE id = ?";

		jdbcTemplate.update(updateQuery, tmalignRunId);

		logger.debug("Completed method: updateTMAlignRunDate level4RunId = {}", tmalignRunId);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setAccessionRunBlastpWordSize(final int wordSize, final int accessionRunId) {
		// System.out.println("Attempting method: setAccessionRunBlastpWordSize
		// accessionRunId = " + accessionRunId
		// + " and wordSize = " + wordSize
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Atempting method: setAccessionRunBlastpWordSize accessionRunId = {} and wordSize = {} "
				+ "and retryable value(s) = DataAccessException.class, BatchUpdateException.class, "
				+ "RuntimeException.class", accessionRunId, wordSize);
		String updateQuery = "UPDATE accession_run SET blastp_word_size = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, wordSize, accessionRunId);
		// System.out.println("Completed method: setAccessionRunBlastpWordSize
		// accessionRunId = " + accessionRunId
		// + " and wordSize = " + wordSize
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Completed method: setAccessionRunBlastpWordSize accessionRunId = {} and wordSize = {} "
				+ "and retryable value(s) = DataAccessException.class, BatchUpdateException.class, "
				+ "RuntimeException.class", accessionRunId, wordSize);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setAccessionRunBlastpThreadCount(final int threadCount, final int accessionRunId) {
		// System.out.println("Attempting method: setAccessionRunBlastpThreadCount
		// accessionRunId = " + accessionRunId
		// + " and threadCount = " + threadCount
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Atempting method: setAccessionRunBlastpThreadCount accessionRunId = {} and threadCount = {} "
				+ "and retryable value(s) = DataAccessException.class, BatchUpdateException.class, "
				+ "RuntimeException.class", accessionRunId, threadCount);
		String updateQuery = "UPDATE accession_run SET blastp_thread_count = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, threadCount, accessionRunId);
		// System.out.println("Completed method: setAccessionRunBlastpThreadCount
		// accessionRunId = " + accessionRunId
		// + " and threadCount = " + threadCount
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Completed method: setAccessionRunBlastpThreadCount accessionRunId = {} and threadCount = {} "
				+ "and retryable value(s) = DataAccessException.class, BatchUpdateException.class, "
				+ "RuntimeException.class", accessionRunId, threadCount);
	}

	// @Transactional
	// @Retryable(maxAttempts = 50, value = {
	// MySQLTransactionRollbackException.class, DataAccessException.class,
	// BatchUpdateException.class,
	// RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier =
	// 2))
	// public void updateLevel2Status(final int level2RunId, final String
	// status) {
	// System.out.println("Attempting method: updateLevel2Status accessionRunId = "
	// + level2RunId);
	// String updateQuery = "UPDATE level2_run SET status = ? WHERE id = ?";
	// jdbcTemplate.update(updateQuery, status, level2RunId);
	// if (status.equals("complete") || status.equals("not enough hits")) {
	// updateQuery = "UPDATE level2_run SET end = CURRENT_TIMESTAMP + 1 WHERE id
	// =
	// ?";
	// jdbcTemplate.update(updateQuery, level2RunId);
	//
	// }
	// System.out.println("Completed method: updateLevel2Status accessionRunId = "
	// +
	// level2RunId);
	// }

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setLevel2RunComplete(final int level2RunId) {
		// System.out.println("Attempting method: setLevel2RunComplete level2RunId = " +
		// level2RunId);
		logger.debug("Attempting method: setLevel2RunComplete level2RunId = {}", level2RunId);

		String updateQuery = "UPDATE level2_run SET end = CURRENT_TIMESTAMP + 1 WHERE id = ?";
		jdbcTemplate.update(updateQuery, level2RunId);

		// System.out.println("Completed method: setLevel2RunComplete level2RunId = " +
		// level2RunId);
		logger.debug("Completed method: setLevel2RunComplete level2RunId = {}", level2RunId);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel2Status(final int level2RunId, final String status) {
		// System.out.println("Attempting method: updateLevel2Status level2RunId = " +
		// level2RunId);
		logger.debug("Attempting method: updateLevel2Status level2RunId = {}", level2RunId);
		String updateQuery = "UPDATE level2_run SET status = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, status, level2RunId);
		// System.out.println("Completed method: updateLevel2Status level2RunId = " +
		// level2RunId);
		logger.debug("Completed method: updateLevel2Status level2RunId = {}", level2RunId);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel2MaxBitScore(final int level2RunId, final Double maxBitScore) {
		// System.out.println("Attempting method: updateLevel2MaxBitScore with
		// accessionRunId = " + level2RunId);
		logger.debug("Attempting method: updateLevel2MaxBitScore with accessionRunId = {}", level2RunId);

		String updateQuery = "UPDATE level2_run SET max_bit_score = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, maxBitScore, level2RunId);

		// System.out.println("Completed method: updateLevel2MaxBitScore with
		// accessionRunId = " + level2RunId);
		logger.debug("Completed method: updateLevel2MaxBitScore with accessionRunId = {}", level2RunId);
	}
	//
	// @Transactional
	// @Retryable(maxAttempts = 50, value = {
	// MySQLTransactionRollbackException.class, DataAccessException.class,
	// BatchUpdateException.class, RuntimeException.class }, backoff =
	// @Backoff(delay = 500, multiplier = 2))
	// public String updateAccessionRunTopHit(int accessionRunId, String
	// accessionIdName) {
	// System.out.println("Attempting method: updateAccessionRunTopHit with
	// accessionRunId and accessionIdName "
	// + accessionRunId + " , " + accessionIdName);
	//
	// String query = "SELECT a.hit_accession_id, a.`xml_Hsp_bit-score`,
	// a.xml_Hsp_evalue, a.`xml_Hit_len`, a.`xml_Hsp_identity` "
	// + "FROM accession_hit a, accession_run b WHERE accession_run_id = ? AND
	// a.hit_taxid = b.query_taxid AND b.id = ? "
	// + "ORDER BY `xml_Hsp_identity` DESC, `xml_Hsp_bit-score` DESC, xml_Hsp_evalue
	// ASC, xml_Hit_len DESC, hit_accession_id ASC LIMIT 20";
	// List<Map<String, Object>> rows = jdbcTemplate.queryForList(query,
	// accessionRunId, accessionRunId);
	// if (rows.size() == 0) {
	// System.out.println("updateAccessionRunTopHit got no hits!");
	// return null;
	// }
	// String result = null;
	// double minEValue = 1000;
	// double maxBitScore = -1;
	// int maxHitLength = -1;
	// int maxIdentity = -1;
	//
	// for (Map<String, Object> row : rows) {
	// String accessionId = (String) row.get("hit_accession_id");
	// double bitScore = (double) row.get("xml_Hsp_bit-score");
	// double eValue = (double) row.get("xml_Hsp_evalue");
	// int hitLength = (int) row.get("xml_Hit_len");
	// int hitIdentity = (int) row.get("xml_Hsp_identity");
	// System.out.println("Got this: accessionId: " + accessionId + " bitScore: " +
	// bitScore + " eValue: " + eValue
	// + " hitLength: " + hitLength + " hitIdentity: " + hitIdentity);
	// // if (accessionId.matches("^[A-Z0-9]{4}_[A-Z0-9]$") &&
	// // !accessionIdName.matches("^[A-Z0-9]{4}_[A-Z0-9]$")) {
	// // continue;
	// // }
	// // The goal here is to NOT reutrn a pdb code if another code is
	// // present, even if
	// // it is a little lower
	// if (!accessionIdName.matches("^[A-Z0-9]{4}_[A-Z0-9]{1,2}$") && result != null
	// && result.matches("^[A-Z0-9]{4}_[A-Z0-9]{1,2}$")) {
	// continue;
	// }
	// if (maxIdentity == -1) {
	// result = accessionId; // We will return the top hit (this
	// // one)... unless (see below)
	// maxBitScore = bitScore;
	// minEValue = eValue;
	// maxHitLength = hitLength;
	// maxIdentity = hitIdentity;
	// } else {
	// if (hitIdentity < maxIdentity) {
	// break;
	// }
	// if (bitScore < maxBitScore) {
	// break;
	// }
	// if (eValue > minEValue) {
	// break;
	// }
	// if (hitLength < maxHitLength) {
	// break;
	// }
	// }
	// if (accessionId.equals(accessionIdName)) {
	// result = accessionId; // We only get here if the accessionId is
	// // tied with the top hit from above
	// break;
	// }
	// }
	// String updateQuery = "UPDATE accession_run SET top_hit_accession_id = ?,
	// max_bit_score = ? WHERE id = ?";
	// jdbcTemplate.update(updateQuery, result, maxBitScore, accessionRunId);
	// System.out.println("Completed method: updateAccessionRunTopHit with
	// accessionRunId and accessionIdName "
	// + accessionRunId + " , " + accessionIdName);
	// return result;
	// }

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	void batchAccessionHitUpdateCddCounts(List<Integer> ids, List<Integer> counts) {
		// System.out.println("Attempting method: batchAccessionHitUpdateCddCounts");
		logger.debug("Attempting method: batchAccessionHitUpdateCddCounts");

		jdbcTemplate.batchUpdate("UPDATE accession_hit SET cdd_count = ? WHERE id = ?",
				new BatchPreparedStatementSetter() {
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setInt(1, counts.get(i));
						ps.setInt(2, ids.get(i));
					}

					public int getBatchSize() {
						return ids.size();
					}
				});
		// System.out.println("Completed method: batchAccessionHitUpdateCddCounts");
		logger.debug("Completed method: batchAccessionHitUpdateCddCounts");
	}

	// @Transactional
	// @Retryable(maxAttempts = 50, value = {
	// MySQLTransactionRollbackException.class, DataAccessException.class,
	// BatchUpdateException.class,
	// RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier =
	// 2))
	// public void updateLevel3Status(final int level3RunId, final String
	// status) {
	// System.out.println("Attempting method: updateLevel3Status accessionRunId = "
	// + level3RunId);
	// String updateQuery = "UPDATE level3_run SET status = ? WHERE id = ?";
	// jdbcTemplate.update(updateQuery, status, level3RunId);
	// if (status.equals("complete")) {
	// updateQuery = "UPDATE level3_run SET end = CURRENT_TIMESTAMP + 1 WHERE id
	// =
	// ?";
	// jdbcTemplate.update(updateQuery, level3RunId);
	//
	// }
	// System.out.println("Completed method: updateLevel3Status accessionRunId = "
	// +
	// level3RunId);
	// }

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setLevel3RunComplete(final int level3RunId) {
		// System.out.println("Attempting method: setLevel3RunComplete level3RunId = " +
		// level3RunId);
		logger.debug("Attempting method: setLevel3RunComplete level3RunId = {}", level3RunId);

		String updateQuery = "UPDATE level3_run SET end = CURRENT_TIMESTAMP + 1 WHERE id = ?";
		jdbcTemplate.update(updateQuery, level3RunId);

		// System.out.println("Completed method: setLevel3RunComplete level3RunId = " +
		// level3RunId);
		logger.debug("Completed method: setLevel3RunComplete level3RunId = {}", level3RunId);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel3Status(final int level3RunId, final String status) {
		// System.out.println("Attempting method: updateLevel3Status accessionRunId = "
		// + level3RunId);
		logger.debug("Attempting method: updateLevel3Status accessionRunId = {}", level3RunId);

		String updateQuery = "UPDATE level3_run SET status = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, status, level3RunId);

		// System.out.println("Completed method: updateLevel3Status accessionRunId = " +
		// level3RunId);
		logger.debug("Completed method: updateLevel3Status accessionRunId = {}", level3RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4Status(final int level4RunId, final String status) {

		logger.debug("Attempting method: updateLevel4Status accessionRunId = {}", level4RunId);

		String updateQuery = "UPDATE level4_run SET status = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, status, level4RunId);

		logger.debug("Completed method: updateLevel4Status accessionRunId = {}", level4RunId);
	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public int updateAccessionHit_rbh_status_full4d(final int ncbiVersionId, final int accessionRunId) {
		// System.out.println("Attempting method: updateAccessionHit_rbh_status_full4d
		// accessionRunId = " + accessionRunId);
		logger.debug("Attempting method: updateAccessionHit_rbh_status_full4d accessionRunId = {}", accessionRunId);
		if (accessionRunId < 0) {
			// System.out.println(
			// "Completed WITH ERROR (bad accessionRunId) method:
			// setAccessionRunBlastpThreadCount accessionRunId = "
			// + accessionRunId);
			logger.error(
					"Completed WITH ERROR (bad accessionRunId) method: setAccessionRunBlastpThreadCount accessionRunId = {}",
					accessionRunId);
			return -1;
		}

		String updateQuery3 = "UPDATE accession_hit SET rbh_status = 'N' WHERE accession_run_id = ? AND rbh_status = 'finished'";
		int count3 = jdbcTemplate.update(updateQuery3, accessionRunId);
		// System.out.println("Last step: " + count3 + " more marked 'N'");
		logger.info("Last step: {} more marked 'N'", count3);

		// System.out.println("Completed method: updateAccessionHit_rbh_status_full4d
		// accessionRunId = " + accessionRunId);
		logger.debug("Completed method: updateAccessionHit_rbh_status_full4d accessionRunId = {}", accessionRunId);
		return count3;
	}

	public CutoffData generateLevelOnePrimaryCutoff(final int accessionRunId) {
		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromAccessionRunId(accessionRunId);
		// System.out.println("Starting generateLevelOnePrimaryCutoff");
		logger.info("Starting generateLevelOnePrimaryCutoff");
		List<DensityRow> densityData = reportService.getDensityData(1, ReportTypeEnum.Primary, accessionRunId,
				defaultLevelOneEvalue, defaultCommonDomains, eukaryotesOnly);
		// System.out.println("Density data has " + densityData.size() + " rows");
		logger.info("Density data has {} rows", densityData.size());
		return CutoffData.newInstance(densityData, 1);
	}

	public CutoffData generateLevelOneFullCutoff(final int accessionRunId) {
		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromAccessionRunId(accessionRunId);
		// System.out.println("Starting generateLevelOneFullCutoff");
		logger.info("Starting generateLevelOneFullCutoff");
		List<DensityRow> densityData = reportService.getDensityData(1, ReportTypeEnum.Full, accessionRunId, -1, -1,
				eukaryotesOnly);
		return CutoffData.newInstance(densityData, 1);
	}

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void insertLevelOneCutoff(int accessionRunId, CutoffData primaryCutoffData, CutoffData fullCutoffData) {
//		// if (primaryCutoffData != null || fullCutoffData != null) {
//		StringBuilder b4 = new StringBuilder();
//		b4.append("INSERT IGNORE INTO");
//		b4.append("    accession_run_density_plot (accession_run_id, density_plot_object, full_density_plot_object) ");
//		b4.append("    VALUES(?,?,?) ");
//
//		byte[] serializedPrimaryData = SerializationUtils.serialize(primaryCutoffData);
//		byte[] serializedFullData = SerializationUtils.serialize(fullCutoffData);
//
//		jdbcTemplate.update(b4.toString(), accessionRunId, serializedPrimaryData, serializedFullData);

	// if (fullCutoffData != null && primaryCutoffData != null){
	// jdbcTemplate.update(b4.toString(), accessionRunId,
	// serializedPrimaryData,
	// serializedFullData);
	// } else if (fullCutoffData == null && primaryCutoffData == null){
	// jdbcTemplate.update(b4.toString(), accessionRunId, null, null);
	// } else if (fullCutoffData == null){
	// jdbcTemplate.update(b4.toString(), accessionRunId,
	// serializedPrimaryData,
	// null);
	// } else {
	// jdbcTemplate.update(b4.toString(), accessionRunId, null,
	// serializedFullData);
	// }

	// byte[] serializedPrimaryData = null;
	// if (primaryCutoffData != null) {
	// serializedPrimaryData =
	// SerializationUtils.serialize(primaryCutoffData);
	// }
	// byte[] serializedFullData = null;
	// if (fullCutoffData != null) {
	// serializedFullData = SerializationUtils.serialize(fullCutoffData);
	// }
	// jdbcTemplate.update(b4.toString(), accessionRunId,
	// serializedPrimaryData,
	// serializedFullData);
	// }
//	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void copyResultsToDups(int accessionRunId) {
		SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate).withProcedureName("COPY_status_to_dups");
		Map<String, Object> inputParameterMap = new HashMap<String, Object>();
		inputParameterMap.put("in_accrunid", accessionRunId);
		SqlParameterSource sqlParameterSource = new MapSqlParameterSource(inputParameterMap);
		simpleJdbcCall.execute(sqlParameterSource);
	}

//	public CutoffData generateLevelTwoPrimaryCutoff(RService rService, final int level2RunId) {
//		System.out.println("Starting generateLevelTwoPrimaryCutoff");
//		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromLevelTwoRunId(level2RunId);
//		List<DensityRow> densityData = reportService.getDensityData(2, ReportTypeEnum.Primary, level2RunId,
//				defaultLevelTwoEvalue, -1, eukaryotesOnly);
//		return CutoffData.newInstance(densityData);
//	}
//
//	public CutoffData generateLevelTwoFullCutoff(RService rService, final int level2RunId) {
//		System.out.println("Starting generateLevelTwoFullCutoff");
//		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromLevelTwoRunId(level2RunId);
//		List<DensityRow> densityData = reportService.getDensityData(2, ReportTypeEnum.Full, level2RunId, -1, -1,
//				eukaryotesOnly);
//		return CutoffData.newInstance(densityData);
//	}

	public CutoffData generateLevelTwoPrimaryCutoff(final int level2RunId) {
		// System.out.println("Starting generateLevelTwoPrimaryCutoff");
		logger.info("Starting generateLevelTwoPrimaryCutoff");
		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromLevelTwoRunId(level2RunId);
		List<DensityRow> densityData = reportService.getDensityData(2, ReportTypeEnum.Primary, level2RunId,
				defaultLevelTwoEvalue, -1, eukaryotesOnly);
		return CutoffData.newInstance(densityData, 2);
	}

	public CutoffData generateLevelTwoFullCutoff(final int level2RunId) {
		// System.out.println("Starting generateLevelTwoFullCutoff");
		logger.info("Starting generateLevelTwoFullCutoff");
		boolean eukaryotesOnly = reportService.isQueryEukaryoteFromLevelTwoRunId(level2RunId);
		List<DensityRow> densityData = reportService.getDensityData(2, ReportTypeEnum.Full, level2RunId, -1, -1,
				eukaryotesOnly);
		return CutoffData.newInstance(densityData, 2);
	}

//	@Transactional
//	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
//			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
//	public void insertLevelTwoCutoff(int level2RunId, CutoffData primaryCutoffData, CutoffData fullCutoffData) {
//		System.out.println("Attempting to insert levelTwoCutoff");
//
//		StringBuilder b4 = new StringBuilder();
//		b4.append("INSERT IGNORE INTO");
//		b4.append("    domain_run_density_plot (domain_run_id, density_plot_object, full_density_plot_object) ");
//		b4.append("    VALUES(?,?,?) ");
//
//		byte[] serializedPrimaryData = SerializationUtils.serialize(primaryCutoffData);
//		byte[] serializedFullData = SerializationUtils.serialize(fullCutoffData);
//		jdbcTemplate.update(b4.toString(), level2RunId, serializedPrimaryData, serializedFullData);
//
//		System.out.println("Finished inserting levelTwoCutoff");
//	}

	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void setOrthologCount(final int accessionRunId, final int orthologCnt) {
		// System.out.println("Attempting method: setOrthologCount accessionRunId = " +
		// accessionRunId
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Attempting method: setOrthologCount accessionRunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId);
		String updateQuery = "UPDATE accession_run SET ortholog_count = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, orthologCnt, accessionRunId);
		// System.out.println("Completed method: setOrthologCount accessionRunId = " +
		// accessionRunId
		// + " and retryable value(s) = DataAccessException.class,
		// BatchUpdateException.class, RuntimeException.class");
		logger.debug("Completed method: setOrthologCount accessionRunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class",
				accessionRunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public int insertLevel4TMAlignRun(int tmAlignRunId, int queryAccLevel4RunId, LevelFourRequestableRow row ) {
		logger.debug("Attempting method: insertLevel4TMAlignRun with level4RunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class", row.getLevel4RunId());

		List<String> nameList = new ArrayList<String>();
		int count = 0;
		if (tmAlignRunId > 0) {
			count=1;
			nameList.add(0, "id");
		}
		nameList.add(count, "level4_run_id");
		nameList.add(count+1, "user_id");
		nameList.add(count+2, "query_accession");
		nameList.add(count+3, "query_acc_level4_run_id");
		nameList.add(count+4, "status");
		if(row.getQueryAccessionData().getPdbSource()!= null && !row.getQueryAccessionData().getPdbSource().contentEquals("I-TASSER")) {
			nameList.add(count+5, "query_pdb_source");
			nameList.add(count+6, "query_pdb");
			nameList.add(count+7, "query_prot_name");
			nameList.add(count+8, "query_tax_id");
			nameList.add(count+9, "query_tax_group");
			nameList.add(count+10, "query_sci_name");
			nameList.add(count+11, "query_common_name");
		}

		StringBuilder b1 = new StringBuilder();
		StringBuilder qMarks = new StringBuilder();
		//Note: This fails if "IGNORE" is not used.
		b1.append("INSERT IGNORE INTO level4_tmalign_run (");

		for (String name : nameList) {
			b1.append("`" + name + "`,");
			qMarks.append("?,");
		}
		
		b1.append("start");
		b1.append(") VALUES (");
		b1.append(qMarks.toString());
		b1.append("NOW()");
		b1.append(")");	
		
		final String insertQuery = b1.toString();
			
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection
					.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
			for (int j=1; j<nameList.size()+1; j++) {
				String name = nameList.get(j-1);
				if (name.equals("id")) {
					ps.setInt(j,  tmAlignRunId);
				} else if (name.equals("level4_run_id")) {
					ps.setInt(j,  row.getLevel4RunId());
				} else if (name.equals("user_id")) {
					ps.setInt(j,  row.getUserId());
				} else if (name.equals("query_accession")) {
					ps.setString(j, row.getQueryAccessionData().getNcbiAccession());
				} else if (name.equals("query_acc_level4_run_id")) {
					ps.setInt(j,  queryAccLevel4RunId);
				} else if (name.equals("status")) {
					ps.setString(j, "Submitted");
				} else if (name.equals("start")) {
					ps.setTimestamp(j, Timestamp.from(Instant.now()));
				} else if (name.equals("query_pdb_source")) {
					ps.setString(j, row.getQueryAccessionData().getPdbSource());
				} else if (name.equals("query_pdb")) {
					ps.setString(j, row.getQueryAccessionData().getPdb());
				} else if (name.equals("query_prot_name")) {
					ps.setString(j, row.getQueryAccessionData().getProteinName());
				} else if (name.equals("query_tax_id")) {
					ps.setInt(j, row.getQueryAccessionData().getSpeciesTaxId());
				} else if (name.equals("query_tax_group")) {
					ps.setString(j, row.getQueryAccessionData().getTaxonomyName());
				} else if (name.equals("query_sci_name")) {
					ps.setString(j, row.getQueryAccessionData().getScientificName());
				} else if (name.equals("query_common_name")) {
					ps.setString(j, row.getQueryAccessionData().getCommonName());
				}
				else {
					logger.error("Type not seen for index: {} and value {}", j);
				}
			}
			return ps;
		}, keyHolder);
		
		logger.debug("Completed method: insertLevel4TMAlignRun with level4RunId = {}", row.getLevel4RunId());
		
		if (tmAlignRunId < 0) {
			//if row is added and auto-increment is used, 
			//return auto-incremented value (id)
			return keyHolder.getKey().intValue();
		} else {
			//if inserting with existing tmAlignRunId, just return that
			return tmAlignRunId;
		}
		
		
	}

	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void insertLevel4TMAlignData(int tmAlignRunId, int level4RunId, String fromAcc, String toAcc, HashMap<String, String> tmRef, LevelFourAccessionRow accRow) {
		logger.debug("Attempting method: insertLevel4TMAlignData with level4RunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class", level4RunId);
		
		String pdb = accRow.getPdb();
		String pdbSource = accRow.getPdbSource();

		List<String> nameList = new ArrayList<String>();
		nameList.add(0, "tmalign_run_id");
		nameList.add(1, "level4_run_id");
		nameList.add(2, "acc1");
		nameList.add(3, "acc2");
		nameList.add(4, "length1");
		nameList.add(5, "length2");
		nameList.add(6, "tmscore1");
		nameList.add(7, "tmscore2");
		nameList.add(8, "d0_1");
		nameList.add(9, "d0_2");
		nameList.add(10, "aligned_length");
		nameList.add(11, "rmsd");
		nameList.add(12, "n_ratio");
		
		if(pdbSource!= null && !pdbSource.contentEquals("I-TASSER")) {
			nameList.add(13, "pdb_source");
			nameList.add(14, "pdb");
			nameList.add(15, "prot_name");
			nameList.add(16, "tax_id");
			nameList.add(17, "tax_group");
			nameList.add(18, "sci_name");
			nameList.add(19, "common_name");
		}
		
		StringBuilder b1 = new StringBuilder();
		StringBuilder qMarks = new StringBuilder();
		//Note: This fails if "IGNORE" is not used.
		b1.append("INSERT IGNORE INTO level4_tmalign_data (");
		
		for (String name : nameList) {
			b1.append("`" + name + "`,");
			qMarks.append("?,");
		}
		b1.deleteCharAt(b1.length() - 1);
		qMarks.deleteCharAt(qMarks.length() - 1);

		b1.append(") VALUES (");
		b1.append(qMarks.toString());
		b1.append(")");		
		
		final String insertQuery = b1.toString();
		
		jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
				for (int j=1; j<nameList.size() +1; j++) {
					String name = nameList.get(j-1);
					if(name.equals("tmalign_run_id")) {
						ps.setInt(j, tmAlignRunId);
					} else if (name.equals("level4_run_id")) {
						ps.setInt(j, level4RunId);
					} else if (name.equals("acc1")) {
						ps.setString(j, fromAcc);
					} else if (name.equals("acc2")) {
						ps.setString(j, toAcc);
					} else if (name.equals("length1")) {
						ps.setInt(j, Integer.parseInt(tmRef.get("chain 1 length")));
					} else if (name.equals("length2")) {
						ps.setInt(j, Integer.parseInt(tmRef.get("chain 2 length")));
					} else if (name.equals("tmscore1")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("chain 1 tm score")));
					} else if (name.equals("tmscore2")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("chain 2 tm score")));
					} else if (name.equals("d0_1")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("chain 1 d0")));
					} else if (name.equals("d0_2")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("chain 2 d0")));
					} else if (name.equals("aligned_length")) {
						ps.setInt(j, Integer.parseInt(tmRef.get("aligned")));
					} else if (name.equals("rmsd")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("rmsd")));
					} else if (name.equals("n_ratio")) {
						ps.setDouble(j, Double.parseDouble(tmRef.get("identical")));
					} else if (name.equals("pdb_source")) {
						ps.setString(j, pdbSource);
					}  else if (name.equals("pdb")) {
						ps.setString(j, pdb);
					} else if (name.equals("prot_name")) {
						ps.setString(j, accRow.getProteinName());
					} else if (name.equals("tax_id")) {
						ps.setInt(j, accRow.getSpeciesTaxId());
					} else if (name.equals("tax_group")) {
						ps.setString(j, accRow.getTaxonomyName());
					} else if (name.equals("sci_name")) {
						ps.setString(j, accRow.getScientificName());
					} else if (name.equals("common_name")) {
						ps.setString(j, accRow.getCommonName());
					}
					else {
						logger.error("Type not seen for index: {} and value {}", j);
					}
				}
			}

			@Override
			public int getBatchSize() {
				return 1;
			}
		});		
		logger.debug("Completed method: insertLevel4TMAlignData with level4RunId = {}", level4RunId);
	}
	
	public boolean tmAlignDataExists(int level4RunId, String acc1, String acc2) {		
	    Connection connection;
		try {
			connection = jdbcTemplate.getDataSource().getConnection();
			PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM level4_tmalign_data WHERE level4_run_id = ? AND acc1 = ? AND acc2 = ?");
			ps.setInt(1,level4RunId);
			ps.setString(2,acc1);
			ps.setString(3,acc2);

			ResultSet rs = ps.executeQuery();
			int n = 0;
			if (rs.next()) {
			    n = rs.getInt(1);
			}	
			if(n>0) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	@Transactional
	  @Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
	      BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	  public void setTMAlignRunStatus(String status, int tmAlignRunId, int level4RunId) {
	    String updateQuery = "UPDATE level4_tmalign_run SET status = ? WHERE id = ? AND level4_run_id = ?";
	    jdbcTemplate.update(updateQuery, status, tmAlignRunId, level4RunId);
	  }


	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void insertLevel4Data(int level4RunId, List<LevelFourAccessionRow> level4data) {
		logger.debug("Attempting method: insertLevel4Data with level4RunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class", level4RunId);

		List<String> nameList = new ArrayList<String>();
		nameList.add(0, "level4_run_id");
		nameList.add(1, "status");
		nameList.add(2, "current_priority");
		nameList.add(3, "default_priority");
		nameList.add(4, "accession");
		nameList.add(5,"fasta");
		
		StringBuilder b1 = new StringBuilder();
		StringBuilder qMarks = new StringBuilder();
		b1.append("INSERT INTO level4_data (");
		
		for (String name : nameList) {
			b1.append("`" + name + "`,");
			qMarks.append("?,");
		}
		//remove trailing comma from end of string
//		b1.deleteCharAt(b1.length() - 1);
//		qMarks.deleteCharAt(qMarks.length() - 1);
		
		b1.append("fasta_end");
		b1.append(") VALUES (");
		b1.append(qMarks.toString());
		b1.append("NOW()");
		b1.append(")");
		b1.append("    ON DUPLICATE KEY UPDATE ");
		b1.append("    status = VALUES(status), ");
		b1.append("    current_priority = VALUES(current_priority), ");
		b1.append("    fasta = VALUES(fasta) ");
		
		final String insertQuery = b1.toString();
		
		

		jdbcTemplate.batchUpdate(insertQuery, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
				LevelFourAccessionRow row = level4data.get(i);
				for (int j=1; j<nameList.size() +1; j++) {
					String name = nameList.get(j-1);
					if (name.equals("level4_run_id")) {
						ps.setInt(j, level4RunId);
					} else if (name.equals("status")) {
						ps.setString(j, row.getStatus());
					} else if (name.equals("current_priority")) {
						ps.setString(j, row.getPriority());
					} else if (name.equals("default_priority")) {
						ps.setString(j,  row.getAutoPriority());
					} else if (name.equals("accession")) {
						ps.setString(j,  row.getNcbiAccession());
					} else if (name.equals("fasta")) {
						ps.setString(j, row.getFasta());
					} else {
						logger.error("Type not seen for index: {} and value {}", j);
					}
				}
			}

			@Override
			public int getBatchSize() {
				return level4data.size();
			}
		});
		
		
		logger.debug("Completed method: insertLevel4Data with level4RunId = {}", level4RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4DataStatus(final int level4RunId, String status, String accession) {
		logger.debug("Attempting method: updateLevel4DataStatus accessionRunId = {}", level4RunId);

		String updateQuery = "UPDATE level4_data SET status = ? WHERE level4_run_id = ? AND accession = ?";
		jdbcTemplate.update(updateQuery, status, level4RunId, accession);

		logger.debug("Completed method: updateLevel4DataStatus accessionRunId = {}", level4RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4CScoreData(final int level4RunId, final LevelFourAccessionRow row, String accession) {
		logger.debug("Attempting method: updateLevel4CScoreData accessionRunId = {}", level4RunId);

		String updateQuery = "UPDATE level4_data SET status = ?, cscore = ?, tm_score = ?, tm_score_error = ?, rmsd = ?, rmsd_error =?, density = ? WHERE level4_run_id = ? AND accession = ?";
		jdbcTemplate.update(updateQuery, row.getStatus(), row.getCscore(), row.getTm_score(), row.getTm_score_error(), row.getRmsd(), row.getRmsd_error(), row.getDensity(), level4RunId, accession);

		logger.debug("Completed method: updateLevel4CScoreData accessionRunId = {}", level4RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4PDBData(final int level4RunId, String pdb, String accession) {
		logger.debug("Attempting method: updateLevel4PDBData accessionRunId = {}", level4RunId);

		String updateQuery = "UPDATE level4_data SET pdb = ? WHERE level4_run_id = ? AND accession = ?";
		jdbcTemplate.update(updateQuery, pdb, level4RunId, accession);

		logger.debug("Completed method: updateLevel4PDBData accessionRunId = {}", level4RunId);
	}
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateLevel4Template(final int level4RunId, String template, String templatePDB) {
		logger.debug("Attempting method: updateLevel4Template accessionRunId = {} with template {}", level4RunId, template);
		
		String updateQuery = "UPDATE level4_run SET template = ? WHERE id = ?";
		jdbcTemplate.update(updateQuery, template, level4RunId);
		
		if(templatePDB != null) {
			String updatePDBQuery = "UPDATE level4_run SET template_pdb = ? WHERE id = ?";
			jdbcTemplate.update(updatePDBQuery, templatePDB, level4RunId);
		}
		
		logger.debug("Attempting method: updateLevel4Template accessionRunId = {} with template {}", level4RunId, template);
	}
	
	
	@Transactional
	@Retryable(maxAttempts = 50, value = { MySQLTransactionRollbackException.class, DataAccessException.class,
			BatchUpdateException.class, RuntimeException.class }, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateUniProtAcc(int level4RunId, HashMap<String, String> accMapping) {
		logger.debug("Attempting method: updateUniProtAcc with level4RunId = {}"
				+ " and retryable value(s) = DataAccessException.class, BatchUpdateException.class, RuntimeException.class", level4RunId);
		List<String> ncbiAcc = new ArrayList<String>();
		List<String> uniprotAcc = new ArrayList<String>();
		for(Map.Entry<String, String> set : accMapping.entrySet()) {
			ncbiAcc.add(set.getKey());
			uniprotAcc.add(set.getValue());
		}
		
		jdbcTemplate.batchUpdate("UPDATE level4_data SET uniprot_acc = ? WHERE accession = ? AND level4_run_id = ?",
				new BatchPreparedStatementSetter() {
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setString(1, uniprotAcc.get(i));
						ps.setString(2, ncbiAcc.get(i));
						ps.setInt(3, level4RunId);
					}

					public int getBatchSize() {
						return ncbiAcc.size();
					}
				});
		// System.out.println("Completed method: batchAccessionHitUpdateCddCounts");
		logger.debug("Completed method: updateUniProtAcc");
		
	}
	



	// public CutoffData rCutoff(RService rService, List<DensityRow>
	// densityData) {
	// if (densityData.size() < 3) {
	// System.out.println("densityData size < 3....returning null cutoff");
	// return null;
	// }
	// Object cutoffLock = new Object();
	// List<Double> simData = new ArrayList<Double>();
	// List<String> orthoData = new ArrayList<String>();
	// List<Double> cutoffVals = null;
	// CutoffData cutoffData = null;
	//
	// for (DensityRow row : densityData) {
	// if (row.getPercSim() < 0.0) {
	// System.out.println("Percent Similarity contains a negative number");
	// return null;
	// } else if (row.getPercSim() > 100.0) {
	// simData.add(100.0);
	// } else {
	// simData.add(row.getPercSim());
	// }
	// orthoData.add(row.getOrtholog());
	// }
	//
	// boolean orthologs = false;
	//
	// // List<Double> data = simData;
	// // List<String> data2 = orthoData;
	//
	// if (simData.size() == 0) {
	// System.out.println("Invalid Percent Similarity Data!");
	// return null;
	// }
	// if (orthoData.size() == 0) {
	// System.out.println("Invalid Ortholog Data!");
	// return null;
	// }
	//
	// // remove first row so not comparing to self
	// simData.remove(0);
	// orthoData.remove(0);
	//
	// StringBuilder b1 = new StringBuilder();
	// b1.append("c(");
	// String prefix = "";
	// for (double row : simData) {
	// b1.append(prefix);
	// prefix = ",";
	// b1.append(String.valueOf(row));
	// }
	// b1.append(")");
	//
	// StringBuilder b2 = new StringBuilder();
	// b2.append("c(");
	// prefix = "";
	// for (String row : orthoData) {
	// b2.append(prefix);
	// prefix = ",";
	// b2.append("'" + row + "'");
	// }
	// b2.append(")");
	//
	// if (orthoData.toString().toLowerCase().contains("y")) {
	// orthologs = true;
	// }
	//
	// synchronized (cutoffLock) {
	// Rengine engine = rService.getEngine();
	// engine.eval("rm(list=ls())");
	// engine.eval("PercentSimilarity=" + b1.toString());
	// engine.eval("OrthologCandidate=" + b2.toString());
	// engine.eval("df= data.frame(PercentSimilarity, OrthologCandidate)");
	// // engine.eval("df<-df[-1,]");
	// // engine.eval("df");
	//
	// engine.eval("densityRes=density(df$PercentSimilarity)");
	// engine.eval("x<-densityRes$x");
	// engine.eval("y<-densityRes$y");
	//
	// REXP resultx = engine.eval("x");
	// REXP resulty = engine.eval("y");
	// System.out.println(resultx);
	// System.out.println(resulty);
	//
	// Double[] xdouble = ArrayUtils.toObject(resultx.asDoubleArray());
	// Double[] ydouble = ArrayUtils.toObject(resulty.asDoubleArray());
	// // System.out.println(xdouble[0]);
	// // System.out.println(xdouble[1]);
	//
	// engine.eval("deriv.1<-diff(x)/diff(y)");
	// engine.eval("deriv.2<-diff(deriv.1)/diff(x[-1])");
	// engine.eval("Changing<-deriv.1*c(deriv.1[-1],0)");
	// engine.eval("crit.points<-which(Changing<=0)");
	// engine.eval("crit.points.max<-crit.points[which(-deriv.2[crit.points]>0)]");
	// engine.eval("crit.points.min<-crit.points[which(deriv.2[crit.points]> 0)]");
	// engine.eval("crit.points.inflect<-crit.points[which(abs(deriv.2[crit.points])==0)]");
	// engine.eval("size.min<-length(crit.points.min)");
	// engine.eval("LUB.crit.point<-rep(0,size.min)");
	//
	// if (orthologs) {
	// engine.eval("for ( i in 1:size.min){
	// LUB.crit.point[i]<-max(which(df$OrthologCandidate=='Y' &
	// df$PercentSimilarity>=x[crit.points.min[i]]))} ");
	// engine.eval("cut.percents<-df$PercentSimilarity[LUB.crit.point]*100 ");
	// engine.eval("if(sum(!is.finite(LUB.crit.point))>0){cut.percents[which(is.finite(LUB.crit.point)==FALSE)]<-100}");
	// } else {
	// engine.eval("LUB.crit.point<-x[crit.points.min]");
	// engine.eval("cut.percents<-x[crit.points.min]*100");
	// engine.eval("cut.percents<-round(cut.percents)");
	// }
	// engine.eval("cut.percents<-unique(cut.percents)");
	//
	// System.out.println("Cutoff");
	// // find peaks
	// REXP rMaxCuts = engine.eval("crit.points.max");
	// Integer[] maxCritLoc = ArrayUtils.toObject(rMaxCuts.asIntArray());
	// REXP rMinCuts = engine.eval("crit.points.min");
	// Integer[] minCritLoc = ArrayUtils.toObject(rMinCuts.asIntArray());
	// REXP rInfPts = engine.eval("crit.points.inflect");
	// Integer[] infLoc = ArrayUtils.toObject(rInfPts.asIntArray());
	//
	// Double cutoffs[];
	// if (maxCritLoc.length >= 2) {
	// REXP rCutoffs = engine.eval("cut.percents");
	// cutoffs = ArrayUtils.toObject(rCutoffs.asDoubleArray());
	// } else {
	// cutoffs = new Double[1];
	// cutoffs[0] = 20.0;
	// }
	//
	// for (int j = 0; j < cutoffs.length; j++) {
	// System.out.println("Cutoff:" + cutoffs[j]);
	// }
	// cutoffVals = Arrays.asList(cutoffs);
	//
	// cutoffData = new CutoffData(Arrays.asList(xdouble),
	// Arrays.asList(ydouble),
	// cutoffVals, Arrays.asList(maxCritLoc),
	// Arrays.asList(minCritLoc), Arrays.asList(infLoc));
	// } // end synchronization lock
	//
	// System.out.println("Finished rCutoff");
	// return cutoffData;
	// }

	// public CutoffData calcCutoff(List<DensityRow> densityData) {
	// if (densityData == null){
	// System.out.println("densityData is null....returning null cutoff");
	// }
	// if (densityData.size() < 3) {
	// System.out.println("densityData size < 3....returning null cutoff");
	// return null;
	// }
	// List<Double> simData = new ArrayList<Double>();
	// List<String> orthoData = new ArrayList<String>();
	// List<Double> cutoffVals = null;
	// CutoffData cutoffData = null;
	//
	// // organize density data for cutoff calculations
	// for (DensityRow row : densityData) {
	// if (row.getPercSim() < 0.0) {
	// System.out.println("Percent Similarity contains a negative number");
	// return null;
	// } else if (row.getPercSim() > 100.0) {
	// simData.add(100.0);
	// } else {
	// simData.add(row.getPercSim());
	// }
	// orthoData.add(row.getOrtholog());
	// }
	// boolean orthologs = false;
	// if (simData.size() == 0) {
	// System.out.println("Invalid Percent Similarity Data!");
	// return null;
	// }
	// if (orthoData.size() == 0) {
	// System.out.println("Invalid Ortholog Data!");
	// return null;
	// }
	// // remove first row so not comparing to self
	// simData.remove(0);
	// orthoData.remove(0);
	//
	// // StringBuilder b1 = new StringBuilder();
	// // b1.append("c(");
	// // String prefix = "";
	// // for (double row : simData) {
	// // b1.append(prefix);
	// // prefix = ",";
	// // b1.append(String.valueOf(row));
	// // }
	// // b1.append(")");
	// //
	// // StringBuilder b2 = new StringBuilder();
	// // b2.append("c(");
	// // prefix = "";
	// // for (String row : orthoData) {
	// // b2.append(prefix);
	// // prefix = ",";
	// // b2.append("'" + row + "'");
	// // }
	// // b2.append(")");
	//
	// // check for orthologs
	// if (orthoData.toString().toLowerCase().contains("y")) {
	// orthologs = true;
	// }
	//
	//
	//
	// // synchronized (cutoffLock) {
	// // Rengine engine = rService.getEngine();
	// // engine.eval("rm(list=ls())");
	// // engine.eval("PercentSimilarity=" + b1.toString());
	// // engine.eval("OrthologCandidate=" + b2.toString());
	// // engine.eval("df= data.frame(PercentSimilarity, OrthologCandidate)");
	// // // engine.eval("df<-df[-1,]");
	// // // engine.eval("df");
	// //
	// // engine.eval("densityRes=density(df$PercentSimilarity)");
	// // engine.eval("x<-densityRes$x");
	// // engine.eval("y<-densityRes$y");
	// //
	// // REXP resultx = engine.eval("x");
	// // REXP resulty = engine.eval("y");
	// // System.out.println(resultx);
	// // System.out.println(resulty);
	// //
	// // Double[] xdouble = ArrayUtils.toObject(resultx.asDoubleArray());
	// // Double[] ydouble = ArrayUtils.toObject(resulty.asDoubleArray());
	// // // System.out.println(xdouble[0]);
	// // // System.out.println(xdouble[1]);
	// //
	// // engine.eval("deriv.1<-diff(x)/diff(y)");
	// // engine.eval("deriv.2<-diff(deriv.1)/diff(x[-1])");
	// // engine.eval("Changing<-deriv.1*c(deriv.1[-1],0)");
	// // engine.eval("crit.points<-which(Changing<=0)");
	// //
	// engine.eval("crit.points.max<-crit.points[which(-deriv.2[crit.points]>0)]");
	// // engine.eval("crit.points.min<-crit.points[which(deriv.2[crit.points]>
	// 0)]");
	// //
	// engine.eval("crit.points.inflect<-crit.points[which(abs(deriv.2[crit.points])==0)]");
	// // engine.eval("size.min<-length(crit.points.min)");
	// // engine.eval("LUB.crit.point<-rep(0,size.min)");
	// //
	// // if (orthologs) {
	// // engine.eval("for ( i in 1:size.min){
	// LUB.crit.point[i]<-max(which(df$OrthologCandidate=='Y' &
	// df$PercentSimilarity>=x[crit.points.min[i]]))} ");
	// //
	// engine.eval("cut.percents<-df$PercentSimilarity[LUB.crit.point]*100 ");
	// //
	// engine.eval("if(sum(!is.finite(LUB.crit.point))>0){cut.percents[which(is.finite(LUB.crit.point)==FALSE)]<-100}");
	// // } else {
	// // engine.eval("LUB.crit.point<-x[crit.points.min]");
	// // engine.eval("cut.percents<-x[crit.points.min]*100");
	// // engine.eval("cut.percents<-round(cut.percents)");
	// // }
	// // engine.eval("cut.percents<-unique(cut.percents)");
	// //
	// // System.out.println("Cutoff");
	// // // find peaks
	// // REXP rMaxCuts = engine.eval("crit.points.max");
	// // Integer[] maxCritLoc = ArrayUtils.toObject(rMaxCuts.asIntArray());
	// // REXP rMinCuts = engine.eval("crit.points.min");
	// // Integer[] minCritLoc = ArrayUtils.toObject(rMinCuts.asIntArray());
	// // REXP rInfPts = engine.eval("crit.points.inflect");
	// // Integer[] infLoc = ArrayUtils.toObject(rInfPts.asIntArray());
	// //
	// // Double cutoffs[];
	// // if (maxCritLoc.length >= 2) {
	// // REXP rCutoffs = engine.eval("cut.percents");
	// // cutoffs = ArrayUtils.toObject(rCutoffs.asDoubleArray());
	// // } else {
	// // cutoffs = new Double[1];
	// // cutoffs[0] = 20.0;
	// // }
	// //
	// // for (int j = 0; j < cutoffs.length; j++) {
	// // System.out.println("Cutoff:" + cutoffs[j]);
	// // }
	// // cutoffVals = Arrays.asList(cutoffs);
	// //
	// // cutoffData = new CutoffData(Arrays.asList(xdouble),
	// Arrays.asList(ydouble),
	// cutoffVals, Arrays.asList(maxCritLoc),
	// // Arrays.asList(minCritLoc), Arrays.asList(infLoc));
	// // } // end synchronization lock
	//
	// System.out.println("Finished calcCutoff");
	// return cutoffData;
	// }

	// public int getUserIdFromAccessionRunId(int accessionRunId) {
	//
	// StringBuilder b = new StringBuilder();
	// b.append(" SELECT b.user_id FROM user_run_accession_run a ");
	// b.append(" JOIN user_run b ON a.user_run_id = b.id ");
	// b.append(" WHERE a.accession_run_id = ? ");
	//
	// int userId = jdbcTemplate.queryForObject(b.toString(), int.class,
	// accessionRunId);
	//
	// return userId;
	// }

	public int getToxCastUserId() {
		return reportService.getToxCastUserId();
	}

	// Delegate methods for reportService
	public void createLevelOneReports(int accessionRunId, int userId, int destination) {
		reportService.createLevelOneReports(accessionRunId, userId, destination);
	}

	public void createLevelTwoReports(int accessionRunId, int lev2RunId, int userId, int destination) {
		reportService.createLevelTwoReports(accessionRunId, lev2RunId, userId, destination);
	}

	public void createLevelThreeReport(int accessionRunId, int lev3RunId, int userId, int destination) {
		reportService.createLevelThreeReport(accessionRunId, lev3RunId, userId, destination);
	}

	public ReportInfo getReportInfo(int accessionRunId, int lev2Id, int lev3Id, int lev4Id) {
		return reportService.getReportInfo(accessionRunId, lev2Id, lev3Id, lev4Id);
	}

	public ReportInfo getLatestUpdateInfo() {
		List<ReportInfo> info = reportService.getUpdateInfo();
		return info.get(0);
	}

	public boolean checkIfEukaryote(int accessionRunId) {
		return reportService.isQueryEukaryoteFromAccessionRunId(accessionRunId);
	}

	public int getDefaultOrthologCount(int accessionRunId, boolean isEukaryote) {
		return reportService.getLevelOneDefaultOrthologCount(accessionRunId, isEukaryote);
	}

}

// class DensityMapper implements RowMapper<DensityRow> {
// @Override
// public DensityRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
// double percSim = rs.getDouble("sim");
// String ortho = rs.getString("rbh_status");
// DensityRow theRow = new DensityRow(percSim, ortho);
//
// return theRow;
// }
// }
