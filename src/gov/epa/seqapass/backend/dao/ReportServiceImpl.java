package gov.epa.seqapass.backend.dao;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.util.SerializationUtils;

import com.google.common.collect.Lists;
import com.mysql.cj.result.Row;

import gov.epa.seqapass.backend.domain.BackEndProvider;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.NCBIProvider;
import gov.epa.seqapass.common.AminoAcid;
import gov.epa.seqapass.common.Chemical;
import gov.epa.seqapass.common.CutoffData;
import gov.epa.seqapass.common.DensityRow;
import gov.epa.seqapass.common.HistogramRow;
import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.LevelFourResultRow;
import gov.epa.seqapass.common.LevelFourStatusRow;
import gov.epa.seqapass.common.LevelOneReportRow;
import gov.epa.seqapass.common.LevelOneStatusRow;
import gov.epa.seqapass.common.LevelThreeReportRow;
import gov.epa.seqapass.common.LevelThreeRequestableRow;
import gov.epa.seqapass.common.LevelThreeResidueResult;
import gov.epa.seqapass.common.LevelThreeStatusRow;
import gov.epa.seqapass.common.LevelThreeViewRequest;
import gov.epa.seqapass.common.LevelTwoReportRow;
import gov.epa.seqapass.common.LevelTwoRequestableRow;
import gov.epa.seqapass.common.LevelTwoStatusRow;
import gov.epa.seqapass.common.Link;
import gov.epa.seqapass.common.ReportInfo;
import gov.epa.seqapass.common.ReportRow;
import gov.epa.seqapass.common.ReportTypeEnum;
import gov.epa.seqapass.common.SpeciesTaxGrouping;
import gov.epa.seqapass.common.TaxEcos;
import gov.epa.seqapass.common.TaxGroup;
import gov.epa.seqapass.common.UniprotMap;
import gov.epa.seqapass.common.ZipRequestable;

public class ReportServiceImpl implements ReportService {
	
	private static Logger logger = LogManager.getLogger(ReportServiceImpl.class);

	private static final String CheckAdminQuery = "SELECT is_admin FROM `user` where id=?";
	private static final String GetDBQuery = "SELECT DATABASE()";
	private static final String GetCanonicalAccessionFromRunID = "SELECT canonical_accession_id FROM accession_run WHERE id = ?";
	private static final String GetTaxIdFromAccessionId = "SELECT taxid FROM protein where accession_id = ?";
	private static final String GetSciNameAtRankFromTaxID = "SELECT SCINAME_at_rank_for_taxid(?,?) as templateSpecies";
	private static final String GetJobsWithinDay = "SELECT COUNT(*) As cnt FROM user_run WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 1 DAY) AND user_id = ?";
	// private static final String MainReportQuery =
	// "SELECT u.id, a.id AS accession_run_id, a.accession_id, a.protein_title,
	// a.accession_taxid, UNIX_TIMESTAMP(u.complete_time) AS COMPLETE_TIME FROM
	// accession_run a JOIN user_run_accession_run ua ON ua.accession_run_id =
	// a.id JOIN user_run u ON u.id = ua.user_run_id";

	// Fields to enable debug csv creation (used in createLevelOneReport,
	// createLevelTwoReport, etc)
	private static final String debugReportDir = "C:\\Users\\csimmo02\\seqapass_reports\\";
	private static final boolean enableDebugOutput = false;

	private JdbcTemplate jdbcTemplate;
	@SuppressWarnings("unused")
	//private RService rService;
	private NCBIKeeper ncbiKeeper;
	private BackEndProvider backEnd;

	private static final double defaultLevelOneEvalue = 0.01;
	private static final double defaultLevelTwoEvalue = 10;
	private static final int defaultCommonDomains = 1;
	private static final double levelThreeSizeTolerance = 30.0;

	// public ReportServiceImpl(JdbcTemplate template) {
	// this.jdbcTemplate = template;
	// }

	public ReportServiceImpl(JdbcTemplate template, NCBIKeeper ncbiKeeper, BackEndProvider backEnd) {
		this.jdbcTemplate = template;
		//this.rService = null;
		this.ncbiKeeper = ncbiKeeper;
		this.backEnd = backEnd;
	}

	@Override
	public boolean isUserAdmin(int userId) {
		String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery, String.class, userId);
		if (isAdmin.toLowerCase().equals("y")) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public List<LevelOneStatusRow> getLevelOneStatusForUser(int userId) {
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		// String dbName = jdbcTemplate.queryForObject(GetDBQuery,
		// String.class);
		String domain = backEnd.getDomainName();
		StringBuilder b = new StringBuilder();

		b.append(" SELECT DISTINCT ");
		b.append("       tt.`SeqAPASS Run Id`, ");
		b.append("       tt.user_id, ");
		b.append("       tt.email, ");
		b.append("       tt.`Accession`, ");
		b.append("       tt.`Maximum bitscore`, ");
		b.append("       tt.BLASTp, ");
		b.append("       tt.BLASTp_queue_num, ");
		b.append("       tt.`Common Domains % complete`, ");
		b.append("       tt.`RPS_queue_num`, ");
		b.append("       tt.`Ortholog % complete`, ");
		b.append("       tt.`RBH_queue_num`, ");
		b.append("       tt.`Start Date`, ");
		b.append("       tt.`Date Completed`, ");
		b.append("       tt.`SeqAPASS Run Duration`, ");
		b.append("       v.update_version");
		b.append("  FROM ");
		b.append("     (SELECT DISTINCT ");
		b.append("         a.id AS `SeqAPASS Run Id`, ");
		b.append("             a.user_id, ");
		b.append("             d.email, ");
		b.append("             c.query_accession_id AS `Accession`, ");
		b.append("             b.max_bit_score AS `Maximum bitscore`, ");
		b.append("             b.blastp_status AS `BLASTp`, ");
		b.append("             QUEUE_BLASTP_NUMBER(b.id) AS `BLASTp_queue_num`, ");
		b.append("             b.rps_completeness AS `Common Domains % complete`, ");
		b.append("             QUEUE_RPS_NUMBER(b.id) AS `RPS_queue_num`, ");
		b.append("             b.rbh_completeness AS `Ortholog % complete`, ");
		b.append("             QUEUE_RBH_NUMBER(b.id) AS `RBH_queue_num`, ");
		b.append("             UNIX_TIMESTAMP(a.submit_time) AS `Start Date`, ");
		b.append(
				"             IF(b.status = 'complete', UNIX_TIMESTAMP(b.completion_date), NULL) AS `Date Completed`, ");
		b.append(
				"             IF(b.status = 'complete', UNIX_TIMESTAMP(b.completion_date) - UNIX_TIMESTAMP(a.submit_time), NULL) AS `SeqAPASS Run Duration`, ");
		b.append("             UNIX_TIMESTAMP(b.submitted_date) AS `lev1Submit`, ");
		b.append("             b.ncbi_version_id ");
		b.append("     FROM ");
		b.append("         user_run a, user_run_accession_run c, accession_run b, user d ");
		b.append("     WHERE ");
		b.append("         a.id = c.user_run_id ");
		b.append("             AND b.id = c.accession_run_id ");
		b.append("             AND d.id = a.user_id) tt, ");

		b.append("                      version v");
		b.append("                WHERE v.id = tt.ncbi_version_id");
		if (!isAdmin) {
			b.append("            AND tt.user_id = ? ");
		}
		b.append("                ORDER BY tt.`SeqAPASS Run Id` DESC; ");

		// b.append(" JOIN ");
		// b.append(" version f ON tt.lev1Submit >
		// UNIX_TIMESTAMP(f.install_date) ");
		// b.append(" AND f.backend_host = ? ");
		// b.append(" AND f.id = (SELECT ");
		// b.append(" MAX(f2.id) ");
		// b.append(" FROM ");
		// b.append(" version f2 ");
		// b.append(" LEFT JOIN ");
		// b.append(" version f3 ON f2.update_version = f3.update_version ");
		// b.append(" AND f2.backend_host = f3.backend_host ");
		// b.append(" AND f2.id < f3.id ");
		// b.append(" WHERE ");
		// b.append(" f2.backend_host = ? ");
		// b.append(" AND f3.id IS NULL ");
		// b.append(" AND UNIX_TIMESTAMP(f2.install_date) < tt.lev1Submit) ");

		if (isAdmin) {
			return jdbcTemplate.query(b.toString(), new LevelOneStatusMapper());
		} else {
			// String userQuery = b.toString() + " WHERE tt.user_id = ?";
			// System.out.println("query: " + b.toString());
			logger.info("query: {}", b.toString());
			return jdbcTemplate.query(b.toString(), new LevelOneStatusMapper(), userId);
		}
	}

	@Override
	public List<ReportRow> getMainReportForUser(int userId) {
		// String CheckAdminQuery = "SELECT is_admin FROM user where id=?";
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);

		// String dbName = jdbcTemplate.queryForObject(GetDBQuery,
		// String.class);
		String domain = backEnd.getDomainName();

		// if (domain.equals("localhost")) {
		// domain = "ag.epa.gov";
		// dbName = "dev_seqapass";
		// System.out.println("localhost using dev");
		// }

		StringBuilder b = new StringBuilder();
		b.append("SELECT * FROM (");
		b.append("SELECT DISTINCT ");

		b.append("        tt.`seq_id`, ");
		b.append("            tt.user_id, ");
		b.append("            tt.accession_run_id, ");
		b.append("            tt.ortholog_count, ");
		b.append("            tt.top_hit_accession_id, ");
		b.append("            tt.query_accession_id, ");
		b.append("            tt.protein_title, ");
		b.append("            tt.query_taxid, ");
		b.append("            tt.name, ");
		b.append("            tt.seqapass_name, ");
		b.append("            tt.`end_time`, ");
		b.append("            v.* ");

		b.append(" FROM ");
		b.append("    (SELECT DISTINCT ");
		b.append("        c.id AS `seq_id`, ");
		b.append("            c.user_id, ");
		b.append("            a.id AS accession_run_id, ");
		b.append("            a.ortholog_count, ");
		b.append("            a.top_hit_accession_id, ");
		b.append("            d.query_accession_id, ");
		b.append("            d.protein_title, ");
		b.append("            a.query_taxid, ");
		b.append("            b.name, ");
		b.append("            e.seqapass_name, ");
		b.append("            UNIX_TIMESTAMP(a.completion_date) AS `end_time`, ");
		b.append("            a.ncbi_version_id ");
		b.append("    FROM ");
		b.append("        accession_run a, taxonomy_name b, user_run c, user_run_accession_run d, taxonomy_node e ");
		b.append("    WHERE ");
		b.append("        d.accession_run_id = a.id ");
		b.append("            AND b.taxid = a.query_taxid ");
		b.append("            AND e.taxid = a.query_taxid ");
		b.append("            AND a.blastp_status NOT REGEXP 'hits' ");
		b.append("            AND a.completion_date IS NOT NULL ");
		b.append("            AND a.status = 'complete' ");
		b.append("            AND b.name_class = 'scientific name' ");
		b.append("            AND c.id = d.user_run_id) tt, ");

		b.append("                      version v");
		b.append("                WHERE v.id = tt.ncbi_version_id");
		if (!isAdmin) {
			b.append("            AND tt.user_id = ? ");
		}
//		b.append("                ORDER BY tt.`seq_id` DESC; ");
		
		b.append("                ORDER BY tt.`seq_id` DESC, v.seqapass_version DESC ) big ");
		b.append("                GROUP BY big.accession_run_id ");
		b.append("                ORDER BY big.accession_run_id DESC; ");


		// b.append(" JOIN ");
		// b.append(" version f ON tt.end_time > UNIX_TIMESTAMP(f.install_date)
		// ");
		// b.append(" AND f.backend_host = ? ");
		// b.append(" AND f.id = (SELECT ");
		// b.append(" MAX(f2.id) ");
		// b.append(" FROM ");
		// b.append(" version f2 ");
		// b.append(" LEFT JOIN ");
		// b.append(" version f3 ON f2.update_version = f3.update_version ");
		// b.append(" AND f2.backend_host = f3.backend_host ");
		// b.append(" AND f2.id < f3.id ");
		// b.append(" WHERE ");
		// b.append(" f2.backend_host = ? ");
		// b.append(" AND f3.id IS NULL ");
		// b.append(" AND UNIX_TIMESTAMP(f2.install_date) < tt.end_time) ");
		// b.append(" ORDER BY tt.seq_id DESC ");

		// System.out.println(b.toString());
		logger.info(b.toString());

		List<Map<String, Object>> rows;

		// if (isAdmin) {
		// rows = jdbcTemplate.queryForList(b.toString(), domain, domain);
		// } else {
		// b.append(" AND tt.user_id = ?");
		// rows = jdbcTemplate.queryForList(b.toString(), domain, domain,
		// userId);
		// }

		if (isAdmin) {
			rows = jdbcTemplate.queryForList(b.toString());
		} else {
			// b.append(" AND tt.user_id = ?");
			rows = jdbcTemplate.queryForList(b.toString(), userId);
		}

		List<ReportRow> results = new LinkedList<ReportRow>();
		if (rows.size() == 0) {
			return results;
		}
		Map<Integer, String> nearClassName = new HashMap<Integer, String>();
		// System.out.println("rows.size() = " + rows.size());
		logger.info("rows.size() = {}", rows.size());
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			int userRunId = (int) row.get("seq_id");
			int accessionRunId = (int) row.get("accession_run_id");
			int orthologCnt = (int) row.get("ortholog_count");
			// String topHitAccession = (String)
			// row.get("top_hit_accession_id");
			String queryAccession = (String) row.get("query_accession_id");
			// String proteinName = (String) row.get("title");
			String proteinNameWSpecies = (String) row.get("protein_title");
			// String proteinName = queryAccession + ": " +
			// proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			// System.out.println("proteinNameWSpecies = " +
			// proteinNameWSpecies);
			String proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			// System.out.println("proteinName = " + proteinName);

			Long ncbiDate = ((Date) row.get("taxonomy_protein_date")).getTime();
			Date tmpUniprotDate = ((Date) row.get("uniprot_date"));
			Long uniprotDate = null;
			if (tmpUniprotDate != null) uniprotDate = tmpUniprotDate.getTime();
			String blastExecVersion = (String) row.get("blast_exec_version");
			Long cddDate = ((Date) row.get("cdd_data_date")).getTime();
			String cobaltExecVersion = (String) row.get("cobalt_exec_version");
			Long cobaltDate = ((Date) row.get("cobalt_data_date")).getTime();
			String itasserExecVersion = (String) row.get("itasser_exec_version");
			String tmalignExecVersion = (String) row.get("tmalign_exec_version");
			Long installDate = ((Date) row.get("install_date")).getTime();
			String javaVersion = (String) row.get("java_version");
			String primefacesVersion = (String) row.get("primefaces_version");
			String tomcatVersion = (String) row.get("tomcat_version");
			String mysqlVersion = (String) row.get("mysql_version");
			String rVersion = (String) row.get("r_version");
			// int updateVersion = (int) row.get("update_version");
			int updateVersion = (int) row.get("update_version");
			String seqapassVersion = (String) row.get("seqapass_version");

			int taxId = (int) row.get("query_taxid");

			// Integer taxonomyTaxid = 1;
			// String taxonomyName = "not found";
			if (!nearClassName.containsKey(taxId)) {
				int ancestorTaxid = getRankOrLowerFromTargetRankAndTaxid("class", taxId);
				if (ancestorTaxid != taxId) {
					int taxonomyTaxid = ancestorTaxid;
					String taxonomyName = getScientificNameFromTaxid(taxonomyTaxid);
					nearClassName.put(taxId, taxonomyName);
				} else {
					nearClassName.put(taxId, "not found");
				}
			}

			String name = (String) row.get("name");
			String seqapassName = (String) row.get("seqapass_name");
			// capitalize first letter in seqapassName
			seqapassName = seqapassName.substring(0, 1).toUpperCase() + seqapassName.substring(1);

			Long endDate = (Long) row.get("end_time");// NULL if not complete

			ReportRow reportRow = new ReportRow();
			reportRow.setTaxonomy(nearClassName.get(taxId));
			// reportRow.setTaxonomyType(taxonomyLevel);
			reportRow.setRunId(userRunId);
			reportRow.setAccessionRunId(accessionRunId);
			reportRow.setAccession(queryAccession);

			reportRow.setOrthologCnt(orthologCnt);

			reportRow.setReportInfo(new ReportInfo(ncbiDate, uniprotDate, blastExecVersion, cddDate, cobaltExecVersion, cobaltDate, itasserExecVersion, tmalignExecVersion,
					installDate, javaVersion, primefacesVersion, tomcatVersion, mysqlVersion, rVersion, updateVersion,
					seqapassVersion, ""));
			reportRow.setKey(accessionRunId + "." + queryAccession);

			reportRow.setQueryProtein(proteinName);
			reportRow.setTaxID(taxId);

			reportRow.setQuerySpeciesName(name);
			reportRow.setQueryCommonName(seqapassName);

			if (endDate == null) {
				endDate = new Long(0);
			}
			if (endDate != null) { // MAY BE NULL IN INITIAL TESTING
				reportRow.setEndDate(endDate);
			}

			results.add(reportRow);
		}
		return results;
	}

	@Override
	public List<LevelOneReportRow> getLevelOneReport(int accessionRunId) {

		ReportInfo reportInfo = getReportInfo(accessionRunId, -1, -1, -1);

		List<LevelOneReportRow> results = new ArrayList<LevelOneReportRow>();
		String topHitAccessionString = null;
		try {
			topHitAccessionString = jdbcTemplate.queryForObject(
					"SELECT top_hit_accession_id FROM accession_run WHERE id = ? LIMIT 1", String.class,
					accessionRunId);
		} catch (DataAccessException e1) {
			System.out
					.println("In: getLevelOneReport - could not find top_hit_accession_id from accession_run with id = "
							+ accessionRunId);
			return results;
		}

		if (topHitAccessionString == null || topHitAccessionString.equals("")) {
			//System.out.println("In: getLevelOneReport - top_hit_accession_id null or blank for accession_run with id: "
			//		+ accessionRunId);
			logger.error("In: getLevelOneReport - top_hit_accession_id null or blank for accession_run with id: {}",
					accessionRunId);
			return results;
		}

		List<Map<String, Object>> allRows = new ArrayList<Map<String, Object>>();
		StringBuilder b = new StringBuilder();
		b.append("SELECT DISTINCT ");
		b.append("       a.hit_accession_id, ");
		b.append("       b.protein_count, ");
		b.append("       b.taxid, ");
		b.append("       c.name, ");
		// b.append(" SEQAPASS_name(b.taxid) AS seqapass_name, ");
		b.append("       IS_eukaryote(b.taxid) AS is_eukaryote, ");
		b.append("       b.seqapass_name, ");
		b.append("       f.title, ");
		b.append("       a.near_class_taxid, ");
		b.append("       a.`xml_Hit_len`, ");
		b.append("       a.`xml_Hsp_identity`, ");
		b.append("       a.`xml_Hsp_positive`, ");
		b.append("       a.`xml_Hsp_evalue`, ");
		b.append("       a.`xml_Hsp_bit-score`, ");
		b.append("       a.rbh_status, ");
		b.append("       a.cdd_count, ");
		b.append("       a.`xml_Hsp_bit-score` / a.`xml_Hsp_bit-score` as `percent_sim`, ");
		b.append("       a.susceptibility, ");
		b.append("       UNIX_TIMESTAMP(e.completion_date) as `completion`, ");
		b.append("       IF(a.xml_Hit_def = '', 1,0) as `is_duplicate`,  ");
		b.append("       b.is_endangered, ");
		b.append("       b.is_model, ");
		b.append("       b.is_ecotox ");
		b.append("  FROM accession_hit a, ");
		b.append("       taxonomy_node b, ");
		b.append("       taxonomy_name c, ");
		b.append("       accession_run e, ");
		b.append("       protein f ");
		b.append(" WHERE a.accession_run_id = ? ");
		b.append("   AND e.id = ? ");
		b.append("   AND a.hit_accession_id = ? ");
		b.append("   AND b.taxid = a.hit_taxid ");
		b.append("   AND c.taxid = a.hit_taxid ");
		b.append("   AND f.accession_id = ? ");
		b.append("   AND c.name_class = 'scientific name' ");
		b.append("   LIMIT 1; ");
		Map<String, Object> row = null;
		try {
			row = jdbcTemplate.queryForMap(b.toString(), accessionRunId, accessionRunId, topHitAccessionString,
					topHitAccessionString);
			allRows.add(row);
		} catch (DataAccessException e) {
			// System.out.println("In: getLevelOneReport - failed to find a BLASTp hit from: " + topHitAccessionString);
			logger.error("In: getLevelOneReport - failed to find a BLASTp hit from: {}", 
					topHitAccessionString);
			return results;
		}
		if (row == null) {
			// System.out.println("In: getLevelOneReport - somehow got a null row for top hit: " + topHitAccessionString);
			logger.error("In: getLevelOneReport - somehow got a null row for top hit: {}", 
					topHitAccessionString);
		}

		int topHitTaxid = (int) row.get("taxid");
		// System.out.println("In: getLevelOneReport - got data for top hit: " + topHitAccessionString);
		logger.info("In: getLevelOneReport - got data for top hit: {}", topHitAccessionString);

		b = new StringBuilder();
//		b.append("SELECT DISTINCT ");
//		b.append("       a.hit_accession_id, ");
//		b.append("       b.protein_count, ");
//		b.append("       b.taxid, ");
//		b.append("       c.name, ");
//		// b.append(" SEQAPASS_name(b.taxid) AS seqapass_name, ");
//		b.append("       IS_eukaryote(b.taxid) AS is_eukaryote,");
//		b.append("       b.seqapass_name, ");
//		b.append("       f.title, ");
//		b.append("       a.near_class_taxid, ");
//		b.append("       a.`xml_Hit_len`, ");
//		b.append("       a.`xml_Hsp_identity`, ");
//		b.append("       a.`xml_Hsp_positive`, ");
//		b.append("       a.`xml_Hsp_evalue`, ");
//		b.append("       a.`xml_Hsp_bit-score`, ");
//		b.append("       a.rbh_status, ");
//		b.append("       a.cdd_count, ");
//		b.append("       a.`xml_Hsp_bit-score` / e.max_bit_score AS `percent_sim`, ");
//		b.append("       a.susceptibility, ");
//		b.append("       UNIX_TIMESTAMP(e.completion_date) as `completion`, ");
//		b.append("       IF(a.xml_Hit_def = '', 1,0) as `is_duplicate`,  ");
//		b.append("       b.is_endangered, ");
//		b.append("       b.is_model, ");
//		b.append("       b.is_ecotox, ");
//		b.append("       g.uniprot_accession ");
//		b.append("  FROM accession_hit a ");
//		b.append("       JOIN taxonomy_node b ON b.taxid = a.hit_taxid ");
//		b.append("       JOIN taxonomy_name c c.taxid = a.hit_taxid ");
//		b.append("       JOIN accession_run e a.accession_run_id = e.id ");
//		b.append("       JOIN protein f f.accession_id = a.hit_accession_id ");
//		b.append("       LEFT JOIN uniprot_ids g ON g.ncbi_accession = a.hit_accession_id ");
//		b.append(" WHERE a.accession_run_id = ? ");
//		b.append("   AND a.rps_status = 'finished' ");
//		b.append("   AND b.taxid != ? ");
//		b.append("   AND a.hit_accession_id != ? "); // SKIP THE TOP ROW
//		b.append("   AND c.name_class = 'scientific name' ");
//		b.append("   ORDER BY `percent_sim` DESC ");
		// b.append(" LIMIT 20000 ");
		b.append("		SELECT DISTINCT  ");
		b.append("		       a.hit_accession_id,  ");
		b.append("		       b.protein_count,  ");
		b.append("		       b.taxid,  ");
		b.append("		       c.name,  ");
		b.append("		       IS_eukaryote(b.taxid) AS is_eukaryote, ");
		b.append("		       b.seqapass_name,  ");
		b.append("		       f.title,  ");
		b.append("		       a.near_class_taxid,  ");
		b.append("		       a.`xml_Hit_len`,  ");
		b.append("		       a.`xml_Hsp_identity`,  ");
		b.append("		       a.`xml_Hsp_positive`,  ");
		b.append("		       a.`xml_Hsp_evalue`,  ");
		b.append("		       a.`xml_Hsp_bit-score`,  ");
		b.append("		       a.rbh_status,  ");
		b.append("		       a.cdd_count,  ");
		b.append("		       a.`xml_Hsp_bit-score` / e.max_bit_score AS `percent_sim`,  ");
		b.append("		       a.susceptibility,  ");
		b.append("		       UNIX_TIMESTAMP(e.completion_date) as `completion`,  ");
		b.append("		       IF(a.xml_Hit_def = '', 1,0) as `is_duplicate`,   ");
		b.append("		       b.is_endangered,  ");
		b.append("		       b.is_model, ");
		b.append("		       b.is_ecotox ");
		b.append("		  FROM accession_hit a  ");
		b.append("		       JOIN taxonomy_node b ON b.taxid = a.hit_taxid   ");
		b.append("		       JOIN taxonomy_name c ON c.taxid = a.hit_taxid  ");
		b.append("		       JOIN accession_run e ON a.accession_run_id = e.id ");
		b.append("		       JOIN protein f ON f.accession_id = a.hit_accession_id  ");
		b.append("		 WHERE a.accession_run_id = ?  ");
		b.append("		   AND a.rps_status = 'finished'   ");
		b.append("		   AND b.taxid != ? ");
		b.append("		   AND a.hit_accession_id != ? ");
		b.append("		   AND c.name_class = 'scientific name'  ");
		b.append("		   ORDER BY `percent_sim` DESC  ");

		// top_hit is chosen according to this order: ORDER BY
		// `xml_Hsp_identity` DESC, `xml_Hsp_bit-score` DESC, xml_Hsp_evalue
		// ASC,
		// xml_Hit_len DESC, hit_accession_id ASC
		
		logger.info("accessionRunId = {}", accessionRunId);
		logger.info("topHitTaxid = {}", topHitTaxid);
		logger.info("topHitAccessionString = {}", topHitAccessionString);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, topHitTaxid,
				topHitAccessionString);
		if (rows.size() == 0) {
			return results;
		}
		
		rows.add(0, row); // Put first row at top of list
		// System.out.println("Preparing " + rows.size() + " LevelOneReport rows...");
		logger.info("Preparing {} LevelOneReport rows...", rows.size());
		System.out.println("Preparing {} LevelOneReport rows..." + rows.size());
		for (int i = 0; i < rows.size(); i++) {
			row = rows.get(i);
			String accession = (String) row.get("hit_accession_id");
			int proteinCount = (int) row.get("protein_count");
			int taxId = (int) row.get("taxid");
			// Map<String, Integer> taxonomyTaxidMap =
			// getClassOrLowerTaxidFromSpeciesTaxid(taxId);

			boolean isEukaryote = (boolean) row.get("is_eukaryote");

			// Integer taxonomyTaxid = 1;
			int taxonomyTaxid = (int) row.get("near_class_taxid");

			// if (ancestorTaxid != taxId) {
			// taxonomyTaxid = ancestorTaxid;
			// }
			String taxonomyName = getScientificNameFromTaxid(taxonomyTaxid);
			String scientificName = (String) row.get("name");
			String seqapassName = (String) row.get("seqapass_name");
			if (seqapassName != null) {
				if (seqapassName.equalsIgnoreCase("primer")) {
					seqapassName = "other sequences";
				}
			} else {
				seqapassName = "Not Found";
			}
			// capitalize first letter in seqapassName
			seqapassName = seqapassName.substring(0, 1).toUpperCase() + seqapassName.substring(1);
			// String proteinName = (String) row.get("title");
			String proteinNameWSpecies = (String) row.get("title");
			// System.out.println("proteinNameWSpecies = " +
			// proteinNameWSpecies);
			String proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			// System.out.println("proteinName = " + proteinName);
			int hitLength = (int) row.get("xml_Hit_len");
			int identity = (int) row.get("xml_Hsp_identity");
			int positives = (int) row.get("xml_Hsp_positive");
			double evalue = (double) row.get("xml_Hsp_evalue");
			double blastPBitScore = (double) row.get("xml_Hsp_bit-score");
			String ortholog = (String) row.get("rbh_status");
			// Float cutoff = (Float) row.get("susceptibility_cutoff"); // NULL?
			// if (cutoff == null) {
			// cutoff = (float) 0.0;
			// }
			Integer commonDomainCount = (Integer) row.get("cdd_count");
			double percentSimilarity = (double) row.get("percent_sim");
			Long endDate = (Long) row.get("completion");// SHOULD NOT BE NULL
			int isDup = (int) row.get("is_duplicate");

			Integer endangeredFlag = (Integer) row.get("is_endangered");
			Integer modelFlag = (Integer) row.get("is_model");
			Integer ecoToxFlag = (Integer) row.get("is_ecotox");
			

			LevelOneReportRow levelOneReportRow = new LevelOneReportRow();
			levelOneReportRow.setAccession(accession);
			levelOneReportRow.setProteinCount(proteinCount);
			levelOneReportRow.setSpeciesTaxId(taxId);
			levelOneReportRow.setEukaryote(isEukaryote);
			// levelOneReportRow.setTaxonomyLevel(taxonomyLevel);
			levelOneReportRow.setTaxonomyTaxid(taxonomyTaxid);
			levelOneReportRow.setScientificName(scientificName);
			if (taxonomyName != null) {
				if (taxonomyName.equalsIgnoreCase("root")) {
					levelOneReportRow.setTaxonomyName(scientificName);
				} else {
					levelOneReportRow.setTaxonomyName(taxonomyName);
				}
			} else {
				levelOneReportRow.setTaxonomyName("Not Found");
			}
			levelOneReportRow.setDefaultTaxonomyName(levelOneReportRow.getTaxonomyName());
			levelOneReportRow.setCommonName(seqapassName);
			levelOneReportRow.setProteinName(proteinName);
			levelOneReportRow.setHitLength(hitLength);
			levelOneReportRow.setIdentity(identity);
			levelOneReportRow.setPositives(positives);
			levelOneReportRow.setEvalue(evalue);
			levelOneReportRow.setBlastPBitScore(blastPBitScore);
			levelOneReportRow.setOrtholog(ortholog);
			levelOneReportRow.setCutoff(0);
			levelOneReportRow.setUpdateVersion(reportInfo.getUpdateVersion());
			if (commonDomainCount == null) {
				levelOneReportRow.setCommonDomainCount(0);
			} else {
				levelOneReportRow.setCommonDomainCount(commonDomainCount);
			}
			levelOneReportRow.setPercentSimilarity(percentSimilarity);
			if (endDate == null) {
				endDate = new Long(0);
			}
			if (endDate != null) { // MAY BE NULL IN INITIAL TESTING
				levelOneReportRow.setEndDate(endDate);
			}
			levelOneReportRow.setIsDup(isDup);

			if (endangeredFlag != null) {
				if (endangeredFlag == 1 || endangeredFlag == 3) {
					levelOneReportRow.setEndangered(true);
				}
				if (endangeredFlag == 2 || endangeredFlag == 3) {
					levelOneReportRow.setThreatened(true);
				}
			}
			if (modelFlag != null && modelFlag == 1) {
				levelOneReportRow.setModel(true);
			}
			if (ecoToxFlag != null && ecoToxFlag == 1) {
				levelOneReportRow.setEcotox(true);
			}
			levelOneReportRow.setUniprotAcc(new ArrayList<String>());
			results.add(levelOneReportRow);
		}
		
//		//map uniprot accs
//		
//		MapSqlParameterSource thing;
//		
//		//get list of all L1 accessions
//		List<String> accs = results.stream().map(LevelOneReportRow::getAccession).collect(Collectors.toList());
//		System.out.println("accs.size() = " + accs.size());
//		for (int i=0; i<accs.size(); i++) {
//			System.out.println("i:" + i + ", " + accs.get(i));
//		}
//			
//		b = new StringBuilder();
//		
//		b.append("SELECT * FROM uniprot_ids WHERE ncbi_accession IN (:accs)");
//		NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
//		List<Map<String, Object>> uniprotMap = new ArrayList<Map<String, Object>>();
//
//		int batchSize = 500;
//		List<List<String>> batch = Lists.partition(accs, batchSize);
//		
//		for (List<String> list : batch) {
//			SqlParameterSource params = new MapSqlParameterSource("accs", list);
//			List<Map<String, Object>> tmpMap = namedJdbcTemplate.queryForList(b.toString(), params);
//			uniprotMap.addAll(tmpMap);
//		}
//		
//		
//		System.out.println("after query for list");
//		System.out.println("With size: " + uniprotMap.size());
//		
//		Map<String, List<String>> finalMap = new HashMap<String, List<String>>();
//		for (Map<String, Object> entry: uniprotMap) {
//
//			String ncbiAcc = (String)entry.get("ncbi_accession");
//			String uniprotAcc = (String)entry.get("uniprot_accession");
//			if (finalMap.containsKey(ncbiAcc)) {
//				finalMap.get(ncbiAcc).add(uniprotAcc);
//			} else {
//				List<String> newList = new ArrayList<String>();
//				newList.add(uniprotAcc);
//				finalMap.put(ncbiAcc, newList);
//			}
//			
//		}
//		System.out.println("after finalMap");
//		//add list of uniprot to each L1 row
//		
//		for (LevelOneReportRow row1: results) {
//			if (finalMap.get(row1.getAccession()) != null) {
//				row1.setUniprotAcc(finalMap.get(row1.getAccession()));
//			}
//		}
		
		
		return results;
	}

	private String getScientificNameFromTaxid(int taxid) {
		if (taxid < 0) {
			return null;
		}
		String query = "SELECT name FROM taxonomy_name WHERE taxid = ? AND name_class = 'scientific name'";
		String name;
		try {
			name = jdbcTemplate.queryForObject(query, String.class, taxid);
		} catch (DataAccessException e) {
			return null;
		}
		// capitalize first letter in scientific name
		name = name.substring(0, 1).toUpperCase() + name.substring(1);
		return name;

	}

	// private String getClassOrLowerScientificName(int speciesTaxid) {
	// if (speciesTaxid < 0) {
	// return null;
	// }
	// Map<String, Integer> levelTaxid =
	// getClassOrLowerTaxidFromSpeciesTaxid(speciesTaxid);
	// if (levelTaxid == null) {
	// return null;
	// }
	// int taxid = levelTaxid.values().iterator().next();
	// return getScientificNameFromTaxid(taxid);
	// }

	/**
	 * 
	 * @param targetRank
	 *            - The rank at which the ancestor taxid is sought
	 * @param taxid
	 *            - The taxid to for which the ancestor is sought
	 * @return taxid of targetRank or next level (that is not 'no rank' lower).
	 *         If none, then the input taxid is returned.
	 */
	private int getRankOrLowerFromTargetRankAndTaxid(String targetRank, int taxid) {
		return (int) jdbcTemplate.queryForObject("SELECT TAXID_at_rank_for_taxid(?,?);", int.class, targetRank, taxid);
	}

	// private Map<String, Integer> getClassOrLowerTaxidFromSpeciesTaxid(int
	// speciesTaxid) {
	// List<String> taxonmyLevels = new ArrayList<String>();
	// taxonmyLevels.add("superkingdom");
	// taxonmyLevels.add("kingdom");
	// taxonmyLevels.add("subkingdom");
	// taxonmyLevels.add("superphylum");
	// taxonmyLevels.add("phylum");
	// taxonmyLevels.add("subphylum");
	// taxonmyLevels.add("superclass");
	// taxonmyLevels.add("class");
	// taxonmyLevels.add("subclass");
	// taxonmyLevels.add("infraclass");
	// taxonmyLevels.add("superorder");
	// taxonmyLevels.add("order");
	// taxonmyLevels.add("suborder");
	// taxonmyLevels.add("infraorder");
	// taxonmyLevels.add("parvorder");
	// taxonmyLevels.add("superfamily");
	// taxonmyLevels.add("family");
	// taxonmyLevels.add("subfamily");
	// taxonmyLevels.add("tribe");
	// taxonmyLevels.add("subtribe");
	// taxonmyLevels.add("genus");
	// taxonmyLevels.add("subgenus");
	// taxonmyLevels.add("species group");
	// taxonmyLevels.add("species subgroup");
	// taxonmyLevels.add("species");
	// taxonmyLevels.add("subspecies");
	// taxonmyLevels.add("varietas");
	// taxonmyLevels.add("forma");
	//
	// String taxLevelOfTaxid = "no rank";
	// int parentTaxId = -1;
	// while (taxLevelOfTaxid.equals("no rank")) {
	// List<Map<String, Object>> rankPlusParentList = jdbcTemplate.queryForList(
	// "SELECT rank, parent_tax_id FROM taxonomy_node WHERE taxid = ? LIMIT 1",
	// speciesTaxid);
	// Map<String, Object> rankPlusParent = rankPlusParentList.get(0);
	// taxLevelOfTaxid = (String) rankPlusParent.get("rank");
	// parentTaxId = (int) rankPlusParent.get("parent_tax_id");
	// if (taxLevelOfTaxid.equals("no rank")) {
	// // System.out.println("Can't work with 'no rank', so moving up to parent
	// taxid `" + parentTaxId);
	//
	// speciesTaxid = parentTaxId;
	// }
	// }
	//
	// Map<String, Integer> result = new HashMap<String, Integer>();
	// String query = "SELECT * FROM taxonomy_tree WHERE `" + taxLevelOfTaxid +
	// "` = ? ";
	// // System.out.println("Trying to find a `" + taxLevelOfTaxid + "` of " +
	// speciesTaxid);
	// List<Map<String, Object>> rows = jdbcTemplate.queryForList(query,
	// speciesTaxid);
	// if (rows.size() == 0) {
	// // System.out.println("Got no rows. Query is: " + query + "\n with
	// taxLevelOfTaxid = " + taxLevelOfTaxid +
	// // " and speciesTaxid = "
	// // + speciesTaxid);
	//
	// return null;
	// }
	// Map<String, Object> row = rows.get(0);
	// for (int i = 7; i < taxonmyLevels.size(); i++) {
	// String taxLevel = taxonmyLevels.get(i);
	// Integer taxid = (Integer) row.get(taxLevel);
	// // System.out.println("Try: " + i + " ...");
	//
	// if (taxid == null) {
	// continue;
	// }
	// // System.out.println("Try: " + i + " where taxLevel = " + taxLevel);
	// result.put(taxLevel, taxid);
	// return result;
	// }
	// return null;
	// }

	@Override
	public List<LevelTwoReportRow> getLevelTwoReport(int accessionRunId, int lev2Id) {
		ReportInfo reportInfo = getReportInfo(accessionRunId, lev2Id, -1, -1);

		List<LevelTwoReportRow> results = new ArrayList<LevelTwoReportRow>();
		String topHitAccessionString = null;
		try {
			topHitAccessionString = jdbcTemplate.queryForObject(
					"SELECT top_hit_accession_id FROM accession_run WHERE id = ? LIMIT 1", String.class,
					accessionRunId);
		} catch (DataAccessException e1) {
			//System.out.println("In: getLevelTwoReport - could not find top_hit_accession_id from accession_run with id = "
			//				+ accessionRunId);
			logger.error("In: getLevelTwoReport - could not find top_hit_accession_id from accession_run with id = {}",
					accessionRunId);
			return results;
		}

		if (topHitAccessionString == null || topHitAccessionString.equals("")) {
			//System.out.println("In: getLevelTwoReport - top_hit_accession_id null or blank for accession_run with id: "
			//		+ accessionRunId);
			logger.error("In: getLevelTwoReport - top_hit_accession_id null or blank for accession_run with id: {}",
					accessionRunId);
			return results;
		}

		// System.out.println("Level 2 - topHitAccessionString " + topHitAccessionString);
		logger.info("Level 2 - topHitAccessionString {}", topHitAccessionString);
// First get the query accession row - to put at the top
		StringBuilder b = new StringBuilder();

		b.append("SELECT DISTINCT  ");
		b.append("       g.hit_accession_id,  ");
		b.append("       b.protein_count,  ");
		b.append("       b.taxid,  ");
		b.append("       c.name,  ");
		// b.append(" SEQAPASS_name(b.taxid) AS seqapass_name, ");
		b.append("       IS_eukaryote(b.taxid) AS is_eukaryote,");
		b.append("       b.seqapass_name, ");
		b.append("       f.title,  ");
		b.append("       d.cdd_accession_num AS `pssm_id`, ");
		b.append("       d.cdd_accession_num, ");
		b.append("       g.xml_Hit_len As `hit_length`, ");
		b.append("       g.xml_Hsp_identity As `identity`, ");
		b.append("       g.xml_Hsp_positive As `positive`, ");
		b.append("       g.xml_Hsp_evalue As `evalue`, ");
		b.append("       g.`xml_Hsp_bit-score`,  ");
		b.append("       g.xml_Hsp_hseq As `FASTA`, ");
		b.append("       a.rbh_status,  ");
		b.append("       a.cdd_count,  ");
		b.append("       g.`xml_Hsp_bit-score` / d.max_bit_score AS `percent_sim`,  ");
		b.append("       UNIX_TIMESTAMP(d.end) as `completion`, ");
		b.append("       0 as `is_duplicate`,  "); // Top row is never duplicate
		b.append("       b.is_endangered, ");
		b.append("       b.is_model, ");
		b.append("       b.is_ecotox ");
		b.append("  FROM accession_hit a,  ");
		b.append("       taxonomy_node b,  ");
		b.append("       taxonomy_name c,  ");
		b.append("       level2_run d,  ");
//		b.append("       accession_run e,  ");
		b.append("       protein f, ");
		b.append("       level2_result g ");
		b.append(" WHERE a.accession_run_id = ?  ");
//		b.append("   AND e.id = ?  ");
		b.append("   AND g.level2_run_id = ? ");
		b.append("   AND d.id = ? ");
		b.append("   AND a.hit_accession_id = ? ");
		b.append("   AND g.hit_accession_id = ? ");
		b.append("   AND a.hit_taxid = b.taxid ");
		b.append("   AND a.hit_taxid = c.taxid ");
		b.append("   AND g.xml_Hsp_num = 1 "); // Others will be in the table,
												// but should not be shown
		b.append("   AND f.accession_id = a.hit_accession_id  ");
		b.append("   AND c.name_class = 'scientific name'  ");
		b.append("   AND a.rps_status = 'finished'  ");
		b.append("   ORDER BY a.id ASC, `percent_sim` DESC LIMIT 1");
		// System.out.println("Level 2 first report query:\n" + b.toString());
		logger.info("Level 2 first report query:\n {}", b.toString());

//		List<Map<String, Object>> rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, accessionRunId, lev2Id,
//				lev2Id, topHitAccessionString, topHitAccessionString);
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, lev2Id,
						lev2Id, topHitAccessionString, topHitAccessionString);
		// List<LevelTwoReportRow> results = new
		// LinkedList<LevelTwoReportRow>();
		if (rows.size() == 0) {
			return results;
		}

		b = new StringBuilder();
		b.append("SELECT DISTINCT  ");
		b.append("       a.hit_accession_id,  ");
		b.append("       b.protein_count,  ");
		b.append("       b.taxid,  ");
		b.append("       c.name,  ");
		// b.append(" SEQAPASS_name(b.taxid) AS seqapass_name, ");
		b.append("       IS_eukaryote(b.taxid) AS is_eukaryote,");
		b.append("       b.seqapass_name, ");
		b.append("       f.title,  ");
		b.append("       d.cdd_accession_num AS `pssm_id`, ");
		b.append("       d.cdd_accession_num, ");
		b.append("       g.xml_Hit_len As `hit_length`, ");
		b.append("       g.xml_Hsp_identity As `identity`, ");
		b.append("       g.xml_Hsp_positive As `positive`, ");
		b.append("       g.xml_Hsp_evalue As `evalue`, ");
		b.append("       g.`xml_Hsp_bit-score`,  ");
		b.append("       g.xml_Hsp_hseq As `FASTA`, ");
		b.append("       a.rbh_status,  ");
		b.append("       a.cdd_count,  ");
		b.append("       g.`xml_Hsp_bit-score` / d.max_bit_score AS `percent_sim`,  ");
		b.append("       UNIX_TIMESTAMP(d.end) as `completion`,  ");
