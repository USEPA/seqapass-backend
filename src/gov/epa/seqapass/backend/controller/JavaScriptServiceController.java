package gov.epa.seqapass.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import gov.epa.seqapass.backend.dao.JavaScriptService;

@RestController
@RequestMapping("/protected/service/javascript")
public class JavaScriptServiceController {

	JavaScriptService jsService;

	public JavaScriptServiceController(JavaScriptService jsService) {
		this.jsService = jsService;
	}

	// get javascript code for given entry name
	@RequestMapping(value = "/getJSCode", method = RequestMethod.GET, headers = "Accept=application/json", params = "name")
	public @ResponseBody String getJSCode(@RequestParam("name") String name) {
		String jsCode = jsService.getJavaScriptCode(name);
		return jsCode;
	}

}
