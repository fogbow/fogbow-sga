package csbase.sga;

import java.util.LinkedList;
import java.util.List;

import sgaidl.ActionNotSupportedException;
import sgaidl.InvalidActionException;
import sgaidl.InvalidTransitionException;
import sgaidl.JobControlAction;
import sgaidl.Pair;
import sgaidl.RunningCommandInfo;
import csbase.server.plugin.service.sgaservice.SGADaemonCommand;
import csbase.sga.executor.JobData;
import csbase.sga.executor.JobExecutor;
import csbase.sga.executor.JobInfo;

/**
 * Commando que encapsula as informa��es espec�ficas do ambiente de execu��o do
 * SGA.
 *
 * @author Tecgraf/PUC-Rio
 */
public class SGALocalCommand extends SGADaemonCommand {
  /** UID */
  private static final long serialVersionUID = -5874016392438461849L;
  /** Dados do job */
  private JobData jobData;
  /** Executor de jobs */
  private JobExecutor executor;

  /**
   * Construtor.
   *
   * @param jobData dados do job
   * @param executor executor de jobs
   */
  protected SGALocalCommand(JobData jobData, JobExecutor executor) {
    this.jobData = jobData;
    this.executor = executor;
  }

  /**
   * Obt�m os dados do job.
   *
   * @return os dados do job
   */
  protected JobData getJobData() {
    return jobData;
  }

  /**
   * Obt�m o executor de jobs.
   *
   * @return o executor de jobs
   */
  protected JobExecutor getExecutor() {
    return executor;
  }

  /**
   * Altera o estado de um comando ou de um job filho do comando.
   *
   * @param action a��o a ser executada sobre o comando
   * @param child identificador do job filho do comando ou nulo se a a��o deve
   *        ser realizada no pr�prio comando
   *
   * @throws InvalidActionException a��o inv�lida
   * @throws ActionNotSupportedException a��o n�o suportada
   * @throws InvalidTransitionException ??? TODO Unir a
   *         InvalidTransitionException com a InvalidActionException
   */
  @Override
  public void control(JobControlAction action, String child)
    throws InvalidActionException, ActionNotSupportedException,
    InvalidTransitionException {
    this.executor.controlJob(jobData, child, action);
  }

  /**
   * Fornece as informa��es de monitora��o de um comando.
   *
   * @return as informa��es de monitora��o de todos os processos.
   */
  @Override
  public RunningCommandInfo getRunningCommandInfo() {
    JobInfo info = this.executor.getJobInfo(jobData);

    if (info == null) {
      //TODO Notificar o comando como perdido

      return new RunningCommandInfo(new Pair[0][], new Pair[0]);
    }

    List<Pair[]> processData = new LinkedList<Pair[]>();

    List<Pair> mainProcessDic = new LinkedList<Pair>();
    for (String key : info.jobParam.keySet()) {
      mainProcessDic.add(new Pair(key, info.jobParam.get(key)));
    }
    processData.add(mainProcessDic.toArray(new Pair[0]));

    for (JobInfo pInfo : info.children) {
      List<Pair> pDic = new LinkedList<Pair>();
      for (String key : pInfo.jobParam.keySet()) {
        pDic.add(new Pair(key, pInfo.jobParam.get(key)));
      }
      processData.add(pDic.toArray(new Pair[0]));
    }

    return new RunningCommandInfo(processData.toArray(new Pair[0][]),
      new Pair[0]);
  }
}