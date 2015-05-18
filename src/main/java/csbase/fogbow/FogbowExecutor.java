package csbase.fogbow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final Executor EXECUTOR = Executors.newCachedThreadPool();

  private static final String REMOTE_COMMAND_LINE =
    "function exec_in_bg { %s; echo $? > %s; }; exec_in_bg &";

  private static final String PROP_INPUT_FILES = "fogbow_input_files_path";
  private static final String PROP_OUTPUT_FILES = "fogbow_output_files_path";
  private static final String PROP_FEDERATION_TOKEN = "fogbow_federation_token";
  private static final String PROP_LOCAL_TOKEN = "fogbow_local_token";

  private static final String PROP_SSH_PRIVATE_KEY_PATH =
    "fogbow_ssh_private_key_path";
  private static final String PROP_SSH_PUBLIC_KEY_PATH =
    "fogbow_ssh_public_key_path";
  private static final String PROP_SSH_USER_NAME = "fogbow_ssh_user_name";
  private static final String PROP_MANAGER_HOST = "fogbow_manager_host";
  private static final String PROP_MANAGER_PORT = "fogbow_manager_port";
  private static final String PROP_IMAGE_NAME = "fogbow_image";
  private static final String PROP_REQUIREMENTS = "fogbow_requirements";
  private static final String PROP_JOB_STATUS_UPDATE_INTERVAL =
    "fogbow_job_status_update_interval";
  private static final String PROP_MAX_RETRIES = "fogbow_max_retries";
  private static final String PROP_RETRY_INTERVAL = "fogbow_retry_interval";
  private static final String PROP_KEEP_INSTANCE = "fogbow_keep_instance";

  private final Logger LOGGER = Logger.getLogger(SGAFogbow.class.getName());

  private static long job_status_update_interval = 10000;
  private static int retry_interval = 30000;
  private static int max_retries = 30;
  private static String remote_sandbox = "/tmp/sandbox/";
  private static boolean keep_instance = false;

  private Properties pluginProperties;

  private Map<FogbowJobData, JobObserver> jobs;

  private Timer checkJobsTimer;

  public FogbowExecutor(final Properties pluginProperties) {
    this.pluginProperties = pluginProperties;

    if (pluginProperties.containsKey(PROP_JOB_STATUS_UPDATE_INTERVAL)) {
      try {
        job_status_update_interval =
          Long.parseLong(pluginProperties
            .getProperty(PROP_JOB_STATUS_UPDATE_INTERVAL)) * 1000;
      }
      catch (Exception e) {
        //Do nothing
      }
    }

    if (pluginProperties.containsKey(PROP_RETRY_INTERVAL)) {
      try {
        retry_interval =
          Integer.parseInt(pluginProperties.getProperty(PROP_RETRY_INTERVAL)) * 1000;
      }
      catch (Exception e) {
        //Do nothing
      }
    }

    if (pluginProperties.containsKey(PROP_MAX_RETRIES)) {
      try {
        max_retries =
          Integer.parseInt(pluginProperties.getProperty(PROP_MAX_RETRIES));
      }
      catch (Exception e) {
        //Do nothing
      }
    }
    if (pluginProperties.containsKey(sgaidl.SGA_SANDBOX_ROOT_DIR.value)) {
      remote_sandbox =
        pluginProperties.getProperty(sgaidl.SGA_SANDBOX_ROOT_DIR.value);
    }
    if (pluginProperties.containsKey(PROP_KEEP_INSTANCE)) {
      try {
        keep_instance =
          Boolean
          .parseBoolean(pluginProperties.getProperty(PROP_KEEP_INSTANCE));
      }
      catch (Exception e) {
        //Do nothing
      }
    }

    jobs = new Hashtable<>();

    checkJobsTimer = new Timer();
    checkJobsTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        String requestId = "";
        synchronized (jobs) {
          Iterator<FogbowJobData> ite = jobs.keySet().iterator();
          while (ite.hasNext()) {
            try {

              FogbowJobData data = ite.next();
              JobObserver observer = jobs.get(data);
              requestId = data.getRequestId();
              if (checkJobFinished(data)) {
                terminate(data);
                ite.remove();
                JobInfo jobInfo = new JobInfo();
                jobInfo.jobParam.put(COMMAND_STATE.value, ProcessState.FINISHED
                  .toString());
                observer.onJobCompleted(jobInfo);
              }
            }
            catch (Exception e) {
              LOGGER.log(Level.FINE,
                "Erro verifying finish state of the job with RequestId = "
                  + requestId, e);
              //TODO Colocar um limite na quantidade de tentativas.
              //            terminate(data);
              //            observer.onJobLost();
            }
          }
        }
      }
    }, job_status_update_interval, job_status_update_interval);
  }

  @Override
  public JobData executeJob(String jobCommand, Map<String, String> extraParams,
    JobObserver observer) {
    String fed_token = getFederationToken(extraParams);
    if (fed_token == null) {
      return null;
    }
    FogbowClient fogbowClient =
      new FogbowClient(pluginProperties.getProperty(PROP_MANAGER_HOST), Integer
        .parseInt(pluginProperties.getProperty(PROP_MANAGER_PORT)), fed_token);

    String requestId = null;
    try {
      requestId =
        fogbowClient.createRequest(pluginProperties
          .getProperty(PROP_REQUIREMENTS), pluginProperties
          .getProperty(PROP_IMAGE_NAME), pluginProperties
          .getProperty(PROP_SSH_PUBLIC_KEY_PATH), extraParams
          .get(PROP_LOCAL_TOKEN));
      LOGGER.log(Level.FINE, "Request {0} created", new Object[] { requestId });
    }
    catch (Exception e) {
      LOGGER.log(Level.WARNING,
        "Couldn't submit request to the Fogbow manager", e);
      return null;
    }

    int retries = 0;
    Request request = null;
    boolean shouldRetry = true;
    while (shouldRetry
      && (request = getRequestInfo(fogbowClient, requestId)) == null) {
      sleep();
      shouldRetry = retries++ < max_retries;
    }
    Instance instance = null;
    while (shouldRetry
      && (instance = getInstanceInfo(fogbowClient, request)) == null) {
      sleep();
      shouldRetry = retries++ < max_retries;
    }

    while (shouldRetry && !checkSSHConnectity(instance)) {
      sleep();
      shouldRetry = retries++ < max_retries;
    }

    if (!shouldRetry) {
      LOGGER.log(Level.WARNING,
        "Max retries exeeded while submitting a request.");
      terminate(request, extraParams);
      return null;
    }

    stageAndExecInBg(jobCommand, extraParams, request, instance, observer);

    FogbowJobData data =
      new FogbowJobData(request.getId(), request.getInstanceId(), extraParams);

    jobs.put(data, observer);

    return data;
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

  private void stageAndExec(String jobCommand, Map<String, String> extraParams,
    Request request, Instance instance, JobObserver observer) {
    String privKey = pluginProperties.getProperty(PROP_SSH_PRIVATE_KEY_PATH);
    String sshUserName = pluginProperties.getProperty(PROP_SSH_USER_NAME);
    try {
      LOGGER
      .log(
        Level.FINE,
        "Transfering files to remote VM. [RequestID = {0}; InstanceID = {1} ({2}:{3})]",
        new Object[] { request.getId(), request.getInstanceId(),
          instance.getSshHost(), Integer.toString(instance.getSshPort()) });

      LOGGER.log(Level.FINER, "Input files: {0}", new Object[] { extraParams
        .get(PROP_INPUT_FILES) });

      stageFiles(new JSONObject(extraParams.get(PROP_INPUT_FILES)), instance,
        privKey, sshUserName, true);

    }
    catch (SGADataTransferException e) {
      LOGGER.log(Level.WARNING, "Couldn't transfer files to remote VM", e);
      terminate(request, extraParams);
      observer.onJobLost();
      return;
    }

    try {
      LOGGER
        .log(
          Level.FINE,
          "Executing command {0} on remote VM. [RequestID = {1}; InstanceID = {2} ({3}:{4})]",
          new Object[] { jobCommand, request.getId(), request.getInstanceId(),
              instance.getSshHost(), Integer.toString(instance.getSshPort()) });
      exec(jobCommand, request, instance, privKey, sshUserName);
    }
    catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failure during remote execution", e);
      terminate(request, extraParams);
      observer.onJobLost();
    }
  }

  private void exec(String jobCommand, Request request, Instance instance,
    String privKey, String sshUserName) throws IOException {
    SSHClientWrapper wrapper = new SSHClientWrapper();

    int retries = 0;
    boolean shouldRetry = true;
    while (shouldRetry) {
      try {
        wrapper.connect(instance.getSshHost(), instance.getSshPort(),
          sshUserName, privKey);
        wrapper.doSshExecution(String.format(REMOTE_COMMAND_LINE, jobCommand,
          getDoneFile(request.getId())));
        shouldRetry = false;
        LOGGER.log(Level.FINE, "Done submitting command");
      }
      catch (Exception e) {
        shouldRetry = retries++ < max_retries;
        LOGGER.log(Level.FINE, "Submitting command attempt #" + retries
          + " failed. Sleeping ...", e);
        sleep();
      }
    }

    wrapper.disconnect();
  }

  private String getDoneFile(String requestId) {
    return remote_sandbox + requestId + ".done";
  }

  private void stageFiles(JSONObject inputsJson, Instance instance,
    String privKey, String sshUserName, boolean in)
    throws SGADataTransferException {

    SSHSGADataTransfer dataTransfer =
      new SSHSGADataTransfer(instance.getSshHost(), instance.getSshPort(),
        sshUserName, privKey);

    int retries = 0;
    for (String localFilePath : inputsJson.keySet()) {
      boolean shouldRetry = true;
      while (shouldRetry) {
        String remoteFilePath =
          remote_sandbox + inputsJson.optString(localFilePath);
        try {
          if (in) {
            dataTransfer.copyTo(split(localFilePath), split(remoteFilePath));
          }
          else {
            dataTransfer.copyFrom(split(remoteFilePath), split(localFilePath));
          }
          shouldRetry = false;
          LOGGER.log(Level.FINE, "Done staging " + (in ? "in" : "out")
            + " file: " + localFilePath);
        }
        catch (SGADataTransferException e) {
          shouldRetry = retries++ < max_retries;
          LOGGER.log(Level.FINE, "Staging " + (in ? "in" : "out")
            + " files attempt #" + retries + " failed. Sleeping ...", e);
          sleep();
        }
      }
    }
  }

  private static String[] split(String filePath) {
    return filePath.split("/");
  }

  private void terminate(Request request, Map<String, String> extraParams) {
    if (this.keep_instance) {
      return;
    }
    if (request == null) {
      return;
    }

    String fed_token = getFederationToken(extraParams);
    if (fed_token == null) {
      return;
    }

    FogbowClient fogbowClient =
      new FogbowClient(pluginProperties.getProperty(PROP_MANAGER_HOST), Integer
        .parseInt(pluginProperties.getProperty(PROP_MANAGER_PORT)), fed_token);
    try {
      fogbowClient.deleteRequest(request.getId());
    }
    catch (Exception e) {
      LOGGER.log(Level.WARNING, "Couldn't delete request", e);
      // Best effort here
    }
    if (request.getInstanceId() != null) {
      try {
        fogbowClient.deleteInstance(request.getInstanceId());
      }
      catch (Exception e) {
        LOGGER.log(Level.WARNING, "Couldn't delete instance", e);
        // Best effort here
      }
    }
  }

  protected boolean checkSSHConnectity(Instance instance) {
    SSHClientWrapper wrapper = new SSHClientWrapper();
    try {
      wrapper.connect(instance.getSshHost(), instance.getSshPort());
    }
    catch (Exception e) {
      return false;
    }
    finally {
      try {
        wrapper.disconnect();
      }
      catch (IOException e) {
        LOGGER.log(Level.FINE, "Could not connect to {0}:{1}.", new Object[] {
          instance.getSshHost(), Integer.toString(instance.getSshPort()) });
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
    }
    catch (Exception e) {
      LOGGER.log(Level.WARNING,
        "Couldn't retrieve Instance info from the Fogbow manager", e);
    }
    return null;
  }

  private void sleep() {
    try {
      Thread.sleep(retry_interval);
    }
    catch (InterruptedException e) {
    }
  }

  private String getFederationToken(Map<String, String> extraParams) {
    String fed_token = extraParams.get(PROP_FEDERATION_TOKEN);
    if (fed_token == null) {
      Path tokenPath =
        Paths.get(pluginProperties.getProperty("fogbow_default_token"));
      try {
        fed_token = new String(Files.readAllBytes(tokenPath));
      }
      catch (IOException e) {
        LOGGER
          .log(Level.SEVERE,
            "Could not read the default federation token with path: "
              + tokenPath, e);
        return null;
      }
    }

    return fed_token;
  }

  private Request getRequestInfo(FogbowClient fogbowClient, String requestId) {
    Request request = null;
    try {
      request = fogbowClient.getRequest(requestId);
      if (request.getInstanceId() != null) {
        return request;
      }
    }
    catch (Exception e) {
      LOGGER.log(Level.WARNING,
        "Couldn't retrieve Request info from the Fogbow manager", e);
    }
    return null;
  }

  private boolean checkJobFinished(FogbowJobData data) throws Exception {
    if (data.getRequestId() == null) {
      return false;
    }
    if (data.getInstanceId() == null) {
      return false;
    }
    Map<String, String> extraParams = data.getExtraParams();

    String fed_token = getFederationToken(extraParams);
    if (fed_token == null) {
      return false;
    }

    FogbowClient fogbowClient =
      new FogbowClient(pluginProperties.getProperty(PROP_MANAGER_HOST), Integer
        .parseInt(pluginProperties.getProperty(PROP_MANAGER_PORT)), fed_token);
    Instance instance = null;
    try {
      instance = fogbowClient.getInstance(data.getInstanceId());
    }
    catch (Exception e) {
      throw e;
    }

    if (instance == null) {
      throw new Exception("Instance " + data.getInstanceId() + " is null.");
    }
    if (instance.getSshHost() == null) {
      throw new Exception("Host information of instance "
        + data.getInstanceId() + " is null.");
    }
    if (instance.getSshPort() == null) {
      throw new Exception("Port information of instance "
        + data.getInstanceId() + " is null.");
    }

    String sshUserName = pluginProperties.getProperty(PROP_SSH_USER_NAME);
    String privKey = pluginProperties.getProperty(PROP_SSH_PRIVATE_KEY_PATH);

    SSHSGADataTransfer dataTransfer =
      new SSHSGADataTransfer(instance.getSshHost(), instance.getSshPort(),
        sshUserName, privKey);
    boolean doneFileExists = false;
    try {
      doneFileExists =
        dataTransfer
          .checkExistence(getDoneFile(data.getRequestId()).split("/"));
    }
    catch (SGADataTransferException e) {
      throw e;
    }

    if (doneFileExists) {
      try {

        LOGGER.log(Level.FINE, "Transfering files from remote VM. [{0}:{1}]",
          new Object[] { instance.getSshHost(),
              Integer.toString(instance.getSshPort()) });

        LOGGER.log(Level.FINER, "Output files: {0}", new Object[] { extraParams
          .get(PROP_INPUT_FILES) });

        stageFiles(new JSONObject(extraParams.get(PROP_OUTPUT_FILES)),
          instance, privKey, sshUserName, false);
      }
      catch (SGADataTransferException e) {
        LOGGER.log(Level.WARNING,
          "Couldn't transfer files back from remote VM", e);
        throw e;
      }
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public boolean recoveryJob(JobData data, JobObserver observer) {
    try {
      if (checkJobFinished((FogbowJobData) data)) {
        terminate((FogbowJobData) data);
        JobInfo jobInfo = new JobInfo();
        jobInfo.jobParam.put(COMMAND_STATE.value, ProcessState.FINISHED
          .toString());
        observer.onJobCompleted(jobInfo);
        return true;
      }
      else {
        jobs.put((FogbowJobData) data, observer);
        return true;
      }
    }
    catch (Exception e) {
      terminate((FogbowJobData) data);
      return false;
    }
  }

  private void terminate(FogbowJobData fogbowJobData) {
    terminate(new Request(fogbowJobData.getRequestId(), fogbowJobData
      .getInstanceId()), fogbowJobData.getExtraParams());
  }

  @Override
  public void controlJob(JobData data, String child, JobControlAction action)
    throws InvalidActionException, ActionNotSupportedException {

    LOGGER.log(Level.FINE,
      "Received action {0} on job. [RequestId={1}; InstanceId={2}]",
      new Object[] { ((FogbowJobData) data).getRequestId(),
          ((FogbowJobData) data).getInstanceId() });

    if (JobControlAction.TERMINATE.equals(action)) {
      FogbowJobData fogbowJobData = (FogbowJobData) data;
      JobObserver observer = jobs.get(fogbowJobData);
      terminate(fogbowJobData);
      //      observer.onJobCompleted(jobInfo);
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
