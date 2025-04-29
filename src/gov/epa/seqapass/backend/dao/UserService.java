package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.backend.domain.User;
import gov.epa.seqapass.common.UserMessage;

import java.util.List;

public interface UserService {

	public List<User> getAllUsers();

	public User getUserById(int userId);

	public int addUser(String email);

	public User getUserInfo(String email);

	public int changeUserPassword(String email, String origPass, String newPass);

	public boolean userExists(String email);

	public int updateNCBIVersion();

	public UserMessage getUserMessages();

	int addUserOld(String email, String isAdmin, String isItasser);

}
