package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.common.Protein;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

//import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class ProteinServiceImpl implements ProteinService {

	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;

//	private static final String GET_ALL_PROTEINS_SQL = "select * from protein limit 15";
	private static final String GET_PROTEIN_BY_ACCESSION_SQL = "select * from protein where accession_id = ?";
	private static final String GET_PROTEINS_BY_TAXID_SQL = "SELECT * FROM ((SELECT * FROM protein WHERE taxid = ? AND find_in_set(?, ncbi_valid_versions) AND accession_id LIKE 'NP_%' LIMIT 200) UNION (SELECT * FROM protein WHERE taxid = ? AND find_in_set(?, ncbi_valid_versions) ORDER BY RAND() LIMIT 200)) a LIMIT 200";
	private static final String GET_FILTERED_PROTEINS_SQL = "SELECT * FROM ((SELECT * FROM protein WHERE taxid = ? AND find_in_set(?, ncbi_valid_versions) AND accession_id LIKE 'NP_%' AND (title collate ascii_general_ci LIKE ? OR accession_id collate ascii_general_ci LIKE ? ) LIMIT 200 ) UNION (SELECT * FROM protein WHERE taxid = ? AND find_in_set(?, ncbi_valid_versions) AND (title collate ascii_general_ci LIKE ? OR accession_id collate ascii_general_ci LIKE ? ) ORDER BY RAND() LIMIT 200)) a LIMIT 200";
//	private static final String GET_FILTERED_PROTEINS_SQL = "SELECT * FROM protein WHERE taxid = ? AND (title collate ascii_general_ci LIKE ? OR accession_id collate ascii_general_ci LIKE ? ) ORDER BY accession_id+0, accession_id LIMIT 200";

	public ProteinServiceImpl(JdbcTemplate template, NCBIKeeper ncbiKeeper) {
		this.jdbcTemplate = template;
		this.ncbiKeeper = ncbiKeeper;
	}

//	@Override
//	public List<Protein> getAllProteins() {
//		return jdbcTemplate.query(GET_ALL_PROTEINS_SQL, new ProteinMapper());
//	}

	@Override
	public Protein getProteinByAccession(String accession) {
		try {
			return jdbcTemplate.queryForObject(GET_PROTEIN_BY_ACCESSION_SQL, new ProteinMapper(), accession);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public List<Protein> getProteinsByTaxID(int taxid) {
		return jdbcTemplate.query(GET_PROTEINS_BY_TAXID_SQL, new ProteinMapper(),
				taxid, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion(),
				taxid, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion());
	}

	@Override
	public List<Protein> getFilteredProteins(int taxid, String search) {
		search = "%" + search + "%";
//		return jdbcTemplate.query(GET_FILTERED_PROTEINS_SQL, new ProteinMapper(), taxid, search, search);
		return jdbcTemplate.query(GET_FILTERED_PROTEINS_SQL, new ProteinMapper(), taxid, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion(), search, search, taxid, ncbiKeeper.getPreferredNCBIProvider().getUpdateVersion(), search, search);
	}

}

class ProteinMapper implements RowMapper<Protein> {
	public Protein mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String name = rs.getString("TITLE");
		String accession = rs.getString("ACCESSION_ID");
		int taxId = rs.getInt("TAXID");

		Protein theProtein = new Protein(name, accession, taxId);
		return theProtein;
	}
}