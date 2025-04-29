package gov.epa.seqapass.backend.domain;

public class LevelTwoCompletedRow {

	private int runId;
	private String accession;
	private int level2RunId;
	private String key;
	private String displayText;

	public LevelTwoCompletedRow() {
	}

	public LevelTwoCompletedRow(int runId, String accession, int level2RunId, String key, String displayText) {
		this.runId = runId;
		this.accession = accession;
		this.level2RunId = level2RunId;
		this.key = key;
		this.displayText = displayText;
	}

	@Override
	public String toString() {
		return "LevelTwoStatusRow [runId=" + runId + ", accession=" + accession + ", level2RunId=" + level2RunId + ", key=" + key
				+ ", displayText=" + displayText + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + runId;
		result = prime * result + ((accession == null) ? 0 : accession.hashCode());
		result = prime * result + (int) level2RunId;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((displayText == null) ? 0 : displayText.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LevelTwoCompletedRow other = (LevelTwoCompletedRow) obj;
		if (runId != other.runId)
			return false;
		if (accession == null) {
			if (other.accession != null)
				return false;
		} else if (!accession.equals(other.accession))
			return false;
		if (level2RunId != other.level2RunId)
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (displayText == null) {
			if (other.displayText != null)
				return false;
		} else if (!displayText.equals(other.displayText))
			return false;
		return true;
	}

	public int getRunId() {
		return runId;
	}

	public void setRunId(int runId) {
		this.runId = runId;
	}

	public String getAccession() {
		return accession;
	}

	public void setAccession(String accession) {
		this.accession = accession;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public int getLevel2RunId() {
		return level2RunId;
	}

	public void setLevel2RunId(int level2RunId) {
		this.level2RunId = level2RunId;
	}

	public String getDisplayText() {
		return displayText;
	}

	public void setDisplayText(String displayText) {
		this.displayText = displayText;
	}
}
