package gov.epa.seqapass.backend.dao;

import gov.epa.seqapass.common.LevelFourAccessionRow;
import gov.epa.seqapass.common.LevelFourRequestableRow;
import gov.epa.seqapass.common.LevelOneRequestable;
import gov.epa.seqapass.common.LevelThreeRequestableRow;

import java.util.List;

/**
 * @author csimmo02
 *
 */
public interface AnalysisService {
	
//	public List<String> debugLevelOneRun();
//	/**
//	 * This method is the interface point of access that the front end code uses to request a level one analysis run. It is designed to
//	 * quickly determine a status for each requested accession id entry and return, for each entry, a string including the requested entry
//	 * and its status. Possible statuses include
//	 * <ul>
//	 * <li>'submitted' for jobs which are new and not yet started</li>
//	 * <li>a string indicating the running status for jobs which have not yet completed</li>
//	 * <li>a string indicating that the blastdbcmd command could not find the accession code</li>
//	 * <li>or a string indicating that the job has already completed (if it had been run previously)</li>
//	 * </ul>
//	 * 
//	 * @param levelOneRequestable
//	 *            (LevelOneRequestable): an object simply including the list of accession id strings and an integer user id
//	 * @return a List&lt;String&gt; with each String containing the requested accession id and its status
//	 */
	public List<String> requestLevelOneRun(LevelOneRequestable levelOneRequest);

	/**
	 * This method is the interface point of access that the front end code uses to request a level two analysis run. It is designed to
	 * quickly determine a status for the requested conserved domain and starting position for the given accession id (previously run
	 * through level one analysis). Possible statuses include
	 * <ul>
	 * <li>a string indicating that the job has been queued or started</li>
	 * <li>a string indicating the job has already been requested or could not run</li>
	 * </ul>
	 * 
	 * @param accessionRunId
	 *            (int): The id for the relevant accession run
	 * @param key
	 *            (String): A unique string indicating the conserved domain
	 * @param startPosition
	 *            (int): An integer starting amino acid position in the query protein for the start of the matching conserved domain
	 *            sequence
	 * @param userId
	 *            (int): The integer id of the user making the request
	 * 
	 * @return a String with the status
	 */
	public String requestLevelTwoRun(int accessionRunId, String key, int startPosition, int userId);

	/**
	 * This method is the interface point of access that the front end code uses to request a level three analysis run. It is designed to
	 * quickly determine a status for the requested job name, template, and set of accession ids for the given accession id (previously run
	 * through level one analysis). Possible statuses include
	 * <ul>
	 * <li>a string indicating that the job has been queued or started</li>
	 * <li>a string indicating the job name has already been used for this accession id</li>
	 * </ul>
	 * 
	 * @param levelThreeRequestableRow
	 *            (LevelThreeRequestableRow): an object containing the accession run number, the user id, the requested job name, the
	 *            template (fasta or accession id), and a list of accession id targets
	 * @return a String indicating the status
	 */
	public String requestLevelThreeRun(LevelThreeRequestableRow levelThreeRequestableRow);

	/**
	 * This method simply inquires as to how many LevelOne, LevelTwo, and LevelThree jobs are associated with the accession id (string) for
	 * each item in the list. It is only accessible to specific user ids. Possible responses include
	 * <ul>
	 * <li>CAA70212.1: 1 , 1 , 8</li>
	 * <li>NP_001011625.1: 1 , 1, 0</li>
	 * <li>INVALID_ID: no results found</li>
	 * </ul>
	 * 
	 * @param levelOneRequestable
	 *            (LevelOneRequestable): an object simply including the list of accession id strings and an integer user id
	 * @return a List&lt;String&gt; with each String containing the requested accession id, a colon, and the three counts of levels 1, 2,
	 *         and 3 runs
	 */
	public List<String> levelOneJobsCount(LevelOneRequestable levelOneRequest);