//		b.append("       IF(a.xml_Hit_def = '', 1,0) as `is_duplicate`,  ");
		b.append("       IF(a.hit_accession_id = a.hit_canonical_id, 0,1) as `is_duplicate`,  ");
		b.append("       b.is_endangered, ");
		b.append("       b.is_model, ");
		b.append("       b.is_ecotox ");
		b.append("  FROM accession_hit a,  ");
		b.append("       taxonomy_node b,  ");
		b.append("       taxonomy_name c,  ");
		b.append("       level2_run d,  ");
//		b.append("       accession_run e,  ");
		b.append("       protein f, ");
		b.append("       level2_result g ");
		b.append(" WHERE a.accession_run_id = ?  ");
//		b.append("   AND e.id = ?  ");
		b.append("   AND g.level2_run_id = ? ");
		b.append("   AND d.id = ? ");
		b.append("   AND a.hit_canonical_id = g.xml_Hit_id ");
		b.append("   AND a.hit_accession_id != ? ");
//		b.append("   AND g.hit_accession_id != ? ");
		b.append("   AND g.xml_Hsp_num = 1 "); // Others will be in the table,
												// but should not be shown
		b.append("   AND a.hit_taxid = b.taxid ");
		b.append("   AND a.hit_taxid = c.taxid ");
		b.append("   AND f.accession_id = a.hit_accession_id  ");
		b.append("   AND c.name_class = 'scientific name'  ");
		b.append("   AND a.rps_status = 'finished'  ");
//		b.append("   ORDER BY `percent_sim` DESC");
		b.append("   ORDER BY `percent_sim` DESC, a.id ASC");

		// System.out.println("Level 2 report query:\n" + b.toString());
		logger.info("Level 2 report query:\n {}", b.toString());
//		rows.addAll(jdbcTemplate.queryForList(b.toString(), accessionRunId, accessionRunId, lev2Id, lev2Id,
//				topHitAccessionString, topHitAccessionString));
		rows.addAll(jdbcTemplate.queryForList(b.toString(), accessionRunId, lev2Id, lev2Id,
				topHitAccessionString));
		if (rows.size() == 0) {
			return results;
		}

		// Map<String, Integer> accIdIndex = new HashMap<String, Integer>();
		for (int i = 0; i < rows.size(); i++) {
			// System.out.println("Preparing row: " + i);
			Map<String, Object> row = rows.get(i);
			String accession = (String) row.get("hit_accession_id");
			int proteinCount = (int) row.get("protein_count");
			int taxId = (int) row.get("taxid");
			// Map<String, Integer> taxonomyTaxidMap =
			// getClassOrLowerTaxidFromSpeciesTaxid(taxId);

			boolean isEukaryote = (boolean) row.get("is_eukaryote");

			int ancestorTaxid = getRankOrLowerFromTargetRankAndTaxid("class", taxId);
			String scientificName = (String) row.get("name");
			Integer taxonomyTaxid = 1;
			String taxonomyName = null;
			if (ancestorTaxid != taxId) {
				// taxonomyLevel = taxonomyTaxidMap.keySet().iterator().next();
				taxonomyTaxid = ancestorTaxid;
				taxonomyName = getScientificNameFromTaxid(taxonomyTaxid);
			}

			if (taxonomyName != null) {
				if (taxonomyName.equalsIgnoreCase("root")) {
					taxonomyName = scientificName;
				}
			} else {
				taxonomyName = "Not Found";
			}

			String seqapassName = (String) row.get("seqapass_name");
			if (seqapassName != null) {
				if (seqapassName.equalsIgnoreCase("primer")) {
					seqapassName = "other sequences";
				}
			} else {
				seqapassName = "Not Found";
			}
			// capitalize first letter in seqapassName
			seqapassName = seqapassName.substring(0, 1).toUpperCase() + seqapassName.substring(1);
			// String proteinName = (String) row.get("title");
			String proteinNameWSpecies = (String) row.get("title");
			// System.out.println("proteinNameWSpecies = " +
			// proteinNameWSpecies);
			String proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			// System.out.println("proteinName = " + proteinName);
			String pssmId = row.get("pssm_id").toString();
			int hitLength = (int) row.get("hit_length");
			int identity = (int) row.get("identity");
			int positive = (int) row.get("positive");
			double evalue = (double) row.get("evalue");
			double blastPBitScore = (double) row.get("xml_Hsp_bit-score");
			String ortholog = (String) row.get("rbh_status");
			double percentSimilarity = (double) row.get("percent_sim");
			Long endDate = (Long) row.get("completion");// SHOULD NOT BE NULL
			int updateVersion = reportInfo.getUpdateVersion();
			int isDup = (int) row.get("is_duplicate");

			Integer endangeredFlag = (Integer) row.get("is_endangered");
			Integer modelFlag = (Integer) row.get("is_model");
			Integer ecoToxFlag = (Integer) row.get("is_ecotox");
			boolean endangered = false;
			boolean threatened = false;
			boolean model = false;
			boolean ecotox = false;
			if (endangeredFlag != null) {
				if (endangeredFlag == 1 || endangeredFlag == 3) {
					endangered = true;
				}
				if (endangeredFlag == 2 || endangeredFlag == 3) {
					threatened = true;
				}
			}
			if (modelFlag != null && modelFlag == 1) {
				model = true;
			}
			if (ecoToxFlag != null && ecoToxFlag == 1) {
				ecotox = true;
			}
			
			String fasta = (String) row.get("FASTA");

			LevelTwoReportRow newRow = new LevelTwoReportRow(accession, proteinCount, taxId, taxonomyName, null,
					taxonomyTaxid, scientificName, seqapassName, "", proteinName, pssmId, "TODO", "TODO", hitLength,
					identity, positive, evalue, blastPBitScore, ortholog, percentSimilarity, -1, "TODO", endDate,
					updateVersion, isDup, isEukaryote, endangered, threatened, model, ecotox, fasta);

			if (endDate == null) {
				endDate = new Long(0);
			}
			results.add(newRow);

		}
		return results;
	}

	@Override
	public List<LevelTwoStatusRow> getLevelTwoStatusForUser(int userId) {
		StringBuilder b = new StringBuilder();
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		String dbName = jdbcTemplate.queryForObject(GetDBQuery, String.class);
		String domain = backEnd.getDomainName();

		b.append("SELECT DISTINCT v.update_version, ");
		b.append("               tt.lev2id,");
		b.append("               tt.email,");
		b.append("               tt.query_accession_id,");
		b.append("               tt.top_hit_accession_id,");
		b.append("               tt.domain_type,");
		b.append("               tt.status,");
		b.append("               tt.start,");
		b.append("               tt.end,");
		b.append("               tt.`SeqAPASS Run Duration`");
		b.append("              FROM");
		b.append("              (SELECT");
		b.append("                            c.id AS `lev2id`,");
		b.append("                            f.email,");
		b.append("                            a.query_accession_id,");
		b.append("                            d.top_hit_accession_id,");
		b.append(
				"                            SUBSTRING_INDEX(SUBSTRING_INDEX(e.full_definition, ',', 2), ',', -1) AS `domain_type`,");
		b.append("                            c.status,");
		b.append("                            UNIX_TIMESTAMP(c.start) AS `start`,");
		b.append("                            IF (c.status = 'complete', UNIX_TIMESTAMP(c.end) , NULL) AS `end`,");
		b.append(
				"                            IF (c.status = 'complete', UNIX_TIMESTAMP(c.end) - UNIX_TIMESTAMP(c.start), NULL) AS `SeqAPASS Run Duration`,");
		b.append("                            d.ncbi_version_id");
		b.append("                 FROM user_run_accession_run a");
		b.append("                 JOIN user_run b");
		b.append("                      ON b.id = a.user_run_id");
		b.append("                 JOIN level2_run c");
		b.append("                      ON c.accession_run_id = a.accession_run_id");
		if (!isAdmin) {
			b.append("      AND c.user_id=? ");
		}
		b.append("                 JOIN accession_run d");
		b.append("                      ON d.id = c.accession_run_id");
		b.append("                 JOIN common_domain e");
		b.append("                      ON c.cdd_accession_num = e.cdd_id AND e.ncbi_version_id = d.ncbi_version_id");
		b.append("                 JOIN user f");
		b.append("                      ON c.user_id = f.id");
		b.append("                WHERE d.ncbi_version_id = e.ncbi_version_id");
		if (!isAdmin) {
			b.append(" AND b.user_id = ? ");
		}
		b.append("             GROUP BY lev2id, query_accession_id");
		b.append("             ORDER BY `lev2id` DESC) tt,");
		b.append("                      version v");
		b.append("                WHERE v.id = tt.ncbi_version_id");
		b.append("                ORDER BY `lev2id` DESC; ");

		// b.append(" SELECT * FROM ");
		// b.append(" (SELECT ");
		// b.append(" c.id AS `lev2id`, ");
		// b.append(" f.email, ");
		// b.append(" a.query_accession_id, ");
		// b.append(" d.top_hit_accession_id, ");
		// b.append(" SUBSTRING_INDEX(SUBSTRING_INDEX(e.full_definition, ',',
		// 2), ',', -1) AS `domain_type`, ");
		// b.append(" c.max_bit_score, ");
		// b.append(" c.status, ");
		// b.append(" UNIX_TIMESTAMP(c.start) AS `start`, ");
		// b.append(" IF (c.status = 'complete', UNIX_TIMESTAMP(c.end) , NULL)
		// AS `end`, ");
		// b.append(" IF (c.status = 'complete', UNIX_TIMESTAMP(c.end) -
		// UNIX_TIMESTAMP(c.start), NULL) AS `SeqAPASS Run Duration`, ");
		// b.append(" UNIX_TIMESTAMP(d.submitted_date) AS 'lev1Submit', ");
		// b.append(" d.ncbi_version_id ");
		// b.append(" FROM user_run_accession_run a ");
		// b.append(" JOIN user_run b ");
		// b.append(" ON b.id = a.user_run_id ");
		// b.append(" JOIN level2_run c ");
		// b.append(" ON c.accession_run_id = a.accession_run_id");
		// if (!isAdmin) {
		// b.append(" AND c.user_id=? ");
		// }
		// b.append(" JOIN accession_run d ");
		// b.append(" ON d.id = c.accession_run_id ");
		// b.append(" JOIN common_domain e ");
		// b.append(" ON c.cdd_accession_num = e.cdd_id AND e.ncbi_version_id =
		// d.ncbi_version_id");
		// b.append(" JOIN user f ");
		// b.append(" ON c.user_id = f.id");
		// b.append(" WHERE d.ncbi_version_id = e.ncbi_version_id");
		// if (!isAdmin) {
		// b.append(" AND b.user_id = ? ");
		// }
		// b.append(" GROUP BY lev2id, query_accession_id ");
		// b.append(" ORDER BY `lev2id` DESC) tt ");
		// b.append(" JOIN ");
		// b.append(" version g ON tt.lev1Submit >
		// UNIX_TIMESTAMP(g.install_date) ");
		// b.append(" AND g.backend_host = ? ");
		//// b.append(" AND g.id = tt.ncbi_version_id ");
		// b.append(" AND g.id = (SELECT ");
		// b.append(" MAX(g2.id) ");
		// b.append(" FROM ");
		// b.append(" version g2 ");
		// b.append(" LEFT JOIN ");
		// b.append(" version g3 ON g2.update_version = g3.update_version ");
		// b.append(" AND g2.backend_host = g3.backend_host ");
		// b.append(" AND g2.id < g3.id ");
		// b.append(" WHERE ");
		// b.append(" g2.backend_host = ? ");
		// b.append(" AND g3.id IS NULL ");
		// b.append(" AND UNIX_TIMESTAMP(g2.install_date) < tt.lev1Submit) ");
		//

		// if (isAdmin) {
		// return jdbcTemplate.query(b.toString(), new LevelTwoStatusMapper(),
		// domain, domain);
		// } else {
		// return jdbcTemplate.query(b.toString(), new LevelTwoStatusMapper(),
		// userId, userId, domain, domain);
		// }
		if (isAdmin) {
			return jdbcTemplate.query(b.toString(), new LevelTwoStatusMapper());
		} else {
			return jdbcTemplate.query(b.toString(), new LevelTwoStatusMapper(), userId, userId);
		}
	}

	@Override
	public List<LevelThreeStatusRow> getLevelThreeStatusForUser(int userId) {
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		String dbName = jdbcTemplate.queryForObject(GetDBQuery, String.class);
		String domain = backEnd.getDomainName();

		StringBuilder b = new StringBuilder();
		b.append("SELECT * FROM (");

		b.append(" SELECT ");

		b.append("     tt.id, tt.query_accession_id, tt.email, tt.job_name, tt.template_name, tt.status, ");
		b.append("     tt.`start`, ");
		b.append("     tt.`end`, ");
		b.append("     tt.`run_duration`, ");
		b.append("     v.update_version ");

		b.append(" FROM ");
		b.append(" (SELECT ");
		b.append("     a.id, d.query_accession_id, c.email, a.job_name, a.template_name, a.status, ");
		b.append("     UNIX_TIMESTAMP(a.start) AS `start`, ");
		// b.append(" UNIX_TIMESTAMP(a.end) AS `end`, ");
		b.append(" IF (a.status = 'complete', UNIX_TIMESTAMP(a.end), NULL) AS `end`, ");
		// b.append(" UNIX_TIMESTAMP(a.end)-UNIX_TIMESTAMP(a.start) AS
		// `run_duration`, ");
		b.append(" IF (a.status = 'complete', UNIX_TIMESTAMP(a.end)-UNIX_TIMESTAMP(a.start), NULL) AS `run_duration`, ");
		b.append("     UNIX_TIMESTAMP(b.submitted_date) AS `lev1Submit`, ");
		b.append("     b.ncbi_version_id ");
		b.append(" FROM ");
		b.append("     level3_run a ");
		b.append("         JOIN ");
		b.append("     accession_run b ON a.accession_run_id = b.id ");
		b.append("     JOIN user c ON c.id = a.user_id");
		b.append("     JOIN user_run_accession_run d ON d.accession_run_id = b.id");
		if (!isAdmin) {
			b.append(" JOIN user_run e ON e.user_id = ? ");
			b.append(" WHERE a.user_id = ? ");
			b.append("   AND e.id = d.user_run_id ");
		}
		// b.append(" GROUP BY query_accession_id, job_name"); // -- FIXME -
		// MAKE THIS CHANGE NEXT DEPLOYMENT TO STAGE
		b.append("     GROUP BY a.id, d.query_accession_id, a.job_name, a.id");

		b.append("   ORDER BY a.id DESC) tt,");

		b.append("                      version v");
		b.append("                WHERE v.id = tt.ncbi_version_id");
//		b.append("                ORDER BY tt.`id` DESC; ");
		
		b.append("                ORDER BY tt.`id` DESC, v.seqapass_version DESC ) big ");
		b.append("                GROUP BY big.id ");
		b.append("                ORDER BY big.id DESC; ");


		// b.append(" JOIN ");
		// b.append(" version g ON tt.lev1Submit >
		// UNIX_TIMESTAMP(g.install_date) ");
		// b.append(" AND g.backend_host = ? ");
		// b.append(" AND g.id = (SELECT ");
		// b.append(" MAX(g2.id) ");
		// b.append(" FROM ");
		// b.append(" version g2 ");
		// b.append(" LEFT JOIN ");
		// b.append(" version g3 ON g2.update_version = g3.update_version ");
		// b.append(" AND g2.backend_host = g3.backend_host ");
		// b.append(" AND g2.id < g3.id ");
		// b.append(" WHERE ");
		// b.append(" g2.backend_host = ? ");
		// b.append(" AND g3.id IS NULL ");
		// b.append(" AND UNIX_TIMESTAMP(g2.install_date) < tt.lev1Submit) ");
		//// b.append(" JOIN version f ON tt.lev1Submit >
		// UNIX_TIMESTAMP(f.install_date) ");
		//// b.append(" AND f.backend_host = ? ");
		//// b.append(" AND f.update_version = (SELECT MAX(update_version) FROM
		// version f2 WHERE ");
		//// b.append(" tt.lev1Submit > UNIX_TIMESTAMP(f2.install_date) ");
		//// b.append(" AND f2.backend_host = ?) ");

		if (isAdmin) {
			return jdbcTemplate.query(b.toString(), new LevelThreeStatusMapper());
		} else {
			return jdbcTemplate.query(b.toString(), new LevelThreeStatusMapper(), userId, userId);
		}

	}

	@Override
	public List<HistogramRow> getLevelOneCutoffHistogram(int accessionRunId, int binCount) {
		StringBuilder b = new StringBuilder();
		b.append("SELECT ( ? *FLOOR(z.sim/ ? )) AS bin, count(*) AS `count` FROM ");
		b.append("  (SELECT  a.`xml_Hsp_bit-score` / b.max_bit_score AS `sim` ");
		b.append("      FROM accession_hit a, ");
		b.append("           accession_run b ");
		b.append("     WHERE a.accession_run_id = ? ");
		b.append("       AND b.id = ? ");
		b.append("       AND a.cdd_count > 0 ) z ");
		b.append("GROUP BY bin; ");
		double binSize = 1.0 / binCount;
		return jdbcTemplate.query(b.toString(), new HistogramMapper(), binSize, binSize, accessionRunId,
				accessionRunId);
	}

	// @Override
	// public CutoffData getLevelOneCutoff(int accessionRunId) {
	//
	// StringBuilder b = new StringBuilder();
	// b.append("SELECT ");
	// b.append(" a.`xml_Hsp_bit-score` / b.max_bit_score AS `sim`, ");
	// b.append(" a.rbh_status ");
	// b.append("FROM ");
	// b.append(" accession_hit a, ");
	// b.append(" accession_run b ");
	// b.append("WHERE ");
	// b.append(" a.accession_run_id = ? ");
	// b.append(" AND b.id = ? ");
	// b.append(" AND a.cdd_count > 0 ");
	// List<DensityRow> densityData = jdbcTemplate.query(b.toString(), new
	// DensityMapper(), accessionRunId, accessionRunId);
	// List<Double> simData = new ArrayList<Double>();
	// List<String> orthoData = new ArrayList<String>();
	//
	// for (DensityRow row : densityData) {
	// simData.add(row.getPercSim());
	// orthoData.add(row.getOrtholog());
	// }
	//
	// // returns lists where 1) density x values 2) density y values 3) cutoff
	// values
	// return RCutoff.calcDensity(rService, jdbcTemplate, simData, orthoData,
	// accessionRunId);
	// }

	// @Override
	// public CutoffData getLevelOnePrimaryCutoff(int accessionRunId) {

	// StringBuilder b = new StringBuilder();
	// b.append("SELECT ");
	// b.append(" density_plot_object ");
	// b.append("FROM ");
	// b.append(" accession_run_density_plot ");
	// b.append("WHERE ");
	// b.append(" accession_run_id = ? ");
	// // List<DensityRow> densityData = jdbcTemplate.query(b.toString(), new
	// // DensityMapper(), accessionRunId);
	// byte[] serializedData = null;
	// try {
	// serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class,
	// accessionRunId);
	// } catch (EmptyResultDataAccessException e) {
	// return null;
	// }
	//
	// System.out.println("Checking if serializedData is null");
	// if (serializedData == null) {
	// System.out.println("returning null for cutoffData");
	// return null;
	// }
	// CutoffData cutoffData =
	// CutoffData cutoffData = (CutoffData)
	// SerializationUtils.deserialize(serializedData);
	// List<Double> simData = new ArrayList<Double>();
	// List<String> orthoData = new ArrayList<String>();

	// for (DensityRow row : densityData) {
	// simData.add(row.getPercSim());
	// orthoData.add(row.getOrtholog());
	// }

	// returns lists where 1) density x values 2) density y values 3) cutoff
	// values
	// return RCutoff.calcDensity(rService, jdbcTemplate, simData,
	// orthoData, accessionRunId);
	// return cutoffData;
	// }
	//
	// @Override
	// public CutoffData getLevelOneFullCutoff(int accessionRunId) {
	// StringBuilder b = new StringBuilder();
	// b.append("SELECT ");
	// b.append(" full_density_plot_object ");
	// b.append("FROM ");
	// b.append(" accession_run_density_plot ");
	// b.append("WHERE ");
	// b.append(" accession_run_id = ? ");
	// // List<DensityRow> densityData = jdbcTemplate.query(b.toString(), new
	// // DensityMapper(), accessionRunId);
	// byte[] serializedData = null;
	// try {
	// serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class,
	// accessionRunId);
	// } catch (EmptyResultDataAccessException e) {
	// return null;
	// }
	// CutoffData cutoffData = (CutoffData)
	// SerializationUtils.deserialize(serializedData);
	// // List<Double> simData = new ArrayList<Double>();
	// // List<String> orthoData = new ArrayList<String>();
	//
	// // for (DensityRow row : densityData) {
	// // simData.add(row.getPercSim());
	// // orthoData.add(row.getOrtholog());
	// // }
	//
	// // returns lists where 1) density x values 2) density y values 3) cutoff
	// // values
	// // return RCutoff.calcDensity(rService, jdbcTemplate, simData,
	// // orthoData, accessionRunId);
	// return cutoffData;
	// }
	//
	// @Override
	// public CutoffData getLevelTwoPrimaryCutoff(int domainRunId) {
	// StringBuilder b = new StringBuilder();
	// b.append("SELECT ");
	// b.append(" density_plot_object ");
	// b.append("FROM ");
	// b.append(" domain_run_density_plot ");
	// b.append("WHERE ");
	// b.append(" domain_run_id = ? ");
	// // List<DensityRow> densityData = jdbcTemplate.query(b.toString(), new
	// // DensityMapper(), accessionRunId);
	// byte[] serializedData = null;
	// try {
	// serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class,
	// domainRunId);
	// } catch (EmptyResultDataAccessException e) {
	// return null;
	// }
	// CutoffData cutoffData = (CutoffData)
	// SerializationUtils.deserialize(serializedData);
	//
	// return cutoffData;
	// }
	//
	// @Override
	// public CutoffData getLevelTwoFullCutoff(int domainRunId) {
	// StringBuilder b = new StringBuilder();
	// b.append("SELECT ");
	// b.append(" full_density_plot_object ");
	// b.append("FROM ");
	// b.append(" domain_run_density_plot ");
	// b.append("WHERE ");
	// b.append(" domain_run_id = ? ");
	// // List<DensityRow> densityData = jdbcTemplate.query(b.toString(), new
	// // DensityMapper(), accessionRunId);
	// byte[] serializedData = null;
	// try {
	// serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class,
	// domainRunId);
	// } catch (EmptyResultDataAccessException e) {
	// return null;
	// }
	// CutoffData cutoffData = (CutoffData)
	// SerializationUtils.deserialize(serializedData);
	//
	// return cutoffData;
	// }

