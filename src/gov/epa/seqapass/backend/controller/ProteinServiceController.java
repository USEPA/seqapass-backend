package gov.epa.seqapass.backend.controller;

import gov.epa.seqapass.backend.dao.ProteinService;
import gov.epa.seqapass.common.Protein;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/protected/service/protein/")
public class ProteinServiceController {

	ProteinService proteinService;

	public ProteinServiceController(ProteinService service) {
		this.proteinService = service;

	}

	@RequestMapping(value = "/{accession:.*}", method = RequestMethod.GET, headers = "Accept=application/json")
	public Protein getProtein(@PathVariable String accession) {
		Protein protein = proteinService.getProteinByAccession(accession);
		return protein;
	}
	
	@RequestMapping(method = RequestMethod.GET, headers = "Accept=application/json", params="taxid")
	public @ResponseBody List<Protein> getProteinsByTaxID(@RequestParam("taxid") int taxid) {
		List<Protein> proteins = proteinService.getProteinsByTaxID(taxid);
		return proteins;
	}
	
	@RequestMapping(method = RequestMethod.GET, headers = "Accept=application/json", params= {"taxid" , "search"})
	public @ResponseBody List<Protein> getFilteredProteins(@RequestParam("taxid") int taxid, @RequestParam("search") String search) {
		List<Protein> proteins = proteinService.getFilteredProteins(taxid, search);
		return proteins;
	}
}
