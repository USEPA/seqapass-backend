package gov.epa.seqapass.backend.domain;

import java.util.Date;

public class NCBIProvider implements Comparable<NCBIProvider> {

	// As of 2019-05-08:

	private int    id;                   //id                    | int(11)
	private int    updateVersion;        //update_version        | int(11)
	private String seqVersion;           // seqapass_version     |  varchar(63)
	private Date   installDate;          //install_date          | timestamp
	private String host;                 // backend_host         |  varchar(255)
	private Date   cobaltExecDate;       //cobalt_exec_date      | date
	private String cobaltExecVersion;    //cobalt_exec_version   | varchar(63)
	private String cobaltExecPath;       //cobalt_exec_path      | (generated)
	private Date   cobaltDataDate;       //cobalt_data_date      | date
	private String cobaltDataVersion;    //cobalt_data_version   | varchar(63)
	private String cobaltDataPath;       //cobalt_data_path      | (generated)
	private Date   cddDataFtpDate;       //cdd_data_ftp_date     | date
	private Date   cddDataDate;          //cdd_data_date         | date
	private Date   blastExecFtpDate;     //blast_exec_ftp_date   | date
	private Date   blastExecDate;        //blast_exec_date       | date
	private String blastExecVersion;     //blast_exec_version    | varchar(255)
	private String blastExecPath;        //blast_exec_path       | (generated)
	private String webdataPath;          //webdata_path          | (hard coded)
	private String webresultsPath;       //webresultsPath        | (hard coded)
	private Date   taxonomyProteinDate;  //taxonomy_protein_date | date
	private int    proteinRecordCount;   //protein_record_count  | int(11)
	private int    taxidRecordCount;     //taxid_record_count    | int(11)
	private String javaVersion;          //java_version          | varchar(255)
	private String primefacesVersion;    //primefaces_version    | varchar(255)
	private String tomcatVersion;        //tomcat_version        | varchar(255)
	private String dbServerVersion;      // db_server_version    |  varchar(255)
	private String rVersion;             //r_version             | varchar(255)
	private String notes;                //notes                 | text       
	
	public NCBIProvider() {
	}

	@Override
	public int compareTo(NCBIProvider o) {
		Integer intId = id;
		Integer compId = o.id;
		return intId.compareTo(compId);
	}

	// Getters and Setters
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getUpdateVersion() {
		return updateVersion;
	}

	public void setUpdateVersion(int updateVersion) {
		this.updateVersion = updateVersion;
	}

	public String getSeqVersion() {
		return seqVersion;
	}

	public void setSeqVersion(String seqVersion) {
		this.seqVersion = seqVersion;
	}

	public Date getInstallDate() {
		return installDate;
	}