//	@Override
//	public List<LevelTwoRequestableRow> getLevelTwoRequestables(String queryAccessionName, int userId) {
//		System.out.println("getLevelTwoRequestables got a queryAccessionName of: " + queryAccessionName);
//		if (queryAccessionName == null || queryAccessionName.equals("null")) {
//			System.out
//					.println("What's going on with the getLeveLTwoRequestables? We were given a queryAaccessionIdName: "
//							+ queryAccessionName + " and userId: " + userId);
//			List<LevelTwoRequestableRow> emptyResults = new ArrayList<LevelTwoRequestableRow>();
//			return emptyResults;
//		}
//		// String canonicalAccessionString = "";
//		Map<String, Object> twoAccessions = null;
//		try {
//			twoAccessions = jdbcTemplate.queryForMap(
//					"SELECT a.canonical_accession_id, a.top_hit_accession_id FROM accession_run a, user_run_accession_run b "
//							+ "WHERE b.query_accession_id = ? AND a.id = b.accession_run_id LIMIT 1",
//					queryAccessionName);
//		} catch (DataAccessException e) {
//			System.out.println(
//					"In getLevelTwoRequestables: failed to find canonical and top hit accession ids from query_accession_id: "
//							+ queryAccessionName);
//			List<LevelTwoRequestableRow> emptyResults = new ArrayList<LevelTwoRequestableRow>();
//			return emptyResults;
//		}
//		if (twoAccessions == null || twoAccessions.size() == 0) {
//			System.out.println(
//					"In getLevelTwoRequestables: got empty results for canonical and top hit accession id from query_accession_id: "
//							+ queryAccessionName);
//			List<LevelTwoRequestableRow> emptyResults = new ArrayList<LevelTwoRequestableRow>();
//			return emptyResults;
//		}
//		String canonicalAccessionString = (String) twoAccessions.get("canonical_accession_id");
//		String topHitAccessionString = (String) twoAccessions.get("top_hit_accession_id");
//
//		System.out.println("Canonical accession id is: " + canonicalAccessionString);
//		System.out.println("Top hit accession id is: " + topHitAccessionString);
//
//		// String admin = jdbcTemplate.queryForObject(CheckAdminQuery,
//		// String.class, userId);
//
//		// boolean isAdmin = false;
//		// if (admin.toLowerCase().equals("y"))
//		// isAdmin = true;
//		boolean isAdmin = isUserAdmin(userId);
//
//		StringBuilder b = new StringBuilder();
//		b.append("SELECT DISTINCT b.id AS `accession_run_id`, ");
//		b.append("                a.xml_Hit_accession, ");
//		b.append("                c.id AS `lev2id`, ");
//		b.append("                a.`xml_Hsp_query-from` AS `start_position`, ");
//		b.append("                b.canonical_accession_id, ");
//		b.append("                SUBSTRING_INDEX(a.xml_Hit_def, '.',1) AS `description`   ");
//		b.append("           FROM (accession_run b, ");
//		b.append("                rps_result a) ");
//		// CONSIDER JOBS THAT HAVE ALREADY BEEN RUN
//		b.append("                LEFT JOIN level2_run c  ");
//		b.append("                       ON a.xml_Hit_accession = c.cdd_accession_num ");
//		b.append("                      AND a.`xml_Hsp_query-from` = c.start_position");
//		b.append("                      AND b.id = c.accession_run_id AND c.end IS NOT NULL");
//		b.append("                      AND c.end IS NOT NULL");
//		// If NOT ADMIN, ONLY SHOW THE ONES THAT USER HAS RUN
//		if (!isAdmin) {
//			b.append("         AND c.user_id = ?");
//		}
//		// PUT CANONICAL BELOW
//		b.append("          WHERE b.canonical_accession_id = ? ");
//		// PUT TOP HIT BELOW
//		b.append("            AND a.accession_id = ? ");
//		b.append("            AND a.ncbi_version_id = b.ncbi_version_id  ");
//		if (isAdmin) {
//			b.append("     GROUP BY a.xml_Hit_accession, a.`xml_Hsp_query-from`");
//		}
//		b.append("          ORDER BY `description`, `start_position`; ");
////		System.out.println("Trying this query" + b.toString());
//
////		System.out.println("Domain Query: " + b.toString());
//
//		if (isAdmin) {
//			return jdbcTemplate.query(b.toString(), new LevelTwoDomainOptionMapper(), canonicalAccessionString,
//					topHitAccessionString);
//		} else {
//			return jdbcTemplate.query(b.toString(), new LevelTwoDomainOptionMapper(), userId, canonicalAccessionString,
//					topHitAccessionString);
//		}
//	}

	@Override
	public List<LevelTwoRequestableRow> getLevelTwoRequestablesNew(int accessionRunId, int userId) {
		if (accessionRunId == -1) {
			return new ArrayList<LevelTwoRequestableRow>();
		}
		// System.out.println("getLevelTwoRequestables got a accessionRunId of: " + accessionRunId);
		logger.info("getLevelTwoRequestables got a accessionRunId of: {}", accessionRunId);
		// if (queryAccessionName == null || queryAccessionName.equals("null"))
		// {
		// System.out.println("What's going on with the getLeveLTwoRequestables?
		// We were given a queryAaccessionIdName: "
		// + queryAccessionName + " and userId: " + userId);
		// List<LevelTwoRequestableRow> emptyResults = new
		// ArrayList<LevelTwoRequestableRow>();
		// return emptyResults;
		// }
		// String canonicalAccessionString = "";
		Map<String, Object> twoAccessions = null;
		String theTwoAccessionQuery= "SELECT a.canonical_accession_id, b.hit_canonical_id FROM accession_run a, accession_hit b WHERE a.id = ? AND b.accession_run_id = a.id AND b.hit_accession_id = a.top_hit_accession_id LIMIT 1";
		// System.out.println("Here's the new query: "+ theTwoAccessionQuery);
		logger.info("Here's the new query: {}", theTwoAccessionQuery);
		try {
			twoAccessions = jdbcTemplate.queryForMap(theTwoAccessionQuery, accessionRunId);
					
		} catch (DataAccessException e) {
			//System.out.println("In getLevelTwoRequestables: failed to find canonical and top hit accession ids from accession_run id: "
			//				+ accessionRunId);
			logger.error("In getLevelTwoRequestables: failed to find canonical and top hit accession ids from accession_run id: {}", 
					accessionRunId);
			List<LevelTwoRequestableRow> emptyResults = new ArrayList<LevelTwoRequestableRow>();
			return emptyResults;
		}
		if (twoAccessions == null || twoAccessions.size() == 0) {
			//System.out.println("In getLevelTwoRequestables: got empty results for canonical and top hit accession id from accession_run id: "
			//				+ accessionRunId);
			logger.error("In getLevelTwoRequestables: got empty results for canonical and top hit accession id from accession_run id: {}", 
					accessionRunId);
			List<LevelTwoRequestableRow> emptyResults = new ArrayList<LevelTwoRequestableRow>();
			return emptyResults;
		}
		String canonicalAccessionString = (String) twoAccessions.get("canonical_accession_id");
		String topHitCanonicalString = (String) twoAccessions.get("hit_canonical_id");

		// System.out.println("Canonical accession id is: " + canonicalAccessionString);
		// System.out.println("Top hit's canonical id is: " + topHitCanonicalString);
		
		logger.info("Canonical accession id is: {}", canonicalAccessionString);
		logger.info("Top hit's canonical id is: {}", topHitCanonicalString);

		// String admin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);

		// boolean isAdmin = false;
		// if (admin.toLowerCase().equals("y"))
		// isAdmin = true;
		boolean isAdmin = isUserAdmin(userId);

		StringBuilder b = new StringBuilder();
		b.append("SELECT DISTINCT b.id AS `accession_run_id`, ");
		b.append("                a.xml_Hit_accession, ");
		b.append("                c.id AS `lev2id`, ");
		b.append("                a.`xml_Hsp_query-from` AS `start_position`, ");
		b.append("                b.canonical_accession_id, ");
		b.append("                SUBSTRING_INDEX(a.xml_Hit_def, '.',1) AS `description`   ");
		b.append("           FROM (accession_run b, ");
		b.append("                rps_result a) ");
		// CONSIDER JOBS THAT HAVE ALREADY BEEN RUN
		b.append("                LEFT JOIN level2_run c  ");
		b.append("                       ON a.xml_Hit_accession = c.cdd_accession_num ");
		b.append("                      AND a.`xml_Hsp_query-from` = c.start_position");
		b.append("                      AND b.id = c.accession_run_id ");
		b.append("                      AND c.end IS NOT NULL");
		// If NOT ADMIN, ONLY SHOW THE ONES THAT USER HAS RUN
		if (!isAdmin) {
			b.append("         AND c.user_id = ?");
		}
		// PUT ID BELOW
		b.append("          WHERE b.id = ? ");
		// b.append(" WHERE b.accession_id = ? ");

		// PUT TOP HIT BELOW
		b.append("            AND a.accession_id = ? ");
		b.append("            AND a.ncbi_version_id = b.ncbi_version_id  ");
		if (isAdmin) {
			b.append("     GROUP BY a.xml_Hit_accession, a.`xml_Hsp_query-from`");
		}
		b.append("          ORDER BY `description`, `start_position`; ");

//		System.out.println("Domain Query: " + b.toString());

		if (isAdmin) {
			return jdbcTemplate.query(b.toString(), new LevelTwoDomainOptionMapper(), accessionRunId,
					topHitCanonicalString);
		} else {
			return jdbcTemplate.query(b.toString(), new LevelTwoDomainOptionMapper(), userId, accessionRunId,
					topHitCanonicalString);
		}
	}

	@Override
	public LevelTwoRequestableRow getLevelTwoInfoByRunId(int lev2Id) {

		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append(
				"     a.accession_run_id, c.top_hit_accession_id, a.cdd_accession_num, a.start_position, b.full_definition ");
		b.append(" FROM level2_run a ");
		b.append("         JOIN common_domain b ");
		b.append(" 		ON a.cdd_accession_num = b.cdd_id ");
		b.append("         JOIN accession_run c ");
		b.append("      ON c.id = a.accession_run_id AND c.ncbi_version_id = b.ncbi_version_id");
		b.append("         AND c.ncbi_version_id = b.ncbi_version_id ");
		b.append(" WHERE a.id = ? ");

		// jdbcTemplate.queryForList(b.toString(), lev2Id );
		Map<String, Object> row = jdbcTemplate.queryForMap(b.toString(), lev2Id);

		int accessionRunId = (int) row.get("accession_run_id");
		String accession = (String) row.get("top_hit_accession_id");
		int domainNumber = (int) row.get("cdd_accession_num");
		int startPosition = (int) row.get("start_position");
		String description = (String) row.get("full_definition");
		String displayText = "(" + startPosition + ") " + description;

		return new LevelTwoRequestableRow(accessionRunId, accession, domainNumber, null, startPosition, displayText,
				lev2Id);

		// return "(" + startPosition + ") " + description;
	}

	@Override
	public LevelThreeRequestableRow getLevelThreeInfoByRunId(int lev3Id) {

		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     accession_run_id, job_name, template_name ");
		b.append(" FROM level3_run ");
		b.append(" WHERE id = ? ");

		Map<String, Object> row = jdbcTemplate.queryForMap(b.toString(), lev3Id);

		int accessionRunId = (int) row.get("accession_run_id");
		String jobName = (String) row.get("job_name");
		String template = (String) row.get("template_name");

		return new LevelThreeRequestableRow(accessionRunId, -1, lev3Id, jobName, template, new ArrayList<String>(),
				null);
	}

	@Override
	public List<LevelThreeReportRow> getLevelThreeReport(LevelThreeViewRequest request) {

		int level3RunId = request.getLevelThreeRunId();
		// System.out.println("level 3 run id:" + level3RunId);
		logger.info("level 3 run id: {}", level3RunId);

		List<Integer> positionList = request.getPositionList();
		int numPos = 0;
		if (positionList != null) {
			numPos = positionList.size();
//			System.out.println("NumPos = " + numPos);
//			System.out.println(positionList.toString());
		}
		List<Integer> parameterList = new ArrayList<Integer>();

		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     a.job_name, ");
		b.append("     a.accession_run_id, ");
		b.append("     b.level1_bitscore, ");
		b.append("     b.sequence_def AS `ncbi_accession`, ");
		b.append("     UNIX_TIMESTAMP(a.end) AS `end` ");
		for (int i = 0; i < numPos; i++) {
			b.append("    , LENGTH( REPLACE (substr(b.seq,1,?), '-', '') ) AS `pos" + String.valueOf(i + 1) + "`,  ");
			b.append("     substr(b.seq,?,1) AS `aa" + String.valueOf(i + 1) + "`");
			parameterList.add(positionList.get(i));
			parameterList.add(positionList.get(i));
		}
		b.append(" FROM ");
		b.append("     level3_run a ");
		b.append("     JOIN level3_result b ON a.id = b.level3_run_id ");
		b.append(" WHERE ");
		b.append("     a.id = ? ");
		b.append("ORDER BY b.level1_bitscore DESC, b.id ASC ");

//		System.out.println("LevelThreeReport Query:" + b.toString());

		List<Map<String, Object>> rows;

		parameterList.add(level3RunId);
		rows = jdbcTemplate.queryForList(b.toString(), parameterList.toArray());

		ReportInfo reportInfo = getReportInfo((int) rows.get(0).get("accession_run_id"), -1, level3RunId, -1);
		int updateVersion = reportInfo.getUpdateVersion();

		List<AminoAcid> aminoAcidInfo = getAminoAcidInfo();

		List<LevelThreeReportRow> levelThreeReportList = new ArrayList<LevelThreeReportRow>();
		String jobName = "";
		if (rows.size() > 0) {
			jobName = (String) rows.get(0).get("job_name");
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String accession = (String) row.get("ncbi_accession");
			long endDate = (long) row.get("end");
			List<LevelThreeResidueResult> residues = new ArrayList<LevelThreeResidueResult>();
			for (int j = 0; j < numPos; j++) {
				String posHeader = "pos" + String.valueOf(j + 1);
				String aaHeader = "aa" + String.valueOf(j + 1);
				long pos = ((Integer) (row.get(posHeader))).longValue();
				int intPos = (int) pos;
				String acidId = (String) row.get(aaHeader);

				AminoAcid aminoAcid = findAminoAcidInfo(acidId, aminoAcidInfo);

				LevelThreeResidueResult newResidue = new LevelThreeResidueResult(intPos, aminoAcid, null, null, null);
				residues.add(newResidue);
			}
			LevelThreeReportRow newRow;
			if (i == 0 || (Double) row.get("level1_bitscore") == 1000000000) {

				//new code
				NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
			    int ncbiVersion = ncbiProvider.getUpdateVersion();
			    int taxId = 0;
			    String proteinNameWSpecies = null;
			    String proteinName = "-";
			    
//			    try {
//			    	Map<String, Object> data = jdbcTemplate.queryForMap(
//			            "SELECT taxid, title FROM protein WHERE accession_id = ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
//			            accession, ncbiVersion);
//			        taxId = (int) data.get("taxid");
//			        proteinNameWSpecies = (String) data.get("title");
//			        proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
//			      } catch (DataAccessException e) {
//			        // ONE MORE POSSIBILITY (STARTS WITH)
//			      
//			      try {
//			        String versionlessAccessionIdName = accession.replaceFirst("\\.\\d+$", "");
//			        Map<String, Object> data = jdbcTemplate.queryForMap(
//			            "SELECT taxid, title FROM protein WHERE accession_id LIKE ? AND FIND_IN_SET(?, ncbi_valid_versions) LIMIT 1",
//			             versionlessAccessionIdName + "%", ncbiVersion);
//			        taxId = (int) data.get("taxid");
//			        proteinNameWSpecies = (String) data.get("title");
//			        proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
//			      } catch (DataAccessException ex) {
//			    	  proteinName = "Not Found";
//			      }
//			      }
			      
			    Map<String, Object> dataAddComp = jdbcTemplate.queryForMap(
			            "SELECT taxid, title FROM level3_result WHERE sequence_def = ? AND level3_run_id = ?",
			            accession, level3RunId);
			        taxId = (int) dataAddComp.get("taxid");
			        proteinNameWSpecies = (String) dataAddComp.get("title");
			        proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			    
			    
//			      = (String) data.get("title");
//					proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			    
				//end new code
				
				StringBuilder b2 = new StringBuilder();
                b2.append(" SELECT a.protein_count, a.taxid, b.name, a.seqapass_name ");
                b2.append("   FROM taxonomy_node a, taxonomy_name b ");
                b2.append("  WHERE a.taxid = ? AND a.taxid = b.taxid AND b.name_class = 'scientific name' LIMIT 1");
                				
//				b2.append(" SELECT a.protein_count, a.taxid, b.name, a.seqapass_name, c.title ");
//				b2.append("   FROM taxonomy_node a, taxonomy_name b, ");
//				b2.append("   (SELECT taxid, title FROM protein WHERE accession_id LIKE ? ) c ");
//				b2.append(
//						"  WHERE c.taxid = a.taxid AND a.taxid = b.taxid AND b.name_class = 'scientific name' LIMIT 1");

				// b2.append(" SELECT a.protein_count, a.taxid, b.name,
				// a.seqapass_name, c.title ");
				// b2.append(" FROM taxonomy_node a, taxonomy_name b, protein c
				// ");
				// b2.append(" WHERE c.accession_id LIKE ? AND c.taxid = a.taxid
				// AND a.taxid = b.taxid AND b.name_class = 'scientific name'
				// LIMIT 1;");
				int proteinCount = 0;
				String taxonomyName = "-";
				String scientificName = "-";
				String seqapassName = "-";

				try {

					Map<String, Object> data = jdbcTemplate.queryForMap(b2.toString(), taxId );
					proteinCount = (int) data.get("protein_count");
//					taxId = (int) data.get("taxid");

					scientificName = (String) data.get("name");
					int ancestorTaxid = getRankOrLowerFromTargetRankAndTaxid("class", taxId);
					if (ancestorTaxid != taxId) {
						taxonomyName = getScientificNameFromTaxid(ancestorTaxid);
					}

					if (taxonomyName.equalsIgnoreCase("root")) {
						taxonomyName = scientificName;
					}

					seqapassName = (String) data.get("seqapass_name");
					if (seqapassName != null && seqapassName.equalsIgnoreCase("primer")) {
						seqapassName = "other sequences";
					}
//					String proteinNameWSpecies = (String) data.get("title");
//					proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
				} catch (DataAccessException e) {
					// THIS JUST MEANS THAT THERE WAS NO PROTEIN MATCHING THE
					// TEMPLATE NAME
				}
				// System.out.println("Level 3 data being packaged up: " +
				// accession + proteinCount + taxId + taxonomyName +
				// scientificName
				// + seqapassName + proteinName + endDate + residues);

				newRow = new LevelThreeReportRow(jobName, accession, proteinCount, taxId, taxonomyName, scientificName,
						seqapassName, proteinName, endDate, updateVersion, residues);
			} else {
				newRow = new LevelThreeReportRow(jobName, accession, 0, 0, "-", "-", "-", "-", endDate, updateVersion,
						residues);
			}
			levelThreeReportList.add(newRow);
		}

		// Assume 1st row is query accession (need to verify/install
		// contingencies)
		determineLevelThreeMatches(levelThreeReportList);

		determineLevelThreeSusceptibility(levelThreeReportList);

		// System.out.println("Finished backend getLevelThreeReport");
		logger.info("Finished backend getLevelThreeReport");

		return levelThreeReportList;

	}
	
	//Same as getLevelThreeReport except optimized for retrieving all residues
	@Override
	public List<LevelThreeReportRow> getLevelThreeReportComplete(LevelThreeViewRequest request) {

		int level3RunId = request.getLevelThreeRunId();
		// System.out.println("level 3 run id:" + level3RunId);
		logger.info("level 3 run id: {}", level3RunId);

		List<Integer> positionList = request.getPositionList();
		int numPos = 0;
		if (positionList != null) {
			numPos = positionList.size();
		}

		StringBuilder b = new StringBuilder();
		
		b.append(" SELECT ");
		b.append("     job_name, ");
		b.append("     accession_run_id, ");
		b.append("     UNIX_TIMESTAMP(end) AS `end` ");
		b.append(" FROM ");
		b.append("     level3_run ");
		b.append(" WHERE ");
		b.append("     id = ? ");
		
		Map<String, Object> runInfo = jdbcTemplate.queryForMap(b.toString(), level3RunId);
		String jobName = (String) runInfo.get("job_name");
		int accRunId = (int) runInfo.get("accession_run_id");
		long endDate = (long) runInfo.get("end");
		
		StringBuilder b1 = new StringBuilder();
		
		b1.append(" SELECT ");
		b1.append("     level1_bitscore, ");
		b1.append("     sequence_def AS `ncbi_accession`, ");
		b1.append("     seq");
		b1.append(" FROM ");
		b1.append("     level3_result ");
		b1.append(" WHERE ");
		b1.append("     level3_run_id = ? ");
		b1.append("ORDER BY level1_bitscore DESC, id ASC ");
		
			
		List<Map<String, Object>> rows;

		rows = jdbcTemplate.queryForList(b1.toString(), level3RunId);

		ReportInfo reportInfo = getReportInfo(accRunId, -1, level3RunId, -1);
		int updateVersion = reportInfo.getUpdateVersion();

		List<AminoAcid> aminoAcidInfo = getAminoAcidInfo();

		List<LevelThreeReportRow> levelThreeReportList = new ArrayList<LevelThreeReportRow>();
		
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String accession = (String) row.get("ncbi_accession");
			String seq = (String) row.get("seq");
			List<LevelThreeResidueResult> residues = new ArrayList<LevelThreeResidueResult>();
			for (int j = 0; j < numPos; j++) {
				int alignPos = positionList.get(j);  //position in alignment (including '-' deleted amino acids)
				
				int pos = -1;
				String acidId = String.valueOf(seq.charAt(alignPos - 1)); //zero based array
				AminoAcid aminoAcid = null;
				
				if (acidId != "-") {
					String tmp = seq.substring(0, alignPos).replace("-", "");  //get substring up to alignPos and remove all dashes
					pos = tmp.length();
					aminoAcid = findAminoAcidInfo(acidId, aminoAcidInfo);
				}

				LevelThreeResidueResult newResidue = new LevelThreeResidueResult(pos, aminoAcid, null, null, null);
				residues.add(newResidue);
			}
			LevelThreeReportRow newRow;
			if (i == 0 || (Double) row.get("level1_bitscore") == 1000000000) {

				//new code
				NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
			    int taxId = 0;
			    String proteinNameWSpecies = null;
			    String proteinName = "-";
			      
			    Map<String, Object> dataAddComp = jdbcTemplate.queryForMap(
			            "SELECT taxid, title FROM level3_result WHERE sequence_def = ? AND level3_run_id = ?",
			            accession, level3RunId);
			        taxId = (int) dataAddComp.get("taxid");
			        proteinNameWSpecies = (String) dataAddComp.get("title");
			        proteinName = proteinNameWSpecies.split("\\[.*?\\]$", 2)[0];
			    
				
				StringBuilder b2 = new StringBuilder();
                b2.append(" SELECT a.protein_count, a.taxid, b.name, a.seqapass_name ");
                b2.append("   FROM taxonomy_node a, taxonomy_name b ");
                b2.append("  WHERE a.taxid = ? AND a.taxid = b.taxid AND b.name_class = 'scientific name' LIMIT 1");
                				

				int proteinCount = 0;
				String taxonomyName = "-";
				String scientificName = "-";
				String seqapassName = "-";

				try {

					Map<String, Object> data = jdbcTemplate.queryForMap(b2.toString(), taxId );
					proteinCount = (int) data.get("protein_count");

					scientificName = (String) data.get("name");
					int ancestorTaxid = getRankOrLowerFromTargetRankAndTaxid("class", taxId);
					if (ancestorTaxid != taxId) {
						taxonomyName = getScientificNameFromTaxid(ancestorTaxid);
					}

					if (taxonomyName.equalsIgnoreCase("root")) {
						taxonomyName = scientificName;
					}

					seqapassName = (String) data.get("seqapass_name");
					if (seqapassName != null && seqapassName.equalsIgnoreCase("primer")) {
						seqapassName = "other sequences";
					}

				} catch (DataAccessException e) {
					// THIS JUST MEANS THAT THERE WAS NO PROTEIN MATCHING THE
					// TEMPLATE NAME
				}

				newRow = new LevelThreeReportRow(jobName, accession, proteinCount, taxId, taxonomyName, scientificName,
						seqapassName, proteinName, endDate, updateVersion, residues);
			} else {
				newRow = new LevelThreeReportRow(jobName, accession, 0, 0, "-", "-", "-", "-", endDate, updateVersion,
						residues);
			}
			levelThreeReportList.add(newRow);
		}

		// Assume 1st row is query accession (need to verify/install
		// contingencies)
		determineLevelThreeMatches(levelThreeReportList);

		determineLevelThreeSusceptibility(levelThreeReportList);

		logger.info("Finished backend getLevelThreeReport");

		return levelThreeReportList;

	}
	
	@Override
	public List<LevelFourRequestableRow> getAvailableTmalignReports(int userId, List<Integer> level4RunIds) {
		
		int numCombinedRuns = level4RunIds.size();
		
		
		boolean isAdmin = isUserAdmin(userId);
		
		//1st query to get number of level4_run_ids associated with each tmalign_run_id
		StringBuilder sb = new StringBuilder();
		List<String> tableList = new ArrayList<String>();
		for (int i=2; i<=numCombinedRuns; i++) {
			tableList.add("t"+i);
		}
		
	    
		// get combined reports
	    sb.append("SELECT t1.id ");
		sb.append("FROM level4_tmalign_run AS t1 ");
		//don't include first table as that is already accounted for
		for (int i=0; i<numCombinedRuns-1; i++) {
			sb.append("JOIN level4_tmalign_run AS ");
			sb.append(tableList.get(i) + " ");
		    sb.append("USING (id) ");	
		}
		sb.append("WHERE t1.level4_run_id = ? ");
		for (int i=0; i<numCombinedRuns-1; i++) {
			sb.append("AND "); 
			sb.append(tableList.get(i));
			sb.append(".level4_run_id = ? ");	
		}
		sb.append("AND t1.id IN (");
		sb.append("SELECT id FROM level4_tmalign_run ");
		if (!isAdmin) {
			sb.append("WHERE user_id = ? ");
		}
		sb.append("GROUP BY id HAVING COUNT(id) = ?) ");
	    sb.append("GROUP BY id ");
	    
	    List<Integer> argList = level4RunIds;
	    if (!isAdmin) {
	    	argList.add(userId);
	    }
	    argList.add(numCombinedRuns);
	    Object[] args = argList.toArray();
	    
	    List<Integer> resIds = new ArrayList<Integer>();
	    
	    
	    if(isAdmin) {
	    	resIds = jdbcTemplate.queryForList(sb.toString(), Integer.class, args);
	    } else {
	    	resIds = jdbcTemplate.queryForList(sb.toString(), Integer.class, args);
	    }
	    
	    //now get reports that only have one of the l4 ids
	    StringBuilder sb1 = new StringBuilder();
	    sb1.append("SELECT t1.id ");
		sb1.append("FROM level4_tmalign_run AS t1 ");
		sb1.append("WHERE t1.level4_run_id = ? ");
		sb1.append("AND t1.id IN (");
		sb1.append("SELECT id FROM level4_tmalign_run ");
		if (!isAdmin) {
			sb1.append("WHERE user_id = ? ");
		}
		sb1.append("GROUP BY id HAVING COUNT(id) = 1) ");
	    sb1.append("GROUP BY id ");
	    for (int i=0; i<level4RunIds.size(); i++) {
	    	List<Integer> singleResIds = new ArrayList<Integer>();
	    	if (isAdmin) {
	    		singleResIds = jdbcTemplate.queryForList(sb1.toString(), Integer.class, level4RunIds.get(i));
	    	} else {
	    		singleResIds = jdbcTemplate.queryForList(sb1.toString(), Integer.class, level4RunIds.get(i), userId);
	    	}
	    	resIds.addAll(singleResIds);
	    }
	 
	    //query info for report selection
	    StringBuilder sb2 = new StringBuilder();
	    sb2.append("SELECT "); 
	    sb2.append("a.id, ");
	    sb2.append("b.source_level, ");
	    sb2.append("query_accession, ");
	    sb2.append("query_acc_level4_run_id, ");
	    sb2.append("query_pdb_source, ");
	    sb2.append("template, ");
	    sb2.append("job_name ");
	    sb2.append("FROM level4_tmalign_run a ");
	    sb2.append("JOIN  level4_run b ON a.query_acc_level4_run_id = b.id ");
	    sb2.append("WHERE a.id IN (");
	    for(int i=0; i< resIds.size(); i++) {
	    	sb2.append("?,");
	    }
	    sb2.deleteCharAt(sb2.length()-1);  //remove last comma
	    sb2.append(") ");
	    sb2.append("GROUP BY a.id ");
	    
	    args = resIds.toArray();
	    
	    List<LevelFourRequestableRow> resRows = new ArrayList<LevelFourRequestableRow>();
	    
	    if (resIds.size() > 0) {
	    	resRows = jdbcTemplate.query(sb2.toString(), new LevelFourRequestableRowMapper(), args);
	    }
	        		
		
		return resRows;
	}
	
	@Override
	public List<LevelFourResultRow> getTmalignReport(int userId, int tmAlignRunId){
		StringBuilder sb = new StringBuilder();
		
		sb.append("SELECT * FROM level4_tmalign_data a ");
		sb.append("JOIN level4_run b ON a.level4_run_id = b.id ");
		sb.append("JOIN level4_tmalign_run c on c.id = a .tmalign_run_id ");
		sb.append("AND b.id = c.level4_run_id ");
		sb.append("WHERE a.tmalign_run_id = ? ");
		
		List<LevelFourResultRow> result = jdbcTemplate.query(sb.toString(), new LevelFourResultRowMapper(), tmAlignRunId);
		
		sb = new StringBuilder();
		sb.append("SELECT * FROM level4_tmalign_run WHERE id = ? LIMIT 1");
		
		LevelFourResultRow queryRow = jdbcTemplate.queryForObject(sb.toString(), new LevelFourQueryResultRowMapper(), tmAlignRunId);
		result.add(0, queryRow);
		
		return result;
	}
	

	public void determineLevelThreeMatches(List<LevelThreeReportRow> levelThreeReport) {
		// Assume 1st row is query accession (need to verify/install
		// contingencies)
		List<LevelThreeResidueResult> baseList = levelThreeReport.get(0).getResidueResultList();
		int numRes = baseList.size();
		int tmp = 0;
		for (LevelThreeReportRow row : levelThreeReport) {
			List<LevelThreeResidueResult> residueList = row.getResidueResultList();
			for (int i = 0; i < numRes; i++) {
				LevelThreeResidueResult queryResidue = baseList.get(i);
				AminoAcid queryAcid = queryResidue.getAminoAcid();
				LevelThreeResidueResult rowResidue = residueList.get(i);
				AminoAcid rowAcid = rowResidue.getAminoAcid();
				if (rowAcid == null) {
					rowResidue.setDirectMatch(false);
					rowResidue.setSideChainMatch(false);
					rowResidue.setSizeMatch(false);
					rowResidue.setTotalMatch(false);
				} else {
					rowResidue.setDirectMatch(rowAcid.getId() == queryAcid.getId());
					rowResidue.setSideChainMatch(rowAcid.getSideChain().equals(queryAcid.getSideChain()));
					rowResidue
							.setSizeMatch(Math.abs(rowAcid.getSize() - queryAcid.getSize()) <= levelThreeSizeTolerance);
					rowResidue.setTotalMatch(rowResidue.getSideChainMatch() || rowResidue.getSizeMatch());
				}
			}

			tmp += 1;
		}

		determineLevelThreeSusceptibility(levelThreeReport);

	}

	public void determineLevelThreeSusceptibility(List<LevelThreeReportRow> levelThreeReport) {
		for (LevelThreeReportRow row : levelThreeReport) {
			List<LevelThreeResidueResult> resList = row.getResidueResultList();
			if (resList.size() > 0) {
				boolean isSusceptible = true;
				for (LevelThreeResidueResult res : resList) {
					isSusceptible = isSusceptible && res.getTotalMatch();
				}
				row.setSusceptible(convertBooleanToYN(isSusceptible));
			}
		}
	}

	public AminoAcid findAminoAcidInfo(String id, List<AminoAcid> acidList) {

		for (AminoAcid a : acidList) {
			if (a.getId() == id.charAt(0)) {
				return a;
			}
		}

		return null;
	}

	@Override
	public List<LevelThreeRequestableRow> getLevelThreeCompleted(int accessionRunId, int userId) {

		List<LevelThreeRequestableRow> levelThreeCompleteList = new ArrayList<LevelThreeRequestableRow>();

		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     accession_run_id, user_id, id, job_name, template_name ");
		b.append(" FROM ");
		b.append("     level3_run ");
		b.append(" WHERE ");
		b.append("     accession_run_id = ?");
		b.append("     AND status = 'complete'");
		if (!isAdmin) {
			b.append("     AND user_id = ? ");
			levelThreeCompleteList = jdbcTemplate.query(b.toString(), new LevelThreeRequestableMapper(), accessionRunId,
					userId);
		} else {
			levelThreeCompleteList = jdbcTemplate.query(b.toString(), new LevelThreeRequestableMapper(),
					accessionRunId);
		}

		return levelThreeCompleteList;
	}


	@Override
	public String getLevelThreeSequence(int levelThreeRunId) {
		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     seq ");
		b.append(" FROM ");
		b.append("     level3_run a ");
		b.append("         JOIN ");
		b.append("     level3_result b ON a.id = b.level3_run_id ");
		b.append(" WHERE ");
		b.append("     a.id = ? ");
		b.append("         AND a.template_name = b.sequence_def ");

		String sequence = jdbcTemplate.queryForObject(b.toString(), String.class, levelThreeRunId);
		return sequence;
	}
	
	@Override
	//This query gets all level four jobs that have fully completed the FASTA generation stage
	public List<LevelFourRequestableRow> getLevelFourCreated(int accessionRunId, int userId) {

		List<LevelFourRequestableRow> levelFourCreatedList = new ArrayList<LevelFourRequestableRow>();

		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     a.accession_run_id, a.user_id, a.id, a.job_name, a.template, a.status, a.source_level, a.level2_run_id, ");
		b.append("     b.start_position, d.full_definition ");
		b.append(" FROM ");
		b.append("     level4_run a ");
		b.append("     LEFT JOIN level2_run b ");
		b.append("     ON a.level2_run_id = b.id ");
		b.append("     JOIN accession_run c ");
		b.append("ON a.accession_run_id = c.id ");
		b.append("     LEFT JOIN common_domain d ");
		b.append("     ON b.cdd_accession_num = d.cdd_id AND c.ncbi_version_id = d.ncbi_version_id ");
		b.append(" WHERE ");
		b.append("     a.accession_run_id = ?");
		b.append(" AND (a.status = 'FASTAs complete' || a.status = 'I-TASSER running' || a.status ='I-TASSER complete')");		
		if (!isAdmin) {
			b.append("     AND a.user_id = ? ");
			levelFourCreatedList = jdbcTemplate.query(b.toString(), new LevelFourRequestableMapper(), accessionRunId,
					userId);
		} else {
			levelFourCreatedList = jdbcTemplate.query(b.toString(), new LevelFourRequestableMapper(),
					accessionRunId);
		}

		return levelFourCreatedList;
	}
	
	@Override
	public List<LevelFourRequestableRow> getLevelFourStarted(int accessionRunId, int userId) {

		List<LevelFourRequestableRow> levelFourList = new ArrayList<LevelFourRequestableRow>();

		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     a.accession_run_id, a.user_id, a.id, a.job_name, a.template, a.source_level, a.level2_run_id, a.status, ");
		b.append("     b.start_position, d.full_definition ");
		b.append(" FROM ");
		b.append("     level4_run a ");
		b.append("      LEFT JOIN level2_run b ");
		b.append("      ON a.level2_run_id = b.id ");
		b.append("      JOIN accession_run c ");
		b.append("      ON a.accession_run_id = c.id ");
		b.append("      LEFT JOIN common_domain d ON ");
		b.append("      b.cdd_accession_num = d.cdd_id ");
		b.append("      AND c.ncbi_version_id = d.ncbi_version_id ");
		b.append(" WHERE ");
		b.append("     a.accession_run_id = ?");
		b.append("    AND ((a.status = 'I-TASSER running') OR  (a.status= 'I-TASSER complete'))");
//		b.append("     AND (status = 'I-TASSER running' || 'I-TASSER complete');");
		if (!isAdmin) {
			b.append("     AND a.user_id = ? ");
			levelFourList = jdbcTemplate.query(b.toString(), new LevelFourRequestableMapper(), accessionRunId,
					userId);
		} else {
			levelFourList = jdbcTemplate.query(b.toString(), new LevelFourRequestableMapper(),
					accessionRunId);
		}

		return levelFourList;
	}

	public static String getOrdinalSuffix(int queueNum) {
		if (queueNum % 10 == 1 && queueNum % 100 != 11) {
			return queueNum + "st";
		}
		if (queueNum % 10 == 2 && queueNum % 100 != 12) {
			return queueNum + "nd";
		}
		if (queueNum % 10 == 3 && queueNum % 100 != 13) {
			return queueNum + "rd";
		}
		return queueNum + "th";
	}

	@Override
	public Long getNcbiDate() {
		return ncbiKeeper.getPreferredNCBIProvider().getTaxonomyProteinDate().getTime();
	}

	@Override
	public ReportInfo getReportInfo(int accessionRunId, int lev2Id, int lev3Id, int lev4Id) {

		StringBuilder b = new StringBuilder();

		// determine database name and domain name
		// b.append(" SELECT DATABASE()");
		// String dbName = jdbcTemplate.queryForObject(b.toString(),
		// String.class);
		String domain = backEnd.getDomainName();

		// get level 1 report info
		b.delete(0, b.length());
		b.append(" SELECT ");
		b.append("     install_date,");
		b.append("     uniprot_date,");
		b.append("     update_version,");
		b.append("     seqapass_version,");
		b.append("     taxonomy_protein_date,");
		b.append("     blast_exec_version,");
		b.append("     itasser_exec_version,");
		b.append("     tmalign_exec_version,");
		b.append("     java_version,");
		b.append("     primefaces_version,");
		b.append("     tomcat_version,");
		b.append("     db_server_version,");
		b.append("     r_version,");
		b.append("     '1970-01-01' as cdd_data_date,");
		b.append("     'empty' as cobalt_data_version,");
		b.append("     '1970-01-01' as cobalt_data_date,");
		b.append("     '1970-01-01' as install_date,");
		b.append("     notes");
		b.append(" FROM ");
		b.append("     version a ");
		b.append("         JOIN ");
		b.append("     accession_run b ON b.completion_date > a.install_date ");
		b.append(" WHERE ");
		b.append("     b.id = ? ");
		b.append("     AND a.backend_host = ? ");
		b.append("     AND a.install_date IS NOT NULL ");
		b.append("     AND update_version = ( ");
		b.append("     SELECT MAX(update_version) ");
		b.append("         FROM version c ");
		b.append("         WHERE c.backend_host = a.backend_host ");
		b.append("            AND c.install_date IS NOT NULL ");
		b.append("            AND b.completion_date > c.install_date )");
		b.append("ORDER BY a.id DESC limit 1");

		ReportInfo reportInfo = jdbcTemplate.queryForObject(b.toString(), new ReportInfoMapper(), accessionRunId,
				domain);

		//only updates CDD date
		if (lev2Id > 0) {
			b.delete(0, b.length());
			b.append(" SELECT ");
			b.append("     MAX(install_date),");
			b.append("     cdd_data_date");
			b.append(" FROM ");
			b.append("     version a ");
			b.append("         JOIN ");
			b.append("     level2_run b ON b.start > a.install_date ");
			b.append(" WHERE ");
			b.append("     b.id = ? ");
			b.append("     AND a.backend_host = ? ");
			b.append("     AND a.install_date IS NOT NULL ");
			b.append("     AND update_version = ( ");
			b.append("     SELECT MAX(update_version) ");
			b.append("         FROM version c ");
			b.append("         WHERE c.backend_host = a.backend_host ");
			b.append("            AND c.install_date IS NOT NULL ");
			b.append("            AND b.start > c.install_date ) ");

			List<Map<String, Object>> row = jdbcTemplate.queryForList(b.toString(), lev2Id, domain);
			Long longDate = ((Date) row.get(0).get("cdd_data_date")).getTime();
			reportInfo.setCddDate(longDate);
		}

		//only updates cobaltDat and cobaltVersion
		if (lev3Id > 0) {
			b.delete(0, b.length());
			b.append(" SELECT ");
			b.append("     MAX(install_date),");
			b.append("     cobalt_data_date,");
			b.append("     cobalt_exec_version");
			b.append(" FROM ");
			b.append("     version a ");
			b.append("         JOIN ");
			b.append("     level3_run b ON b.start > a.install_date ");
			b.append(" WHERE ");
			b.append("     b.id = ? ");
			b.append("     AND a.backend_host = ? ");
			b.append("     AND a.install_date IS NOT NULL ");
			b.append("     AND update_version = ( ");
			b.append("     SELECT MAX(update_version) ");
			b.append("         FROM version c ");
			b.append("         WHERE c.backend_host = a.backend_host ");
			b.append("            AND c.install_date IS NOT NULL ");
			b.append("            AND b.start > c.install_date ) ");

			List<Map<String, Object>> row = jdbcTemplate.queryForList(b.toString(), lev3Id, domain);
			reportInfo.setCobaltDate(((Date) row.get(0).get("cobalt_data_date")).getTime());
			reportInfo.setCobaltVersion(row.get(0).get("cobalt_exec_version").toString());
		}
		
		//only updates itasserVersion, tmalignVersion, and uniprotDate
		if (lev4Id > 0) {
			b.delete(0, b.length());
			b.append(" SELECT *");
			b.append(" FROM ");
			b.append("     version a ");
			b.append("         JOIN ");
			b.append("     level4_run b ON b.start > a.install_date ");
			b.append(" WHERE ");
			b.append("     b.id = ? ");
			b.append("     AND a.backend_host = ? ");
			b.append("     AND a.install_date IS NOT NULL ");
			b.append("     AND update_version = ( ");
			b.append("     SELECT MAX(update_version) ");
			b.append("         FROM version c ");
			b.append("         WHERE c.backend_host = a.backend_host ");
			b.append("            AND c.install_date IS NOT NULL ");
			b.append("            AND b.start > c.install_date ) ");
			b.append(" ORDER BY install_date desc");
			
			List<ReportInfo> infos = jdbcTemplate.query(b.toString(), new ReportInfoMapper(), lev4Id, domain);
			
			//gets report info for current report (not necessarily latest report info version)
			ReportInfo currentReportInfo = infos.get(0); //selects oldest that fits query
			
			reportInfo.setItasserVersion(currentReportInfo.getItasserVersion());
			reportInfo.setTmalignVersion(currentReportInfo.getTmalignVersion());
			reportInfo.setUniprotDate(currentReportInfo.getUniprotDate());
			
		}

		return reportInfo;
	}

	@Override
	public String getBackendInfo() {
		StringBuilder b = new StringBuilder();
		b.append(" SELECT DATABASE()");

		String database = jdbcTemplate.queryForObject(b.toString(), String.class);
		String domain = backEnd.getDomainName();

		return database + ":" + domain;
	}

	@Override
	public List<ReportInfo> getUpdateInfo() {
		StringBuilder b = new StringBuilder();
		b.append(" SELECT DATABASE()");
		String dbName = null;
		try {
			dbName = jdbcTemplate.queryForObject(b.toString(), String.class);
		} catch (Exception e) {
			// This happens if database is down. Returns empty list
			return new ArrayList<ReportInfo>();
		}
		String domain = backEnd.getDomainName();

		b.delete(0, b.length());
		b.append(" SELECT * FROM version");
		b.append(" WHERE ");
		b.append("     backend_host = ?");
		b.append(" AND install_date IS NOT NULL ");
		b.append(" ORDER BY id DESC, update_version DESC, seqapass_version DESC");

		return jdbcTemplate.query(b.toString(), new ReportInfoMapper(), domain);

	}

//	@Override
//	public void convertLevelOneCutoff(int accessionRunId) {
//		StringBuilder b = new StringBuilder();
//		b.append("SELECT  ");
//		b.append("    density_plot_object ");
//		b.append("FROM ");
//		b.append("    accession_run_density_plot ");
//		b.append("WHERE ");
//		b.append("    accession_run_id = ? ");
//
//		byte[] serializedData = null;
//		try {
//			serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class, accessionRunId);
//		} catch (EmptyResultDataAccessException e) {
//			System.out.println("Null data!");
//		} catch (Exception e1) {
//			System.out.println("Could not retrieve serialized CutoffData object for accessionRunId=" + accessionRunId);
//			return;
//		}
//
//		gov.epa.seqapass.backend.domain.CutoffData oldCutoffData = null;
//		gov.epa.seqapass.common.CutoffData newCutoffData = new gov.epa.seqapass.common.CutoffData();
//		try {
//			oldCutoffData = (gov.epa.seqapass.backend.domain.CutoffData) SerializationUtils.deserialize(serializedData);
//			newCutoffData.setCutoffValues(oldCutoffData.getCutoffValues());
//			newCutoffData.setInfLoc(oldCutoffData.getInfLoc());
//			newCutoffData.setMaxCritLoc(oldCutoffData.getMaxCritLoc());
//			newCutoffData.setMinCritLoc(oldCutoffData.getMinCritLoc());
//			newCutoffData.setxData(oldCutoffData.getxData());
//			newCutoffData.setyData(oldCutoffData.getyData());
//		} catch (Exception e2) {
//			// TODO Auto-generated catch block
//			System.out.println("Could not convert CutoffData for accessionRunId=" + accessionRunId);
//			return;
//		}
//
//		if (oldCutoffData != null && newCutoffData != null) {
//			b.delete(0, b.length());
//			b.append("Update");
//			b.append("    accession_run_density_plot SET density_plot_object = ? ");
//			b.append("    WHERE accession_run_id = ? ");
//
//			byte[] serializedCutoffData = SerializationUtils.serialize(newCutoffData);
//			jdbcTemplate.update(b.toString(), serializedCutoffData, accessionRunId);
//
//			System.out.println("Converted CutoffData for accessionRunId=" + accessionRunId);
//		} else {
//			System.out.println("Failed conversion for accessionRunId=" + accessionRunId);
//		}
//	}
//
//	@Override
//	public void convertLevelTwoCutoff(int domainRunId) {
//		StringBuilder b = new StringBuilder();
//		b.append("SELECT  ");
//		b.append("    density_plot_object ");
//		b.append("FROM ");
//		b.append("    domain_run_density_plot ");
//		b.append("WHERE ");
//		b.append("    domain_run_id = ? ");
//
//		byte[] serializedData = null;
//		try {
//			serializedData = jdbcTemplate.queryForObject(b.toString(), byte[].class, domainRunId);
//		} catch (EmptyResultDataAccessException e) {
//			System.out.println("Null data!");
//		} catch (Exception e1) {
//			System.out.println("Could not retrieve serialized CutoffData object for domainRunId=" + domainRunId);
//			return;
//		}
//
//		gov.epa.seqapass.backend.domain.CutoffData oldCutoffData = null;
//		gov.epa.seqapass.common.CutoffData newCutoffData = new gov.epa.seqapass.common.CutoffData();
//		try {
//			oldCutoffData = (gov.epa.seqapass.backend.domain.CutoffData) SerializationUtils.deserialize(serializedData);
//			newCutoffData.setCutoffValues(oldCutoffData.getCutoffValues());
//			newCutoffData.setInfLoc(oldCutoffData.getInfLoc());
//			newCutoffData.setMaxCritLoc(oldCutoffData.getMaxCritLoc());
//			newCutoffData.setMinCritLoc(oldCutoffData.getMinCritLoc());
//			newCutoffData.setxData(oldCutoffData.getxData());
//			newCutoffData.setyData(oldCutoffData.getyData());
//		} catch (Exception e2) {
//			// TODO Auto-generated catch block
//			System.out.println("Could not convert CutoffData for domainRunId=" + domainRunId);
//			return;
//		}
//
//		if (oldCutoffData != null && newCutoffData != null) {
//			b.delete(0, b.length());
//			b.append("Update");
//			b.append("    domain_run_density_plot SET density_plot_object = ? ");
//			b.append("    WHERE domain_run_id = ? ");
//
//			byte[] serializedCutoffData = SerializationUtils.serialize(newCutoffData);
//			jdbcTemplate.update(b.toString(), serializedCutoffData, domainRunId);
//			System.out.println("Converted CutoffData for domainRunId=" + domainRunId);
//		} else {
//			System.out.println("Failed conversion for domainRunId=" + domainRunId);
//		}
//	}

