package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.Species;

import java.util.List;



public interface SpeciesService {

	public List<Species> getSpeciesAutoComplete(String searchString);
	
}
