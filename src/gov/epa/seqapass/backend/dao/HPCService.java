package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.Protein;

import java.util.List;

public interface HPCService {

	public List<Protein> getTaxidsFromAccessions(List<Protein> proteinsToLookup);

}
