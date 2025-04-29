package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.SpeciesService;
import gov.epa.seqapass.common.Species;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/protected/service/species/")
public class SpeciesServiceController {

	SpeciesService speciesService;

	public SpeciesServiceController(SpeciesService service) {
		this.speciesService = service;

	}

	@RequestMapping(method = RequestMethod.GET, headers = "Accept=application/json", params="search")
	public @ResponseBody List<Species> autocompleteSpecies(@RequestParam("search") String search) {
		List<Species> species = speciesService.getSpeciesAutoComplete(search);
		return species;
	}	
}
