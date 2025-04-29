package gov.epa.seqapass.backend.externalProcess;

//import gov.epa.seqapass.backend.domain.NCBIKeeper;
//import gov.epa.seqapass.backend.domain.NCBIProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessTypeKeeper {
	private static final Map<String, ProcessTypeProvider> processTypes = new HashMap<String, ProcessTypeProvider>();

	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry [accession_id] e.g.: NP_000116.2 <br/>
	 * -outfmt %a <br/>
	 */
	public static final String getCanonicalAccessionProcess = "getCanonicalAccessionProcess";
	
	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry [accession_id] e.g.: NP_000116.2 <br/>
	 * -outfmt "%a %T " <br/>
	 */
	public static final String getCanonicalAccessionAndTaxidsProcess = "getCanonicalAccessionAndTaxidsProcess";

	/**
	 * blastdb_aliastool <br/>
	 * -seqid_file_in [path to acclist] e.g. [path]/7955.acclist_proto <br/>
	 * -seqid_file_out [path to acclist output] e.g. path/7955.acclist <br/>
	 */
	public static final String fixAccListProcess = "fixAccListProcess";
	
	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry [accession_id] e.g.: NP_000116.2 <br/>
	 * -outfmt %f <br/>
	 */
	public static final String getOneFastaProcess = "getOneFastaProcess";

	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry_batch run2_ERO62871.1.txt <br/>
	 * -outfmt %f <br/>
	 */
	public static final String getManyFastasProcess = "getManyFastasProcess";

	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry NP_000116.2 <br/>
	 * -outfmt %f <br/>
	 * -out NP_000116.2.fasta <br/>
	 */
	public static final String saveFastaProcess = "saveFastaProcess";

	/**
	 * blastdbcmd <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -dbtype prot <br/>
	 * -entry_batch run2_ERO62871.1.txt <br/>
	 * -out run2_ERO62871.1.fsa <br/>
	 */
	public static final String saveTempFastaProcess = "saveTempFastaProcess";

	/**
	 * blastp <br/>
	 * -query fasta_seq.fsa <br/>
	 * -subject fasta_seq.fsa <br/>
	 * -max_target_seqs 20000 <br/>
	 * -outfmt 5 <br/>
	 */
	public static final String selfAlignProcess = "selfAlignProcess";

	/**
	 * blastp <br/>
	 * -query fasta_seq.fsa <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -word_size 3 <br/>
	 * -num_threads 16 <br/>
	 * -evalue 10 <br/>
	 * -max_target_seqs 20000 <br/>
	 * -outfmt 5 <br/>
	 */
	public static final String findHitProteinsProcess = "findHitProteinsProcess";

	/**
	 * blastp <br/>
	 * -query fasta_seq.fsa <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -word_size 3 <br/>
	 * -num_threads 16 <br/>
	 * -evalue 10 <br/>
	 * -max_target_seqs 20000 <br/>
	 * -outfmt 5 <br/>
	 * -out [path to temp output file] e.g.: /path/to/temp/dir/blastp_12_output.xml <br/>
	 */
	public static final String findHitProteinsToFileProcess = "findHitProteinsToFileProcess";

	/**
	 * blastp <br/>
	 * -query one_or_more_accessions.fasta <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -seqidlist all_accession_codes_from_species_of_original_query_protein.txt <br/>
	 * -word_size 3 <br/>
	 * -evalue 10 <br/>
	 * -outfmt 5 <br/>
	 */
	public static final String findRbhBlastResultsProcess = "findRbhBlastResultsProcess";

	/**
	 * blastp <br/>
	 * -query one_or_more_accessions.fasta <br/>
	 * -db [path to nr database] e.g.: /project/seqapass/blast/data/blast-nr-db/nr <br/>
	 * -seqidlist all_accession_codes_from_species_of_original_query_protein.txt <br/>
	 * -word_size 3 <br/>
	 * -evalue 10 <br/>
	 * -outfmt 5 <br/>
	 * -out [path to temp output file] e.g.: /path/to/temp/dir/blastp_12_output.xml <br/>
	 */
	public static final String findRbhBlastResultsToFileProcess = "findRbhBlastResultsToFileProcess";

	/**
	 * rpsblast <br/>
	 * -query [path to fasta file with one or more accession ids to run] e.g.: one_or_more_accessions.fasta <br/>
	 * -db [path to rps cdd database] e.g.: /project/seqapass/blast/data/blast-cdd-db/Cdd <br/>
	 * -evalue 0.01 <br/>
	 * -outfmt 5 <br/>
	 */
	public static final String findRpsBlastResultsProcess = "findRpsBlastResultsProcess";

	/**
	 * rpsblast <br/>
	 * -query [path to fasta file with one or more accession ids to run] e.g.: one_or_more_accessions.fasta <br/>
	 * -db [path to rps cdd database] e.g.: /project/seqapass/blast/data/blast-cdd-db/Cdd <br/>
	 * -evalue 0.01 <br/>
	 * -outfmt 5 <br/>
	 * -out [path to temp output file] e.g.: /path/to/temp/dir/blastp_12_output.xml <br/>
	 */
	public static final String findRpsBlastResultsToFileProcess = "findRpsBlastResultsToFileProcess";

	/**
	 * cobalt <br/>
	 * -i [path to fasta file with sequences to align] e.g.: sequencesToAlign.fsa <br/>
	 * -rpsdb [path to cobalt cdd database] e.g.: /project/seqapass/blast/data/blast-cdd-db//cdd_clique_0.75 <br/>
	 * -alph [Alphabet for used k-mer counting] e.g. regular
	 */
	public static final String runCobalt = "runCobalt";

