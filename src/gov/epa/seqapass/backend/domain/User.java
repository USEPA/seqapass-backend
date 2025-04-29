package gov.epa.seqapass.backend.domain;

public class User {

	public User() {
	};

//	public User(int userid, String firstName, String lastName, String email, String isAdmin) {
//		this.userid = userid;
//		this.firstName = firstName;
//		this.lastName = lastName;
//		this.email = email;
//		this.isAdmin = isAdmin;
//
//	}
	
	public User(String firstName, String lastName, String email) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;

	}
	
	public User(int userid, String email, String isAdmin, String enabled) {
		this.userid = userid;
		this.email = email;
		this.isAdmin = isAdmin;
		this.isEnabled = enabled;
	}
	
	public User(int userid, String email, String isAdmin, String isItasser, String enabled) {
		this.userid = userid;
		this.email = email;
		this.isAdmin = isAdmin;
		this.isItasser = isItasser;
		this.isEnabled = enabled;
	}


	private int userid;
	private String firstName;
	private String lastName;
	private String email;
	private String isAdmin;
	private String isItasser;
	private String isEnabled;

	public int getUserid() {
		return userid;
	}

	public void setUserid(int userid) {
		this.userid = userid;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getIsAdmin() {
		return isAdmin;
	}

	public void setIsAdmin(String isAdmin) {
		this.isAdmin = isAdmin;
	}

	@Override
	public String toString() {
		return "User [userid=" + userid + ", firstName=" + firstName + ", lastName=" + lastName + ", email=" + email + "]";
	}

	public String getIsEnabled() {
		return isEnabled;
	}

	public void setIsEnabled(String isEnabled) {
		this.isEnabled = isEnabled;
	}

	public String getIsItasser() {
		return isItasser;
	}

	public void setIsItasser(String isItasser) {
		this.isItasser = isItasser;
	}

}
