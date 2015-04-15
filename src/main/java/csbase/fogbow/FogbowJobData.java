package csbase.fogbow;

import csbase.sga.executor.JobData;

public class FogbowJobData implements JobData {

	private String requestId;

	public FogbowJobData(String requestId) {
		this.requestId = requestId;
	}
	
	public String getRequestId() {
		return requestId;
	}
	
}
