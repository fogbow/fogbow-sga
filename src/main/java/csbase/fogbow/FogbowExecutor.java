package csbase.fogbow;

import java.util.Map;

import sgaidl.ActionNotSupportedException;
import sgaidl.InvalidActionException;
import sgaidl.JobControlAction;
import csbase.sga.executor.JobData;
import csbase.sga.executor.JobExecutor;
import csbase.sga.executor.JobInfo;
import csbase.sga.executor.JobObserver;

public class FogbowExecutor implements JobExecutor {

	private static final String PROP_INPUT_FILES = "fogbow_input_files";
	private static final String PROP_VOMS_PROXY = "fogbow_voms_proxy";
	private static final String PROP_SSH_PRIVATE_KEY_PATH = "fogbow_ssh_private_key_path";
	private static final String PROP_SSH_PUBLIC_KEY_PATH = "fogbow_ssh_public_key_path";
	private static final String PROP_MANAGER_HOST = "fogbow_manager_host";
	private static final String PROP_MANAGER_PORT = "fogbow_manager_port";
	
	@Override
	public JobData executeJob(String jobCommand,
			Map<String, String> extraParams, JobObserver observer) {
		SSHSGADataTransfer dataTransfer = new SSHSGADataTransfer(host, port, pubKey);
		return new FogbowJobData(requestId);
	}

	@Override
	public boolean retrieveJob(JobData data, JobObserver observer) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void controlJob(JobData data, String child, JobControlAction action)
			throws InvalidActionException, ActionNotSupportedException {
		// TODO Auto-generated method stub

	}

	@Override
	public JobInfo getJobInfo(JobData data) {
		// TODO Auto-generated method stub
		return null;
	}

}
