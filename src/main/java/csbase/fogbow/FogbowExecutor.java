package csbase.fogbow;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.fogbowcloud.cli.FogbowClient;
import org.fogbowcloud.cli.Instance;
import org.fogbowcloud.cli.Request;
import org.json.JSONObject;

import sgaidl.ActionNotSupportedException;
import sgaidl.COMMAND_STATE;
import sgaidl.InvalidActionException;
import sgaidl.JobControlAction;
import sgaidl.ProcessState;
import csbase.server.plugin.service.sgaservice.SGADataTransferException;
import csbase.sga.executor.JobData;
import csbase.sga.executor.JobExecutor;
import csbase.sga.executor.JobInfo;
import csbase.sga.executor.JobObserver;

public class FogbowExecutor implements JobExecutor {

	private static final Logger LOGGER = Logger.getLogger(FogbowExecutor.class.getName());
	private static final Executor EXECUTOR = Executors.newCachedThreadPool();
	
	private static final String PROP_INPUT_FILES = "fogbow_input_files_path";
	private static final String PROP_OUTPUT_FILES = "fogbow_output_files_path";
	private static final String PROP_VOMS_PROXY = "fogbow_voms_proxy";
	private static final String PROP_SSH_PRIVATE_KEY_PATH = "fogbow_ssh_private_key_path";
	private static final String PROP_SSH_PUBLIC_KEY_PATH = "fogbow_ssh_public_key_path";
	private static final String PROP_SSH_USER_NAME = "fogbow_ssh_user_name";
	private static final String PROP_MANAGER_HOST = "fogbow_manager_host";
	private static final String PROP_MANAGER_PORT = "fogbow_manager_port";
	private static final String PROP_IMAGE_NAME = "fogbow_image";
	private static final String PROP_REQUIREMENTS = "fogbow_requirements";
	
	private static final int RETRY_INTERVAL = 30000;
	private static final String REMOTE_SANDBOX = "/tmp/sandbox/";
	
	@Override
	public JobData executeJob(String jobCommand,
			Map<String, String> extraParams, JobObserver observer) {
		FogbowClient fogbowClient = new FogbowClient(extraParams.get(PROP_MANAGER_HOST), 
				Integer.parseInt(extraParams.get(PROP_MANAGER_PORT)), 
				extraParams.get(PROP_VOMS_PROXY));
		
		String requestId = null;
		try {
			requestId = fogbowClient.createRequest(extraParams.get(PROP_REQUIREMENTS), 
					extraParams.get(PROP_IMAGE_NAME), 
					extraParams.get(PROP_SSH_PUBLIC_KEY_PATH), null);
		} catch (Exception e) {
			observer.onJobLost();
			LOGGER.log(Level.WARNING, "Couldn't submit request to the Fogbow manager", e);
			return null;
		}
		
		Request request = null;
		while ((request = getRequestInfo(fogbowClient, requestId)) == null) {
			sleep();
		}
		Instance instance = null;
		while ((instance = getInstanceInfo(fogbowClient, request)) == null) {
			sleep();
		}
		while (!checkSSHConnectity(instance)) {
			sleep();
		}
		
		stageAndExecInBg(jobCommand, extraParams, request, instance, observer);
		
		return new FogbowJobData(request.getId(), 
				request.getInstanceId(), extraParams);
	}