//	@Override
//	public void convertAllCutoffs() {
//
//		StringBuilder b = new StringBuilder();
//		b.append("SELECT  ");
//		b.append("    accession_run_id ");
//		b.append("FROM ");
//		b.append("    accession_run_density_plot ");
//
//		List<Integer> accessionRunIdList = jdbcTemplate.queryForList(b.toString(), int.class);
//
//		for (Integer accRunId : accessionRunIdList) {
//			System.out.println(accRunId);
//			convertLevelOneCutoff(accRunId);
//		}
//
//		b.delete(0, b.length());
//		b.append("SELECT  ");
//		b.append("    domain_run_id ");
//		b.append("FROM ");
//		b.append("    domain_run_density_plot ");
//
//		List<Integer> domainRunIdList = jdbcTemplate.queryForList(b.toString(), int.class);
//
//		for (Integer domRunId : domainRunIdList) {
//			System.out.println(domRunId);
//			convertLevelTwoCutoff(domRunId);
//		}
//
//		System.out.println("Conversion complete!");
//
//	}

	@Override
	public SpeciesTaxGrouping getTaxGroups(SpeciesTaxGrouping speciesTaxGrp) {

		String requestedRank = speciesTaxGrp.getRequestedRank();

		for (Map.Entry<Integer, TaxGroup> entry : speciesTaxGrp.getGroupMap().entrySet()) {
			Integer taxonomyTaxId = 1;
			int ancestorTaxId = getRankOrLowerFromTargetRankAndTaxid(requestedRank, entry.getKey());
			if (ancestorTaxId != entry.getKey()) {
				taxonomyTaxId = ancestorTaxId;
			}
			entry.getValue().setId(taxonomyTaxId);
			entry.getValue().setName(getScientificNameFromTaxid(taxonomyTaxId));
			entry.getValue().setLevel(requestedRank); // currently sets level
														// based on requested
														// rank, not actual
														// level found
		}

		return speciesTaxGrp;
	}

	// @Override
	// public void createLevelOneReportSettingsFile(int accessionRunId, int
	// userId,
	// int destination) {
	//
	//// String NEW_LINE_SEPARATOR = "\r\n";
	// String NEW_LINE_SEPARATOR = "\n";
	//
	// ReportInfo theInfo = getReportInfo(accessionRunId, -1, -1);
	//
	// List<LevelOneReportRow> level1Report = getLevelOneReport(accessionRunId);
	// System.out.println("finished gathering report info in
	// createLevelOneReports");
	//
	// String dest = null;
	// List<String> theAccessionList = new ArrayList<String>();
	//
	// StringBuilder b = new StringBuilder();
	// b.append(" SELECT ");
	// b.append(" query_accession_id ");
	// b.append(" FROM ");
	// b.append(" user_run_accession_run a ");
	// b.append(" JOIN ");
	// b.append(" user_run b ON a.user_run_id = b.id ");
	// b.append(" WHERE a.accession_run_id = ? ");
	// if (destination == 2) {
	// b.append(" AND b.user_id = ? ");
	// dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
	// } else {
	// dest = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
	// }
	//
	// String theAccession = null;
	// if (theInfo.getUpdateVersion() <3){
	// //old version based on canonical accession
	// if(destination==2){
	// List<Map<String, Object>> rows = null;
	// rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, userId);
	// for (int j=0; j< rows.size(); j++){
	// String queryAccession = rows.get(j).get("query_accession_id").toString();
	// theAccessionList.add(queryAccession);
	// }
	// } else {
	// theAccession = jdbcTemplate.queryForObject(
	// GetCanonicalAccessionFromRunID, String.class,
	// accessionRunId);
	// theAccessionList.add(theAccession);
	// }
	// } else {
	// b.append("LIMIT 1;"); //limits to 1 if user resubmits multiple times
	// if(destination==2){
	// theAccession = jdbcTemplate.queryForObject(b.toString(),
	// String.class,accessionRunId, userId);
	// } else {
	// theAccession = jdbcTemplate.queryForObject(b.toString(),
	// String.class,accessionRunId);
	// }
	//
	// theAccessionList.add(theAccession);
	// }
	//
	//
	// for (int j = 0; j < theAccessionList.size(); j++) {
	//
	// theAccession = theAccessionList.get(j);
	// String outputDirectory = "";
	// if (!enableDebugOutput){
	// //FOR STAGING AND PRODUCTION USE
	// outputDirectory = dest + "/" + theAccession + "_v"
	// + theInfo.getUpdateVersion() + "/Level1Reports/";
	// } else {
	// // For debugging only
	// outputDirectory = debugReportDir + theAccession + "_v"
	// + theInfo.getUpdateVersion() + "\\Level1Reports\\";
	// }
	//
	// System.out.println("outputReport: " + outputDirectory);
	// System.out.println("Creating level report settings at "
	// + outputDirectory);
	//
	// int runId = 1; //seqapass run id
	//
	// String outputFile = outputDirectory + Integer.toString(runId) + "_" +
	// theAccession + "_levelOneReportSettings.csv";
	//
	// CSVPrinter csvFilePrinter = null;
	//
	// CSVFormat csvFileFormat = CSVFormat.DEFAULT
	// .withRecordSeparator(NEW_LINE_SEPARATOR);
	//
	// String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new
	// Date());
	//
	// File report = new File(outputFile);
	// if (!report.exists()) {
	// try {
	//
	// File directory = new File(String.valueOf(outputDirectory));
	// if (!directory.exists()) {
	// directory.mkdirs();
	// }
	//
	// FileWriter fileWriter = new FileWriter(outputFile);
	// csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
	//
	// LevelOneReportRow row = level1Report.get(0);
	//
	// csvFilePrinter.printRecord("Level 1 Report Settings");
	// csvFilePrinter.printRecord("");
	// csvFilePrinter.printRecord("Analysis TimeStamp",
	// convertUnixTimeToDate(row.getEndDate()));
	// csvFilePrinter.printRecord("SeqAPASS version", row.getUpdateVersion());
	// csvFilePrinter.printRecord("Query Species",row.getTaxonomyName());
	// csvFilePrinter.printRecord("Query Protein",row.getProteinName());
	// csvFilePrinter.printRecord("Query Accession",row.getAccession());
	// csvFilePrinter.printRecord("Ortholog Count","INSERT");
	// csvFilePrinter.printRecord("L1 Cutoff Value","INSERT");
	// csvFilePrinter.printRecord("E-value",defaultLevelOneEvalue);
	// csvFilePrinter.printRecord("Sorted by Taxonomic Group","Class");
	// csvFilePrinter.printRecord("Common Domains",defaultCommonDomains);
	// csvFilePrinter.printRecord("Species Read Across","Y");
	// csvFilePrinter.printRecord("Show Only Eukaryotes","Unchecked");
	// csvFilePrinter.printRecord("Report","Full & Primary");
	//
	// fileWriter.flush();
	// fileWriter.close();
	// csvFilePrinter.close();
	//
	// } catch (IOException e1) {
	// // TODO Auto-generated catch block
	// e1.printStackTrace();
	// }
	// } else {
	// System.out.println("Output report file already exists: " + outputFile);
	// }
	// }
	// }

	@Override
	public void createLevelOneReports(int accessionRunId, int userId, int destination) {
		// System.out.println("Starting to create the CSV files with
		// accessionRunId: " + accessionRunId + " and destination: " +
		// destination);
		boolean eukaryotesOnly = isQueryEukaryoteFromAccessionRunId(accessionRunId);

		String NEW_LINE_SEPARATOR = "\n";
		Object[] lev1FullCSVHeader = { "Data Version", "NCBI Accession", "Identical Protein", "Protein Count",
				"Species Tax ID", "Taxonomic Group", "Scientific Name", "Common Name", "Protein Name", "Hit Length",
				"Identity", "Positives", "Evalue", "BLASTp Bitscore", "Ortholog Candidate", "Ortholog Count", "Cut-off",
				"Common Domain Count", "Percent Similarity", "Susceptibility Prediction", "Analysis Completed",
				"Eukaryote", "ECOTOX", "ECOTOX Date" };

		Object[] lev1PrimaryCSVHeader = { "Data Version", "NCBI Accession", "Protein Count", "Species Tax ID",
				"Taxonomic Group", "Filtered Taxonomic Group", "Scientific Name", "Common Name", "Protein Name",
				"BLASTp Bitscore", "Ortholog Candidate", "Ortholog Count", "Cut-off", "Percent Similarity",
				"Susceptibility Prediction", "Analysis Completed", "Eukaryote", "ECOTOX", "ECOTOX Date" };

		List<LevelOneReportRow> theLev1FullReport = getLevelOneReport(accessionRunId);
		int orthologCount = returnLevOneOrthologCount(theLev1FullReport);

		List<DensityRow> densityRows = getDensityData(1, ReportTypeEnum.Full, accessionRunId, -1, -1, eukaryotesOnly);
		CutoffData cutoffData = CutoffData.newInstance(densityRows, 1); // new
																		// CutoffData(rows);
		// CutoffData cutoffData = getLevelOneFullCutoff(accessionRunId);
		if (cutoffData == null) {
			// CutoffData primaryCutoffData =
			// getLevelOnePrimaryCutoff(accessionRunId);
			densityRows = getDensityData(1, ReportTypeEnum.Primary, accessionRunId, defaultLevelOneEvalue,
					defaultCommonDomains, eukaryotesOnly);
			CutoffData primaryCutoffData = CutoffData.newInstance(densityRows, 1); // new
																				// CutoffData(rows);
			if (primaryCutoffData != null) {
				// System.out.println("Calculating level 1 Full susceptibility using ");
				logger.info("Calculating level 1 Full susceptibility using first default cutoff value.");
				updateLevOneSusceptibility(theLev1FullReport, primaryCutoffData.getCutoffValues().get(0)); // Uses
																											// 1st
																											// (default)
				// cutoff value
			} else {
				// System.out.println("Error:  Primary cutoff data is null!!!  Using cutoff = 100.0");
				logger.error("Error:  Primary cutoff data is null!!!  Using cutoff = 100.0");
				updateLevOneSusceptibility(theLev1FullReport, 100.0);
			}
		} else {
			updateLevOneSusceptibility(theLev1FullReport, cutoffData.getCutoffValues().get(0)); // Uses
																								// 1st
																								// (default)
																								// cutoff
																								// value
		}

		// String theAccession =
		// jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID,
		// String.class, accessionRunId);

		ReportInfo theInfo = getReportInfo(accessionRunId, -1, -1, -1);
		// System.out.println("finished gathering report info in createLevelOneReports");
		logger.info("finished gathering report info in createLevelOneReports");

		String dest = null;
		List<String> theAccessionList = new ArrayList<String>();

		StringBuilder b = new StringBuilder();
		b.append(" SELECT ");
		b.append("     query_accession_id ");
		b.append(" FROM ");
		b.append("     user_run_accession_run a ");
		b.append("         JOIN ");
		b.append("     user_run b ON a.user_run_id = b.id ");
		b.append("     WHERE a.accession_run_id = ? ");
		if (destination == 2) {
			b.append("     AND b.user_id = ?  ");
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
		} else {
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
		}

		String theAccession = null;
		if (theInfo.getUpdateVersion() < 3) {
			// old version based on canonical accession
			if (destination == 2) {
				List<Map<String, Object>> rows = null;
				rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, userId);
				for (int j = 0; j < rows.size(); j++) {
					String queryAccession = rows.get(j).get("query_accession_id").toString();
					theAccessionList.add(queryAccession);
				}
			} else {
				theAccession = jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID, String.class,
						accessionRunId);
				theAccessionList.add(theAccession);
			}
		} else {
			b.append("LIMIT 1;"); // limits to 1 if user resubmits multiple
									// times
			if (destination == 2) {
				theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, accessionRunId, userId);
			} else {
				theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, accessionRunId);
			}

			theAccessionList.add(theAccession);
		}

		for (int j = 0; j < theAccessionList.size(); j++) {

			theAccession = theAccessionList.get(j);
			String outputDirectory = "";
			if (!enableDebugOutput) {
				// FOR STAGING AND PRODUCTION USE
				outputDirectory = dest + "/" + theAccession + "_v" + theInfo.getUpdateVersion() + "/Level1Reports/";
			} else {
				// For debugging only
				outputDirectory = debugReportDir + theAccession + "_v" + theInfo.getUpdateVersion()
						+ "\\Level1Reports\\";
			}

			// System.out.println("outputReport: " + outputDirectory);
			// System.out.println("Creating level 1 full report at " + outputDirectory);
			logger.info("outputReport: {}", outputDirectory);
			logger.info("Creating level 1 full report at {}", outputDirectory);

			String outputReportFile = outputDirectory + theAccession + "_Full_v" + theInfo.getUpdateVersion() + ".csv";
			String outputPngFile = outputDirectory + theAccession + "_Full_v" + theInfo.getUpdateVersion()
					+ "_cutoff.png";
			// convert report to csv
			CSVPrinter csvFilePrinter = null;

			CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
			// add to zip file

			String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

			File report = new File(outputReportFile);
			if (!report.exists()) {
				try {

					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						directory.mkdirs();
					}

					FileWriter fileWriter = new FileWriter(outputReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					csvFilePrinter.printRecord(lev1FullCSVHeader);

					for (LevelOneReportRow record : theLev1FullReport) {
						List<String> lev1Record = new ArrayList<String>();
						// lev1Record.add(""); // Insert blank column at start
						// so
						// // that it matches output from
						// // primefaces datatable export
						lev1Record.add(String.valueOf(record.getUpdateVersion()));
						lev1Record.add(record.getAccession());
						lev1Record.add(record.getIsDup() == 0 ? "N" : "Y");
						lev1Record.add(String.valueOf(record.getProteinCount()));
						lev1Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev1Record.add(record.getDefaultTaxonomyName());
						lev1Record.add(record.getScientificName());
						lev1Record.add(record.getCommonName());
						lev1Record.add(record.getProteinName());
						lev1Record.add(String.valueOf(record.getHitLength()));
						lev1Record.add(String.valueOf(record.getIdentity()));
						lev1Record.add(String.valueOf(record.getPositives()));
						lev1Record.add(String.valueOf(scientificFormatter.format(record.getEvalue())));
						lev1Record.add(twoDecimalDigitFormatter.format(record.getBlastPBitScore()));
						lev1Record.add(record.getOrtholog());
						lev1Record.add(String.valueOf(orthologCount));
						lev1Record.add(String.valueOf(twoDecimalDigitFormatter.format(record.getCutoff())));
						lev1Record.add(String.valueOf(record.getCommonDomainCount()));
						lev1Record.add(
								String.valueOf(twoDecimalDigitFormatter.format(record.getPercentSimilarity() * 100.0)));
						lev1Record.add(record.getSusceptible());
						lev1Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev1Record.add(record.isEukaryote() ? "Y" : "N");
						lev1Record.add("=HYPERLINK(\"https://cfpub.epa.gov/ecotox/explore.cfm?ncbi="
								+ record.getSpeciesTaxId() + "\")");
						lev1Record.add(record.isEcotox() ? timestamp : "-");
						csvFilePrinter.printRecord(lev1Record);
					}

					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.error("Output report file already exists: {}", outputReportFile);
			}

			// System.out.println("finished creating level 1 full report");
			// System.out.println("now creating level 1 full cutoff png");
			logger.info("finished creating level 1 full report");
			logger.info("now creating level 1 full cutoff png");

			File png = new File(outputPngFile);
			if (!png.exists()) {
				// Generate Level 1 Full Cutoff Png
				try {
					if (cutoffData != null && !cutoffData.getxData().isEmpty()) {
						ByteArrayOutputStream fullLevOnePng = createCutoffGraph(1, accessionRunId, ReportTypeEnum.Full,
								eukaryotesOnly);
						if (fullLevOnePng != null) {
							OutputStream os = new FileOutputStream(outputPngFile);
							fullLevOnePng.writeTo(os);
							os.flush();
							os.close();
							fullLevOnePng.close();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// System.out.println("finished creating level 1 full cutoff png");
			logger.info("finished creating level 1 full cutoff png");

			LevelOneReportRow queryRow = null;
			String outputSettingsFile = null;
			File settingsFile = null;
			if (theLev1FullReport.size() > 0) {
				queryRow = theLev1FullReport.get(0);

				outputSettingsFile = outputDirectory + theAccession + "_Full_v" + theInfo.getUpdateVersion()
						+ "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Query Species", queryRow.getTaxonomyName());
						csvFilePrinter.printRecord("Query Protein", queryRow.getProteinName());
						csvFilePrinter.printRecord("Query Accession", queryRow.getAccession());
						csvFilePrinter.printRecord("Ortholog Count", orthologCount);
						csvFilePrinter.printRecord("L1 Cutoff", "Default");
						csvFilePrinter.printRecord("L1 Cutoff Value",
								(twoDecimalDigitFormatter.format(queryRow.getCutoff())));
						csvFilePrinter.printRecord("E-value", 10);
						csvFilePrinter.printRecord("Sorted by Taxonomic Group", "Class");
						csvFilePrinter.printRecord("Common Domains", 0);
						csvFilePrinter.printRecord("Species Read Across", "Y");
						csvFilePrinter.printRecord("Show Only Eukaryotes", "Unchecked");
						csvFilePrinter.printRecord("Report", "Full");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}

				// System.out.println("Finished level 1 full settings file");
				logger.info("Finished level 1 full settings file");
			}

			// Generate Primary Report
			List<LevelOneReportRow> theLev1PrimaryReport = filterLevOnePrimaryReport(theLev1FullReport);

			int primaryOrthologCount = returnLevOneOrthologCount(theLev1PrimaryReport);

			// System.out.println("Calling getLevelOnePrimaryCutoff");
			logger.info("Calling getLevelOnePrimaryCutoff");

			densityRows = getDensityData(1, ReportTypeEnum.Primary, accessionRunId, defaultLevelOneEvalue,
					defaultCommonDomains, eukaryotesOnly);
			CutoffData primaryCutoffData = CutoffData.newInstance(densityRows, 1); // new
																				// CutoffData(rows);
			// CutoffData primaryCutoffData =
			// getLevelOnePrimaryCutoff(accessionRunId);
			if (primaryCutoffData != null) {
				// System.out.println("primaryCutoffData != null");
				logger.info("primaryCutoffData != null");
				updateLevOneSusceptibility(theLev1PrimaryReport, primaryCutoffData.getCutoffValues().get(0)); // Uses
																												// 1st
																												// (default)
																												// cutoff
				// value
			} else {
				// System.out.println("Error:  Primary cutoff data is null!!!  Using cutoff = 100.0");
				logger.error("Error:  Primary cutoff data is null!!!  Using cutoff = 100.0");
				updateLevOneSusceptibility(theLev1PrimaryReport, 100.0);
			}

			String outputPrimaryReportFile = outputDirectory + theAccession + "_Primary_v" + theInfo.getUpdateVersion()
					+ ".csv";
			String outputPrimaryPngFile = outputDirectory + theAccession + "_Primary_v" + theInfo.getUpdateVersion()
					+ "_cutoff.png";
			// convert report to csv
			// CSVPrinter csvFilePrinter = null;

			// System.out.println("creating level 1 primary report");
			logger.info("creating level 1 primary report");

			report = new File(outputPrimaryReportFile);
			if (!report.exists()) {
				// CSVFormat csvFileFormat =
				// CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
				// add to zip file
				try {

					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						directory.mkdirs();
					}

					FileWriter fileWriter = new FileWriter(outputPrimaryReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					csvFilePrinter.printRecord(lev1PrimaryCSVHeader);

					for (LevelOneReportRow record : theLev1PrimaryReport) {
						List<String> lev1Record = new ArrayList<String>();
						// lev1Record.add("");
						lev1Record.add(String.valueOf(record.getUpdateVersion()));
						lev1Record.add(record.getAccession());
						lev1Record.add(String.valueOf(record.getProteinCount()));
						lev1Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev1Record.add(record.getDefaultTaxonomyName());
						lev1Record.add(record.getTaxonomyName());
						lev1Record.add(record.getScientificName());
						lev1Record.add(record.getCommonName());
						lev1Record.add(record.getProteinName());
						lev1Record.add(twoDecimalDigitFormatter.format(record.getBlastPBitScore()));
						lev1Record.add(record.getOrtholog());
						lev1Record.add(String.valueOf(primaryOrthologCount));
						lev1Record.add(String.valueOf(twoDecimalDigitFormatter.format(record.getCutoff())));
						lev1Record.add(
								String.valueOf(twoDecimalDigitFormatter.format(record.getPercentSimilarity() * 100.0)));
						lev1Record.add(record.getSusceptible());
						lev1Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev1Record.add(record.isEukaryote() ? "Y" : "N");
						lev1Record.add("=HYPERLINK(\"https://cfpub.epa.gov/ecotox/explore.cfm?ncbi="
								+ record.getSpeciesTaxId() + "\")");
						lev1Record.add(record.isEcotox() ? timestamp : "-");
						csvFilePrinter.printRecord(lev1Record);
					}

					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.warn("Output report file already exists: " + outputReportFile);
			}

			// System.out.println("finished creating level 1 primary report");
			// System.out.println("now creating level 1 primary cutoff png");
			logger.info("finished creating level 1 primary report");
			logger.info("now creating level 1 primary cutoff png");

			png = new File(outputPrimaryPngFile);
			if (!png.exists()) {
				// Generate Level 1 Full Cutoff Png
				try {
					if (primaryCutoffData != null && !primaryCutoffData.getxData().isEmpty()) {
						ByteArrayOutputStream primaryLevOnePng = createCutoffGraph(1, accessionRunId,
								ReportTypeEnum.Primary, eukaryotesOnly);
						if (primaryLevOnePng != null) {
							OutputStream os = new FileOutputStream(outputPrimaryPngFile);
							primaryLevOnePng.writeTo(os);
							os.flush();
							os.close();
							primaryLevOnePng.close();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// System.out.println("finished creating level 1 primary cutoff png");
			logger.info("finished creating level 1 primary cutoff png");

			if (theLev1PrimaryReport.size() > 0) {
				queryRow = theLev1PrimaryReport.get(0);

				outputSettingsFile = outputDirectory + theAccession + "_levelOnePrimaryReportSettings.csv";
				outputSettingsFile = outputDirectory + theAccession + "_Primary_v" + theInfo.getUpdateVersion()
						+ "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Query Species", queryRow.getTaxonomyName());
						csvFilePrinter.printRecord("Query Protein", queryRow.getProteinName());
						csvFilePrinter.printRecord("Query Accession", queryRow.getAccession());
						csvFilePrinter.printRecord("Ortholog Count", primaryOrthologCount);
						csvFilePrinter.printRecord("L1 Cutoff", "Default");
						csvFilePrinter.printRecord("L1 Cutoff Value",
								(twoDecimalDigitFormatter.format(queryRow.getCutoff())));
						csvFilePrinter.printRecord("E-value", defaultLevelOneEvalue);
						csvFilePrinter.printRecord("Sorted by Taxonomic Group", "Class");
						csvFilePrinter.printRecord("Common Domains", defaultCommonDomains);
						csvFilePrinter.printRecord("Species Read Across", "Y");
						csvFilePrinter.printRecord("Show Only Eukaryotes", "Unchecked");
						csvFilePrinter.printRecord("Report", "Primary");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}
				// System.out.println("Finished creating level 1 primary settings file");
				logger.info("Finished creating level 1 primary settings file");
			}
		}

	}

	@Override
	public void createLevelTwoReports(int accessionRunId, int lev2Id, int userId, int destination) {

		// CWS TODO
		boolean eukaryotesOnly = isQueryEukaryoteFromLevelTwoRunId(lev2Id);

		String NEW_LINE_SEPARATOR = "\n";
		Object[] lev2FullCSVHeader = { "Update Version", "NCBI Accession", "Identical Protein", "Protein Count",
				"Species Tax ID", "Taxonomic Group", "Scientific Name", "Common Name", "Protein Name", "NCBI PSSM ID",
				"NCBI Domain ID", "Domain Name", "Hit Length", "Identity", "Positive", "Evalue", "BLASTp Bitscore",
				"Ortholog Candidate", "Ortholog Count", "Cut-off", "Percent Similarity", "Susceptibility Prediction",
				"Analysis Completed", "Eukaryote", "ECOTOX", "ECOTOX Date" };

		Object[] lev2PrimaryCSVHeader = { "Update Version", "NCBI Accession", "Protein Count", "Species Tax ID",
				"Taxonomic Group", "Filtered Taxonomic Group", "Scientific Name", "Common Name", "Protein Name",
				"Domain Name", "BLASTp Bitscore", "Ortholog Candidate", "Ortholog Count", "Cut-off",
				"Percent Similarity", "Susceptibility Prediction", "Analysis Completed", "Eukaryote", "ECOTOX",
				"ECOTOX Date" };

		LevelTwoRequestableRow info = getLevelTwoInfoByRunId(lev2Id);
		String displayText = info.getDisplayText();
		String[] domainParts = displayText.split(",", 3);
		String ncbiDomainId = domainParts[0].split("\\) ", 2)[1];
		String domainLoc = displayText.split(ncbiDomainId)[0];
		String domainName = domainParts[1];

		List<LevelTwoReportRow> theLev2FullReport = getLevelTwoReport(accessionRunId, lev2Id);
		int orthologCount = returnLevTwoOrthologCount(theLev2FullReport);

		// Susceptibility is currently based on default cutoff
		List<DensityRow> densityRows = getDensityData(2, ReportTypeEnum.Full, lev2Id, defaultLevelTwoEvalue, -1,
				eukaryotesOnly);
		CutoffData cutoffData = CutoffData.newInstance(densityRows, 2); // new
																		// CutoffData(rows);
		// CutoffData cutoffData = getLevelTwoFullCutoff(lev2Id);
		if (cutoffData == null) {
			densityRows = getDensityData(2, ReportTypeEnum.Primary, lev2Id, defaultLevelTwoEvalue, -1, eukaryotesOnly);
			CutoffData primaryCutoffData = CutoffData.newInstance(densityRows, 2); // new
																				// CutoffData(rows);
			// CutoffData primaryCutoffData = getLevelTwoPrimaryCutoff(lev2Id);
			if (primaryCutoffData != null) {
				updateLevTwoSusceptibility(theLev2FullReport, primaryCutoffData.getCutoffValues().get(0)); // Uses
																											// 1st
				// (default)
			} else {
				// System.out.println("Error:  Primary cutoff data is null for level 2 report!!!  Using cutoff = 100.0");
				logger.error("Error:  Primary cutoff data is null for level 2 report!!!  Using cutoff = 100.0");
				updateLevTwoSusceptibility(theLev2FullReport, 100.0);
			}
			// cutoff
		} else {
			updateLevTwoSusceptibility(theLev2FullReport, cutoffData.getCutoffValues().get(0)); // Uses
																								// 1st
																								// (default)
																								// cutoff
		}

		// String theAccession =
		// jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID,
		// String.class, accessionRunId);

		ReportInfo theReportInfo = getReportInfo(accessionRunId, lev2Id, -1, -1);

		String dest = null;
		List<String> theAccessionList = new ArrayList<String>();

		StringBuilder b = new StringBuilder();
		b.append(" SELECT DISTINCT(c.query_accession_id) ");
		b.append(" FROM level2_run a ");
		b.append("     JOIN accession_run b ");
		b.append("     ON b.id = a.accession_run_id ");
		b.append("     JOIN user_run_accession_run c ");
		b.append("     ON c.accession_run_id = b.id ");
		b.append(" WHERE a.id = ? ");

		if (destination == 2) {
			b.append("     AND user_id = ?");
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
		} else {
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
		}

		// System.out.println("getting accessionList!!!");
		logger.info("getting accessionList!!!");

		String theAccession = null;
		if (theReportInfo.getUpdateVersion() < 3) {
			if (destination == 2) {
				List<Map<String, Object>> rows = null;

				rows = jdbcTemplate.queryForList(b.toString(), lev2Id, userId);

				for (int j = 0; j < rows.size(); j++) {
					String queryAccession = rows.get(j).get("query_accession_id").toString();
					theAccessionList.add(queryAccession);
				}
			} else {
				theAccession = jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID, String.class,
						accessionRunId);
				theAccessionList.add(theAccession);
			}
		} else {
			if (destination == 2) {
				theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, lev2Id, userId);
			} else {
				theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, lev2Id);
			}
			theAccessionList.add(theAccession);
		}

		//System.out.println("theAccessionList.size(): " + theAccessionList.size());
		logger.info("theAccessionList.size(): {}", theAccessionList.size());

		// String dest = null;
		// if (destination == 1) {
		// dest =
		// ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
		// } else {
		// dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
		// }

		for (int j = 0; j < theAccessionList.size(); j++) {

			theAccession = theAccessionList.get(j);

			String outputDirectory = "";
			if (!enableDebugOutput) {
				// FOR STAGING AND PRODUCTION USE
				outputDirectory = dest + "/" + theAccession + "_v" + theReportInfo.getUpdateVersion()
						+ "/Level2Reports/" + ncbiDomainId + "(" + info.getStartPosition() + ")/";
			} else {
				// For debugging only
				outputDirectory = debugReportDir + theAccession + "_v" + theReportInfo.getUpdateVersion()
						+ "/Level2Reports/" + ncbiDomainId + "(" + info.getStartPosition() + ")/";
			}

			String outputReportFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")" + "_Full_v"
					+ theReportInfo.getUpdateVersion() + ".csv";
			String outputPngFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")" + "_Full_v"
					+ theReportInfo.getUpdateVersion() + "_cutoff.png";

			CSVPrinter csvFilePrinter = null;
			CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);

			String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

			File report = new File(outputReportFile);
			if (!report.exists()) {
				// add to zip file
				try {
					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						directory.mkdirs();
					}

					FileWriter fileWriter = new FileWriter(outputReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					csvFilePrinter.printRecord(lev2FullCSVHeader);

					for (LevelTwoReportRow record : theLev2FullReport) {
						List<String> lev2Record = new ArrayList<String>();
						// lev2Record.add("");
						lev2Record.add(String.valueOf(record.getUpdateVersion()));
						lev2Record.add(record.getAccession());
						lev2Record.add(record.getIsDup() == 0 ? "N" : "Y");
						lev2Record.add(String.valueOf(record.getProteinCount()));
						lev2Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev2Record.add(record.getDefaultTaxonomyName());
						lev2Record.add(record.getScientificName());
						lev2Record.add(record.getCommonName());
						lev2Record.add(record.getProteinName());
						lev2Record.add(String.valueOf(record.getPssmId()));
						lev2Record.add(ncbiDomainId);
						lev2Record.add(domainName);
						lev2Record.add(String.valueOf(record.getHitLength()));
						lev2Record.add(String.valueOf(record.getIdentity()));
						lev2Record.add(String.valueOf(record.getPositive()));
						lev2Record.add(String.valueOf(scientificFormatter.format(record.getEvalue())));
						lev2Record.add(twoDecimalDigitFormatter.format(record.getBlastPBitScore()));
						lev2Record.add(record.getOrtholog());
						lev2Record.add(String.valueOf(orthologCount));
						lev2Record.add(String.valueOf(twoDecimalDigitFormatter.format(record.getCutoff())));
						lev2Record.add(
								String.valueOf(twoDecimalDigitFormatter.format(record.getPercentSimilarity() * 100.0)));
						lev2Record.add(record.getSusceptible());
						lev2Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev2Record.add(record.isEukaryote() ? "Y" : "N");
						lev2Record.add("=HYPERLINK(\"https://cfpub.epa.gov/ecotox/explore.cfm?ncbi="
								+ record.getSpeciesTaxId() + "\")");
						lev2Record.add(record.isEcotox() ? timestamp : "-");
						csvFilePrinter.printRecord(lev2Record);
					}

					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.warn("Output report file already exists: {}", outputReportFile);
			}

			File png = new File(outputPngFile);

			if (!png.exists()) {
				// Generate Level 2 Full Cutoff Png
				try {
					// FileWriter fileWriter = new FileWriter(outputPngFile);
					// BufferedWriter bw = new BufferedWriter(fileWriter);
					if (cutoffData != null && !cutoffData.getxData().isEmpty()) {
						ByteArrayOutputStream fullLevTwoPng = createCutoffGraph(2, lev2Id, ReportTypeEnum.Full,
								eukaryotesOnly);
						if (fullLevTwoPng != null) {
							OutputStream os = new FileOutputStream(outputPngFile);
							fullLevTwoPng.writeTo(os);
							os.flush();
							os.close();
							fullLevTwoPng.close();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			LevelTwoReportRow queryRow = null;
			String outputSettingsFile = null;
			File settingsFile = null;

			if (theLev2FullReport.size() > 0) {
				queryRow = theLev2FullReport.get(0);

				outputSettingsFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")" + "_Full_v"
						+ theReportInfo.getUpdateVersion() + "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Query Species", queryRow.getTaxonomyName());
						csvFilePrinter.printRecord("Query Protein", queryRow.getProteinName());
						csvFilePrinter.printRecord("Query Domain", displayText);
						csvFilePrinter.printRecord("Ortholog Count", orthologCount);
						csvFilePrinter.printRecord("L2 Cutoff", "Default");
						csvFilePrinter.printRecord("L2 Cutoff Value",
								(twoDecimalDigitFormatter.format(queryRow.getCutoff())));
						csvFilePrinter.printRecord("E-value", 10);
						csvFilePrinter.printRecord("Sorted by Taxonomic Group", "Class");
						csvFilePrinter.printRecord("Species Read Across", "Y");
						csvFilePrinter.printRecord("Show Only Eukaryotes", "Unchecked");
						csvFilePrinter.printRecord("Report", "Full");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}

				// System.out.println("Finished level 2 full settings file");
				logger.info("Finished level 2 full settings file");
			}

			// Generate Primary Report
			List<LevelTwoReportRow> theLev2PrimaryReport = filterLevTwoPrimaryReport(theLev2FullReport);

			int primaryOrthologCount = returnLevTwoOrthologCount(theLev2PrimaryReport);

			densityRows = getDensityData(2, ReportTypeEnum.Primary, lev2Id, -1, -1, eukaryotesOnly);
			CutoffData primaryCutoffData = CutoffData.newInstance(densityRows, 2); // new
																				// CutoffData(rows);
			// CutoffData primaryCutoffData = getLevelTwoPrimaryCutoff(lev2Id);
			if (primaryCutoffData != null) {
				updateLevTwoSusceptibility(theLev2PrimaryReport, primaryCutoffData.getCutoffValues().get(0)); // Uses
																												// 1st
																												// (default)
																												// cutoff
				// value
			} else {
				// System.out.println("Error:  Primary cutoff data is null for level 2 report!!!  Using cutoff = 100.0");
				logger.error("Error:  Primary cutoff data is null for level 2 report!!!  Using cutoff = 100.0");
				updateLevTwoSusceptibility(theLev2PrimaryReport, 100.0);
			}

			String outputPrimaryReportFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")"
					+ "_Primary_v" + theReportInfo.getUpdateVersion() + ".csv";
			String outputPrimaryPngFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")"
					+ "_Primary_v" + theReportInfo.getUpdateVersion() + "_cutoff.png";
			// convert report to csv
			// CSVPrinter csvFilePrinter = null;

			report = new File(outputPrimaryReportFile);
			if (!report.exists()) {
				// CSVFormat csvFileFormat =
				// CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
				// add to zip file
				try {

					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						directory.mkdirs();
					}

					FileWriter fileWriter = new FileWriter(outputPrimaryReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					csvFilePrinter.printRecord(lev2PrimaryCSVHeader);

					for (LevelTwoReportRow record : theLev2PrimaryReport) {
						List<String> lev2Record = new ArrayList<String>();
						// lev2Record.add("");
						lev2Record.add(String.valueOf(record.getUpdateVersion()));
						lev2Record.add(record.getAccession());
						lev2Record.add(String.valueOf(record.getProteinCount()));
						lev2Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev2Record.add(record.getDefaultTaxonomyName());
						lev2Record.add(record.getTaxonomyName());
						lev2Record.add(record.getScientificName());
						lev2Record.add(record.getCommonName());
						lev2Record.add(record.getProteinName());
						lev2Record.add(domainName);
						lev2Record.add(twoDecimalDigitFormatter.format(record.getBlastPBitScore()));
						lev2Record.add(record.getOrtholog());
						lev2Record.add(String.valueOf(primaryOrthologCount));
						lev2Record.add(String.valueOf(twoDecimalDigitFormatter.format(record.getCutoff())));
						lev2Record.add(
								String.valueOf(twoDecimalDigitFormatter.format(record.getPercentSimilarity() * 100.0)));
						lev2Record.add(record.getSusceptible());
						lev2Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev2Record.add(record.isEukaryote() ? "Y" : "N");
						lev2Record.add("=HYPERLINK(\"https://cfpub.epa.gov/ecotox/explore.cfm?ncbi="
								+ record.getSpeciesTaxId() + "\")");
						lev2Record.add(record.isEcotox() ? timestamp : "-");
						csvFilePrinter.printRecord(lev2Record);
					}

					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.warn("Output report file already exists: {}", outputReportFile);
			}

			png = new File(outputPrimaryPngFile);
			if (!png.exists()) {
				// Generate Level 2 Primary Cutoff Png
				try {
					if (primaryCutoffData != null && !primaryCutoffData.getxData().isEmpty()) {
						ByteArrayOutputStream primaryLevTwoPng = createCutoffGraph(2, lev2Id, ReportTypeEnum.Primary,
								eukaryotesOnly);
						if (primaryLevTwoPng != null) {
							OutputStream os = new FileOutputStream(outputPrimaryPngFile);
							primaryLevTwoPng.writeTo(os);
							os.flush();
							os.close();
							primaryLevTwoPng.close();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (theLev2PrimaryReport.size() > 0) {
				queryRow = theLev2PrimaryReport.get(0);

				outputSettingsFile = outputDirectory + ncbiDomainId + "(" + info.getStartPosition() + ")" + "_Primary_v"
						+ theReportInfo.getUpdateVersion() + "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Query Species", queryRow.getTaxonomyName());
						csvFilePrinter.printRecord("Query Protein", queryRow.getProteinName());
						csvFilePrinter.printRecord("Query Domain", displayText);
						csvFilePrinter.printRecord("Ortholog Count", primaryOrthologCount);
						csvFilePrinter.printRecord("L2 Cutoff", "Default");
						csvFilePrinter.printRecord("L2 Cutoff Value",
								(twoDecimalDigitFormatter.format(queryRow.getCutoff())));
						csvFilePrinter.printRecord("E-value", defaultLevelTwoEvalue);
						csvFilePrinter.printRecord("Sorted by Taxonomic Group", "Class");
						csvFilePrinter.printRecord("Species Read Across", "Y");
						csvFilePrinter.printRecord("Show Only Eukaryotes", "Unchecked");
						csvFilePrinter.printRecord("Report", "Full");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}

				// System.out.println("Finished creating level 2 settings file");
				logger.info("Finished creating level 2 settings file");
			}

		}

	}

	@Override
	public void createLevelThreeReport(int accessionRunId, int lev3RunId, int userId, int destination) {

		String NEW_LINE_SEPARATOR = "\n";
		List<Object> lev3CSVPrimaryHeaderList = new ArrayList<Object>();
		List<Object> lev3CSVFullHeaderList = new ArrayList<Object>();
		lev3CSVPrimaryHeaderList.add("Update Version");
		lev3CSVPrimaryHeaderList.add("NCBI Accession");
		lev3CSVPrimaryHeaderList.add("Protein Count");
		lev3CSVPrimaryHeaderList.add("Species Tax ID");
		lev3CSVPrimaryHeaderList.add("Taxonomic Group");
		lev3CSVPrimaryHeaderList.add("Scientific Name");
		lev3CSVPrimaryHeaderList.add("Common Name");
		lev3CSVPrimaryHeaderList.add("Protein Name");
		lev3CSVPrimaryHeaderList.add("Analysis Completed");
		lev3CSVPrimaryHeaderList.add("Susceptible");

		for (int i = 0; i < lev3CSVPrimaryHeaderList.size(); i++) {
			lev3CSVFullHeaderList.add(lev3CSVPrimaryHeaderList.get(i));
		}

		List<LevelThreeReportRow> theLev3Report = downloadLevel3Complete(accessionRunId, lev3RunId);

		// System.out.println("Finished downloadLevel3Complete");
		logger.info("Finished downloadLevel3Complete");

		LevelThreeRequestableRow info = getLevelThreeInfoByRunId(lev3RunId);

		// Finish building level 3 Primary header
		for (int i = 0; i < theLev3Report.get(0).getResidueResultList().size(); i++) {
			String pos = String.valueOf(i + 1);
			lev3CSVPrimaryHeaderList.add("Position " + pos);
			lev3CSVPrimaryHeaderList.add("Amino Acid " + pos);
			lev3CSVPrimaryHeaderList.add("Total Match " + pos);
		}
		Object[] lev3CSVPrimaryHeader = lev3CSVPrimaryHeaderList.toArray();

		// Finish building level 3 Full header
		for (int i = 0; i < theLev3Report.get(0).getResidueResultList().size(); i++) {
			String pos = String.valueOf(i + 1);
			lev3CSVFullHeaderList.add("Position " + pos);
			lev3CSVFullHeaderList.add("Amino Acid " + pos);
			lev3CSVFullHeaderList.add("Direct Match " + pos);
			lev3CSVFullHeaderList.add("Side Chain " + pos);
			lev3CSVFullHeaderList.add("Side Chain Match " + pos);
			lev3CSVFullHeaderList.add("MW " + pos);
			lev3CSVFullHeaderList.add("MW Match " + pos);
			lev3CSVFullHeaderList.add("Total Match " + pos);
		}
		Object[] lev3CSVFullHeader = lev3CSVFullHeaderList.toArray();

		CSVPrinter csvFilePrinter = null;
		CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);

		// String theAccession =
		// jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID,
		// String.class, accessionRunId);

		ReportInfo theReportInfo = getReportInfo(accessionRunId, -1, lev3RunId, -1);

		String dest = null;
		List<String> theAccessionList = new ArrayList<String>();

		StringBuilder b = new StringBuilder();
		b.append(" SELECT DISTINCT(c.query_accession_id) ");
		b.append(" FROM level3_run a ");
		b.append("     JOIN accession_run b ");
		b.append("     ON b.id = a.accession_run_id ");
		b.append("     JOIN user_run_accession_run c ");
		b.append("     ON c.accession_run_id = b.id ");
		b.append(" WHERE a.id = ? ");

		if (destination == 2) {
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
		} else {
			dest = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
		}

		String theAccession = null;
		if (theReportInfo.getUpdateVersion() < 3) {
			if (destination == 2) {
				b.append("     AND user_id = ?");
				List<Map<String, Object>> rows = null;
				rows = jdbcTemplate.queryForList(b.toString(), lev3RunId, userId);

				for (int j = 0; j < rows.size(); j++) {
					String queryAccession = rows.get(j).get("query_accession_id").toString();
					theAccessionList.add(queryAccession);
				}
			} else {
				theAccession = jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID, String.class,
						accessionRunId);
				theAccessionList.add(theAccession);
			}
		} else {
			if (destination == 2) {
				b.append("     AND user_id = ?");
				theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, lev3RunId, userId);
			} else {

			}
			theAccession = jdbcTemplate.queryForObject(b.toString(), String.class, lev3RunId);
			theAccessionList.add(theAccession);
		}

		List<AminoAcid> aminoAcidInfo = getAminoAcidInfo();

		for (int j = 0; j < theAccessionList.size(); j++) {

			theAccession = theAccessionList.get(j);

			String outputDirectory = "";
			if (!enableDebugOutput) {
				// FOR STAGING AND PRODUCTION USE
				outputDirectory = dest + "/" + theAccession + "_v" + theReportInfo.getUpdateVersion()
						+ "/Level3Reports/";
			} else {
				// For debugging only
				outputDirectory = debugReportDir + theAccession + "_v" + theReportInfo.getUpdateVersion()
						+ "\\Level3Reports\\";
			}

			// Generate primary level 3 report
			String outputReportFile = outputDirectory + info.getJobName() + "(" + lev3RunId + ")" + "_Primary_v"
					+ theReportInfo.getUpdateVersion() + ".csv";

			File report = new File(outputReportFile);
			// System.out.println("checking if directory exists: " + outputDirectory);
			logger.info("checking if directory exists: {}", outputDirectory);
			if (!report.exists()) {
				try {
					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						// System.out.println("   directory does NOT exist....creating it");
						logger.info("   directory does NOT exist....creating it");
						directory.mkdirs();
						// System.out.println("finished creating directory");
						logger.info("finished creating directory");
					}

					// System.out.println("writing to file: " + outputReportFile);
					logger.info("writing to file: {}", outputReportFile);

					// System.out.println("opening fileWriter");
					logger.info("opening fileWriter");
					FileWriter fileWriter = new FileWriter(outputReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					// System.out.println("printing header");
					logger.info("printing header");
					csvFilePrinter.printRecord(lev3CSVPrimaryHeader);

					for (LevelThreeReportRow record : theLev3Report) {
						determineLevelThreeMatches(theLev3Report);
						List<String> lev3Record = new ArrayList<String>();
						lev3Record.add(String.valueOf(record.getUpdateVersion()));
						lev3Record.add(record.getAccession());
						lev3Record.add(String.valueOf(record.getProteinCount()));
						lev3Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev3Record.add(record.getTaxonomyName());
						lev3Record.add(record.getScientificName());
						lev3Record.add(record.getCommonName());
						lev3Record.add(record.getProteinName());
						lev3Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev3Record.add(record.getSusceptible());
						for (LevelThreeResidueResult res : record.getResidueResultList()) {
							if (res.getAminoAcid() == null) {
								lev3Record.add("-");
								lev3Record.add("-");
								lev3Record.add("N");
							} else {
								lev3Record.add(String.valueOf(res.getPosition()));
								lev3Record.add(String.valueOf(res.getAminoAcid().getId()));
								lev3Record.add(convertBooleanToYN(res.getTotalMatch()));
							}

						}

						csvFilePrinter.printRecord(lev3Record);
					}
					// System.out.println("flushing and closing");
					logger.info("flushing and closing");
					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

					// System.out.println("all closed");
					logger.info("all closed");

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.warn("Output report file already exists: {}", outputReportFile);
			}

			LevelThreeReportRow queryRow = null;
			String outputSettingsFile = null;
			File settingsFile = null;

			if (theLev3Report.size() > 0) {
				queryRow = theLev3Report.get(0);

				outputSettingsFile = outputDirectory + info.getJobName() + "(" + lev3RunId + ")" + "_Primary_v"
						+ theReportInfo.getUpdateVersion() + "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Level 3 Run Name", info.getJobName());
						csvFilePrinter.printRecord("Template Species", info.getTemplate());
						csvFilePrinter.printRecord("Query Residues", "All");
						csvFilePrinter.printRecord("Query Accession", theAccession);
						// csvFilePrinter.printRecord("Ortholog Count","");
						csvFilePrinter.printRecord("Report", "Primary");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}

				// System.out.println("Finished level 3 primary settings file");
				logger.info("Finished level 3 primary settings file");
			}

			// Generate full level 3 report
			outputReportFile = outputDirectory + info.getJobName() + "(" + lev3RunId + ")" + "_Full_v"
					+ theReportInfo.getUpdateVersion() + ".csv";

			report = new File(outputReportFile);
			// System.out.println("checking if directory exists: " + outputDirectory);
			logger.info("checking if directory exists: {}", outputDirectory);
			if (!report.exists()) {
				try {
					File directory = new File(String.valueOf(outputDirectory));
					if (!directory.exists()) {
						// System.out.println("   directory does NOT exist....creating it");
						logger.info("   directory does NOT exist....creating it");
						directory.mkdirs();
						// System.out.println("finished creating directory");
						logger.info("finished creating directory");
					}

					// System.out.println("writing to file: " + outputReportFile);
					logger.info("writing to file: {}", outputReportFile);

					// System.out.println("opening fileWriter");
					logger.info("opening fileWriter");
					FileWriter fileWriter = new FileWriter(outputReportFile);
					csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
					// System.out.println("printing header");
					logger.info("printing header");
					csvFilePrinter.printRecord(lev3CSVFullHeader);

					for (LevelThreeReportRow record : theLev3Report) {
						determineLevelThreeMatches(theLev3Report);
						List<String> lev3Record = new ArrayList<String>();
						lev3Record.add(String.valueOf(record.getUpdateVersion()));
						lev3Record.add(record.getAccession());
						lev3Record.add(String.valueOf(record.getProteinCount()));
						lev3Record.add(String.valueOf(record.getSpeciesTaxId()));
						lev3Record.add(record.getTaxonomyName());
						lev3Record.add(record.getScientificName());
						lev3Record.add(record.getCommonName());
						lev3Record.add(record.getProteinName());
						lev3Record.add(convertUnixTimeToDate(record.getEndDate()));
						lev3Record.add(record.getSusceptible());
						for (LevelThreeResidueResult res : record.getResidueResultList()) {
							if (res.getAminoAcid() == null) {
								lev3Record.add("-");
								lev3Record.add("-");
								lev3Record.add("N"); // direct match
								lev3Record.add("-"); // side chain
								lev3Record.add("N"); // side chain match
								lev3Record.add("-"); // mw
								lev3Record.add("N"); // mw match
								lev3Record.add("N");
							} else {
								lev3Record.add(String.valueOf(res.getPosition()));
								lev3Record.add(String.valueOf(res.getAminoAcid().getId()));
								lev3Record.add(convertBooleanToYN(res.getDirectMatch())); // direct
																							// match
								lev3Record.add(res.getAminoAcid().getSideChain()); // side
																					// chain
								lev3Record.add(convertBooleanToYN(res.getSideChainMatch())); // side
																								// chain
																								// match
								lev3Record.add(String.valueOf(res.getAminoAcid().getSize())); // mw
								lev3Record.add(convertBooleanToYN(res.getSizeMatch())); // mw
																						// match
								lev3Record.add(convertBooleanToYN(res.getTotalMatch()));
							}

						}

						csvFilePrinter.printRecord(lev3Record);
					}
					// System.out.println("flushing and closing");
					logger.info("flushing and closing");
					fileWriter.flush();
					fileWriter.close();
					csvFilePrinter.close();

					// System.out.println("all closed");
					logger.info("all closed");

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				// System.out.println("Output report file already exists: " + outputReportFile);
				logger.warn("Output report file already exists: {}", outputReportFile);
			}

			if (theLev3Report.size() > 0) {
				queryRow = theLev3Report.get(0);

				outputSettingsFile = outputDirectory + info.getJobName() + "(" + lev3RunId + ")" + "_Full_v"
						+ theReportInfo.getUpdateVersion() + "_ReportSettings.csv";

				settingsFile = new File(outputSettingsFile);
				if (!settingsFile.exists()) {
					try {

						File directory = new File(String.valueOf(outputDirectory));
						if (!directory.exists()) {
							directory.mkdirs();
						}

						FileWriter fileWriter = new FileWriter(outputSettingsFile);
						csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);

						csvFilePrinter.printRecord("Level 1 Report Settings");
						csvFilePrinter.printRecord("");
						csvFilePrinter.printRecord("Analysis TimeStamp", convertUnixTimeToDate(queryRow.getEndDate()));
						csvFilePrinter.printRecord("SeqAPASS version", queryRow.getUpdateVersion());
						csvFilePrinter.printRecord("Level 3 Run Name", info.getJobName());
						csvFilePrinter.printRecord("Template Species", info.getTemplate());
						csvFilePrinter.printRecord("Query Residues", "All");
						csvFilePrinter.printRecord("Query Accession", theAccession);
						csvFilePrinter.printRecord("Ortholog Count", "");
						csvFilePrinter.printRecord("Report", "Full");

						fileWriter.flush();
						fileWriter.close();
						csvFilePrinter.close();
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
				}

			}
		}

	}

	public String convertBooleanToYN(boolean val) {
		if (val) {
			return "Y";
		}
		return "N";
	}

	// public void createSoftLink(int accessionRunId, String queryAccession,
	// String canonicalAccession, int destination) {
	//
	// ReportInfo reportInfo = getReportInfo(accessionRunId, -1, -1);
	// int updateVersion = reportInfo.getUpdateVersion();
	//
	// String dest = null;
	// if (destination == 2) {
	// dest = ncbiKeeper.getPreferredNCBIProvider().getPathPublicReports();
	// } else {
	// dest = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports();
	// }
	//
	// String linkName = queryAccession + "_v" + updateVersion + "/";
	// String targetName = canonicalAccession + "_v" + updateVersion + "/";
	// String directory = dest + "/" + linkName;
	// File linkFile = new File(directory);
	// // Check if softlink already exists....if not create it
	// if (!linkFile.exists()) {
	// // create link in seqapass_reports directory
	// String targetFile = dest + "/" + targetName;
	// try {
	// Files.createSymbolicLink(Paths.get(directory), Paths.get(targetFile));
	// } catch (IOException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }
	// }

	// Old method description
	// This method creates either a softlink or a directory of reports (CSV and
	// * PNG files) for a given accessionRunId. If the userId <= 0 : creates a
	// * directory of reports(if it doesn't already exist) for the canonical
	// * accession corresponding to the accessionRunId in the seqapass_reports
	// * directory. It then creates softlinks to each of the queryAccessions
	// that
	// * correspond to this canonical accession If the userId <= 0
	/**
	 * This method has been modified so that it no longer creates softlinks. For
	 * any given userId it creates a directory of reports (if it doesn't already
	 * exist) for the query accession corresponding to the accessionRunId in the
	 * seqapass_reports directory.
	 * 
	 * @param accessionRunId
	 * @param userId
	 */
	@Override
	public void createReportsAndLinksForRun(int accessionRunId, int userId, boolean createLevelOne,
			boolean createLevelTwo, boolean createLevelThree) {

		// get the query accession id
		List<Map<String, Object>> rows = null;
		StringBuilder b = new StringBuilder();
		b.append(" SELECT a.query_accession_id ");
		b.append(" FROM user_run_accession_run a");
		if (userId > 0) {
			b.append(" JOIN user_run b ON b.id = a.user_run_id ");
		}
		b.append(" WHERE a.accession_run_id = ? ");

		if (userId <= 0) {
			rows = jdbcTemplate.queryForList(b.toString(), accessionRunId);
		} else {
			b.append(" AND b.user_id = ?");
			// System.out.println("user query is : " + b.toString());
			logger.info("user query is : {}", b.toString());
			rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, userId);
		}

		// String canonicalAccession =
		// jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID,
		// String.class,
		// accessionRunId);
		//
		// System.out.println("Canonical accession: " + canonicalAccession);

		createCanonicalOrQueryReportsForRun(accessionRunId, userId, createLevelOne, createLevelTwo, createLevelThree);
		// // create canonical for seqapass_reports directory
		// if (userId <= 0) {
		// createCanonicalOrQueryReportsForRun(accessionRunId, -1);
		// }
		//
		// System.out.println("Number of rows: " + rows.size());
		//
		// for (int j = 0; j < rows.size(); j++) {
		// String queryAccession =
		// rows.get(j).get("query_accession_id").toString();
		// System.out.println("Query accession: " + queryAccession);
		// if (userId <= 0) {
		// if (!canonicalAccession.equals(queryAccession)) {
		// // create soft link for seqapass_reports directory
		// System.out.println("Creating soft link");
		// createSoftLink(accessionRunId, queryAccession, canonicalAccession,
		// 1);
		// }
		// } else {
		// // create reports for public_reports directory
		// System.out.println("Creating public report");
		// createCanonicalOrQueryReportsForRun(accessionRunId, userId); //
		// creates
		// // query
		// // accession
		// // report
		// // not
		// // canonical
		// }
		// }

		// System.out.println("Canonical accession: " + canonicalAccession);

	}

	public void createCanonicalOrQueryReportsForRun(int accessionRunId, int userId, boolean createLevelOne,
			boolean createLevelTwo, boolean createLevelThree) {

		// System.out.println("Inside createCanonicalOrQueryReportsForRun with accRunId:" + accessionRunId + ", userId:" + userId);
		logger.info("Inside createCanonicalOrQueryReportsForRun with accRunId: {}, userId: {}", 
				accessionRunId, userId);

		// currently userId is -1, unless toxcast user
		int destination = 1;
		if (userId > 0) {
			destination = 2;
		}

		if (createLevelOne) {
			createLevelOneReports(accessionRunId, userId, destination);
		}

		StringBuilder b = new StringBuilder();
		if (createLevelTwo) {
			List<Map<String, Object>> lev2Runs = null;

			// get all lev2run ids for accessionRunId
			b.delete(0, b.length());
			if (userId <= 0) {
				b.append(" SELECT id FROM level2_run WHERE accession_run_id = ?");
				lev2Runs = jdbcTemplate.queryForList(b.toString(), accessionRunId);
			} else {
				b.append(" SELECT id FROM level2_run WHERE accession_run_id = ? AND user_id = ?");
				lev2Runs = jdbcTemplate.queryForList(b.toString(), accessionRunId, userId);
			}
			for (int i = 0; i < lev2Runs.size(); i++) {
				Map<String, Object> row = lev2Runs.get(i);
				int lev2Id = (int) row.get("id");
				createLevelTwoReports(accessionRunId, lev2Id, userId, destination);
			}
		}

		if (createLevelThree) {
			List<Map<String, Object>> lev3Runs = null;
			// get all lev3run ids for accessionRunId
			b.delete(0, b.length());
			if (userId <= 0) {
				b.append(" SELECT id FROM level3_run WHERE accession_run_id = ?");
				lev3Runs = jdbcTemplate.queryForList(b.toString(), accessionRunId);
			} else {
				b.append(" SELECT id FROM level3_run WHERE accession_run_id = ? AND user_id = ?");
				lev3Runs = jdbcTemplate.queryForList(b.toString(), accessionRunId, userId);
			}
			for (int i = 0; i < lev3Runs.size(); i++) {
				Map<String, Object> row = lev3Runs.get(i);
				int lev3Id = (int) row.get("id");
				createLevelThreeReport(accessionRunId, lev3Id, userId, destination);
			}
		}
	}

	@Override
	public void createAllReports(boolean createLevelOne, boolean createLevelTwo, boolean createLevelThree) {

		List<Integer> failedSeqapassReports = new ArrayList<Integer>();
		List<Integer> failedPublicReports = new ArrayList<Integer>();

		// create all reports for seqapass reports destination
		StringBuilder b = new StringBuilder();
		b.append(" SELECT DISTINCT(id) FROM accession_run ORDER BY id ASC");
		List<Map<String, Object>> lev1Runs = jdbcTemplate.queryForList(b.toString());
		for (int i = 0; i < lev1Runs.size(); i++) {
			Map<String, Object> row = lev1Runs.get(i);
			int accessionRunId = (int) row.get("id");
			try {
				createReportsAndLinksForRun(accessionRunId, -1, createLevelOne, createLevelTwo, createLevelThree); // this
																													// creates
				// reports
				// for
				// seqapass_reports
				// destination
			} catch (Exception e) {
				failedSeqapassReports.add(accessionRunId);
			}
		}

		// create reports for toxcast user in public_reports destination
		int toxCastUserId = getToxCastUserId();
		b.delete(0, b.length());
		b.append(" SELECT ");
		b.append("     a.id");
		b.append(" FROM ");
		b.append("     accession_run a ");
		b.append("         JOIN ");
		b.append("     user_run_accession_run b ON a.id = b.accession_run_id ");
		b.append("     JOIN user_run c ON c.id = b.user_run_id ");
		b.append("     where c.user_id = ? ");

		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		rows = jdbcTemplate.queryForList(b.toString(), toxCastUserId);
		for (Map<String, Object> row : rows) {
			int accessionRunId = (int) row.get("id");
			try {
				createReportsAndLinksForRun(accessionRunId, toxCastUserId, createLevelOne, createLevelTwo,
						createLevelThree);
			} catch (Exception e) {
				failedPublicReports.add(accessionRunId);
			}
		}

		if (failedSeqapassReports.size() > 0) {
			// System.out.println("createAllReports: The following reports failed to generate in seqapass_reports directory:");
			logger.error("createAllReports: The following reports failed to generate in seqapass_reports directory:");
			for (Integer accRunId : failedSeqapassReports) {
				// System.out.println(accRunId);
				logger.error(accRunId);
			}
		}

		if (failedPublicReports.size() > 0) {
			// System.out.println("createAllReports: The following reports failed to generate in public_reports directory:");
			logger.error("createAllReports: The following reports failed to generate in public_reports directory:");
			for (Integer accRunId : failedPublicReports) {
				logger.error(accRunId);
			}
		}

	}

	@Override
	public void createAllReportsForRun(int accessionRunId, boolean createLevelOne, boolean createLevelTwo,
			boolean createLevelThree) {
		// create all reports for seqapass reports destination
		try {
			createReportsAndLinksForRun(accessionRunId, -1, createLevelOne, createLevelTwo, createLevelThree); // this
																												// creates
			// reports for
			// seqapass_reports
			// destination
		} catch (Exception e) {
			// System.out.println("Failed to generate report in seqapass_reports directory for accessionRunId: " + accessionRunId);
			logger.error("Failed to generate report in seqapass_reports directory for accessionRunId: {}", 
					accessionRunId);
		}

		// create reports for toxcast user in public_reports destination
		// this will check if accessionRunId has been run by taxCastUserId
		// if so, report will be created in public_reports directory
		// if not, no report is created
		try {
			int toxCastUserId = getToxCastUserId();
			createReportsAndLinksForRun(accessionRunId, toxCastUserId, createLevelOne, createLevelTwo,
					createLevelThree);
		} catch (Exception e) {
			// System.out.println("Failed to generate report in public_reports directory for accessionRunId: " + accessionRunId);
			logger.error("Failed to generate report in public_reports directory for accessionRunId: {}", 
					accessionRunId);
		}
	}

	@Override
	public void createAllReportsForRange(int start, int end, boolean createLevelOne, boolean createLevelTwo,
			boolean createLevelThree) {

		List<Integer> failedSeqapassReports = new ArrayList<Integer>();
		List<Integer> failedPublicReports = new ArrayList<Integer>();
		// get all accessionRunIds
		StringBuilder b = new StringBuilder();
		b.append(" SELECT DISTINCT(id) FROM accession_run ");
		b.append(" WHERE id >= ? AND id <= ? ORDER BY id ASC");
		List<Map<String, Object>> lev1Runs = jdbcTemplate.queryForList(b.toString(), start, end);
		for (int i = 0; i < lev1Runs.size(); i++) {
			Map<String, Object> row = lev1Runs.get(i);
			int accessionRunId = (int) row.get("id");
			try {
				createReportsAndLinksForRun(accessionRunId, -1, createLevelOne, createLevelTwo, createLevelThree);
			} catch (Exception e) {
				failedSeqapassReports.add(accessionRunId);
			}
		}

		int toxCastUserId = getToxCastUserId();
		b.delete(0, b.length());
		b.append(" SELECT ");
		b.append("     a.id");
		b.append(" FROM ");
		b.append("     accession_run a ");
		b.append("         JOIN ");
		b.append("     user_run_accession_run b ON a.id = b.accession_run_id ");
		b.append("     JOIN user_run c ON c.id = b.user_run_id ");
		b.append("     where c.user_id = ? ");
		b.append("     AND a.id >= ? AND a.id <= ? ORDER BY id ASC ");

		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		rows = jdbcTemplate.queryForList(b.toString(), toxCastUserId, start, end);
		for (Map<String, Object> row : rows) {
			int accessionRunId = (int) row.get("id");
			try {
				createReportsAndLinksForRun(accessionRunId, toxCastUserId, createLevelOne, createLevelTwo,
						createLevelThree); // this
				// creates
				// reports
				// for
				// public_reports
				// destination
			} catch (Exception e) {
				failedPublicReports.add(accessionRunId);
			}
		}

		if (failedSeqapassReports.size() > 0) {
			// System.out.println("createAllReportsForRange: The following reports failed to generate in seqapass_reports directory:");
			logger.error("createAllReportsForRange: The following reports failed to generate in seqapass_reports directory:");
			for (Integer accRunId : failedSeqapassReports) {
				// System.out.println(accRunId);
				logger.error(accRunId);
			}
		}

		if (failedPublicReports.size() > 0) {
			// System.out.println("createAllReportsForRange: The following reports failed to generate in public_reports directory:");
			logger.error("createAllReportsForRange: The following reports failed to generate in public_reports directory:");
			for (Integer accRunId : failedPublicReports) {
				// System.out.println(accRunId);
				logger.error(accRunId);
			}
		}

	}

	/**
	 * Constructs the primary level one report based on user selected eValue and
	 * common domain count
	 * 
	 * @param fullList
	 *            - List of LevelOneReportRow objects in full level one report
	 * @return primaryList - List of LevelOneReportRow objects in primary level
	 *         one report
	 */
	public List<LevelOneReportRow> filterLevOnePrimaryReport(List<LevelOneReportRow> fullList) {
		int primaryLevOneCommonDomainLimit = 1;
		double primaryLevOneEvalueLimit = 0.01;

		List<LevelOneReportRow> primaryList = new ArrayList<LevelOneReportRow>();

		for (LevelOneReportRow row : fullList) {
			if (row.getCommonDomainCount() >= primaryLevOneCommonDomainLimit
					&& row.getEvalue() <= primaryLevOneEvalueLimit) {
				primaryList.add(LevelOneReportRow.newInstance(row));
			}
		}

		return primaryList;
	}

	/**
	 * Constructs the primary level two report based on user selected eValue
	 * 
	 * @param fullList
	 *            - List of LevelTwoReportRow objects in full level two report
	 * @return - List of LevelTwoReportRow objects in primary level two report
	 */
	public List<LevelTwoReportRow> filterLevTwoPrimaryReport(List<LevelTwoReportRow> fullList) {

		int primaryLevTwoEvalueLimit = 10;

		List<LevelTwoReportRow> primaryList = new ArrayList<LevelTwoReportRow>();

		for (LevelTwoReportRow row : fullList) {
			if (row.getEvalue() <= primaryLevTwoEvalueLimit) {
				primaryList.add(LevelTwoReportRow.newInstance(row));
			}
		}

		return primaryList;
	}

	NumberFormat scientificFormatter = new DecimalFormat("0.000E0");
	DecimalFormat oneDecimalDigitFormatter = new DecimalFormat("#.0");
	DecimalFormat twoDecimalDigitFormatter = new DecimalFormat("#.00");

	/**
	 * this method formats a timestamp(long) as a string in "yyyy MM dd
	 * HH:mm:ss" format
	 * 
	 * @param time
	 *            (long)
	 * @return the formatted date as a string
	 */
	public String convertUnixTimeToDate(long time) {
		if (time != 0) {
			time *= 1000; // Unix_timestamp give seconds, we need milliseconds
			Date date = new Date(time);
			Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
			return format.format(date);
		} else
			return "Not Finished";
	}

	/**
	 * Returns number of orthologs in given level 1 full or primary report
	 * 
	 * @param lev1Report
	 *            - list of LevelOneReportRow objects
	 * @return number of orthologs in report
	 */
	public int returnLevOneOrthologCount(List<LevelOneReportRow> lev1Report) {
		// get ortholog count
		int orthologCount = 0;
		for (LevelOneReportRow row : lev1Report) {
			if (row.getOrtholog().toLowerCase().equals("y")) {
				orthologCount++;
			}
		}
		if (orthologCount <= 0) {
			// setOrtholog_count(0);
			return 0;
		} else {
			return orthologCount - 1;
		}
	}

	/**
	 * Returns number of orthologs in given level 2 full or primary report
	 * 
	 * @param lev2Report
	 *            - list of LevelTwoReportRow objects
	 * @return number of orthologs in report
	 */
	public int returnLevTwoOrthologCount(List<LevelTwoReportRow> lev2Report) {
		// get ortholog count
		int orthologCount = 0;
		for (LevelTwoReportRow row : lev2Report) {
			if (row.getOrtholog().toLowerCase().equals("y")) {
				orthologCount++;
			}
		}
		if (orthologCount <= 0) {
			// setOrtholog_count(0);
			return 0;
		} else {
			return orthologCount - 1;
		}

	}

	/**
	 * Updates level one susceptibility determination based on current cutoff
	 * and choice of species read across
	 * 
	 * @param lev1Report
	 *            - list of levelOneReportRow objects
	 * @param cutoff
	 *            - cutoff value (double)
	 * @param useDefaultSpeciesReadAcross
	 *            - boolean specifying whether to use species read-across when
	 *            determining susceptibility
	 */
	public void updateLevOneSusceptibility(List<LevelOneReportRow> lev1Report, double cutoff) {

		List<Integer> badTaxGroupIds = getBadTaxGroupIds();
		// System.out.println("inside updateLevOneSusceptibility with cutoff: " + cutoff + " for " + lev1Report.size() + " rows");
		logger.info("inside updateLevOneSusceptibility with cutoff: {} for {} rows", 
				cutoff, lev1Report.size());

		Set<Integer> susceptibleGroups = new HashSet<Integer>();
		for (LevelOneReportRow row : lev1Report) {
			row.setCutoff(cutoff);
			// check if ortholog
			if (row.getOrtholog().toLowerCase().equals("y")) {
				row.setSusceptible("Y");
				if (100 * row.getPercentSimilarity() > cutoff) {
					int rowTaxGroupId = row.getTaxonomyTaxid();
					if (!badTaxGroupIds.contains(rowTaxGroupId)) {
						susceptibleGroups.add(rowTaxGroupId);
					}
				}
			}
			// check if percSim > cutoff
			else if (100 * row.getPercentSimilarity() > cutoff) {
				row.setSusceptible("Y");
				int rowTaxGroupId = row.getTaxonomyTaxid();
				if (!badTaxGroupIds.contains(rowTaxGroupId)) {
					susceptibleGroups.add(rowTaxGroupId);
				}
			} else {
				row.setSusceptible("N");
			}
		}

		// System.out.println("finished ortholog & percSim determination");
		logger.info("finished ortholog & percSim determination");

		// check for same tax group as previously determined susceptible tax
		// group
		for (LevelOneReportRow row : lev1Report) {
			if (susceptibleGroups.contains(row.getTaxonomyTaxid())) {
				row.setSusceptible("Y");
			}
		}

		// Set<Integer> susceptibleGroups = new HashSet<Integer>();
		// for (LevelOneReportRow row : lev1Report) {
		// row.setCutoff(cutoff);
		// // check if ortholog
		// if (row.getOrtholog().toLowerCase().equals("y")) {
		// row.setSusceptible("Y");
		// if (100 * row.getPercentSimilarity() > cutoff) {
		// susceptibleGroups.add(row.getTaxonomyTaxid());
		// }
		// }
		// // check if percSim > cutoff
		// else if (100 * row.getPercentSimilarity() > cutoff) {
		// row.setSusceptible("Y");
		// susceptibleGroups.add(row.getTaxonomyTaxid());
		// } else {
		// row.setSusceptible("N");
		// }
		// }
		//
		// System.out.println("finished ortholog & percSim determination");
		//
		// // check for same tax group as previously determined susceptible tax
		// group
		// for (LevelOneReportRow row : lev1Report) {
		// if (susceptibleGroups.contains(row.getTaxonomyTaxid())) {
		// row.setSusceptible("Y");
		// }
		// }

		// System.out.println("finished species read-across");
		logger.info("finished species read-across");
	}

	/**
	 * Updates level two susceptibility determination based on current cutoff
	 * and choice of species read across
	 * 
	 * @param lev2Report
	 *            - list of levelTwoReportRow objects
	 * @param cutoff
	 *            - cutoff value (double)
	 * @param useDefaultSpeciesReadAcross
	 *            - boolean specifying whether to use species read-across when
	 *            determining susceptibility
	 */
	public void updateLevTwoSusceptibility(List<LevelTwoReportRow> lev2Report, double cutoff) {

		List<Integer> badTaxGroupIds = getBadTaxGroupIds();

		Set<Integer> susceptibleGroups = new HashSet<Integer>();
		for (LevelTwoReportRow row : lev2Report) {
			row.setCutoff(cutoff);
			// check if ortholog
			if (row.getOrtholog().toLowerCase().equals("y")) {
				row.setSusceptible("Y");
				if (100 * row.getPercentSimilarity() > cutoff) {
					row.setSusceptible("Y");
					if (100 * row.getPercentSimilarity() > cutoff) {
						int rowTaxGroupId = row.getTaxonomyTaxid();
						if (!badTaxGroupIds.contains(rowTaxGroupId)) {
							susceptibleGroups.add(rowTaxGroupId);
						}
					}
				}
			}
			// check if percSim > cutoff
			else if (100 * row.getPercentSimilarity() > cutoff) {
				row.setSusceptible("Y");
				row.setSusceptible("Y");
				if (100 * row.getPercentSimilarity() > cutoff) {
					int rowTaxGroupId = row.getTaxonomyTaxid();
					if (!badTaxGroupIds.contains(rowTaxGroupId)) {
						susceptibleGroups.add(rowTaxGroupId);
					}
				}
			} else {
				row.setSusceptible("N");
			}
		}

		// check for same tax group as previously determined susceptible tax
		// group
		for (LevelTwoReportRow row : lev2Report) {
			if (susceptibleGroups.contains(row.getTaxonomyTaxid())) {
				row.setSusceptible("Y");
			}
		}

		// Set<Integer> susceptibleGroups = new HashSet<Integer>();
		// for (LevelTwoReportRow row : lev2Report) {
		// row.setCutoff(cutoff);
		// // check if ortholog
		// if (row.getOrtholog().toLowerCase().equals("y")) {
		// row.setSusceptible("Y");
		// if (100 * row.getPercentSimilarity() > cutoff) {
		// susceptibleGroups.add(row.getTaxonomyTaxid());
		// }
		// }
		// // check if percSim > cutoff
		// else if (100 * row.getPercentSimilarity() > cutoff) {
		// row.setSusceptible("Y");
		// susceptibleGroups.add(row.getTaxonomyTaxid());
		// } else {
		// row.setSusceptible("N");
		// }
		// }
		//
		// // check for same tax group as previously determined susceptible tax
		// group
		// for (LevelTwoReportRow row : lev2Report) {
		// if (susceptibleGroups.contains(row.getTaxonomyTaxid())) {
		// row.setSusceptible("Y");
		// }
		// }
	}

	/**
	 * Generates Level 1 or 2 cutoff png binary for adding to report download
	 * 
	 * @param level
	 * @param id
	 *            (accessionRunId:lev2RunId for level 1:2 respectively)
	 * @param reportType
	 *            (1 = primary, 2 = full)
	 * 
	 */
	public ByteArrayOutputStream createCutoffGraph(int level, int id, ReportTypeEnum reportType,
			boolean eukaryotesOnly) {

		ChartPanel chartPanel = createCutoffChart(level, id, reportType, eukaryotesOnly);

		if (chartPanel != null) {

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				ChartUtilities.writeChartAsPNG(out, chartPanel.getChart(), 560, 367);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return out;
		} else {
			return null;
		}
	}

	// /**
	// *
	// * @param level
	// * - cutoff level 1 or 2
	// * @param id
	// * - accessionRunId for level 1, lev2RunId for level 2
	// * @param reportType
	// * - 0 for full report, 1 for primary report
	// * @param saveToView
	// * - boolean specifying whether reportView cutoff variables should be
	// updated
	// * @return ChartPanel object containing cutoff graph
	// */
	// public ChartPanel createCutoffChart(int level, int id, int reportType,
	// boolean saveToView) {
	//
	// CutoffData cutData = null;
	// if (level == 1) {
	// if (reportType == 1) {
	// // cutData = getLevelOnePrimaryCutoff(id);
	// cutData = CutoffData.newInstance(densityRows);
	// } else {
	// cutData = getLevelOneFullCutoff(id);
	// }
	// } else {
	// if (reportType == 1) {
	// cutData = getLevelTwoPrimaryCutoff(id);
	// } else {
	// cutData = getLevelTwoFullCutoff(id);
	// }
	// }
	//
	// if (cutData == null)
	// return null;
	//
	// ChartPanel chartPanel = cutData.genCutoffChart();
	//
	// return chartPanel;
	// }

	/**
	 * 
	 * @param level
	 *            - cutoff level 1 or 2
	 * @param id
	 *            - accessionRunId for level 1, lev2RunId for level 2
	 * @param reportType
	 *            - 0 for full report, 1 for primary report
	 * @param saveToView
	 *            - boolean specifying whether reportView cutoff variables
	 *            should be updated
	 * @return ChartPanel object containing cutoff graph
	 */
	public ChartPanel createCutoffChart(int level, int id, ReportTypeEnum reportType, boolean eukaryotesOnly) {

		double eValue = -1;
		int commonDomains = -1;
		if (level == 1) {
			eValue = defaultLevelOneEvalue;
			if (reportType == ReportTypeEnum.Primary) {
				commonDomains = defaultCommonDomains;
			}
		} else {
			eValue = defaultLevelTwoEvalue;
		}

		List<DensityRow> densityData = getDensityData(level, reportType, id, eValue, commonDomains, eukaryotesOnly);
		CutoffData cutData = CutoffData.newInstance(densityData, level);
		// CutoffData cutData = null;
		// if (level == 1) {
		// if (reportType == 1) {
		// // cutData = getLevelOnePrimaryCutoff(id);
		// cutData = CutoffData.newInstance(densityRows);
		// } else {
		// cutData = getLevelOneFullCutoff(id);
		// }
		// } else {
		// if (reportType == 1) {
		// cutData = getLevelTwoPrimaryCutoff(id);
		// } else {
		// cutData = getLevelTwoFullCutoff(id);
		// }
		// }

		if (cutData == null)
			return null;

		ChartPanel chartPanel = cutData.genCutoffChart(level);

		return chartPanel;
	}

	/**
	 * This method returns a level 3 report (List<LevelThreeReportRow>)
	 * containing all available residues. This method is intended to be used
	 * when downloading multiple reports without viewing via a datatable. It
	 * does not set up any of the view variables required for level3report.xhtml
	 */
	public List<LevelThreeReportRow> downloadLevel3Complete(int accessionRunId, int lev3RunId) {

		// Get all sequence and residue position info

		// Get template sequence, split to list while adding position and left
		// padding to strings
		String sequence = getLevelThreeSequence(lev3RunId);
		// 1st integer represents location with all (-) removed, 2nd integer
		// represents actual location in sequence returned from cobalt,
		Map<Integer, Integer> thisSequenceMap = new LinkedHashMap<Integer, Integer>();
		thisSequenceMap.clear();
		List<String> pickListSource = new ArrayList<String>();
		int longest = String.valueOf(sequence.length()).length() + 1;
		String formatStatement = "%" + longest + "s";
		int loc = 0;
		for (int i = 0; i < sequence.length(); i++) {
			if (!Character.toString(sequence.charAt(i)).equals("-")) {
				loc++;
				String newString = String.valueOf(loc) + sequence.charAt(i);
				newString = String.format(formatStatement, newString);
				pickListSource.add(String.format(formatStatement, newString));
				thisSequenceMap.put(loc, i + 1);
			}
		}
		// Populate list that contains actual position in sequence
		List<Integer> positionList = new ArrayList<Integer>();
		for (String row : pickListSource) {
			int actualPosition = thisSequenceMap.get(Integer.parseInt(row.replaceAll("[^0-9]", "")));
			positionList.add(actualPosition);
		}

		LevelThreeViewRequest request = new LevelThreeViewRequest(lev3RunId, positionList);
		// Get level 3 report
		List<LevelThreeReportRow> thisLev3Report = getLevelThreeReportComplete(request);
//		List<LevelThreeReportRow> thisLev3Report = getLevelThreeReport(request);
		// Get corresponding level 1 report
		List<LevelOneReportRow> thisLevelOneReport = getLevelOneReport(accessionRunId);

		// System.out.println("Finished getting level 1 report corresponding to level 3 run");
		logger.info("Finished getting level 1 report corresponding to level 3 run");

		// Set level 1 info on level 3 report
		for (LevelOneReportRow oneRow : thisLevelOneReport) {
			for (LevelThreeReportRow threeRow : thisLev3Report) {
				if (threeRow.getAccession().equals(oneRow.getAccession())) {
					threeRow.setSpeciesTaxId(oneRow.getSpeciesTaxId());
					threeRow.setProteinCount(oneRow.getProteinCount());
					threeRow.setTaxonomyName(oneRow.getTaxonomyName());
					threeRow.setScientificName(oneRow.getScientificName());
					threeRow.setCommonName(oneRow.getCommonName());
					threeRow.setProteinName(oneRow.getProteinName());
				}
			}
		}

		determineLevelThreeMatches(thisLev3Report);

		return thisLev3Report;

	}

	@Override
	public byte[] requestZippedReports(List<ZipRequestable> zipRequests) {

		// System.out.println("Inside requestZippedReports");
		logger.info("Inside requestZippedReports");

		ZipOutputStream zos = null;
		ByteArrayOutputStream byteArrayOutputStream = null;
		if (zipRequests != null && zipRequests.size() > 0) {
			String srcDirectory = ncbiKeeper.getPreferredNCBIProvider().getPathSeqapassReports(); // always
																									// uses
																									// seqapass_reports
			byteArrayOutputStream = new ByteArrayOutputStream();
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream);
			zos = new ZipOutputStream(bufferedOutputStream);

			for (ZipRequestable zipRequestable : zipRequests) {
				int accRunId = zipRequestable.getAccessionRunId();
				int userId = zipRequestable.getUserId();
				Boolean lev1Report = zipRequestable.getLev1Report();
				Boolean lev2Report = zipRequestable.getLev2Report();
				Boolean lev3Report = zipRequestable.getLev3Report();

				// String accession =
				// jdbcTemplate.queryForObject(GetQueryAccessionFromRunID,
				// String.class, accRunId);
				String queryAccession = zipRequestable.getQueryAccessionId();

				// String canonicalAccession =
				// jdbcTemplate.queryForObject(GetCanonicalAccessionFromRunID,
				// String.class,
				// zipRequestable.getAccessionRunId());

				ReportInfo info = getReportInfo(accRunId, -1, -1, -1);
				int updateVersion = info.getUpdateVersion();

				String baseDir = queryAccession + "_v" + updateVersion;

				if (lev1Report) {
					String levelOneDirectory = baseDir + "/Level1Reports/";
					String levelOneSrcPath = srcDirectory + "/" + levelOneDirectory;
					// System.out.println("Downloading " + levelOneDirectory);
					logger.info("Downloading {}", levelOneDirectory);

					try {
						zos.putNextEntry(new ZipEntry(queryAccession + "_v" + updateVersion + "/"));
						zos.closeEntry();
						File dir = new File(levelOneSrcPath);

						if (dir.exists()) {
							File[] files = dir.listFiles();

							for (File file : files) {

								if (file.exists()) {

									FileInputStream fin = new FileInputStream(file);
									String fileName = file.getName();

									// fileName =
									// fileName.replace(canonicalAccession,
									// queryAccession);

									// zos.putNextEntry(new
									// ZipEntry(levelOneDirectory +
									// file.getName()));
									zos.putNextEntry(new ZipEntry(levelOneDirectory + fileName));
									byte[] readBuffer = new byte[1024];
									int amountRead;
									int written = 0;
									while ((amountRead = fin.read(readBuffer)) > 0) {
										zos.write(readBuffer, 0, amountRead);
										written += amountRead;
									}
									zos.closeEntry();
									fin.close();
								}
							}
						} else {
							// write error text file
							String errorMsg = "There was a problem accessing reports for this accession.";
							zos.putNextEntry(new ZipEntry(queryAccession + "_v" + updateVersion + "/error.txt"));
							zos.write(errorMsg.getBytes());
							zos.closeEntry();
						}

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				if (lev2Report) {
					// System.out.println("downloading level 2 report(s):");
					logger.info("downloading level 2 report(s):");

					// determine all level2 reports for given level 1 run (from
					// this user only if not admin)

					// level 2 results
					List<LevelTwoRequestableRow> levelTwoDomains = getLevelTwoRequestablesNew(accRunId, userId);

					// Create list of completed level 2 runs
					List<LevelTwoRequestableRow> levelTwoCompleted = new ArrayList<LevelTwoRequestableRow>();
					for (LevelTwoRequestableRow domainRow : levelTwoDomains) {
						if (domainRow.getLevel2RunId() > 0) {
							levelTwoCompleted.add(domainRow);
						}
					}

					List<File> fileDirs = new ArrayList<File>();
					String levelTwoDirectory = null;
					String levelTwoSrcPath = null;
					// now request level 2 reports
					for (LevelTwoRequestableRow domainRow : levelTwoCompleted) {
						// only get completed level 2 domains
						if (domainRow.getLevel2RunId() > 0) {

							String displayText = domainRow.getDisplayText();
							String[] domainParts = displayText.split(",", 3);
							String ncbiDomainId = domainParts[0].split("\\) ", 2)[1];
							levelTwoDirectory = baseDir + "/Level2Reports/";
							levelTwoSrcPath = srcDirectory + "/" + levelTwoDirectory;
							// System.out.println("Downloading " + levelTwoDirectory);
							logger.info("Downloading {}", levelTwoDirectory);

							// append to file array only reports available to
							// this user (all reports for admin)
							// fileDirs.add(new File(levelTwoSrcPath +
							// ncbiDomainId + "(" + domainRow.getStartPosition()
							// + ")"));
							fileDirs.add(new File(ncbiDomainId + "(" + domainRow.getStartPosition() + ")"));

							// System.out.println("Adding level 2 fileDir: " + ncbiDomainId + "("
							//		+ domainRow.getStartPosition() + ")");
							logger.info("Adding level 2 fileDir: {} ({})", 
									ncbiDomainId, domainRow.getStartPosition());
						}

					}

					// download from file array
					if (fileDirs.size() > 0) {
						try {
							zos.putNextEntry(new ZipEntry(levelTwoDirectory));
							zos.closeEntry();
							for (File dir : fileDirs) {

								File thisDir = new File(levelTwoSrcPath + dir.getPath());
								File[] files = thisDir.listFiles();

								if (files != null && files.length > 0) {
									for (File file : files) {
										FileInputStream fin = new FileInputStream(file);
										zos.putNextEntry(
												new ZipEntry(levelTwoDirectory + dir.getPath() + "/" + file.getName()));
										byte[] readBuffer = new byte[1024];
										int amountRead;
										int written = 0;
										while ((amountRead = fin.read(readBuffer)) > 0) {
											zos.write(readBuffer, 0, amountRead);
											written += amountRead;
										}
										zos.closeEntry();
										fin.close();
									}
								}
							}

						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}

				if (lev3Report) {
					// System.out.println("downloading level 3 report(s):");
					logger.info("downloading level 3 report(s):");

					List<LevelThreeRequestableRow> levelThreeRuns = getLevelThreeCompleted(accRunId, userId);

					List<File> files = new ArrayList<File>();
					String levelThreeDirectory = null;
					for (LevelThreeRequestableRow level3Run : levelThreeRuns) {
						// System.out.println(level3Run.getJobName());
						logger.info(level3Run.getJobName());

						List<String> reportType = new ArrayList<String>();

						// temp fix to handle old and new level 3 names (single
						// report and full/primary report)
						reportType.add("");
						reportType.add("_Primary");
						reportType.add("_Full");
						for (int j = 0; j < 3; j++) {

							// test code
							int lev3RunId = level3Run.getLevel3RunId();
							String levelThreeFile = baseDir + "/Level3Reports/" + level3Run.getJobName() + "("
									+ lev3RunId + ")" + reportType.get(j) + "_v" + updateVersion + ".csv";

							levelThreeDirectory = queryAccession + "_v" + updateVersion + "/Level3Reports/";

							// System.out.println("Downloading " + levelThreeFile);
							logger.info("Downloading {}", levelThreeFile);

							try {
								FileInputStream fin = new FileInputStream(srcDirectory + "/" + levelThreeFile);
								zos.putNextEntry(new ZipEntry(levelThreeFile));
								byte[] readBuffer = new byte[1024];
								int amountRead;
								int written = 0;
								while ((amountRead = fin.read(readBuffer)) > 0) {
									zos.write(readBuffer, 0, amountRead);
									written += amountRead;
								}
								zos.closeEntry();
								fin.close();
							} catch (Exception e) {
								// TODO: handle exception
								// System.out.println("Exception downloading level 3 csv: " + levelThreeFile);
								// System.out.println(e.getMessage());
								logger.error("Exception downloading level 3 csv: {}", levelThreeFile);
								logger.error(e.getMessage());
							}
						}
					}

				}
			}

			try {
				zos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		return byteArrayOutputStream.toByteArray();
	}

	@Override
	public int getToxCastUserId() {
		StringBuilder b = new StringBuilder();
		b.append(" SELECT id FROM `user` where email = 'ToxCast@epa.gov' ");

		int toxCastUserId = jdbcTemplate.queryForObject(b.toString(), int.class);
		return toxCastUserId;
	}

	@Override
	public List<Integer> getBadTaxGroupIds() {
		List<Integer> badTaxIds = new ArrayList<Integer>();
		badTaxIds.add(1);

		return badTaxIds;
	}

	@Override
	public List<DensityRow> getDensityData(int level, ReportTypeEnum reportType, int runId, double eValue,
			int commonDomains, boolean eukaryotesOnly) {

		List<DensityRow> densityData = null;
		StringBuilder b = new StringBuilder();

		switch (level) {
		case 1: // level 1 report

			if (reportType.equals(ReportTypeEnum.Primary)) { // level 1 primary
																// report
				// PRIMARY
				b.append("SELECT  ");
				b.append("    a.`xml_Hsp_bit-score` / b.max_bit_score AS `sim`, ");
				b.append("    a.rbh_status ");
				b.append("FROM ");
				b.append("    accession_hit a, ");
				b.append("    accession_run b ");
				b.append("WHERE ");
				b.append("    a.accession_run_id = ? ");
				b.append("        AND b.id = ? ");
				// b.append(" AND a.cdd_count IS NOT NULL "); // FOR FULL REPORT
				b.append("        AND a.xml_Hsp_evalue <= ? "); // FOR PRIMARY
																// REPORT
				b.append("        AND a.cdd_count >= ? "); // FOR PRIMARY REPORT
				if (eukaryotesOnly) {
					b.append("      AND IS_eukaryote(a.hit_taxid) = 1 ");
				}
				b.append("  ORDER BY sim DESC");
				densityData = jdbcTemplate.query(b.toString(), new DensityMapper(), runId, runId, eValue,
						commonDomains);

			} else if (reportType.equals(ReportTypeEnum.Full)) { // level 1 full
																	// report
				// FULL
				b.append("SELECT  ");
				b.append("    a.`xml_Hsp_bit-score` / b.max_bit_score AS `sim`, ");
				b.append("    a.rbh_status ");
				b.append("FROM ");
				b.append("    accession_hit a, ");
				b.append("    accession_run b ");
				b.append("WHERE ");
				b.append("    a.accession_run_id = ? ");
				b.append("        AND b.id = ? ");
				b.append("        AND a.cdd_count IS NOT NULL "); // FOR FULL
																	// REPORT
				if (eukaryotesOnly) {
					b.append("      AND IS_eukaryote(a.hit_taxid) = 1 ");
				}
				// b.append(" AND a.xml_Hsp_evalue <= 0.01 "); // FOR PRIMARY
				// REPORT
				// b.append(" AND a.cdd_count > 0 "); // FOR PRIMARY REPORT
				b.append("  ORDER BY sim DESC");
				densityData = jdbcTemplate.query(b.toString(), new DensityMapper(), runId, runId);

			}

			break;
		case 2: // level 2 report

			if (reportType.equals(ReportTypeEnum.Primary)) { // level 2 primary
																// report
				// PRIMARY
				b.append(" SELECT DISTINCT ");
				b.append("     a.`xml_Hsp_bit-score`/ b.max_bit_score AS `sim`, ");
				b.append("     a.hit_accession_id, ");
				b.append("     c.rbh_status AS `rbh_status` ");
				b.append(" FROM ");
				b.append("     level2_result a ");
				b.append("         JOIN ");
				b.append("     level2_run b ON a.level2_run_id = b.id ");
				b.append("         JOIN ");
				b.append("     accession_hit c ON c.accession_run_id = b.accession_run_id ");
				b.append("         AND c.hit_canonical_id = a.hit_accession_id ");

				if (eukaryotesOnly) {
					b.append("      AND IS_eukaryote(c.hit_taxid) = 1 ");
				}
				b.append(" WHERE ");
				b.append("     a.level2_run_id = ? ");
				b.append(" AND a.xml_Hsp_num = 1 "); // Others will be in the
														// table, but should not
														// be shown
				// b.append(" AND a.xml_Hsp_evalue <= ? "); // for primary
				// only
				b.append(" AND c.rbh_status != 'not run' ");
				b.append(" ORDER BY sim DESC ");

				densityData = jdbcTemplate.query(b.toString(), new DensityMapper(), runId);

			} else if (reportType.equals(ReportTypeEnum.Full)) { // level 2 full
																	// report
				// FULL
				b.append(" SELECT DISTINCT ");
				b.append("     a.`xml_Hsp_bit-score`/ b.max_bit_score AS `sim`, ");
				b.append("     a.hit_accession_id, ");
				b.append("     c.rbh_status AS `rbh_status` ");
				b.append(" FROM ");
				b.append("     level2_result a ");
				b.append("         JOIN ");
				b.append("     level2_run b ON a.level2_run_id = b.id ");
				b.append("         JOIN ");
				b.append("     accession_hit c ON c.accession_run_id = b.accession_run_id ");
				b.append("         AND c.hit_canonical_id = a.hit_accession_id ");
				if (eukaryotesOnly) {
					b.append("      AND IS_eukaryote(c.hit_taxid) = 1 ");
				}
				b.append(" WHERE ");
				b.append("     a.level2_run_id = ? ");
				b.append(" AND a.xml_Hsp_num = 1 "); // Others will be in the
														// table, but should not
														// be shown
				b.append("         AND c.rbh_status != 'not run' ");
				b.append(" ORDER BY sim DESC ");

				densityData = jdbcTemplate.query(b.toString(), new DensityMapper(), runId);

			}

			break;

		default:
			break;
		}

		return densityData;
	}

	@Override
	public String getSciNameAtRank(String accessionId, String rank) {
		int taxId = jdbcTemplate.queryForObject(GetTaxIdFromAccessionId, int.class, accessionId);
		String sciName = jdbcTemplate.queryForObject(GetSciNameAtRankFromTaxID, String.class, rank, taxId);
		return sciName;
	}

	@Override
	public List<TaxEcos> getEndangered() {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT DISTINCT taxid, ecos_id ");
		sb.append(" FROM taxonomy_node ");
		sb.append(" WHERE is_endangered = 1 OR is_endangered = 3");
		sb.append(" ORDER BY taxid; ");
		List<TaxEcos> theEndangered = jdbcTemplate.query(sb.toString(), new EcosMapper());
		return theEndangered;
	}

	@Override
	public List<TaxEcos> getThreatened() {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT DISTINCT taxid, ecos_id");
		sb.append(" FROM taxonomy_node ");
		sb.append(" WHERE is_endangered = 2 OR is_endangered = 3");
		sb.append(" ORDER BY taxid; ");
		List<TaxEcos> theThreatened = jdbcTemplate.query(sb.toString(), new EcosMapper());
		return theThreatened;
	}

	@Override
	public List<Integer> getModelOrganisms() {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT DISTINCT taxid ");
		sb.append(" FROM taxonomy_node ");
		sb.append(" WHERE is_model = 1 ");
		sb.append(" ORDER BY taxid ");
		List<Integer> theModels = jdbcTemplate.queryForList(sb.toString(), Integer.class);
		return theModels;
	}

	@Override
	public boolean isQueryEukaryoteFromAccessionRunId(int accessionRunId) {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT ");
		sb.append("     IS_eukaryote(b.query_taxid) ");
		sb.append(" FROM ");
		sb.append("     user_run_accession_run a ");
		sb.append("         JOIN accession_run b ");
		sb.append("     ON a.accession_run_id = b.id ");
		sb.append(" WHERE accession_run_id = ? ");
		sb.append(" LIMIT 1");
		int res = jdbcTemplate.queryForObject(sb.toString(), Integer.class, accessionRunId);
		// System.out.println("The eukaryote status for accession_run id: " + accessionRunId + " is res");
		logger.info("The eukaryote status for accession_run id: {} is res", accessionRunId);
		return res > 0 ? true : false;
	}

	@Override
	public boolean isQueryEukaryoteFromLevelTwoRunId(int levelTwoRunId) {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT ");
		sb.append("     IS_eukaryote(b.query_taxid) ");
		sb.append(" FROM ");
		sb.append("     level2_run a ");
		sb.append("         JOIN accession_run b ");
		sb.append("     ON a.accession_run_id = b.id ");
		sb.append(" WHERE a.id = ?; ");
		int res = jdbcTemplate.queryForObject(sb.toString(), Integer.class, levelTwoRunId);
		return res > 0 ? true : false;
	}

	@Override
	public List<AminoAcid> getAminoAcidInfo() {
		// TODO Auto-generated method stub
		// return jdbcTemplate.query(b.toString(), new LevelTwoStatusMapper(),
		// domain, dbName, domain, dbName);
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT id, name, side_chain, size FROM amino_acid;");
		List<AminoAcid> theList = jdbcTemplate.query(sb.toString(), new AminoAcidMapper());
		return theList;
	}
	
	@Override
	public int getPreviousDataVersion() {
		String backendHost = ncbiKeeper.getPreferredNCBIProvider().getHost();
		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT update_version FROM version where backend_host = ? order by id desc limit 1;");
		int currentDataVersion = jdbcTemplate.queryForObject(sb.toString(), int.class, backendHost);
		return currentDataVersion - 1;
	}
	
	@Override
	//get all ncbi version ids corresponding to a given data version
	public int getNCBIVersions(int dataVersion){
		String backendHost = ncbiKeeper.getPreferredNCBIProvider().getHost();
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT id FROM version");
		sb.append(" where update_version = ? and backend_host = ? limit 1");
		
//		List<Integer> theList = new ArrayList<Integer>();
//		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sb.toString(), dataVersion, backendHost);
		
		int ncbiVersionId = jdbcTemplate.queryForObject(sb.toString(),  int.class, dataVersion, backendHost);
		
//		Map<String, Object> row = null;
//		for (int i = 0; i < rows.size(); i++) {
//			row = rows.get(i);
//			int ncbiVersionId = (int) row.get("id");
//			if (!theList.contains(ncbiVersionId)) {
//				theList.add(ncbiVersionId);
//			}
//		}
		
		return ncbiVersionId;
	}

	@Override
	public List<LevelTwoRequestableRow> getToxCastLevelTwoRunInfo() {

		int curNCBI = ncbiKeeper.getPreferredNCBIProvider().getId();
		String backendHost = ncbiKeeper.getPreferredNCBIProvider().getHost();
		int toxCastUserId = getToxCastUserId();

		// System.out.println("Current NCBI version id: " + curNCBI);
		// System.out.println("Backend host: " + backendHost);
		// System.out.println("ToxCast user id: " + toxCastUserId);

		String getOldVersionQuery = "SELECT id FROM version where backend_host = ? order by id desc limit 1,1;";
		int oldNCBI = jdbcTemplate.queryForObject(getOldVersionQuery, int.class, backendHost);
		// System.out.println("Previous NCBI version id: " + oldNCBI);

		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT DISTINCT *  ");
		sb.append("   FROM  ");
		sb.append("     (SELECT a.cdd_accession_num, a.start_position, d.query_accession_id  ");
		sb.append("     FROM  ");
		sb.append("       level2_run a ");
		sb.append("     JOIN accession_run b ON a.accession_run_id = b.id ");
		sb.append("     JOIN version c ON b.ncbi_version_id = c.id ");
		sb.append("     JOIN user_run_accession_run d ON a.accession_run_id = d.accession_run_id ");
		sb.append("     WHERE a.user_id = ? ");
		sb.append("       AND c.id = ? ");
		sb.append("     ORDER BY a.id DESC) AS i ");
		sb.append("   JOIN ");
		sb.append("     (SELECT h.user_id, e.accession_run_id, e.query_accession_id, g.update_version ");
		sb.append("     FROM ");
		sb.append("       user_run h ");
		sb.append("     JOIN user_run_accession_run e ON h.id = e.user_run_id ");
		sb.append("     JOIN accession_run f ON e.accession_run_id = f.id ");
		sb.append("     JOIN version g ON f.ncbi_version_id = g.id ");
		sb.append("     WHERE h.user_id = ? ");
		sb.append("       AND g.id = ?) AS j  ");
		sb.append("   ON i.query_accession_id = j.query_accession_id; ");

		List<LevelTwoRequestableRow> theList = new ArrayList<LevelTwoRequestableRow>();

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sb.toString(), toxCastUserId, oldNCBI, toxCastUserId,
				curNCBI);

		Map<String, Object> row = null;
		for (int i = 0; i < rows.size(); i++) {
			row = rows.get(i);
			int accessionRunId = (int) row.get("accession_run_id");
			int cddId = (int) row.get("cdd_accession_num");
			int startPos = (int) row.get("start_position");
			theList.add(
					new LevelTwoRequestableRow(accessionRunId, null, cddId, cddId + ":" + startPos, startPos, null));
		}
		// System.out.println("Level 2 requestable list of size: " + theList.size());
		logger.info("Level 2 requestable list of size: {}", theList.size());

		return theList;

	}

	@Override
	public int getLevelOneDefaultOrthologCount(int accessionRunId, boolean isEukaryote) {
		StringBuilder b = new StringBuilder();
		b.append(" SELECT  ");
		b.append("     count(DISTINCT b.taxid) AS count, ");
		b.append("     IS_EUKARYOTE(b.taxid) AS is_eukaryote ");
		b.append(" FROM ");
		b.append(" accession_hit a ");
		b.append(" JOIN ");
		b.append(" accession_run e ON a.accession_run_id = e.id ");
		b.append(" JOIN ");
		b.append(" taxonomy_node b ON b.taxid = a.hit_taxid ");
		b.append(" WHERE ");
		b.append("     a.accession_run_id = ?");
		b.append("         AND a.rps_status = 'finished' ");
		b.append("         AND a.`xml_Hsp_evalue` <= ? ");
		b.append("         AND a.cdd_count >= ? ");
		b.append("         AND rbh_status = 'Y' ");
		b.append("         GROUP BY is_eukaryote ");

		if (isEukaryote) {
			b.append(" HAVING is_eukaryote = 1");
		}

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(b.toString(), accessionRunId, defaultLevelOneEvalue,
				defaultCommonDomains);

		Map<String, Object> row = null;
		int orthologCnt = 0;
		for (int i = 0; i < rows.size(); i++) {
			row = rows.get(i);
			Long tmp = (long) row.get("count");
			orthologCnt += tmp;
		}

		if (orthologCnt != 0) {
			orthologCnt--; // remove 1 for queryAccession
		}

		return orthologCnt;
	}

	@Override
	public List<Link> getLinksForGroup(String group) {

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT * FROM link where link_group = ?;");

//		System.out.println("Query: " + sb.toString());
//		System.out.println("group: " + group);

		List<Link> theList = jdbcTemplate.query(sb.toString(), new LinkMapper(), group);

		return theList;
	}

	@Override
	public int jobsWithinDay(int userId) {
		int numJobs = jdbcTemplate.queryForObject(GetJobsWithinDay, int.class, userId);
		return numJobs;
	}

	public static double getDefaultleveloneevalue() {
		return defaultLevelOneEvalue;
	}

	@Override
	public List<Chemical> getChemicalSearch(String search) {
		System.out.println("Inside getChemicalSearch");
		logger.info("Inside getChemicalSearch");
		///////////////////////
		//1st search casNum
		///////////////////
		
		//remove any dashes (b/c casNum may contain dashes) and check to see if it is an integer
		//check if string with "-" removed is integer for casNum query
		String curSearchString = search.replace("-","");
		boolean isInt = isInteger(curSearchString, 10);
		List<Chemical> casRes = new ArrayList<Chemical>();
		if (isInt) {
		    Integer searchInt = Integer.parseInt(curSearchString);
			String casQuery = "SELECT * from ecotox_chemical where cas_number =  ?";
			casRes = jdbcTemplate.query(casQuery, new ChemicalMapper(), searchInt);
		} 
		
		System.out.println("After Cas# query: " + casRes.size());
		logger.info("After Cas# query: {}", casRes.size());
		
		///////////////////
		//2nd search dtxsid
		///////////////////
		//check if search is Integer for dtxsid query
		isInt = isInteger(search, 10);
		String dtxsidQuery = "SELECT * from ecotox_chemical where dtxsid = ?";
		List<Chemical> dtxsidRes = new ArrayList<Chemical>();
		if(isInt) {
			dtxsidRes = jdbcTemplate.query(dtxsidQuery, new ChemicalMapper(), "DTXSID" + search);
		} else if (search.toUpperCase().contains("DTXSID")) {
			dtxsidRes = jdbcTemplate.query(dtxsidQuery, new ChemicalMapper(), search);
		}
		
		System.out.println("After DTXSID query: " + dtxsidRes.size());
		logger.info("After DTXSID query: {}", dtxsidRes.size());
		
		////////////////////////////////////////////////////////////
		//and lastly search ecotox_preferred_name, chem_primary_name
		////////////////////////////////////////////////////////////
		List<Chemical> nameRes = new ArrayList<Chemical>();
		String nameQuery = "SELECT * from ecotox_chemical WHERE LOWER(CONCAT_WS('', ecotox_preferred_name, chem_primary_name)) LIKE LOWER(?)";
		nameRes = jdbcTemplate.query(nameQuery, new ChemicalMapper(), "%" + search + "%");
		
		
		System.out.println("After nameRes query: " + nameRes.size());
		logger.info("After nameRes query: {}", nameRes.size());
		
		//combine all results
		List<Chemical> resList = new ArrayList<Chemical>(casRes);
		resList.addAll(dtxsidRes);
		resList.addAll(nameRes);
		
		//limit returned results to 200 entries
		int k = resList.size();
		if (k > 200) {
			resList.subList(200, k).clear();
		}
		
		System.out.println("Returning resList: " + resList.size());
		logger.info("Returning resList: {}", resList.size());
		
		return resList;
	}
	
	public static boolean isInteger(String s, int radix) {
	    if(s.isEmpty()) return false;
	    for(int i = 0; i < s.length(); i++) {
	        if(i == 0 && s.charAt(i) == '-') {
	            if(s.length() == 1) return false;
	            else continue;
	        }
	        if(Character.digit(s.charAt(i),radix) < 0) return false;
	    }
	    return true;
	}

	@Override
	public Integer getLevelFourDataCount(int level4RunId) {
		StringBuilder sb = new StringBuilder();
		sb.append(" SELECT count(*)  ");
		sb.append("   FROM  ");
		sb.append("       level4_data ");
		sb.append("     WHERE level4_run_id = ? ");
		
		
		int level4DataCount = jdbcTemplate.queryForObject(sb.toString(), int.class, level4RunId);
		
		return level4DataCount;
	}
	
	@Override
	public List<LevelFourAccessionRow> getLevelFourDataRows(int level4RunId){
		StringBuilder sb = new StringBuilder();
		sb.append("Select level4_run_id, current_priority, default_priority,");
		sb.append("status, accession, fasta, cscore, tm_score, tm_score_error,");
		sb.append("rmsd, rmsd_error, density, pdb,");
		sb.append("UNIX_TIMESTAMP(itasser_start) as itasser_start,");
		sb.append("UNIX_TIMESTAMP(itasser_end) as itasser_end");
		sb.append(" FROM level4_data a");
		sb.append("   WHERE level4_run_id = ? ");
		sb.append("   ORDER BY cscore DESC");
		
		List<LevelFourAccessionRow> rows = jdbcTemplate.query(sb.toString(), new LevelFourDataMapper(), level4RunId );
		
		sb.setLength(0);
		sb.append("Select template FROM level4_run where id = ?");
		String template = jdbcTemplate.queryForObject(sb.toString(), String.class, level4RunId);
		
		for (LevelFourAccessionRow row : rows) {
			row.setTemplate(template);
		}
		
		return rows;
	}
	
	@Override
	public List<LevelFourResultRow> getLevelFourResultRows(int level4RunId){
		
		List<LevelFourResultRow> results = new ArrayList<LevelFourResultRow>();
		
		//First get I-TASSER PDB results
		StringBuilder sb = new StringBuilder();
		sb.append("Select acc1, acc2, length1, length2, tmscore1, tmscore2" );
		sb.append(" FROM level4_tmalign_data");
		sb.append(" WHERE level4_run_id = ? ");
		
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sb.toString(), level4RunId);
		if (rows.size() == 0) {
			return results;
		}
		
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			LevelFourResultRow res = new LevelFourResultRow();
			String acc1 = (String) row.get("acc1");
			String acc2 = (String) row.get("acc2");
			int length1 = (int) row.get("length1");
			int length2 = (int) row.get("length2");
			double tmscore1 = (double) row.get("tmscore1");
			double tmscore2 = (double) row.get("tmscore2");
			res.setPdbSource("I-TASSER");
			res.setAcc1(acc1);
			res.setAcc2(acc2);
			res.setLength1(length1);
			res.setLength2(length2);
			res.setVal1(tmscore1);
			res.setVal2(tmscore2);
			res.setAvgVal((tmscore1 + tmscore2)/2.0);
			results.add(res);
		}
		
		return results;
		
		
		
	};
	
	@Override
	public List<LevelFourStatusRow> getLevelFourStatusForUser(int userId) {
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		String dbName = jdbcTemplate.queryForObject(GetDBQuery, String.class);
		String domain = backEnd.getDomainName();

		StringBuilder b = new StringBuilder();
		b.append("SELECT ");
		b.append("a.id, a.job_name, a.template, a.status, ");
		b.append("UNIX_TIMESTAMP(b.fasta_end) AS fasta_end, ");
		b.append("UNIX_TIMESTAMP(b.itasser_start) AS itasser_start, ");
		b.append("UNIX_TIMESTAMP(b.itasser_end) AS itasser_end, ");
		b.append("UNIX_TIMESTAMP(b.tmalign_start) AS tmalign_start, ");
		b.append("UNIX_TIMESTAMP(b.tmalign_end) AS tmalign_end, ");
		b.append("(SELECT COUNT(*) FROM level4_data where level4_run_id = a.id AND fasta is not null group by level4_run_id) AS numFastas, ");
		b.append("c.email, d.top_hit_accession_id, ");
		b.append("e.update_version ");
		
		//b.append("IF (a.status = 'complete', UNIX_TIMESTAMP(a.end)-UNIX_TIMESTAMP(a.start), NULL) AS `run_duration` ");
		
		b.append("FROM level4_run a ");
		b.append("JOIN ");
		b.append("level4_data b ON a.id = b.level4_run_id ");
		b.append("JOIN ");
		b.append("user c ON c.id = a.user_id ");
		b.append("JOIN ");
		b.append("accession_run d ON d.id = a.accession_run_id ");
		b.append("JOIN ");
		b.append("version e ON e.id = d.ncbi_version_id ");
		b.append(" AND e.seqapass_version = ( ");
		b.append("      SELECT e1.seqapass_version FROM version AS e1 ");
		b.append("      WHERE d.ncbi_version_id = e1.id ");
		b.append("      ORDER BY e1.seqapass_version desc LIMIT 1) ");
		b.append(" AND e.backend_host = ?");
		if (!isAdmin) {
			b.append("WHERE a.user_id = ? ");
		}
		b.append("ORDER BY a.id DESC");	

		List<LevelFourStatusRow> tmp;
		if (isAdmin) {
			tmp = jdbcTemplate.query(b.toString(), new LevelFourStatusMapper(), domain);
		} else {
			tmp = jdbcTemplate.query(b.toString(), new LevelFourStatusMapper(), domain, userId);
		}
		
		//condense results to one row per level4_run_id
		List<LevelFourStatusRow> results = new ArrayList<LevelFourStatusRow>();

		if (tmp.size() > 0) {
		  //for each run id, get min/max values needed
		  int runId = -1;
		  LevelFourStatusRow resRow = null;
		  List<Long> startTime = new ArrayList<Long>();
		  List<Long> endTime = new ArrayList<Long>();
		  int fastasRun = 0;
		
		  //determine start/end times & fastas running/completed I-TASSER
		  for (LevelFourStatusRow row : tmp) {
//		  for (int i=10645; i<tmp.size(); i++) {
//			LevelFourStatusRow row = tmp.get(i);
			if (row.getRunId() != runId) {
				runId = row.getRunId();
				
				
				//will not trigger first time through
				if (resRow != null) {  //this means there was a previous valid runId
					//need to save start and end time for this previous collection
					//find start end values for previous runId
					if (startTime.size() > 0) {
						resRow.setItasserStart(Collections.min(startTime));
					} else {
						resRow.setItasserStart(0);
					}
					if (endTime.size() > 0) {
						resRow.setItasserEnd(Collections.max(endTime));
					} else {
						resRow.setItasserEnd(0);
					}
					if (resRow.getItasserEnd() < resRow.getItasserStart()) {
						resRow.setItasserEnd(resRow.getItasserStart());
					}
					resRow.setItasserFastas(fastasRun);
					results.add(resRow);
				}
					
				resRow = row;  //save new result row
				//clear lists
				startTime.clear();
				endTime.clear();
				fastasRun = 0;
			}
			if (row.getItasserStart() > 0) {
				startTime.add(row.getItasserStart());
				fastasRun++;
			}
			if (row.getItasserEnd() > 0) {
				endTime.add(row.getItasserEnd());
			}
			
		  }
		  // add last resRow
		  //find start end values for last runId (if not -1)
		  if (resRow != null) {
			if (startTime.size() > 0) {
				resRow.setItasserStart(Collections.min(startTime));
			} else {
				resRow.setItasserStart(0);
			}
			if (endTime.size() > 0) {
				resRow.setItasserEnd(Collections.max(endTime));
			} else {
				resRow.setItasserEnd(0);
			}
			if (resRow.getItasserEnd() < resRow.getItasserStart()) {
				resRow.setItasserEnd(resRow.getItasserStart());
			}
		  }
		  resRow.setItasserFastas(fastasRun);
		  results.add(resRow);
		
		  for (LevelFourStatusRow row: results) {
			row.setItasserDuration(row.getItasserEnd() - row.getItasserStart());
		  }
		}
		return results;

	}

	
	@Override
	public List<LevelFourStatusRow> getTMAlignStatusForUser(int userId) {
		boolean isAdmin = isUserAdmin(userId);
		// String isAdmin = jdbcTemplate.queryForObject(CheckAdminQuery,
		// String.class, userId);
		String dbName = jdbcTemplate.queryForObject(GetDBQuery, String.class);
		String domain = backEnd.getDomainName();

		StringBuilder b = new StringBuilder();
		b.append("SELECT  ");
		b.append("a.id as tmalignRunId, ");
		b.append("(SELECT COUNT(*) FROM level4_tmalign_run where id = a.id group by id) AS numJobs, ");
		b.append("b.id as level4RunId, ");
		b.append("d.email, ");
		b.append("b.job_name, ");
		b.append("a.query_accession, ");
		b.append("a.query_acc_level4_run_id, ");
		b.append("a.status, ");
		b.append("UNIX_TIMESTAMP(a.start) AS `start`, ");
		b.append("UNIX_TIMESTAMP(a.end) AS `end`, ");
		b.append("f.update_version ");
		b.append("FROM level4_tmalign_run a ");
		b.append("JOIN level4_run b ON b.id = a.level4_run_id ");
		b.append("JOIN user d ON d.id = b.user_id ");
		b.append("JOIN accession_run e ON e.id = b.accession_run_id ");
		b.append("JOIN version f ON f.id = e.ncbi_version_id ");
		if (!isAdmin) {
			b.append("WHERE a.user_id = ? ");
		}
		b.append("ORDER BY a.id desc, b.id asc ");

		List<LevelFourStatusRow> tmp;
		if (isAdmin) {
			tmp = jdbcTemplate.query(b.toString(), new TMAlignStatusMapper());
		} else {
			tmp = jdbcTemplate.query(b.toString(), new TMAlignStatusMapper(), userId);
		}

		// condense results to one row per tmalign_run_id
		List<LevelFourStatusRow> results = new ArrayList<LevelFourStatusRow>();
		
		int lastRunId = -9999;
		for (LevelFourStatusRow row: tmp) {
			if (row.getRunId() != lastRunId) {
				row.setOtherRunIdsLabel(String.valueOf(row.getOtherRunId()));
				results.add(row);
				lastRunId = row.getRunId(); 
			} else {
				LevelFourStatusRow existingRow = results.get(results.size()-1);
				existingRow.setOtherRunIdsLabel(existingRow.getOtherRunIdsLabel() + ", " + String.valueOf(row.getOtherRunId()));
				existingRow.setJobName(existingRow.getJobName() + ", " + row.getJobName());
			}
		}
		
		return results;

	}

	//currently not used
	@Override
	public List<UniprotMap> getUniprotMaps(List<String> accs) {
		
		StringBuilder b = new StringBuilder();
		
		b.append("SELECT * FROM uniprot_ids WHERE ncbi_accession IN (:accs)");
		NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		List<Map<String, Object>> uniprotMap = new ArrayList<Map<String, Object>>();

		int batchSize = 500;
		List<List<String>> batch = Lists.partition(accs, batchSize);
		
		for (List<String> list : batch) {
			SqlParameterSource params = new MapSqlParameterSource("accs", list);
			List<Map<String, Object>> tmpMap = namedJdbcTemplate.queryForList(b.toString(), params);
			uniprotMap.addAll(tmpMap);
		}
		
		List<UniprotMap> mapList = new ArrayList<UniprotMap>();
		for (Map<String, Object> entry: uniprotMap) {

			String ncbiAcc = (String)entry.get("ncbi_accession");
			String uniprotAcc = (String)entry.get("uniprot_accession");
			
			UniprotMap map = mapList.stream().filter(row -> ncbiAcc.equals(row.getNcbiAcc())).findAny().orElse(null);
			if (map == null) {
				map = new UniprotMap(ncbiAcc, new ArrayList<String>());
				mapList.add(map);
			}
			map.getUniprotAccs().add(uniprotAcc);
			
		}
		
		return mapList;
		
	}
	
	@Override
	public List<UniprotMap> getUniprotMapsByAccId(int accRunId, boolean euksOnly) {
		
		List<UniprotMap> mapList = new ArrayList<UniprotMap>();
		StringBuilder b = new StringBuilder();
		
		//get List of all Level 1 accs
		String topHitAccessionString = null;
		try {
			topHitAccessionString = jdbcTemplate.queryForObject(
					"SELECT top_hit_accession_id FROM accession_run WHERE id = ? LIMIT 1", String.class,
					accRunId);
		} catch (DataAccessException e1) {
			System.out
					.println("In: getUniprotMaps - could not find top_hit_accession_id from accession_run with id = "
							+ accRunId);
			return mapList;
		}
		
		if (topHitAccessionString == null || topHitAccessionString.equals("")) {
			logger.error("In: getUniprotMaps - top_hit_accession_id null or blank for accession_run with id: {}",
					accRunId);
			return mapList;
		}
		
		List<String> accs = new ArrayList<String>();

		b.append("SELECT DISTINCT ");
		b.append("       b.taxid ");
		b.append("  FROM accession_hit a, ");
		b.append("       taxonomy_node b ");
		b.append(" WHERE a.accession_run_id = ? ");
		b.append("   AND a.hit_accession_id = ? ");
		b.append("   AND b.taxid = a.hit_taxid ");
		b.append("   LIMIT 1; ");
		
		int topHitTaxId = 0;
		try {
			topHitTaxId = (int) jdbcTemplate.queryForObject(b.toString(), new Object[] {accRunId, topHitAccessionString}, Integer.class);
		} catch (DataAccessException e) {
			logger.error("In: getUniprotMaps - failed to find a BLASTp hit from: {}", 
					topHitAccessionString);
			return mapList;
		}
		
		b.setLength(0);
		b.append("SELECT DISTINCT   ");
		b.append("a.hit_accession_id, ");
		b.append("IS_eukaryote(b.taxid) AS is_eukaryote ");
		b.append("FROM accession_hit a   ");
		b.append("JOIN taxonomy_node b ON b.taxid = a.hit_taxid    ");
		b.append("WHERE a.accession_run_id = ?  ");
		b.append("AND a.rps_status = 'finished'    ");
		b.append("AND b.taxid != ?  ");
		b.append("AND a.hit_accession_id != ?   ");
		if (euksOnly) {
			b.append("HAVING is_eukaryote = 1");
		}
		
		List<Map<String, Object>> tmpRes = jdbcTemplate.queryForList(b.toString(), new Object[] {accRunId, topHitTaxId,
				topHitAccessionString});
		
		for(Map<String, Object> entry : tmpRes) {
			accs.add((String)entry.get("hit_accession_id"));
		}
		
		logger.info("Running uniprot mapping query for {} accessions", accs.size());
		
		b.setLength(0);
		b.append("SELECT * FROM uniprot_ids WHERE ncbi_accession IN (:accs)");
		NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		List<Map<String, Object>> uniprotMap = new ArrayList<Map<String, Object>>();

		int batchSize = 500;
		List<List<String>> batch = Lists.partition(accs, batchSize);
		
		int counter = 1;
		for (List<String> list : batch) {
			System.out.println("Running batch " + counter + " of " + batch.size());
			logger.info("Running batch {} of {}", counter, batch.size());
			SqlParameterSource params = new MapSqlParameterSource("accs", list);
			List<Map<String, Object>> tmpMap = namedJdbcTemplate.queryForList(b.toString(), params);
			uniprotMap.addAll(tmpMap);
			counter++;
		}
		
		for (Map<String, Object> entry: uniprotMap) {

			String ncbiAcc = (String)entry.get("ncbi_accession");
			String uniprotAcc = (String)entry.get("uniprot_accession");
			
			UniprotMap map = mapList.stream().filter(row -> ncbiAcc.equals(row.getNcbiAcc())).findAny().orElse(null);
			if (map == null) {
				map = new UniprotMap(ncbiAcc, new ArrayList<String>());
				mapList.add(map);
			}
			map.getUniprotAccs().add(uniprotAcc);
			
			
		}
		
		return mapList;
		
	}
	
	@Override
	public List<UniprotMap> getUniprotMapsDebug() {
		
		List<String> accs = new ArrayList<String>(Arrays.asList("P02766.1","BAF62350.1","NP_001009137.1","XP_004059337.1","XP_032008476.1","XP_003262011.1","XP_011788674.1","XP_003830303.2","XP_033033949.1","PNJ80944.1","XP_007520987.1","XP_006835104.1","XP_010379114.1","XP_037661503.1","XP_008567837.1","XP_017713537.1","XP_045233838.1","XP_003914322.1","BAL44721.1","NP_001248608.1","XP_011716303.1","XP_025220794.1","XP_011930274.1","XP_023063511.2","XP_007972558.2","BAL44623.1","BAL44894.1","BAL44395.1","XP_011829762.1","XP_040120739.1","OBS66960.1","XP_032741652.1","XP_008827061.1","XP_028630684.1","XP_019842524.1","XP_005901105.1","XP_027381806.1","NP_776392.1","XP_010827267.1","XP_031220913.1","XP_003510202.1","XP_034374503.1","XP_020757674.1","NP_036813.2","XP_006054058.1","XP_015095412.1","XP_005065406.1","KAF5919156.1","KAB0378251.1","KAB0363513.1","XP_005355862.1","XP_021006209.1","XP_041499130.1","XP_010975545.1","XP_032323067.1","XP_010945083.1","XP_043300308.1","KAF4024940.1","XP_043745461.1","NP_038725.1","XP_021070542.1","OWK01751.1","XP_036061035.1","CAF5209872.1","XP_038186754.1","XP_008071577.1","XP_036100942.1","XP_003784812.1","XP_014700846.1","XP_007130607.1","XP_002713532.1","XP_012512049.1","XP_012638014.1","XP_045383556.1","XP_047382532.1","XP_001495232.1","XP_008534198.1","XP_046528180.1","XP_017517240.1","XP_016022240.1","NP_999377.1","XP_047647607.1","XP_037365611.1","XP_004654909.2","NP_001009800.1","XP_036741701.1","XP_029778706.1","XP_005697069.1","XP_006740682.1","XP_004623610.1","XP_032277213.1","XP_004702987.1","XP_006970270.1","XP_028742378.1","XP_039699857.1","XP_021541759.1","XP_011360247.1","XP_024436214.1","XP_035952590.1","XP_006920065.1","XP_036679389.1","NP_001267607.1","XP_016051482.1","XP_028380393.1","XP_033692834.1","XP_026256539.1","XP_039110384.1","XP_045742840.1","XP_002926711.1","XP_045637900.1","XP_025742184.1","XP_008689169.1","XP_020042936.1","XP_026351933.1","XP_008146547.1","XP_006899615.1","XP_004273768.1","VFV44147.1","XP_046942856.1","XP_030148078.1","XP_006150635.1","XP_027791551.1","XP_025852654.1","XP_025285019.2","CAD7674923.1","XP_041597813.1","XP_005337518.1","XP_046284418.1","XP_034851630.1","XP_032943341.1","XP_047684633.1","XP_045314532.1","XP_025787160.1","XP_043414470.1","NP_001274485.1","XP_014937489.1","XP_040335093.1","XP_042766816.1","XP_019321015.1","XP_003406797.1","XP_027432317.1","NP_001297167.1","XP_027953942.1","XP_038528336.1","XP_037021526.1","XP_045706076.1","XP_004412984.1","XP_004479072.1","XP_019596843.1","XP_007455219.1","XP_004579648.1","XP_036888371.1","XP_004386960.1","XP_026983338.1","XP_012581282.1","AHJ09600.1","XP_032459092.1","KAF0884915.1","NXD75078.1","XP_009959734.1","XP_004742936.1","KAF1528120.1","KAF1494964.1","AHJ09599.1","NXU47974.1","XP_010143708.1","NXN15555.1","XP_006091733.1","XP_030346135.1","XP_005153591.1","KFQ44654.1","NWS40652.1","NXJ61050.1","XP_042531164.1","XP_040840617.1","NXJ81022.1","KFR10426.1","XP_024613725.1","NXY81898.1","XP_021484171.1","XP_036176822.1","BAL44397.1","ELK28254.1","XP_015710943.1","XP_042724624.1","XP_022351500.1","XP_044096932.1","NXI42575.1","XP_012870315.1","NP_990666.1","NWX15130.1","XP_047554501.1","XP_042664847.1","NP_001278332.1","XP_032715589.1","XP_009876927.1","NXY67572.1","XP_030408833.1","XP_044861259.1","XP_039377606.1","XP_010126308.1","XP_022442489.1","NXE15031.1","XP_029073773.1","NXN28781.1","NXN76154.1","XP_035173261.1","NXE24378.1","KFV13345.1","NWH18329.1","NWX71824.1","NXT34043.1","NXW28164.1","NXA22034.1","KAF1621688.1","NXS74971.1","NXV40506.1","NXK29199.1","NXD69721.1","KAF1650774.1","NWT43286.1","XP_009698263.1","KGL94100.1","NXW09862.1","NWY57389.1","KAF1446481.1","NWU48399.1","KAF1570732.1","NXJ91301.1","NXS40406.1","PKU44794.1","XP_009908675.1","XP_032856950.1","NXT49788.1","KFQ75458.1","KAF1629976.1","XP_021243802.1","KAF1426642.1","NXL47528.1","KAF1529325.1","NXF53139.1","XP_014792398.1","NXU27509.1","NXW22596.1","KAF1475719.1","XP_009914842.1","KAF1596745.1","NXV28667.1","NXW63257.1","KAF1526126.1","KFW72348.1","NXJ32546.1","KAF1638481.1","KFZ54365.1","NWS57129.1","KAF1435027.1","KAF1559863.1","NXL61013.1","KAF1675972.1","XP_010572655.1","NXC68613.1","XP_032167077.1","NXL33792.1","XP_026697509.1","NWW51075.1","XP_009074323.1","XP_010290062.1","NXF68793.1","KAG8516770.1","XP_010301291.1","KAF1412823.1","KFW84075.1","XP_010199950.1","NXW45868.1","XP_032641182.1","NXJ01997.1","NXH78693.1","NXE74685.1","NXV10904.1","KFV47397.1","NXP16134.1","NWW95736.1","XP_045882003.1","NXL88109.1","NXQ98166.1","XP_010176961.1","XP_029867735.1","NWX04479.1","NXW85665.1","NXN53807.1","XP_010173086.1","NXV75689.1","KAF1666993.1","NXG76376.1","XP_019329301.1","XP_010005130.1","NXK97170.1","NXJ03469.1","XP_030301962.1","NXK80276.1","XP_030732472.1","NWH50278.1","NXN44959.1","KAF1487179.1","XP_040443023.1","TFK12297.1","XP_009476272.1","XP_005439576.1","NXG65086.1","NWR61414.1","NP_001278331.1","NXI53392.1","XP_037236804.1","XP_005857126.1","NXF41195.1","NXC38089.1","NXX77572.1","AGU01757.1","XP_009687724.1","XP_036274729.1","NXI72790.1","VCW68471.1","NP_001277530.1","NWI34816.1","NWH60255.1","NXT83408.1","NXJ49048.1","NXU74712.1","XP_005280049.1","XP_005372800.1","XP_025970519.1","KQK76138.1","XP_034615330.1","NXH16424.1","XP_027738977.1","XP_010152258.1","NXE47738.1","KFP53833.1","KFR06287.1","XP_008938423.1","NXF70835.1","XP_012302669.1","NWU61075.1","XP_009559979.1","NXN97824.1","NWR29048.1","NXX51314.1","NXF85921.1","NXP55680.1","NXC33280.1","NXS99954.1","NWI87362.1","NXL97769.1","NXG02676.1","XP_027605798.1","XP_019382180.1","NWX43065.1","NXP70586.1","XP_038247080.1","XP_013038971.1","NXQ80845.1","XP_005504287.1","XP_019402943.1","XP_040404626.1","NXP25373.1","XP_035409143.1","NXO57270.1","XP_007058858.1","NWS68736.1","NXY40801.1","NXG18496.1","NP_001297303.1","NWI58517.1","NWZ23755.1","XP_032038710.1","NWS97360.1","XP_010212340.1","NWQ64918.1","NXU88634.1","XP_025943222.1","XP_013798283.1","BAL44618.1","NWR73820.1","BAL44619.1","NXS53383.1","NXK13159.1","XP_010333768.1","XP_014636798.1","XP_020848253.1","NXX87698.1","XP_032559737.1","XP_010618006.1","XP_027494616.1","XP_006271319.1","XP_006016463.1","AIY27457.1","NWU80278.1","NXA33015.1","XP_009989980.1","XP_027702915.1","XP_017686696.1","NWI22168.1","NWR91455.1","BAL44620.1","XP_008926004.1","XP_044280219.1","NXE89902.1","XP_031468771.1","NXM29761.1","NWU12835.1","BAL44722.1","NWJ02627.1","NWX88841.1","XP_006113666.1","NWY03430.1","XP_025905442.1","NXA46496.1","XP_009637753.1","NXK33498.1","XP_027530517.1","NXD15543.1","XP_017353470.1","NXY23570.1","XP_032149005.1","NWV93797.1","NWT20297.1","NXF08844.1","NWH73763.1","OXB65230.1","NWS11677.1","NXI05584.1","NWW78208.1","NXI78537.1","NXD89326.1","OXB81692.1","NXE34829.1","NXB34142.1","NXM46687.1","NXY05543.1","NXA65817.1","NXC60294.1","NXH83517.1","NWV03728.1","NXB17590.1","NXO09634.1","NWW19503.1","XP_020646571.1","NWU20971.1","NWW62983.1","NXB00121.1","XP_015270051.1","NXH27561.1","NWY17876.1","XP_017582894.1","XP_031980135.1","NWT82716.1","NXJ20273.1","XP_010399540.1","XP_041885040.1","NWV53192.1","NXB64864.1","NWR14563.1","NWV59508.1","NXI22360.1","NWY40650.1","NXR32862.1","NXU13209.1","P30623.1","NWX37792.1","XP_001515893.1","NWY92288.1","NXH08913.1","NXY53011.1","NXL25565.1","NXB79036.1","NXC81949.1","NWW13936.1","XP_007443587.1","NXA93003.1","NXU44964.1","XP_041255197.1","XP_014119726.1","NXO30957.1","NWV30314.1","XP_032907504.1","NXP39704.1","XP_041333565.1","NXK59470.1","NXQ43592.1","NWZ94745.1","NXM18032.1","XP_030824801.1","XP_039913752.1","NXB46276.1","NXR17059.1","KAF2985196.1","NWY64570.1","NWR04952.1","NXN03101.1","NWV80305.1","NXR65632.1","XP_014729799.1","XP_005418350.1","XP_005041951.1","NXD32996.1","NXE68637.1","QOC68394.1","XP_039586089.1","NWR46017.1","NXH60222.1","NXU98626.1","NXY29145.1","XP_005517256.1","NXO90662.1","XP_015473451.1","NXR52224.1","XP_023776971.1","NWZ67558.1","NXC96590.1","NXP89933.1","NXO76671.1","NWZ74911.1","NXN86957.1","NXF25427.1","XP_038622638.1","NXA78058.1","NWS81281.1","NWT22914.1","XP_018765253.1","XP_033010980.1","XP_021381410.1","NXO62595.1","NXD20890.1","XP_037983853.1","ACH45369.1","NXM58974.1","NWX65307.1","NXB92012.1","NWT93837.1","NWI43360.1","NWT50025.1","NXQ25600.1","NXS27830.1","NWT63614.1","NXQ76055.1","XP_028592934.1","NWZ07553.1","NWY33259.1","XP_036234474.1","NXC10278.1","NWH31278.1","NXI10894.1","NXH49806.1","NWU38324.1","NXR78566.1","NWS33082.1","NWZ39186.1","KAB0390217.1","XP_042320547.1","NXO33789.1","KAH0617753.1","XP_034981345.1","NWQ96054.1","NXG42591.1","NXG84107.1","NP_001028140.1","NXR15648.1","XP_024070422.2","XP_026526070.1","XP_026554929.1","KAG8133554.1","XP_003223563.1","XP_043833420.1","P49143.1","P49142.1","NXS10699.1","XP_028018064.1","NXL09673.1","XP_034285553.1","NXT65457.1","XP_032079284.1","XP_031802342.1","XP_036611450.1","XP_039189635.1","XP_015677414.1","P42204.1","XP_009581629.1","Q29616.1","AOY34451.1","NXV86535.1","XP_013923749.1","KAI1243356.1","XP_003474061.4","NXM90216.1","NXL81576.1","NXU61838.1","NWH85074.1","RLW13327.1","NWS03595.1","KAF4803269.1","NXX15508.1","NXX09650.1","OPJ83405.1","NP_001081348.1","KAG8442104.1","NXQ15703.1","XP_006004110.1","CCT61363.1","XP_030045992.1","XP_040210045.1","PIO29408.1","XP_018410815.1","XP_029447036.1","NWV36207.1","XP_041102022.1","POI29788.1","XP_033854795.1","NP_001096539.1","XP_039668247.1","KAG8570970.1","XP_031167686.1","KAG7455192.1","NWQ78223.1","ABU54858.1","XP_006634121.1","NWU91179.1","ETE73992.1","XP_035271403.1","XP_043921383.1","XP_036375241.1","KAG9333828.1","KAI1889196.1","XP_029904895.1","XP_023127646.1","XP_042272597.1","MBN3313341.1","TRZ15230.1","XP_007257071.1","NXU04673.1","XP_044217266.1","XP_036437521.1","XP_023284573.1","XP_022614751.1","AKQ09561.1","XP_018557359.1","RMC15460.1","ACF17710.1","XP_046905871.1","XP_026088492.1","TSK58114.1","XP_017311720.1","XP_029356618.1","CAB1324032.1","XP_046692490.1","XP_041912502.1","KAG5275582.1","KAG7228894.1","XP_042611761.1","XP_039983766.1","XP_041696171.1","KAF4070848.1","XP_036970126.1","XP_012686987.1","XP_035509808.1","XP_028817433.1","AAF21245.1","XP_016132655.1","AFP99134.1","XP_026800137.2","XP_033790834.1","MBN3303308.1","XP_008280086.1","QKX44762.1","XP_010737406.1","XP_037624974.1","XP_016373114.1","TKS74172.1","XP_030626268.1","XP_016300158.1","KAI2651860.1","XP_040895576.1","XP_039549364.1","XP_042339915.1","XP_043076064.1","XP_029563641.1","XP_040288698.1","XP_017544717.1","XP_013978737.2","KAF4099361.1","XP_024277974.1","KAG7265088.1","XP_046156318.1","XP_010870615.1","XP_038858769.1","XP_035641761.1","XP_020355007.1","XP_029486693.1","XP_023859751.1","XP_036834390.1","XP_041791133.1","XP_029284692.1","XP_027035404.1","XP_043899528.1","XP_046248067.1","XP_008336692.1","NP_001005598.2","XP_026161360.1","XP_033476887.1","ACM41837.1","XP_020502021.1","KAG7316658.1","XP_035504763.1","XP_031733571.1","XP_029988749.1","XP_044539574.1","BBK70503.1","ROL42332.1","XP_020365676.1","XP_041648043.1","XP_030228472.1","XP_032382952.1","XP_020781585.1","XP_019487172.1","XP_007951652.1","XP_043545315.1","TNM87634.1","KAG9478976.1","XP_033996720.1","NXT94731.1","XP_023671556.1","XP_011618262.2","NXC18848.1","XP_033821246.1","XP_018613285.1","XP_034529517.1","XP_015337693.1","AFM89595.1","BBA94084.1","XP_026879618.2","XP_034035090.1","XP_041045253.1","XP_038664511.1","XP_039610594.1","KAF7651208.1","XP_032875375.1","XP_028659460.1","AMG14013.1","XP_032830738.1","ABI93606.1","CAG01154.1","GCB61201.1","NXX37373.1","KAA0708771.1","BAX00165.1","ABY60456.1","NXT04960.1","XP_034439520.1","NWW37640.1","KAF5900441.1","TWW57296.1","GCC29230.1","NXS01592.1","XP_041847567.1","XP_029948469.1","XP_028258060.1","XP_011682724.1","XP_039467100.1","XP_005753002.1","XP_011490033.1","XP_030582112.1","XP_046652769.1","XP_004539434.1","XP_046462071.1","XP_003442376.2","XP_024151212.1","XP_005915250.1","XP_026023090.1","XP_006780090.1","XP_039867462.1","RVE74536.1","XP_032781491.1","XP_027870942.1","XP_038056511.1","NP_001161674.1","XP_014326472.1","XP_035012922.1","XP_020461208.1","XP_032416998.1","XP_028297582.1","XP_015229497.1","XP_047219322.1","XP_041463715.1","XP_043993628.1","XP_047436854.1","XP_038557744.1","XP_019742351.1","XP_013417218.1","XP_045885885.1","XP_037102711.1","XP_010792090.1","KAF3832577.1","KAF3686377.1","XP_022086775.1","XP_014853117.1","XP_034384675.1","XP_022795898.1","XP_012711774.2","XP_028999690.1","XP_008403674.1","XP_033631317.1","ELU04386.1","ABD60085.1","XP_034052811.1","XP_033968320.1","XP_034725255.1","XP_044065494.1","XP_038162676.1","CAG5855094.1","XP_026234383.1","XP_014914711.1","XP_007549949.1","XP_019954278.1","XP_020604490.1","TRZ00357.1","XP_027036168.1","XP_020915054.1","XP_040055409.1","KAG0311602.1","XP_022046015.1","XP_017296698.1","XP_031564984.1","AMB61037.1","XP_013857377.1","XP_015808416.1","KAI0226771.1","XP_037307943.1","XP_033743046.1","XP_032228739.1","MPC29633.1","KAG0210749.1","KAG0051077.1","KAF9958009.1","KAF9186217.1","KAF9185963.1","KAG0249726.1","XP_043234035.1","KAG0236777.1","XP_021354517.1","CAH1789207.1","XP_037552231.1","XP_045593190.1","KAF9354283.1","KAG0273113.1","KAF9927559.1","GJJ72971.1","KAF9153089.1","KAF9359862.1","KAF9121785.1","KAG0072738.1","KAF9157786.1","KAF8938321.1","KAG0369034.1","KAF9387334.1","BAN21410.1","KAF9996703.1","XP_042218140.1","XP_039453382.1","OAQ35520.1","EDS39825.1","KAF9409349.1","XP_021883127.1","KAG0204948.1","XP_026331233.1","KAF9096021.1","KAG0297062.1","KAF9107342.1","KAF8934111.1","KAF9337720.1","KAF9127104.1","KAG0085676.1","KAF9285585.1","KAG9067293.1","KAG0346031.1","XP_021954007.1","KAF9550982.1","CRK98013.1","KFH71036.1","KAG0031102.1","TNN36041.1","XP_037087075.1","KAF9217817.1","KAF9313749.1","KAF9978726.1","KAG0234337.1","XP_014673477.1","ACO11259.1","XP_047470258.1","KAF9210187.1","KAI1310201.1","KAF9107294.1","KAF8980238.1","XP_018800574.1","XP_004345681.1","XP_042887875.1","KAG0003331.1","XP_005715913.1","CAH1268200.1","KXS19415.1","CAG8440047.1","XP_043461328.1","CAG5006891.1","KAF9435353.1","XP_021694962.1","KAF6025609.1","KAG5180760.1","KAF0719105.1","XP_037920251.1","ORY93863.1","XP_037805469.1","CDJ88371.1","XP_019552339.2","OQR86692.1","XP_034302776.1","NXT28654.1","XP_039960768.1","XP_040766282.1","XP_011205149.1","XP_007763634.1","XP_035681430.1","XP_011388009.1","XP_040173747.1","AFX60441.1","PWZ00484.1","XP_041411356.1","XP_040230836.1","XP_031780935.1","XP_044126627.1","XP_045519931.1","KNC33744.1","XP_041774672.1","XP_310683.3","XP_019634516.1","TEA40424.1","CAF4956537.1","ETN60118.1","XP_007008289.1","KAI0031428.1","XP_037938353.1","KAG0373647.1","XP_038218058.1","NWI65393.1","XP_022126924.1","XP_040576684.1","CAG8434098.1","XP_045203187.1","XP_004520066.1","SAM80935.1","QLQ82068.1","XP_045507236.1","KAG1715855.1","KZO96437.1","CAH0715135.1","XP_046037849.1","EGT50667.1","XP_017480629.1","XP_013148190.1","CAG7853962.1","CAG8750957.1","XP_036318424.1","XP_035784381.1","KAF6767138.1","KAG6940771.1","XP_040629556.1","NXK48555.1","XP_026748558.1","VDO65188.1","XP_011179849.1","XP_021940381.1","KAF9584860.1","RXK37831.1","PIO60950.1","KAG1466029.1","XP_035908769.1","XP_037889185.1","CAH0106078.1","XP_044259823.1","ORE13728.1","RCH98695.1","XP_028474725.1","SOV04633.1","NWI78027.1","XP_013107889.1","XP_013198009.1","XP_005183996.1","KAH8550813.1","XP_975774.1","CEM35627.1","GAX73777.1","SPC67070.1","RCH79062.1","XP_037045549.1","KAI0049250.1","SPO21979.1","XP_012204680.1","PAV22034.1","ACO15689.1","XP_036232237.1","XP_028035401.1","NWH98028.1","XP_004924356.1","KAF7303240.1","CAD5219530.1","KAF8527544.1","XP_025379092.1","OXC64271.1","XP_037967108.1","KAG1197823.1","KAE9416256.1","RVE42046.1","XP_003037389.1","KAG2176595.1","NP_001040980.1","TMW48980.1","NXR94330.1","XP_023340351.1","VDO43604.1","KAI0278163.1","XP_008610124.1","AQZ17904.1","XP_014365761.2","CAD7585699.1","RWW90459.1","EIE76252.1","XP_018990470.1","OXM78536.1","XP_026735404.1","OXG16059.1","CDH10081.1","CAD6198848.1","PXF47814.1","OWZ77463.1","NXP69069.1","NXQ01321.1","CAD7194638.1","PWN50583.1","RZC32383.1","ORX88718.1","KAH9997746.1","XP_037141621.1","NXA07493.1","PVF96071.1","CAD7456000.1","RKP06628.1","ORY72431.1","OAJ41027.1","KAH0817427.1","TIC32756.1","XP_044014719.1","XP_033607701.1","CAD7259918.1","XP_019874376.1","XP_026285516.1","XP_025355103.1","NXM67201.1","PSN54567.1","XP_006956179.1","TYJ55289.1","OWZ30818.1","OXG78993.1","OXG40232.1","OWZ43050.1","OXG95197.1","CDS82217.1","OXC83886.1","OXG61701.1","UOH82059.1","XP_002490453.1","XP_013245159.1","OXL07841.1","OXG23783.1","KIM79784.1","OWZ39685.1","OXG80831.1","KAF8482038.1","OBZ90462.1","OXC60648.1","XP_013168854.1","OWZ53707.1","OXH08794.1","KHN78443.1","AOA61003.1","OXG62414.1","KAA6418948.1","CCA37246.1","OXG31415.1","XP_047519142.1","OWZ40799.1","OXG85480.1","CDS13924.1","XP_012050782.1","XP_031862292.1","OXG57283.1","CAD7400811.1","OXH10084.1","KIO26543.1","OWT38938.1","OXG19351.1","OCB86163.1","KAI0720138.1","XP_029739170.1","KAG9295749.1","OXG49115.1","OZJ03264.1","EPT01600.1","ORE03457.1","ODM89239.1","XP_003678692.1","XP_023461404.1","CAG8735221.1","CAG8503028.1","OAD09024.1","SJM82253.1","KAH9044569.1","SCV03906.1","KAF1803133.1","CDF88519.1","XP_024343743.1","XP_033223940.1","XP_031620592.1","CBQ72002.1","RGB33322.1","THH06727.1","XP_003108740.1","TFY64296.1","KAF8072742.1","KAG2193461.1","RKP26115.1","RDB28420.1","XP_037300664.1","KAH3685046.1","RIB12922.1","KAH6560618.1","KAG5682490.1","XP_022629262.1","KIO08581.1","CAG8634823.1","XP_013020384.1","PIC35736.1","XP_030246754.1","GBC02082.1","KAH9858361.1","THH13472.1","RIA97322.1","XP_013023417.1","ETS62992.1","PKC04630.1","TFK56727.1","XP_011276551.1","GAN10387.1","GAC76733.1","NXS81757.1","XP_025179008.1","XP_014658270.1","OCF78099.1","EXX75103.1","KDQ15730.1","KAF9818000.1","KAH8120077.1","KAE8543449.1","XP_035454786.1","SJX61875.1","XP_018567686.1","XP_037222106.1","CDH51278.1","XP_002634425.2","KAG2180969.1","KAH9968643.1","KAF8922035.1","VVC92690.1","EPB89049.1","XP_015607061.1","XP_047539235.1","PBK76789.1","XP_045455040.1","OCF58095.1","XP_031348164.1","SCU83762.1","KIJ66254.1","PBL00568.1","XP_022323819.1","KAH3841548.1","KAI0464462.1","TRM66088.1","XP_774994.1","CAB3406812.1","KOB71017.1","KFB38941.1","RLN68226.1","KAG0152392.1","XP_034255261.1","NXQ57068.1","POM73762.1","KAI0001709.1","KAH9178800.1","KAH8393355.1","KZT30890.1","KAG7377538.1","OJA19551.1","KAF0743300.1","MBW0520623.1","KIM47747.1","XP_026498137.1","KRT83229.1","KAG2224515.1","XP_002061575.1","KAF8216045.1","XP_024577074.1","KAI0304568.1","EDW09957.1","XP_043045817.1","ESK90901.1","KAG0683287.1","KAF9270612.1","KAG9402108.1","EPB70610.1","XP_011497972.1","ODO01622.1","KTB33405.1","XP_022837287.1","XP_019004666.1","KNZ45725.1","KAH9004106.1","KAH9982799.1","SCU81448.1","XP_014290292.1","BDA46426.1","KNE95237.1","KAG8842481.1","KAF8978918.1","KAF8604096.1","XP_040745749.1","CAB3225675.1","XP_011697174.1","XP_019022713.1","KAF8167624.1","XP_030559131.1","PPQ99565.1","ORY26270.1","XP_045463646.1","KAF4319963.1","TMW64575.1","PCH34049.1","PIA18357.1","KAG2524514.1","KAG8842320.1","POW07072.1","KIR37790.1","KAI0274979.1","KIS00064.1","XP_017027866.1","XP_002049822.1","XP_006678893.1","KIR28313.1","XP_024886834.1","SAM04555.1","KAG5362773.1","CEP18613.1","XP_024513149.1","KIR35068.1","XP_019030098.1","KIR93796.1","KIY56670.1","GAQ80565.1","XP_028163878.1","KGB76034.1","XP_017866254.1","AAF34361.1","KAH9463956.1","AAF34368.1","TDL28062.1","XP_046996626.1","AAF34365.1","KAI0073937.1","KAH7334416.1","KAF9415710.1","KAI0318994.1","TGZ32723.1","AAF34362.1","XP_028129353.1","XP_041971814.1","AAF34369.1","XP_018916818.1","KAF8260735.1","KAH8413663.1","ODQ67470.1","KIR70207.1","XP_032521771.1","CUS21247.1","XP_034478345.1","AAF34367.1","AAF34364.1","KAF2071377.1","KAE8269848.1","KAG2230390.1","KAG9086819.1","CAE1234312.1","KII93164.1","KEP52878.1","XP_031024480.1","KAH9816180.1","EUC63934.1","XP_041298519.1","XP_039286615.1","KAH9049411.1","EMG46263.1","KAA1066611.1","EMD41840.1","KZV96406.1","KAG4408862.1","AAF34358.1","XP_047114727.1","KAG8749312.1","KAG7211165.1","KAE8230715.1","KIK30211.1","KAG2200928.1","XP_002547533.1","XP_003336785.2","KAG8764429.1","KAF9459822.1","OAX43001.1","KAI0334372.1","KAF7741130.1","ORX46781.1","CAH2251842.1","KAH8299741.1","KAG2062263.1","KAG1828072.1","KDQ63211.1","XP_007860182.1","XP_041230297.1","PLW38572.1","KIH67158.1","XP_025575999.1","XP_025349127.1","KIY52739.1","XP_041358433.1","KAE8238696.1","XP_023167438.2","KAF8506791.1","XP_041161490.1","KLO20095.1","KAG2350774.1","KAH7916484.1","KAI0639976.1","KAF9454116.1","TEB38882.1","OAV94989.1","XP_014779890.1","CDO55945.1","KAG0171543.1","O74492.1","KAH7930588.1","XP_031854372.1","XP_029644452.1","XP_016276964.1","XP_018284111.1","KAF7347693.1","KZV76928.1","XP_017780472.1","KAG0184825.1","RNA07004.1","KIK49458.1","NP_588333.1","KAH7622906.1","VDM62745.1","KAG5725867.1","ANZ73819.1","TBU25327.1","KAG0164369.1","KAH0591028.1","KAG5735696.1","KNZ76996.1","XP_020815500.1","XP_005832211.1","XP_018335346.1","KAI1725077.1","XP_034106489.1","XP_009524521.1","KAH7886086.1","KAG1838344.1","KAH8369827.1","KAG0707452.1","KAF7311077.1","VEN41078.1","KAI0363741.1","XP_044752917.1","NXX58885.1","KAF4573213.1","XP_008556524.1","CAG8602419.1","KAH8835501.1","KAF6201298.1","ROT72003.1","ONH70153.1","KAI0964426.1","KAG2756452.1","XP_011315399.1","XP_007360610.1","XP_006685736.1","KDN41473.1","XP_009540946.1","RZF48085.1","KAG9222503.1","XP_505757.1","KAI0778742.1","XP_007374438.1","KAF5384909.1","BAP72942.1","KIM31891.1","KAI0652421.1","KAE9026904.1","KAH9937375.1","KAB8281321.1","KAF8913120.1","QDZ25621.1","KAH8319768.1","VFQ73147.1","XP_029177791.1","XP_002556190.1","XP_041310949.1","XP_046972409.1","XP_011258566.1","KAE8948208.1","KAF7265295.1","OCH96437.1","KAF2896740.1","XP_018279516.1","XP_021196389.1","RAL52147.1","KAG2022945.1","XP_036151217.1","KAF9056109.1","PNH08636.1","TIA92071.1","TRY67374.1","XP_043016081.1","KAE8182255.1","GJJ13362.1","KAI0756333.1","KAG2426800.1","KAG4080195.1","PRP86939.1","XP_018272364.1","CAG8512634.1","KAH3666702.1","XP_041214781.1","KAE8183672.1","KIM70254.1","XP_007405492.1","PYI10155.1","GJE86184.1","XP_011644103.1","GCE98775.1","KAI0784939.1","CDR38186.1","CAH0547725.1","XP_023940809.1","KAI0361009.1","KZT54110.1","KAF9786825.1","AAZ14903.1","KDR85463.1","KAF5276468.1","KAG8959007.1","CAG8499419.1","GAD99021.1","XP_047036404.1","CAA7406656.1","XP_036634398.1","AAF34360.1","KAG1783541.1","TIA93656.1","KAI0068125.1","KAF4379728.1","XP_037142977.1","TFK88654.1","KAI0828942.1","XP_004368294.1","KAF8514744.1","XP_012177363.1","KDE03153.1","KIR58198.1","KAH8402489.1","KAF9502357.1","XP_009266127.1","RDX51866.1","CAG8510216.1","TFK30909.1","XP_002947784.1","XP_015125401.1","KAA1467951.1","XP_014567232.1","XP_002495435.1","KXZ53990.1","KAH8252968.1","KAG2149614.1","XP_022677287.1","SGZ29757.1","KIK07196.1","XP_001873392.1","KAG2454315.1","KAG6943385.1","XP_041183611.1","XP_026482002.1","KAF2653601.1","GIL69413.1","KAH8237300.1","KAG2076910.1","KAF4623064.1","XP_041191201.1","PFH49693.1","KAG6610842.1","XP_041247171.1","SCV67909.1","KAG2044420.1","KAF1335788.1","KAG2766191.1","RCK66714.1","KAF9041735.1","QRV97534.1","CEQ41898.1","THV07531.1","KAG3125167.1","XP_022915670.1","CAH2351706.1","SCZ94026.1","KAG5876114.1","XP_021872869.1","PWW72404.1","KAH8289009.1","XP_043286332.1","XP_025466468.1","XP_022457663.1","KAF8973521.1","RUS12517.1","TFK36984.1","POY72716.1","CAD5226278.1","KAG1885514.1","KAI0673697.1","KAG5359848.1","XP_020207487.1","KAG1696394.1","VDC01953.1","KAH6916988.1","XP_034947271.1","KAG5419132.1","TPX59864.1","KAG9314085.1","KAF9535666.1","KJA25903.1","XP_008032622.1","KAI0714980.1","XP_002431598.1","KAG5364273.1","XP_018988192.1","CAF1367767.1","RPA97721.1","KAF8078891.1","XP_033330500.1","XP_023028072.1","XP_001699572.1","XP_014600833.1","XP_019770857.1","XP_011865530.1","ETL77771.1","OSD06242.1","TXT07108.1","KIK70702.1","TYZ59927.1","XP_037780271.1","KOO25041.1","KAH7727472.1","XP_018030439.1","OSX73950.1","KAF4509516.1","TKA51101.1","KUF93044.1","XP_005648475.1","VDL81795.1","KAG7396358.1","CAH2069553.1","KAF8632977.1","KAI0660630.1","ETI30714.1","KAH8785599.1","XP_044697354.1","XP_029662730.1","TCD62852.1","KWU42566.1","SPO39867.1","XP_023757506.1","CAG5082955.1","KAG0634800.1","XP_030765851.1","XP_044588530.1","KAG0126302.1","CAB4104260.1","PPQ67136.1","KAF8832087.1","KAF9645433.1","XP_002836631.1","XP_018211764.1","KAH8915333.1","KAF5388536.1","RPD65139.1","XP_007878024.1","CDI52650.1","XP_043523747.1","ODV91812.1","XP_024372427.1","KAH8099329.1","RDX71478.1","CAZ80822.1","KAG1653048.1","RPD82220.1","KAF9486302.1","CEP23153.1","MBW04518.1","KAF5323516.1","XP_029047439.1","XP_020071389.1","XP_034187124.1","XP_020436980.1","XP_013934079.1","GBG25257.1","SJL06189.1","XP_003662124.1","XP_012152977.1","XP_044731902.1","KAH9602128.1","KAG7866072.1","KZP31732.1","KAF8323744.1","XP_027907845.1","XP_038907370.1","XP_008915430.1","XP_015938886.1","EKC99909.1","XP_021853848.1","XP_012279327.1","KAH8274622.1","XP_020575158.1","XP_043495792.1","CAE7818530.1","ETP53024.1","TFL03574.1","RUP24108.1","XP_014176308.1","XP_007378846.1","KAF2089245.1","AFK39087.1","KAH9897843.1","XP_018349823.1","XP_007390815.1","CEL53848.1","XP_011400045.1","ETP25036.1","KAH9913323.1","XP_031828128.1","KAF4040905.1","XP_002502299.1","TNY18532.1","KOX78431.1","XP_046362897.1","XP_025461161.1","XP_637625.1","XP_039746625.1","XP_016967796.1","XP_018738371.1","ETO59470.1","XP_020262725.1","OWB68129.1","XP_034825819.1","KAG6503710.1","KZT74938.1","PUU82860.1","GER57365.1","KAG8984535.1","KAF8665437.1","ODV93324.1","XP_018377962.1","EWC47274.1","XP_035733988.1","XP_005702816.1","XP_047357913.1","TPX43527.1","XP_009022893.1","CAD1480860.1","XP_014483574.1","XP_001271134.1","XP_002143532.1","KAI0601395.1","XP_043673224.1","XP_013297802.1","XP_002912174.1","XP_038878039.1","XP_046824090.1","KAE9398443.1","XP_025618227.1","KAF5368414.1","XP_018405784.1","KAF7392696.1","XP_003194690.1","CAD1845017.1","XP_016176187.1","KIR82728.1","KIY32215.1","XP_017223815.1","KAG2733209.1","XP_006401115.1","XP_019039943.1","XP_025361717.1","KAF7395377.1","KJE04781.1","KAG1765131.1","KAG2486997.1","KAI0697568.1","TFK77368.1","XP_005844568.1","XP_011650818.1","XP_019050194.1","SCU78183.1","CAG7822568.1","PYH97283.1","EHA23039.1","XP_001384494.2","XP_030966985.1","TPR09022.1","KIR52529.1","OJT04473.1","XP_012058240.1","XP_003285396.1","XP_023540278.1","KAG0462117.1","KAF2430117.1","XP_028224884.1","XP_015437852.1","KAG5054648.1","XP_008438230.1","KIR45615.1","TXG62431.1","GFZ47507.1","KAF9074816.1","KAE8387728.1","KAF0921480.1","XP_024993054.1","XP_017991246.1","XP_026401601.1","KAF8899013.1","KAI1641263.1","KAH7488013.1","XP_022035969.1","OBZ78960.1","XP_016928261.1","KAH9065826.1","CAG8461523.1","KAG6597227.1","XP_045762730.1","KOC61522.1","XP_043248702.1","CAA7032353.1","KKY32575.1","KAG7028699.1","KAG9154851.1","OWM64319.1","RSH95375.1","XP_018047434.1","XP_015171487.1","XP_022946227.1","PKA60075.1","XP_004352618.1","XP_040724066.1","XP_033354661.1","XP_033183143.1","XP_019262035.1","XP_033303944.1","XP_028486116.1","XP_001525849.1","KAF9891095.1","XP_011118547.1","XP_012238393.1","AAF34359.1","XP_043586609.1","KAF8846067.1","XP_016448179.1","KAF7824274.1","RCN26384.1","XP_014514969.1","EPS45924.1","XP_012165152.1","KAF3187348.1","KAF3959003.1","KFK27350.1","GFP93897.1","PYI35698.1","PHU31002.1","GES58503.1","XP_009594716.1","XP_023927360.1","KAF3913497.1","XP_022975601.1","VDM65764.1","KAF8225106.1","QEU59826.1","XP_016770415.2","KAG9004769.1","KIK97558.1","KAG2310851.1","KAF3541963.1","KAH8340035.1","XP_011172724.1","GFG32423.1","XP_012750936.1","XP_001961270.1","OCK81393.1","KAG6799648.1","KAB8074703.1","KAG7948648.1","XP_020282167.1","CCX08272.1","KKY18306.1","XP_019019572.1","XP_003693777.2","XP_044492526.1","XP_017753188.1","KIP10065.1","XP_043784631.1","EDV56558.1","KAH7525242.1","SHO78806.1","KIR84070.1","XP_006619863.1","TYI78406.1","KAF2439314.1","XP_017054229.1","KAF1976784.1","XP_016911133.1","OAY77130.1","KAI2501885.1","XP_015895634.1","XP_025336904.1","KAG9435575.1","POS73003.1","XP_028509041.1","XP_009126771.1","CAA3018816.1","XP_039481871.2","KAG5627182.1","XP_002090356.1","RYN61979.1","XP_043645084.1","KAG2368871.1","KAF5842047.1","KKK26281.1","XP_010323150.1","XP_021620824.1","XP_018390687.1","KAH9081735.1","XP_018471832.1","PKU72179.1","XP_020066272.1","XP_037715732.1","PSC67174.1","XP_018300083.1","XP_043022915.1","PBC30679.1","XP_007134280.1","KAF5446063.1","KAF8585132.1","XP_022897823.1","VDD20906.1","XP_001213847.1","XP_025342270.1","XP_042997963.1","KAF7339326.1","XP_001986108.1","KAG5409060.1","KAF4300758.1","XP_046557689.1","KAF3988468.1","XP_013616398.1","XP_047177997.1","XP_038932994.1","XP_020131066.1","OOF99813.1","KUI52480.1","XP_009794375.1","XP_040992849.1","XP_017440729.1","XP_039785825.1","RII08952.1","XP_013682941.1","KAF8807838.1","XP_014203749.1","KIL71671.1","XP_017011879.2","BAT96961.1","XP_016594323.1","CDM30575.1","XP_002033306.1","KAI0489004.1","KAF3623538.1","KAF7784093.1","KIY67201.1","KAG6897117.1","XP_033153631.1","KAI1502893.1","KAG8624958.1","KAF8102533.1","KAH0766084.1","RVD87939.1","XP_009400745.1","KAF2223949.1","XP_016026967.1","QES69803.1","OVA01280.1","KAG5315633.1","KAF8140567.1","XP_044725071.1","XP_025159923.1","KZV14960.1","CAD7427628.1","TYH67966.1","KAG5321869.1","KAG6009025.1","KAH8723361.1","VVB16432.1","KAF5352371.1","NP_724982.1","KAH0448822.1","KAB2026297.1","PIN15879.1","PWA40688.1","XP_033233240.1","XP_043621876.1","TYG65871.1","KAG5946653.1","XP_025797921.1","CAE7254876.1","XP_034696059.1","XP_025384092.1","XP_017079135.1","XP_012223550.1","XP_024714679.1","XP_041537304.1","KAI1270735.1","PON87512.1","XP_025527276.1","KGO72993.1","KJH46183.1","XP_033425605.1","TKY68770.1","KAH0946484.1","KAF3923086.1","CRL19311.1","XP_002027146.1","PYI18199.1","KAI1793167.1","KAF7563036.1","OJZ89568.1","XP_010691956.1","KAG1715181.1","GAA88855.1","RLN15471.1","KAG5337709.1","XP_017145889.1","XP_030381455.1","OXU19200.1","CRG85955.1","XP_025518233.1","AQL05905.1","KKK20713.1","KAH6559264.1","KAH9733663.1","KAG5991681.1","KAI1187250.1","XP_025568756.1","XP_033583704.1","XP_025479040.1","OJI85236.1","XP_010275253.1","KAI1751379.1","XP_040675351.1","XP_037425288.1","KAG7823034.1","XP_040378006.1","XP_044649031.1","XP_035355319.1","XP_040791538.1","XP_025496275.1","KAG0618551.1","KAG5313667.1","KAH7358916.1","XP_024708176.1","XP_025535538.1","KAG5981836.1","KAF5867372.1","KAI0800704.1","KAF2871575.1","XP_040752815.1","ORZ20272.1","XP_015468481.1","XP_006279807.1","VAI07005.1","XP_012266992.1","EGI66747.1","XP_042017802.1","XP_044370251.1","XP_018147170.1","CBI25230.3","KZL85237.1","KAF7580311.1","GHP10813.1","GCB18457.1","KAH6840874.1","XP_001399842.1","XP_002616970.1","KAF5196039.1","XP_015630534.1","XP_027335119.1","XP_034014752.1","GFZ00754.1","OQE41215.1","KAF8698651.1","XP_001911890.1","PSS11952.1","XP_046470508.1","XP_015517101.1","XP_017887934.1","KFX49206.1","KAE8557257.1","RYC57509.1","VBB76204.1","RDK44026.1","XP_046603250.1","CAE8719186.1","EJD48050.1","XP_046410104.1","KAH6813049.1","KAF4550360.1","KAG7358788.1","XP_026626761.1","KAF9638684.1","XP_028787278.1","RDH20121.1","KAI1332721.1","EMR65083.1","KAF2502661.1","KAI1850676.1","PRW32890.1","KAE8152661.1","EPS71047.1","GHJ88419.1","KYR02207.1","KAE8352589.1","XP_029319137.1","RHZ75248.1","XP_024168943.1","CAB1103107.1","KAG8063166.1","KAI1250703.1","KAG7120027.1","XP_010228544.1","XP_020194400.1","OEL37243.1","XP_018711435.1","KAH9883267.1","KAI0554655.1","KAI1120815.1","KAF3357829.1","SGZ56037.1","KAI0448986.1","KAI1389873.1","XP_045957121.1","XP_008092223.1","OCF31366.1","XP_040692943.1","TMS34484.1","KAI0540612.1","KAI1734189.1","GEQ68729.1","XP_007833179.1","KAI0973955.1","KAH7105875.1","XP_014562096.1","XP_007717931.1","CCE81488.1","XP_033650630.1","KAG5975386.1","XP_017123157.1","KAI0514592.1","NXS23390.1","XP_028494935.1","QBM90793.1","XP_003008739.1","XP_034120134.1","GAP85888.1","KAI0257225.1","SCU99694.1","OCF44899.1","KAI0347715.1","KAI0152771.1","GAW17141.1","RYP61771.1","KAI1140496.1","RWA08433.1","KAI0102001.1","KAH8367132.1","CAG9568472.1"));
		
		StringBuilder b = new StringBuilder();
		
		b.append("SELECT * FROM uniprot_ids WHERE ncbi_accession IN (:accs)");
		NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		List<Map<String, Object>> uniprotMap = new ArrayList<Map<String, Object>>();

		int batchSize = 500;
		List<List<String>> batch = Lists.partition(accs, batchSize);
		
		for (List<String> list : batch) {
			SqlParameterSource params = new MapSqlParameterSource("accs", list);
			List<Map<String, Object>> tmpMap = namedJdbcTemplate.queryForList(b.toString(), params);
			uniprotMap.addAll(tmpMap);
		}
		
//		Map<String, List<String>> finalMap = new HashMap<String, List<String>>();
		List<UniprotMap> mapList = new ArrayList<UniprotMap>();
		for (Map<String, Object> entry: uniprotMap) {

			String ncbiAcc = (String)entry.get("ncbi_accession");
			String uniprotAcc = (String)entry.get("uniprot_accession");
			
			UniprotMap map = mapList.stream().filter(row -> ncbiAcc.equals(row.getNcbiAcc())).findAny().orElse(null);
			if (map == null) {
				map = new UniprotMap(ncbiAcc, new ArrayList<String>());
				mapList.add(map);
			}
			map.getUniprotAccs().add(uniprotAcc);
			
			
			
			
			
//			if (finalMap.containsKey(ncbiAcc)) {
//				finalMap.get(ncbiAcc).add(uniprotAcc);
//			} else {
//				List<String> newList = new ArrayList<String>();
//				newList.add(uniprotAcc);
//				finalMap.put(ncbiAcc, newList);
//			}
			
		}
		
		return mapList;
		
	}

}

class LevelOneStatusMapper implements RowMapper<LevelOneStatusRow> {
	public LevelOneStatusRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int runId = rs.getInt("SeqAPASS Run Id");
		int updateVersion = rs.getInt("update_version");
		String accession = rs.getString("Accession");
		String email = rs.getString("email");
		double maxBitScore = rs.getDouble("Maximum bitscore");
		String blastp = rs.getString("BLASTp");
		int blastpQueueNum = rs.getInt("BLASTp_queue_num");
		String commonDomains = null;
		if (rs.getObject("Common Domains % complete") != null) {
			commonDomains = "" + rs.getInt("Common Domains % complete");
		}
		int rpsQueueNum = rs.getInt("RPS_queue_num");
		String ortholog = null;
		if (rs.getObject("Ortholog % complete") != null) {
			ortholog = "" + rs.getInt("Ortholog % complete");
		}
		int rbhQueueNum = rs.getInt("RBH_queue_num");
		long startDate = rs.getLong("Start Date");
		long endDate = rs.getLong("Date Completed");
		long runDuration = rs.getLong("SeqAPASS Run Duration");
		if (endDate < startDate && endDate != 0) {
			runDuration = 1;
			endDate = startDate;
		}

		if (blastp.equals("queued")) {
			String queuePos = ReportServiceImpl.getOrdinalSuffix(blastpQueueNum);
			blastp = queuePos + " in queue";
		} else {
			blastp = "     " + blastp;
		}
		if (commonDomains == null) {
			String queuePos = ReportServiceImpl.getOrdinalSuffix(rpsQueueNum);
			commonDomains = queuePos + " in queue";
		} else {
			commonDomains = commonDomains + "%";
		}
		if (ortholog == null) {
			String queuePos = ReportServiceImpl.getOrdinalSuffix(rbhQueueNum);
			ortholog = queuePos + " in queue";
		} else {
			ortholog = ortholog + "%";
		}
		LevelOneStatusRow theRow = new LevelOneStatusRow(runId, updateVersion, accession, email, maxBitScore, blastp,
				commonDomains, ortholog, startDate, endDate, runDuration);

		return theRow;
	}
}

// class LevelOneReportMapper implements RowMapper<LevelOneReportRow> {
//
// @Override
// public LevelOneReportRow mapRow(ResultSet rs, int rowNumber) throws
// SQLException {
// String accession = rs.getString("hit_accession_id");
// int proteinCount = rs.getInt("protein_count");
// int taxId = rs.getInt("taxid");
// String className = rs.getString("class");
// if (className == null) {
// className = rs.getString("order");
// }
// String scientificName = rs.getString("name");
// String commonName = rs.getString("name");
// String proteinName = rs.getString("protein_title");
// int hitLength = 0;
// int identity = 0;
// int positives = 0;
// double evalue = 0;
// double blastPBitScore = 0;
// String ortholog = "ToDo";
// double percentSimilarity = 0;
// double cutoff = 0;
// int commonDomainCount = 0;
// String susceptible = "ToDo";
// long endDate = rs.getLong("COMPLETION_DATE");
//
// LevelOneReportRow theRow = new LevelOneReportRow(accession, proteinCount,
// taxId, className, scientificName, commonName,
// proteinName, hitLength, identity, positives, evalue, blastPBitScore,
// ortholog, percentSimilarity, cutoff,
// commonDomainCount, susceptible, endDate);
//
// return theRow;
// }
// }

class LevelTwoStatusMapper implements RowMapper<LevelTwoStatusRow> {

	@Override
	public LevelTwoStatusRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int runId = rs.getInt("lev2id");
		int updateVersion = rs.getInt("update_version");
		String accession = rs.getString("query_accession_id");
		String domainAccession = rs.getString("top_hit_accession_id");
		String email = rs.getString("email");
		String domainType = rs.getString("domain_type");
		// double maxBitScore = rs.getDouble("max_bit_score");
		double maxBitScore = 0;
		String blastp = rs.getString("status");
		long startDate = rs.getLong("start");
		long endDate = rs.getLong("end");
		long runDuration = rs.getLong("SeqAPASS Run Duration");
		LevelTwoStatusRow theRow = new LevelTwoStatusRow(runId, updateVersion, accession, domainAccession, email,
				domainType, maxBitScore, blastp, startDate, endDate, runDuration);

		return theRow;
	}
}

class LevelTwoDomainOptionMapper implements RowMapper<LevelTwoRequestableRow> {

	@Override
	public LevelTwoRequestableRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int runId = rs.getInt("accession_run_id");
		int domainNumber = rs.getInt("xml_Hit_accession");
		int startPosition = rs.getInt("start_position");
		String key = domainNumber + ":" + startPosition;

		String accession = (String) rs.getString("canonical_accession_id");
		String displayText = "(" + startPosition + ") " + rs.getString("description");
		int lev2RunId = 0;
		Object lev2RunThing = rs.getObject("lev2id");

		if (lev2RunThing != null) {
			lev2RunId = (int) lev2RunThing;
		}
		LevelTwoRequestableRow theRow = new LevelTwoRequestableRow(runId, accession, domainNumber, key, startPosition,
				displayText, lev2RunId);
		// System.out.print("Jammed sting = " + runId + accession + domainNumber
		// + key + displayText + lev2RunId);
		return theRow;
	}
}

class LevelThreeStatusMapper implements RowMapper<LevelThreeStatusRow> {

	@Override
	public LevelThreeStatusRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int runId = rs.getInt("id");
		int updateVersion = rs.getInt("update_version");
		String accession = rs.getString("query_accession_id");
		String email = rs.getString("email");
		String jobName = rs.getString("job_name");
		String templateAccession = rs.getString("template_name");
		String cobalt = rs.getString("status");
		long startDate = rs.getLong("start");
		long endDate = rs.getLong("end");
		long runDuration = rs.getLong("run_duration");
		LevelThreeStatusRow theRow = new LevelThreeStatusRow(runId, updateVersion, accession, email, jobName,
				templateAccession, cobalt, startDate, endDate, runDuration);

		return theRow;
	}

}