//	private NCBIKeeper ncbiKeeper;

//	public ProcessTypeKeeper(NCBIKeeper keeper) {
	public ProcessTypeKeeper() {
//		this.ncbiKeeper = keeper;
		createProcessTypes();
	}

	public static ProcessTypeProvider getProcessTypeProviderByName(String name) {
		return processTypes.get(name);
	}

	static Map<String, ProcessTypeProvider> getProcessTypes() {
		return processTypes;
	}

	private void createProcessTypes() {
		if (processTypes.size() > 0) {
			return;
		}
		// --- GET CANONICAL ACCESSION ID ---
//		NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider getCanonicalAccession = new ProcessTypeProvider();
		getCanonicalAccession.setName(getCanonicalAccessionProcess);
		getCanonicalAccession.setCommand("blastdbcmd");
//		getCanonicalAccession.setPath(ncbiProvider.getBlastExecPath());
		List<String> argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry");
		argNames.add("-outfmt");
		getCanonicalAccession.setArgNames(argNames);
		processTypes.put(getCanonicalAccessionProcess, getCanonicalAccession);
		
		// --- GET CANONICAL ACCESSION ID ---
//		NCBIProvider ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider getCanonicalAccessionAndTaxids = new ProcessTypeProvider();
		getCanonicalAccessionAndTaxids.setName(getCanonicalAccessionAndTaxidsProcess);
		getCanonicalAccessionAndTaxids.setCommand("blastdbcmd");
//		getCanonicalAccession.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry");
		argNames.add("-outfmt");
		getCanonicalAccessionAndTaxids.setArgNames(argNames);
		processTypes.put(getCanonicalAccessionAndTaxidsProcess, getCanonicalAccessionAndTaxids);

		// --- FIX an ACC list to optimize it ---
		ProcessTypeProvider fixAccList = new ProcessTypeProvider();
		fixAccList.setName(fixAccListProcess);
		fixAccList.setCommand("blastdb_aliastool");
		argNames = new ArrayList<String>();
		argNames.add("-seqid_file_in");
		argNames.add("-seqid_file_out");
		fixAccList.setArgNames(argNames);
		processTypes.put(fixAccListProcess, fixAccList);
		
		// --- GET ONE FASTA FILE ---
//		ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider getOneFasta = new ProcessTypeProvider();
		getOneFasta.setName(getOneFastaProcess);
		getOneFasta.setCommand("blastdbcmd");
//		getOneFasta.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry");
		argNames.add("-outfmt");
		// argNames.add("-target_only"); // target_only PREVENTS OTHER ACCESSIONS, BUT CAUSES ISSUES WITH pir AND prf DBs
		getOneFasta.setArgNames(argNames);
		processTypes.put(getOneFastaProcess, getOneFasta);

		// --- GET MANY FASTAs FILE ---
//		ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider getManyFastas = new ProcessTypeProvider();
		getManyFastas.setName(getManyFastasProcess);
		getManyFastas.setCommand("blastdbcmd");
//		getManyFastas.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry_batch");
		argNames.add("-outfmt");
		// argNames.add("-target_only"); // target_only PREVENTS OTHER ACCESSIONS, BUT CAUSES ISSUES WITH pir AND prf DBs
		getManyFastas.setArgNames(argNames);
		processTypes.put(getManyFastasProcess, getManyFastas);

		// --- SAVE FASTA FILE ---
//		ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider saveFasta = new ProcessTypeProvider();
		saveFasta.setName(saveFastaProcess);
		saveFasta.setCommand("blastdbcmd");
//		saveFasta.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry");
		argNames.add("-outfmt");
		argNames.add("-out");
		// argNames.add("-target_only"); // target_only PREVENTS OTHER ACCESSIONS, BUT CAUSES ISSUES WITH pir AND prf DBs
		saveFasta.setArgNames(argNames);
		processTypes.put(saveFastaProcess, saveFasta);

		// --- SAVE TEMP FASTA FILE ---
//		ncbiProvider = ncbiKeeper.getPreferredNCBIProvider();
		ProcessTypeProvider saveTempFasta = new ProcessTypeProvider();
		saveTempFasta.setName(saveTempFastaProcess);
		saveTempFasta.setCommand("blastdbcmd");
//		saveTempFasta.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-db");
		argNames.add("-dbtype");
		argNames.add("-entry_batch");
		argNames.add("-out");
		// argNames.add("-target_only"); // target_only PREVENTS OTHER ACCESSIONS, BUT CAUSES ISSUES WITH pir AND prf DBs
		saveTempFasta.setArgNames(argNames);
		processTypes.put(saveTempFastaProcess, saveTempFasta);

		// --- FIND MAX BITSCORE ---
		ProcessTypeProvider self = new ProcessTypeProvider();
		self.setName(selfAlignProcess);
		self.setCommand("blastp");
//		self.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-subject");
		argNames.add("-max_target_seqs");
		argNames.add("-outfmt");
		self.setArgNames(argNames);
		processTypes.put(selfAlignProcess, self);

		// --- FIND HIT PROTEINS ---
		ProcessTypeProvider blastp = new ProcessTypeProvider();
		blastp.setName(findHitProteinsProcess);
		blastp.setCommand("blastp");
//		blastp.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		argNames.add("-word_size");
		argNames.add("-num_threads");
		argNames.add("-evalue");
		argNames.add("-max_target_seqs");
		argNames.add("-outfmt");
		blastp.setArgNames(argNames);
		processTypes.put(findHitProteinsProcess, blastp);

		// --- FIND HIT PROTEINS TO FILE ---
		ProcessTypeProvider blastpToFile = new ProcessTypeProvider();
		blastpToFile.setName(findHitProteinsToFileProcess);
		blastpToFile.setCommand("blastp");
//		blastpToFile.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		argNames.add("-word_size");
		argNames.add("-num_threads");
		argNames.add("-evalue");
		argNames.add("-max_target_seqs");
		argNames.add("-outfmt");
		argNames.add("-out");
		blastp.setArgNames(argNames);
		processTypes.put(findHitProteinsToFileProcess, blastpToFile);

		// --- rbhBLAST RUN RESULTS ---
		ProcessTypeProvider rbhBlast = new ProcessTypeProvider();
		rbhBlast.setName(findRbhBlastResultsProcess);
		rbhBlast.setCommand("blastp");
//		rbhBlast.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		// argNames.add("-gilist"); // gilists no longer used
		argNames.add("-seqidlist");
		argNames.add("-word_size");
		argNames.add("-evalue");
		argNames.add("-outfmt");
		rbhBlast.setArgNames(argNames);
		processTypes.put(findRbhBlastResultsProcess, rbhBlast);

		// --- rbhBLAST RUN RESULTS TO FILE ---
		ProcessTypeProvider rbhBlastToFile = new ProcessTypeProvider();
		rbhBlastToFile.setName(findRbhBlastResultsToFileProcess);
		rbhBlastToFile.setCommand("blastp");
//		rbhBlastToFile.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		argNames.add("-seqidlist");
		argNames.add("-word_size");
		argNames.add("-evalue");
		argNames.add("-outfmt");
		argNames.add("-out");
		rbhBlastToFile.setArgNames(argNames);
		processTypes.put(findRbhBlastResultsToFileProcess, rbhBlastToFile);

		// --- rpsBLAST RUN RESULTS ---
		ProcessTypeProvider rpsBlast = new ProcessTypeProvider();
		rpsBlast.setName(findRpsBlastResultsProcess);
		rpsBlast.setCommand("rpsblast");
//		rpsBlast.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		argNames.add("-evalue");
		argNames.add("-outfmt");
		rpsBlast.setArgNames(argNames);
		processTypes.put(findRpsBlastResultsProcess, rpsBlast);

		// --- rpsBLAST RUN RESULTS TO FILE ---
		ProcessTypeProvider rpsBlastToFile = new ProcessTypeProvider();
		rpsBlastToFile.setName(findRpsBlastResultsToFileProcess);
		rpsBlastToFile.setCommand("rpsblast");
//		rpsBlastToFile.setPath(ncbiProvider.getBlastExecPath());
		argNames = new ArrayList<String>();
		argNames.add("-query");
		argNames.add("-db");
		argNames.add("-evalue");
		argNames.add("-outfmt");
		argNames.add("-out");
		rpsBlastToFile.setArgNames(argNames);
		processTypes.put(findRpsBlastResultsToFileProcess, rpsBlastToFile);

		// --- COBALT RUN ---
		ProcessTypeProvider cobalt = new ProcessTypeProvider();
		cobalt.setName(runCobalt);
		cobalt.setCommand("cobalt");
//		cobalt.setPath(ncbiProvider.getCobaltExecPath()); // general solution using ncbiProvider
		argNames = new ArrayList<String>();
		argNames.add("-i");
		argNames.add("-rpsdb");
		argNames.add("-alph");
		argNames.add("-blast_evalue");
		argNames.add("-clusters");
		cobalt.setArgNames(argNames);
		processTypes.put(runCobalt, cobalt);
	}
}
