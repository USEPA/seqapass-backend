package gov.epa.seqapass.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import gov.epa.seqapass.backend.dao.UserService;
import gov.epa.seqapass.backend.domain.User;

@RestController
@RequestMapping("/protected/service/user")
public class UserServiceController {

	UserService userService;

	public UserServiceController(UserService service) {
		this.userService = service;

	}	

	@RequestMapping( method = RequestMethod.GET, headers = "Accept=application/json", params="id")
	public @ResponseBody User getUser(@RequestParam("id") int id) {
		User user = userService.getUserById(id);
		return user;
	}
	
	@RequestMapping( method = RequestMethod.GET, headers = "Accept=application/json", params="email")
	public @ResponseBody User getUserInfo(@RequestParam("email") String email){
		User user = userService.getUserInfo(email);
		return user;
	}

	@RequestMapping(method = RequestMethod.GET, headers = "Accept=application/json")
	public List<User> getAllUsers() {
		List<User> users = userService.getAllUsers();
		return users;
	}
	
	// addUser GET method
	@RequestMapping(value="/addUser", method = RequestMethod.GET, headers = "Accept=application/json", params = {"email"})
	public @ResponseBody int addUser(@RequestParam("email") String email){
		return userService.addUser(email);
	}
	
	// change user password
	@RequestMapping(value="/changePass", method = RequestMethod.GET, headers="Accept=application/json", params = {"email", "origPass", "newPass"})
	public @ResponseBody int changePass(@RequestParam("email") String email, @RequestParam("origPass") String origPass, @RequestParam("newPass") String newPass)
	{
		return userService.changeUserPassword(email, origPass, newPass);
	}
	
	// check if user exists
	@RequestMapping(value="/userExists", method = RequestMethod.GET, headers="Accept=application/json", params = "email")
	public @ResponseBody boolean userExists(@RequestParam("email") String email){
		return userService.userExists(email);
	}
	
	@RequestMapping(value="/ping", method = RequestMethod.GET, headers = "Accept=application/json")
	public boolean pingBackend() {
		return true;
	}
}