class HistogramMapper implements RowMapper<HistogramRow> {

	@Override
	public HistogramRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String bin = rs.getString("bin");
		int binCount = rs.getInt("count");
		HistogramRow theRow = new HistogramRow(bin, binCount);

		return theRow;
	}

}

class DensityMapper implements RowMapper<DensityRow> {

	@Override
	public DensityRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		double percSim = rs.getDouble("sim");
		String ortho = rs.getString("rbh_status");
		DensityRow theRow = new DensityRow(percSim, ortho);

		return theRow;
	}

}

class EcosMapper implements RowMapper<TaxEcos> {

	@Override
	public TaxEcos mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int taxid = rs.getInt("taxid");
		int ecosId = rs.getInt("ecos_id");
		TaxEcos taxEcos = new TaxEcos(taxid, ecosId);

		return taxEcos;
	}

}

class LevelThreeRequestableMapper implements RowMapper<LevelThreeRequestableRow> {

	@Override
	public LevelThreeRequestableRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int accessionRunId = rs.getInt("accession_run_id");
		int userId = rs.getInt("user_id");
		int level3Id = rs.getInt("id");
		String jobName = rs.getString("job_name");

		List<String> dummyList = new ArrayList<String>();
		// dummyList.add("NP_000116.2");
		// dummyList.add("1ERR_A");
		String template = rs.getString("template_name");
		String additionalComparisons = "";
		// List<String> dummyList = new ArrayList<String>();
		// dummyList.add("");
		LevelThreeRequestableRow theRow = new LevelThreeRequestableRow(accessionRunId, userId, level3Id, jobName,
				template, dummyList, additionalComparisons);