	private void stageAndExecInBg(final String jobCommand,
			final Map<String, String> extraParams, final Request request, 
			final Instance instance, final JobObserver observer) {
		EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				stageAndExec(jobCommand, extraParams, request, instance, observer);
			}
		});
	}
	
	private void stageAndExec(String jobCommand,
			Map<String, String> extraParams, Request request, 
			Instance instance, JobObserver observer) {
		
		String privKey = null;
		try {
			privKey = IOUtils.toString(
					new FileInputStream(extraParams.get(PROP_SSH_PRIVATE_KEY_PATH)));
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Couldn't read SSH private key", e);
			terminate(request, extraParams);
			observer.onJobLost();
			return;
		}
		String sshUserName = extraParams.get(PROP_SSH_USER_NAME);
		try {
			stageFiles(new JSONObject(extraParams.get(PROP_INPUT_FILES)), 
					request, instance, privKey, sshUserName, true);
		} catch (SGADataTransferException e) {
			LOGGER.log(Level.WARNING, "Couldn't transfer files to remote VM", e);
			terminate(request, extraParams);
			observer.onJobLost();
			return;
		}
		
		try {
			exec(jobCommand, instance, privKey, sshUserName);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failure during remote execution", e);
			terminate(request, extraParams);
			observer.onJobLost();
			return;
		}
		
		try {
			stageFiles(new JSONObject(extraParams.get(PROP_OUTPUT_FILES)), 
					request, instance, privKey, sshUserName, false);
		} catch (SGADataTransferException e) {
			LOGGER.log(Level.WARNING, "Couldn't transfer files back from remote VM", e);
			terminate(request, extraParams);
			observer.onJobLost();
			return;
		}
		
		terminate(request, extraParams);
		observer.onJobCompleted(new JobInfo());
	}

	private void exec(String jobCommand, Instance instance, String privKey,
			String sshUserName) throws IOException {
		SSHClientWrapper wrapper = new SSHClientWrapper();
		wrapper.connect(instance.getSshHost(), instance.getSshPort(), 
				sshUserName, privKey);
		wrapper.doSshExecution(jobCommand);
		wrapper.disconnect();
	}

	private void stageFiles(JSONObject inputsJson, Request request,
			Instance instance, String privKey, String sshUserName, boolean in) throws SGADataTransferException {
		SSHSGADataTransfer dataTransfer = new SSHSGADataTransfer(
				instance.getSshHost(), instance.getSshPort(), 
				sshUserName, privKey);
		String[] localFiles = new String[inputsJson.length()];
		String[] remoteFiles = new String[inputsJson.length()];
		
		int i = 0;
		for (String localFilePath : inputsJson.keySet()) {
			localFiles[i] = localFilePath;
			remoteFiles[i] = REMOTE_SANDBOX + inputsJson.optString(localFilePath);
			i++;
		}
		if (in) {
			dataTransfer.copyTo(localFiles, remoteFiles);
		} else {
			dataTransfer.copyFrom(remoteFiles, localFiles);
		}
	}
	
	private void terminate(Request request, Map<String, String> extraParams) {
		FogbowClient fogbowClient = new FogbowClient(extraParams.get(PROP_MANAGER_HOST), 
				Integer.parseInt(extraParams.get(PROP_MANAGER_PORT)), 
				extraParams.get(PROP_VOMS_PROXY));
		try {
			fogbowClient.deleteRequest(request.getId());
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Couldn't delete request", e);
			// Best effort here
		}
		if (request.getInstanceId() != null) {
			try {
				fogbowClient.deleteInstance(request.getInstanceId());
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Couldn't delete instance", e);
				// Best effort here
			}
		}
	}

	protected boolean checkSSHConnectity(Instance instance) {
		SSHClientWrapper wrapper = new SSHClientWrapper();
    	try {
    		wrapper.connect(instance.getSshHost(), instance.getSshPort());
		} catch (Exception e) {
			return false;
		} finally {
			try {
				wrapper.disconnect();
			} catch (IOException e) {
			}
		}
		return true;
	}
	
	private Instance getInstanceInfo(FogbowClient fogbowClient, Request request) {
		Instance instance = null;
		try {
			instance = fogbowClient.getInstance(request.getInstanceId());
			if (instance.getSshHost() != null) {
				return instance;
			}
			sleep();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Couldn't retrieve Instance info from the Fogbow manager", e);
		}
		return null;
	}

	private void sleep() {
		try {
			Thread.sleep(RETRY_INTERVAL);
		} catch (InterruptedException e) {
		}
	}

	private Request getRequestInfo(FogbowClient fogbowClient, String requestId) {
		Request request = null;
		try {
			request = fogbowClient.getRequest(requestId);
			if (request.getInstanceId() != null) {
				return request;
			}
			sleep();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Couldn't retrieve Request info from the Fogbow manager", e);
		}
		return null;
	}

	@Override
	public boolean retrieveJob(JobData data, JobObserver observer) {
		return false;
	}

	@Override
	public void controlJob(JobData data, String child, JobControlAction action)
			throws InvalidActionException, ActionNotSupportedException {
		if (JobControlAction.TERMINATE.equals(action)) {
			FogbowJobData fogbowJobData = (FogbowJobData) data;
			terminate(new Request(fogbowJobData.getRequestId(), 
					fogbowJobData.getInstanceId()), fogbowJobData.getExtraParams());
			return;
		}
		
		throw new ActionNotSupportedException();
	}

	@Override
	public JobInfo getJobInfo(JobData data) {
		JobInfo jobInfo = new JobInfo();
		jobInfo.jobParam.put(COMMAND_STATE.value, ProcessState.RUNNING.toString());
		return jobInfo;
	}

}
