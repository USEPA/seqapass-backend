package gov.epa.seqapass.backend.domain;

import java.io.Serializable;
import java.util.List;

//This is the original cutoff data object used for version 1.0 of SeqAPASS
public class CutoffData_v1 implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3031773747034477920L;
	private List<Double> xData;
	private List<Double> yData;
	private List<Double> cutoffValues;
	private List<Integer> maxCritLoc;
	private List<Integer> minCritLoc;
	private List<Integer> infLoc;

	public CutoffData_v1() {
	}
	
	public CutoffData_v1(List<Double> xData, List<Double> yData, List<Double> cutoffValues, List<Integer> maxCritLoc, List<Integer> minCritLoc, List<Integer> infLoc) {
		this.xData = xData;
		this.yData = yData;
		this.cutoffValues = cutoffValues;
		this.maxCritLoc = maxCritLoc;
		this.minCritLoc = minCritLoc;
		this.infLoc = infLoc;
	}

	@Override
	public String toString() {
		return "CutoffData [xData=" + xData.toString() + ", yData=" + yData.toString() + ", cutoffValues=" + cutoffValues.toString() + ", maxCritLoc=" + maxCritLoc + ", minCritLoc=" + minCritLoc + "infLoc=" + infLoc + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + xData.toString().hashCode();
		result = prime * result + yData.toString().hashCode();
		result = prime * result + cutoffValues.toString().hashCode();
		result = prime * result + maxCritLoc.toString().hashCode();
		result = prime * result + minCritLoc.toString().hashCode();
		result = prime * result + infLoc.toString().hashCode();
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
		CutoffData_v1 other = (CutoffData_v1) obj;
		if (xData == null) {
			if (other.xData != null)
				return false;
		} else if (!xData.equals(other.xData))
			return false;
		if (yData == null) {
			if (other.yData != null)
				return false;
		} else if (!yData.equals(other.yData))
			return false;
		if (cutoffValues == null) {
			if (other.cutoffValues != null)
				return false;
		} else if (!cutoffValues.equals(other.cutoffValues))
			return false;
		if (maxCritLoc == null) {
			if (other.maxCritLoc != null)
				return false;
		} else if (!maxCritLoc.equals(other.maxCritLoc))
			return false;
		if (minCritLoc == null) {
			if (other.minCritLoc != null)
				return false;
		} else if (!minCritLoc.equals(other.minCritLoc))
			return false;
		if (infLoc == null) {
			if (other.infLoc != null)
				return false;
		} else if (!infLoc.equals(other.infLoc))
			return false;
		return true;
	}

	public List<Double> getxData() {
		return xData;
	}

	public void setxData(List<Double> xData) {
		this.xData = xData;
	}

	public List<Double> getyData() {
		return yData;
	}

	public void setyData(List<Double> yData) {
		this.yData = yData;
	}

	public List<Double> getCutoffValues() {
		return cutoffValues;
	}

	public void setCutoffValues(List<Double> cutoffValues) {
		this.cutoffValues = cutoffValues;
	}

	public List<Integer> getMaxCritLoc() {
		return maxCritLoc;
	}

	public void setMaxCritLoc(List<Integer> maxCritLoc) {
		this.maxCritLoc = maxCritLoc;
	}

	public List<Integer> getMinCritLoc() {
		return minCritLoc;
	}

	public void setMinCritLoc(List<Integer> minCritLoc) {
		this.minCritLoc = minCritLoc;
	}

	public List<Integer> getInfLoc() {
		return infLoc;
	}

	public void setInfLoc(List<Integer> infLoc) {
		this.infLoc = infLoc;
	}

}