		return theRow;
	}

}

class LevelFourRequestableMapper implements RowMapper<LevelFourRequestableRow> {

	@Override
	public LevelFourRequestableRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int accessionRunId = rs.getInt("accession_run_id");
		int userId = rs.getInt("user_id");
		int level4Id = rs.getInt("id");
		String jobName = rs.getString("job_name");
		String status = rs.getString("status");
		int sourceLevel = rs.getInt("source_level");
		int level2RunId = rs.getInt("level2_run_id");
		int startPos = rs.getInt("start_position");
		String fullDef = rs.getString("full_definition");
		
		String domainID = null;
		if (fullDef != null) {
			domainID = fullDef.split(",", 3)[0];
		}
		
//		String domainName = domainParts[1];

//		List<String> dummyList = new ArrayList<String>();
		// dummyList.add("NP_000116.2");
		// dummyList.add("1ERR_A");
		String template = rs.getString("template");
		// List<String> dummyList = new ArrayList<String>();
		// dummyList.add("");
		LevelFourRequestableRow theRow = new LevelFourRequestableRow(accessionRunId, userId, level4Id, jobName, template, status, sourceLevel, level2RunId, startPos, domainID);

		return theRow;
	}

}


class LevelFourDataMapper implements RowMapper<LevelFourAccessionRow> {

