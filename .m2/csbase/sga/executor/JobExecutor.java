/**
 * $Id$
 */
package csbase.sga.executor;

import java.util.Map;

import sgaidl.ActionNotSupportedException;
import sgaidl.InvalidActionException;
import sgaidl.JobControlAction;

/**
 * O Executor � o respons�vel por iniciar a execu��o de jobs em um ambiente
 * espec�fico e por monitorar o estado do job.
 *
 * Atrav�s de um {@link JobObserver observer} o executor notifica o
 * {@link JobObserver#onCommandCompleted t�rmino} e a
 * {@link JobObserver#onCommandLost perda} do comando.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobExecutor {

  /**
   * Executa um job no ambiente de execu��o. Um job representa um comando de
   * execu��o, que � um programa ou script junto com os seus argumento, no
   * ambiente de execu��o.
   *
   * @param jobCommand comando do job que deve ser executado.
   * @param extraParams parametros extras usados pelo ambiente de execu��o para
   *        executar o job
   * @param observer observador do job
   *
   * @return os dados do job
   */
  public JobData executeJob(String jobCommand, Map<String, String> extraParams,
    JobObserver observer);

  /**
   * Recupera um job no ambiente de execu��o.
   *
   * Quando o servidor � reiniciado � necess�rio obt�r o estado dos jobs que
   * estavam em execu��o antes da parada no ambiente de execu��o.
   *
   * @param data dados do job
   * @param observer observador do job
   *
   * @return true se o job foi recuperado e o observador cadastrado e false se
   *         n�o foi poss�vel recuperar o job no ambiente de execu��o
   */
  public boolean retrieveJob(JobData data, JobObserver observer);

  /**
   * Exerce uma a��o sobre um job ou sobre um processo filho do job.
   *
   * @param data os dados do job
   * @param child o processo filho do job
   * @param action a a��o a ser exercida
   *
   * @throws InvalidActionException se a a��o � inv�lida
   * @throws ActionNotSupportedException se a ���o n�o � suportada pelo ambiente
   *         de execu��o
   */
  public void controlJob(JobData data, String child, JobControlAction action)
    throws InvalidActionException, ActionNotSupportedException;

  /**
   * Obt�m as informa��es sobre a execu��o do job no ambiente de execu��o.
   *
   * @param data os dados do job
   *
   * @return as informa��es sobre o job
   */
  public JobInfo getJobInfo(JobData data);
}
