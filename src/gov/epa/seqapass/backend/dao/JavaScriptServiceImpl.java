package gov.epa.seqapass.backend.dao;

import org.springframework.jdbc.core.JdbcTemplate;

public class JavaScriptServiceImpl implements JavaScriptService {
	
	private JdbcTemplate jdbcTemplate;
	
	public JavaScriptServiceImpl(JdbcTemplate jdbcTemplate){
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public String getJavaScriptCode(String name) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT code ");
		sb.append("  FROM javascript ");
		sb.append("     WHERE name = ? ");
		
		String theCode;
		try {
			theCode = jdbcTemplate.queryForObject(sb.toString(), String.class, name);
		} catch (Exception e) {
			theCode = "";
		}
		return theCode;
	}

}
