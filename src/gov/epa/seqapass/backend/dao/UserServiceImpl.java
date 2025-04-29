package gov.epa.seqapass.backend.dao;


import gov.epa.seqapass.backend.domain.NCBIKeeper;
import gov.epa.seqapass.backend.domain.BackEndProvider;

import gov.epa.seqapass.backend.domain.User;
import gov.epa.seqapass.common.UserMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EnableRetry
public class UserServiceImpl implements UserService {
	
	private static Logger logger = LogManager.getLogger(UserServiceImpl.class);

	private JdbcTemplate jdbcTemplate;
	private NCBIKeeper ncbiKeeper;
	private BackEndProvider backEnd;

	private static final String GET_ALL_USERS_SQL = "select * from `user` limit 15";
	private static final String GET_USER_BY_ID_SQL = "select * from `user` where id=?";
	private static final String GET_USER_BY_EMAIL_SQL = "select email, id, is_admin, is_itasser, enabled from `user` where email=?";
//	private static final String ADD_USER_SQL = "insert into user (EMAIL, LAST_NAME, FIRST_NAME, PASSWORD, IS_ADMIN) values (?,?,?,?,?)";
	private static final String GET_USER_PASS_SQL = "SELECT password FROM `user` where email = ?";
	private static final String UPDATE_USER_PASS_SQL = "UPDATE user SET password = ? where email = ?";
	private static final String GET_USER_EXISTS = "SELECT COUNT(*) FROM `user` where email = ?";
	private static final String GET_USER_MESSAGES = "SELECT * FROM status WHERE host = ? ORDER BY id DESC LIMIT 1";

	public UserServiceImpl(JdbcTemplate template, NCBIKeeper ncbiKeeper, BackEndProvider backEnd) {
		this.jdbcTemplate = template;
		this.ncbiKeeper = ncbiKeeper;
		this.backEnd = backEnd;
	}

	@Override
	public List<User> getAllUsers() {
		return jdbcTemplate.query(GET_ALL_USERS_SQL, new UserMapper());
	}

	@Override
	public User getUserById(int userId) {
		return jdbcTemplate.queryForObject(GET_USER_BY_ID_SQL, new UserMapper(), userId);
	}

	// @Override
	// @Transactional
	// @Retryable(maxAttempts=2, value=RuntimeException.class, backoff= @Backoff(delay = 1000, multiplier=2))
	// public int addUser(String email, String lastName, String firstName, String password, String isAdmin) {
	// // jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, password, isAdmin); // This causes duplicate key exception - for
	// transaction testing purposes only
	// // System.out.println("Trying addUser");
	// //// throw new RuntimeException("Runtime exception");
	// // jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, password, isAdmin);
	// // return -1;
	// BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	// String hashedPassword = passwordEncoder.encode(password);
	// return jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, hashedPassword, isAdmin);
	//
	// }
	
	@Override
	@Transactional
	@Retryable(maxAttempts = 2, value = RuntimeException.class, backoff = @Backoff(delay = 1000, multiplier = 2))
	public int addUser(String email) {
		StringBuilder b = new StringBuilder();
		b.append("insert into user (EMAIL) values (?)");

		return jdbcTemplate.update(b.toString(), email);
	}

//	@Override
//	@Transactional
//	@Retryable(maxAttempts = 2, value = RuntimeException.class, backoff = @Backoff(delay = 1000, multiplier = 2))
//	public int addUserOld(String email, String lastName, String firstName, String password, String isAdmin) {
//		// jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, password, isAdmin); // This causes duplicate key exception - for
//		// transaction testing purposes only
//		// System.out.println("Trying addUser");
//		// // throw new RuntimeException("Runtime exception");
//		// jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, password, isAdmin);
//		// return -1;
//		List<String> parmList = new ArrayList<String>();
//		StringBuilder onDup = new StringBuilder();
//		onDup.append(" ON DUPLICATE KEY UPDATE EMAIL=?,");
//		int numParms = 0;
//		StringBuilder b = new StringBuilder();
//		b.append("insert into user (EMAIL");
//		parmList.add(email);
//		if (lastName != null && !lastName.isEmpty()) {
//			b.append(", LAST_NAME");
//			onDup.append("LAST_NAME=?,");
//			parmList.add(lastName);
//			numParms++;
//		}
//		if (firstName != null && !firstName.isEmpty()) {
//			b.append(", FIRST_NAME");
//			onDup.append("FIRST_NAME=?,");
//			parmList.add(firstName);
//			numParms++;
//		}
//		if (password != null && !password.isEmpty()) {
//			b.append(", PASSWORD");
//			onDup.append("PASSWORD=?,");
//			BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
//			String hashedPassword = passwordEncoder.encode(password);
//			parmList.add(hashedPassword);
//			numParms++;
//		}
//		b.append(", IS_ADMIN) values (?,?"); // must have email and isAdmin
//		onDup.append("IS_ADMIN=?");
//		parmList.add(isAdmin);
//
//		for (int i = 0; i < numParms; i++) {
//			b.append(",?");
//		}
//		b.append(")");
//		b.append(onDup.toString());
//
//		List<String> tempList = new ArrayList<String>(parmList);
//		for (String val : tempList) {
//			parmList.add(val);
//		}
//
//		System.out.println("Query: " + b.toString());
//
//		return jdbcTemplate.update(b.toString(), parmList.toArray());
//
//		// return jdbcTemplate.update(ADD_USER_SQL, email, lastName, firstName, hashedPassword, isAdmin);
//
//	}
	