	@Override
	public LevelFourAccessionRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		
		String priority = rs.getString("current_priority");
		String autoPriority = rs.getString("default_priority");
		String status = rs.getString("status");
		String accession = rs.getString("accession");
		String fasta = rs.getString("fasta");
		double cscore = rs.getDouble("cscore");
		if(rs.wasNull()) {
			cscore = -9999;
		}
		double tm_score = rs.getDouble("tm_score");
		double tm_score_error = rs.getDouble("tm_score_error");
		double rmsd = rs.getDouble("rmsd");
		double rmsd_error = rs.getDouble("rmsd_error");
		double density = rs.getDouble("density");
		String pdb = rs.getString("pdb");

		long itasserStart = rs.getLong("itasser_start");
		long itasserEnd = rs.getLong("itasser_end");
		if(rs.wasNull()) {
			itasserEnd = itasserStart;
		}
		int level4RunId = rs.getInt("level4_run_id");
		
		LevelFourAccessionRow theRow = new LevelFourAccessionRow(status, priority, autoPriority, accession, fasta,
				cscore, tm_score, tm_score_error, rmsd, rmsd_error, density, pdb, itasserStart, itasserEnd, level4RunId);

		return theRow;
	}

}

class LevelFourStatusMapper implements RowMapper<LevelFourStatusRow> {

