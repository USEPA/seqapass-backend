package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.Species;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class SpeciesServiceImpl implements SpeciesService {

	private JdbcTemplate jdbcTemplate;

	public SpeciesServiceImpl(JdbcTemplate template) {
		this.jdbcTemplate = template;
	}

	@Override
	public List<Species> getSpeciesAutoComplete(String searchString) {
		try {
			Integer taxid = Integer.parseInt(searchString);
			String query = "SELECT taxid, name FROM taxonomy_name WHERE taxid = ?";
			return jdbcTemplate.query(query, new SpeciesMapper(), taxid);
		} catch (NumberFormatException e) {
			// Do nothing, this just means that it was not an integer
		}
		StringBuilder b = new StringBuilder();
		b.append("(SELECT taxid, name FROM taxonomy_name WHERE uc_name LIKE ? LIMIT 200 )");
		b.append("UNION (SELECT taxid, name FROM taxonomy_name WHERE MATCH(`name`) AGAINST(?) LIMIT 200 )");
		b.append("UNION (SELECT a.taxid, a.name FROM taxonomy_name a, name_frag b WHERE b.name LIKE ? AND a.id = b.id LIMIT 200 )");
		String startsWithSearchString = searchString.toUpperCase() + "%";
		return jdbcTemplate.query(b.toString(), new SpeciesMapper(), startsWithSearchString, searchString, startsWithSearchString);
	}
}

class SpeciesMapper implements RowMapper<Species> {
	public Species mapRow(ResultSet rs, int rowNumber) throws SQLException {
		String name = rs.getString("name");
		int taxId = rs.getInt("taxid");

		Species theSpecies = new Species(taxId, name);
		return theSpecies;
	}
}
