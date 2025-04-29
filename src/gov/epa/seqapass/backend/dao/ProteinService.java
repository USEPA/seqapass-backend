package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.Protein;

import java.util.List;

public interface ProteinService {

//	public List<Protein> getAllProteins();

	public Protein getProteinByAccession(String accession);

	public List<Protein> getProteinsByTaxID(int taxid);

	public List<Protein> getFilteredProteins(int taxid, String search);

}