	@Override
	public LevelFourStatusRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int runId = rs.getInt("id");
		int updateVersion = rs.getInt("update_version");
		String accession = rs.getString("top_hit_accession_id");
		String email = rs.getString("email");
		String jobName = rs.getString("job_name");
		String template = rs.getString("template");
		int fastasCompleted = rs.getInt("numFastas");
		long fastaEnd = rs.getLong("fasta_end");
		long itasserStart = rs.getLong("itasser_start");
		long itasserEnd = rs.getLong("itasser_end");
		long tmalignStart = rs.getLong("tmalign_start");
		long tmalignEnd = rs.getLong("tmalign_end");
		
		String status = rs.getString("status");
		LevelFourStatusRow theRow = new LevelFourStatusRow(runId, updateVersion, accession, email, jobName,
				template, status, fastasCompleted, fastaEnd, itasserStart, itasserEnd, tmalignStart, tmalignEnd);

		return theRow;
	}

}

class TMAlignStatusMapper implements RowMapper<LevelFourStatusRow> {

	@Override
	public LevelFourStatusRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int tmalignRunId = rs.getInt("tmalignRunId");
		int numJobs = rs.getInt("numJobs");
		int level4RunId = rs.getInt("level4RunId");
		int updateVersion = rs.getInt("update_version");
		String queryAccession = rs.getString("query_accession");
		int queryAccLevel4RunId = rs.getInt("query_acc_level4_run_id");
		String email = rs.getString("email");
		String jobName = rs.getString("job_name");
		//String template = rs.getString("template");
		String status = rs.getString("status");
		long tmalignStart = rs.getLong("start");
		long tmalignEnd = rs.getLong("end");
		
		
		LevelFourStatusRow theRow = new LevelFourStatusRow(tmalignRunId, level4RunId, updateVersion, 
				queryAccession, queryAccLevel4RunId, email, jobName, status, tmalignStart, tmalignEnd, numJobs);

		return theRow;
	}

}

class LevelFourResultRowMapper implements RowMapper<LevelFourResultRow> {
	
	@Override
	public LevelFourResultRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String acc1 = rs.getString("acc1");
		String acc2 = rs.getString("acc2");
		int length1 = rs.getInt("length1");
		int length2 = rs.getInt("length2");
		double tmscore1 = rs.getDouble("tmscore1");
		double tmscore2 = rs.getDouble("tmscore2");
		int level4RunId = rs.getInt("level4_run_id");  //of acc2
		String template = rs.getString("template");  //of acc2
		String pdbSource = rs.getString("pdb_source");
		//The following fields are null for I-TASSER sources
		String protName = rs.getString("prot_name");
		int taxId = rs.getInt("tax_id");
		String taxGrp = rs.getString("tax_group");
		String sciName = rs.getString("sci_name");
		String commonName = rs.getString("common_name");
		String pdb = rs.getString("pdb");
//		String queryPdb = rs.getString("query_pdb");
//		//if other source pdb is either listed in pdb or query_pdb
//		//if i-tasser, then both will be null
//		if (pdb == null) {
//			pdb = queryPdb;
//		}
		
		
		LevelFourResultRow theRow = new LevelFourResultRow(acc1, acc2, length1, length2, 
			tmscore1, tmscore2, level4RunId, template, pdbSource, protName, taxId, taxGrp,
			sciName, commonName, pdb);
		
		return theRow;
		
	}
	
}

class LevelFourQueryResultRowMapper implements RowMapper<LevelFourResultRow> {
	
	@Override
	public LevelFourResultRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String queryAcc = rs.getString("query_accession");
		int queryAccRunId = rs.getInt("query_acc_level4_run_id");
		String queryPDBSource = rs.getString("query_pdb_source");
		if(rs.wasNull()) {
			queryPDBSource = "I-TASSER";
		}
		String queryProtName = rs.getString("query_prot_name");
		if(rs.wasNull()) {
			queryProtName = "-";
		}
		int queryTaxId = rs.getInt("query_tax_id");
		if(rs.wasNull()) {
			queryTaxId = -9999;
		}
		String queryTaxGrp = rs.getString("query_tax_group");
		if(rs.wasNull()) {
			queryTaxGrp = "-";
		}
		String querySciName = rs.getString("query_sci_name");
		if(rs.wasNull()) {
			querySciName = "-";
		}
		String queryCommonName = rs.getString("query_common_name");
		if(rs.wasNull()) {
			queryCommonName = "-";
		}
		String queryPdb = rs.getString("query_pdb");
		
		LevelFourResultRow theRow = new LevelFourResultRow();
		theRow.setAcc1(queryAcc);
		theRow.setAcc2(queryAcc);
		theRow.setLevel4RunId(queryAccRunId);
		theRow.setPdbSource(queryPDBSource);
		theRow.setProteinName(queryProtName);
		theRow.setSpeciesTaxId(queryTaxId);
		theRow.setTaxonomyName(queryTaxGrp);
		theRow.setScientificName(querySciName);
		theRow.setCommonName(queryCommonName);
		theRow.setPdb(queryPdb);
		
		return theRow;
		
	}
	
}


class LevelFourRequestableRowMapper implements RowMapper<LevelFourRequestableRow> {
	
	@Override
	public LevelFourRequestableRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
		
		int tmAlignRunId = rs.getInt("id");
		String query_accession = rs.getString("query_accession");
		int queryLevel4RunId = rs.getInt("query_acc_level4_run_id");
		String queryPdbSource = rs.getString("query_pdb_source");
		if(rs.wasNull()) {
			queryPdbSource = "I-TASSER";
		}
		String template = rs.getString("template");
		String jobName = rs.getString("job_name");
		int sourceLevel = rs.getInt("source_level");
		
		
		LevelFourRequestableRow theRow = new LevelFourRequestableRow(query_accession, queryLevel4RunId, queryPdbSource, template, jobName, tmAlignRunId, sourceLevel);
		
		return theRow;
		
	}
	
}

class ReportInfoMapper implements RowMapper<ReportInfo> {

	@Override
	public ReportInfo mapRow(ResultSet rs, int rowNumber) throws SQLException {

		Long ncbiDate = rs.getDate("taxonomy_protein_date").getTime();
		String blastExecVersion = rs.getString("blast_exec_version");
		Long cddDate = rs.getDate("cdd_data_date").getTime();
		String cobaltVersion = rs.getString("cobalt_data_version");
		Long cobaltDate = rs.getDate("cobalt_data_date").getTime();
		String itasserVersion = rs.getString("itasser_exec_version");
		String tmalignVersion = rs.getString("tmalign_exec_version");
		java.sql.Date tmpUniprotDate = rs.getDate("uniprot_date");
		Long uniprotDate = null;
		if(!rs.wasNull()) {
			uniprotDate = tmpUniprotDate.getTime();
		}
		Long installDate = rs.getDate("install_date").getTime();
		String javaVersion = rs.getString("java_version");
		String primefacesVersion = rs.getString("primefaces_version");
		String tomcatVersion = rs.getString("tomcat_version");
		String dbServerVersion = rs.getString("db_server_version");
		// String rVersion = rs.getString("r_version");
		int updateVersion = rs.getInt("update_version");
		String seqapassVersion = rs.getString("seqapass_version");
		String notes = rs.getString("notes");

		ReportInfo info = new ReportInfo(ncbiDate, uniprotDate, blastExecVersion, cddDate, cobaltVersion, cobaltDate, itasserVersion, tmalignVersion,
				installDate, javaVersion, primefacesVersion, tomcatVersion, dbServerVersion, null, updateVersion, seqapassVersion,
				notes);

		return info;
	}

}

class AminoAcidMapper implements RowMapper<AminoAcid> {

	@Override
	public AminoAcid mapRow(ResultSet rs, int rowNumber) throws SQLException {
		char id = rs.getString("id").charAt(0);
		String name = rs.getString("name");
		String sideChain = rs.getString("side_chain");
		float size = rs.getFloat("size");
		AminoAcid theAcid = new AminoAcid(id, name, sideChain, size);
		return theAcid;
	}

}

class LinkMapper implements RowMapper<Link> {

	@Override
	public Link mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String url = rs.getString("url");
		String label = rs.getString("label");
		String infoHeader = rs.getString("info_header");
		String infoText = rs.getString("info_text");
		Link theLink = new Link(url, label, infoHeader, infoText);
		return theLink;
	}

}


class ChemicalMapper implements RowMapper<Chemical> {

	@Override
	public Chemical mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int id = rs.getInt("id");
		int casNum = rs.getInt("cas_number");
		String dtxsid = rs.getString("dtxsid");
		String chemPrimaryName = rs.getString("chem_primary_name");
		String ecotoxPreferredName = rs.getString("ecotox_preferred_name");
		
		Chemical theRow = new Chemical(ecotoxPreferredName, chemPrimaryName, casNum, dtxsid);
		
		return theRow;
	}
}