	@Override
	@Transactional
	@Retryable(maxAttempts = 2, value = RuntimeException.class, backoff = @Backoff(delay = 1000, multiplier = 2))
	public int addUserOld(String email, String isAdmin, String isItasser) {

		List<String> parmList = new ArrayList<String>();
		StringBuilder onDup = new StringBuilder();

		
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE user SET is_admin = ?, is_itasser = ? WHERE email LIKE ?");

		return jdbcTemplate.update(sb.toString(), isAdmin, isItasser, email);

	}

	@Recover
	public void recover(RuntimeException e) {
		// System.out.println("This method is run when @Retryable fails maxAttempts");
		logger.error("This method is run when @Retryable fails maxAttempts");
	}

	@Override
	public User getUserInfo(String email) {
		return jdbcTemplate.queryForObject(GET_USER_BY_EMAIL_SQL, new UserMapper(), email);
	}

	@Override
	public int changeUserPassword(String email, String origPass, String newPass) {
		String origPassDBHash = jdbcTemplate.queryForObject(GET_USER_PASS_SQL, String.class, email);
		if (BCrypt.checkpw(origPass, origPassDBHash)) {
			BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
			String hashedPassword = passwordEncoder.encode(newPass);
			return jdbcTemplate.update(UPDATE_USER_PASS_SQL, hashedPassword, email);
		} else {
			// System.out.println("BadMatch");
			logger.error("BadMatch");
			return 0;
		}

	}

	@Override
	public boolean userExists(String email) {
		int userCount = jdbcTemplate.queryForObject(GET_USER_EXISTS, int.class, email);
		if (userCount < 1)
			return false;
		else
			return true;
	}

	@Override
	public UserMessage getUserMessages() {
		String domain = backEnd.getDomainName();
		return jdbcTemplate.queryForObject(GET_USER_MESSAGES, new UserMessageMapper(), domain);
	}
	
	public int updateNCBIVersion(){
		ncbiKeeper.updateNCBIProviderMap();
		return ncbiKeeper.getPreferredNCBIProviderID();
	}

}

class UserMapper implements RowMapper<User> {
	public User mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int id = rs.getInt("ID");
//		String firstName = rs.getString("FIRST_NAME");
//		String lastName = rs.getString("LAST_NAME");
		String email = rs.getString("EMAIL");
		String isAdmin = rs.getString("IS_ADMIN");
		String isItasser = rs.getString("IS_ITASSER");
		String enabled = rs.getString("ENABLED");

		User theUser = new User(id, email, isAdmin, isItasser, enabled);
		return theUser;
	}
}

class UserMessageMapper implements RowMapper<UserMessage> {
	public UserMessage mapRow(ResultSet rs, int rowNumber) throws SQLException {
		int id = rs.getInt("ID");
		String preLoginMsg = rs.getString("LOGIN_PRE_BLOCK_MESSAGE");
		String postLoginMsg = rs.getString("LOGIN_POST_BLOCK_MESSAGE");
		String preSubmitMsg = rs.getString("SUBMIT_PRE_BLOCK_MESSAGE");
		String postSubmitMsg = rs.getString("SUBMIT_POST_BLOCK_MESSAGE");
		Timestamp loginTimestamp = rs.getTimestamp("LOGIN_BLOCK_TIME");
		Long loginBlockTime = null;
		if (loginTimestamp != null) {
			loginBlockTime = loginTimestamp.getTime();
		}
		Timestamp submitTimestamp = rs.getTimestamp("SUBMIT_BLOCK_TIME");
		Long submitBlockTime = null;
		if (submitTimestamp != null) {
			submitBlockTime = submitTimestamp.getTime();
		}
		UserMessage theUserMessage = new UserMessage(preLoginMsg, postLoginMsg, preSubmitMsg, postSubmitMsg, loginBlockTime,
				submitBlockTime, id);
		return theUserMessage;
	}
}
