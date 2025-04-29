package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.Partition;
import gov.epa.seqapass.common.Protein;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
//import java.util.Collection;
import java.util.List;
//import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HPCServiceImpl implements HPCService {
	
	private static Logger logger = LogManager.getLogger(HPCServiceImpl.class);

	private JdbcTemplate jdbcTemplate;
	private static final String GET_TAXIDS_SQL = "select accession_id, taxid from protein where accession_id in (";

	public HPCServiceImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	@Override
	public List<Protein> getTaxidsFromAccessions(List<Protein> proteinsToLookup) {
		// System.out.println("Get Links For Group: Number of proteins to lookup = " + proteinsToLookup.size());
		logger.info("Get Links For Group: Number of proteins to lookup = {}", 
				proteinsToLookup.size());
		List<Protein> resultList = new ArrayList<Protein>();
		int chunkSize = 300;
		Partition<Protein> partitions = Partition.ofSize(proteinsToLookup, chunkSize);
		StringBuilder b = new StringBuilder();
		for (int i=0; i<partitions.size();i++) {
			b.setLength(0);
			List<Protein> proteins = partitions.get(i);
			b.append("'");
			b.append(proteins.get(0).getAccession());
			b.append("'");
			for (int j=1;j<proteins.size();j++) {
				b.append(",'");
				b.append(proteins.get(j).getAccession());
				b.append("'");
			}
			b.append(")");
			// System.out.println("QUERY for retrieving taxids: " + GET_TAXIDS_SQL + b.toString());
			logger.info("QUERY for retrieving taxids: {}", GET_TAXIDS_SQL + b.toString());
			resultList.addAll(jdbcTemplate.query(GET_TAXIDS_SQL+b.toString(), new ProteinTaxidMapper()));
		}
		
		return resultList;
	}
}

class ProteinTaxidMapper implements RowMapper<Protein> {
	public Protein mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String accession = rs.getString("ACCESSION_ID");
		int taxId = rs.getInt("TAXID");
		Protein theProtein = new Protein("name", accession, taxId);
		return theProtein;
	}
}