	public void setInstallDate(Date installDate) {
		this.installDate = installDate;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Date getCobaltExecDate() {
		return cobaltExecDate;
	}

	public void setCobaltExecDate(Date cobaltExecDate) {
		this.cobaltExecDate = cobaltExecDate;
	}

	public String getCobaltExecVersion() {
		return cobaltExecVersion;
	}

	public void setCobaltExecVersion(String cobaltExecVersion) {
		this.cobaltExecVersion = cobaltExecVersion;
	}

	public String getCobaltExecPath() {
		return cobaltExecPath;
	}

	public void setCobaltExecPath(String cobaltExecPath) {
		this.cobaltExecPath = cobaltExecPath;
	}

	public Date getCobaltDataDate() {
		return cobaltDataDate;
	}

	public void setCobaltDataDate(Date cobaltDataDate) {
		this.cobaltDataDate = cobaltDataDate;
	}

	public String getCobaltDataVersion() {
		return cobaltDataVersion;
	}

	public void setCobaltDataVersion(String cobaltDataVersion) {
		this.cobaltDataVersion = cobaltDataVersion;
	}

	public String getCobaltDataPath() {
		return cobaltDataPath;
	}

	public void setCobaltDataPath(String cobaltDataPath) {
		this.cobaltDataPath = cobaltDataPath;
	}

	public Date getCddDataFtpDate() {
		return cddDataFtpDate;
	}

	public void setCddDataFtpDate(Date cddDataFtpDate) {
		this.cddDataFtpDate = cddDataFtpDate;
	}

	public Date getCddDataDate() {
		return cddDataDate;
	}

	public void setCddDataDate(Date cddDataDate) {
		this.cddDataDate = cddDataDate;
	}

	public Date getBlastExecFtpDate() {
		return blastExecFtpDate;
	}

	public void setBlastExecFtpDate(Date blastExecFtpDate) {
		this.blastExecFtpDate = blastExecFtpDate;
	}

	public Date getBlastExecDate() {
		return blastExecDate;
	}

	public void setBlastExecDate(Date blastExecDate) {
		this.blastExecDate = blastExecDate;
	}

	public String getBlastExecVersion() {
		return blastExecVersion;
	}

	public void setBlastExecVersion(String blastExecVersion) {
		this.blastExecVersion = blastExecVersion;
	}

	public String getBlastExecPath() {
		return blastExecPath;
	}

	public void setBlastExecPath(String blastExecPath) {
		this.blastExecPath = blastExecPath;
	}

	public String getWebdataPath() {
		return webdataPath;
	}

	public void setWebdataPath(String webdataPath) {
		this.webdataPath = webdataPath;
	}

	public String getWebresultsPath() {
		return webresultsPath;
	}

	public void setWebresultsPath(String webresultsPath) {
		this.webresultsPath = webresultsPath;
	}

	public Date getTaxonomyProteinDate() {
		return taxonomyProteinDate;
	}

	public void setTaxonomyProteinDate(Date taxonomyProteinDate) {
		this.taxonomyProteinDate = taxonomyProteinDate;
	}

	public int getProteinRecordCount() {
		return proteinRecordCount;
	}

	public void setProteinRecordCount(int proteinRecordCount) {
		this.proteinRecordCount = proteinRecordCount;
	}

	public int getTaxidRecordCount() {
		return taxidRecordCount;
	}

	public void setTaxidRecordCount(int taxidRecordCount) {
		this.taxidRecordCount = taxidRecordCount;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public void setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	public String getPrimefacesVersion() {
		return primefacesVersion;
	}

	public void setPrimefacesVersion(String primefacesVersion) {
		this.primefacesVersion = primefacesVersion;
	}

	public String getTomcatVersion() {
		return tomcatVersion;
	}

	public void setTomcatVersion(String tomcatVersion) {
		this.tomcatVersion = tomcatVersion;
	}

	public String getDbServerVersion() {
		return dbServerVersion;
	}

	public void setDbServerVersion(String dbServerVersion) {
		this.dbServerVersion = dbServerVersion;
	}

	public String getrVersion() {
		return rVersion;
	}

	public void setrVersion(String rVersion) {
		this.rVersion = rVersion;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	// --------- Additional getters -----------

	public String getNCBIVersionIdDir() {
		return getWebresultsPath() + "/ncbi_version_id_" + getId() + "." + getUpdateVersion();
	}

	public String getPathNrData() {
		return getWebdataPath() + "/blast-nr-db/nr";
	}

	public String getCddDataPath() {
		return getWebdataPath() + "/blast-cdd-db/Cdd";
	}

	public String getPathFasta() {
		return getNCBIVersionIdDir() + "/fasta_files";
	}

	public String getPathAcclist() {
		return getNCBIVersionIdDir() + "/acclists";
	}

	public String getPathTempFasta() {
		return getNCBIVersionIdDir() + "/temp_fasta_files";
	}
	
	public String getPathPublicReports() {
		return getWebresultsPath() + "/public_reports";
	}
	
	public String getPathSeqapassReports() {
		return getWebresultsPath() + "/seqapass_reports";
	}
}