	/**
	 * This method deletes all LevelOne, LevelTwo, and LevelThree jobs associated with each LevelOne accession id (string) for each item in
	 * the list. It is only accessible to specific user ids. Possible responses include
	 * <ul>
	 * <li>CAA70212.1: removed 1 , 1 , 8</li>
	 * <li>NP_001011625.1: removed 1 , 1 , 0</li>
	 * <li>INVALID_ID: no results found</li>
	 * </ul>
	 * 
	 * @param levelOneRequestable
	 *            (LevelOneRequestable): an object simply including the list of accession id strings and an integer user id
	 * @return a List&lt;String&gt; with each String containing the requested accession id, a colon, and the action taken for the three
	 *         counts of levels 1, 2, and 3 runs
	 */
	public List<String> levelOneJobsDelete(LevelOneRequestable levelOneRequest);

	public void killAllThreads();

	//public Boolean rerunLevelTwoCutoff(int lev2RunId);

	//public Boolean rerunLevelTwoCutoffForRange(int start, int end);
	
	public String setPoolSize(String name, int size);
	
	
	/**
	 * This method is the interface point of access that the front end code uses to create a level four analysis run. It is designed to
	 * quickly determine a status for the requested job name, template, and set of accession ids for the given accession id (previously run
	 * through level one analysis). Possible statuses include
	 * <ul>
	 * <li>a string indicating that the job has been created</li>
	 * <li>a string indicating that the job has been queued or started (Not implemented yet)</li>
	 * <li>a string indicating failure along with a possible reason</li>
	 * </ul>
	 * 
	 * @param levelFourRequestableRow
	 *            (LevelFourRequestableRow): an object containing the accession run number, the user id, the requested job name, the
	 *            template (PDB accession)
	 * @return a String indicating the status
	 */
	public String createLevelFourRun(LevelFourRequestableRow levelFourRequestableRow);

    
	/**
	 *This method is the interface point of access that the front end code uses to create a level four FASTA run. It is designed to
	 * quickly determine a status for the requested FASTA generation.  Possible statuses include
	 * <ul>
	 * <li>a string indicating that the FASTAs have been requested</li>
	 * <li>a string indicating that the job has failed along with a possible reason</li>
	 * <li>a string indicating that no accession data was provided</li>
	 * </ul>
	 * 
	 * @param levelFourRequestableRow
	 *            (LevelFourRequestableRow): an object containing the accession run number, the user id, the requested job name, and
	 *            the requested accessions for which FASTAs will be created.
	 * @return a String indicating the status
	 */
	public String requestLevelFourFASTAs(LevelFourRequestableRow accessions);
	
	public String requestLevelFourItasser(LevelFourRequestableRow request);

	public String requestLevelFourTMAlign(List<LevelFourRequestableRow> requests);
	
	/**
	 *This method requests the submission of toxcast level one runs
	 *The accessions selected to run is based on the # of toxcast runs in previous data version
	 * 
	 * @return a String indicating the status
	 */
	public String requestToxcastLevelOne();
	
	/**
	 *This method requests the submission of toxcast level one runs
	 *The accessions selected to run is based on the # of toxcast runs in supplied data version
	 * 
	 * @param dataVersion
	 *            an integer for the requested data version of previous toxcast runs that are to be repeated
	 * @param batchSize
	 *            an integer for the requested number of toxCast level 2 runs to be started
	 * @return a String indicating the status
	 */
	public String requestToxcastLevelTwo(int dataVersion, int batchSize);
	
	/**
	 *This method requests the submission of toxcast level one runs
	 *The accessions selected to run is based on the # of toxcast runs in supplied data version
	 * 
	 * @param dataVersion
	 *            an integer for the requested data version of previous toxcast runs that are to be repeated
	 * @return a String indicating the status
	 */
	public String requestToxcastLevelOne(int dataVersion);
	
	/**
	 *This method requests the submission of toxcast level two runs
	 *The accessions selected to run is based on the # of toxcast runs in previous data version
	 * 
	 * @return a String indicating the status
	 */
	public String requestToxcastLevelTwo();
	
	/**
	 *This method requests the submission of toxcast level two runs
	 *The accessions selected to run is based on the # of toxcast runs in supplied data version
	 * 
	 * @param dataVersion
	 *            an integer for the requested data version of previous toxcast runs that are to be repeated
	 * @return a String indicating the status
	 */
	public String requestToxcastLevelTwo(int dataVersion);
	
	public String findUniProtId(String inputAcc);
	
	public String updateUniprot(int accRunId);
	
	public String updateLevel4(int accRunId);

	
	
//	public String RBHHPC();

}
