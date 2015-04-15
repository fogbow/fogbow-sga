/**
 * $Id$
 */
package csbase.sga.executor;

import java.util.Map;

import sgaidl.ActionNotSupportedException;
import sgaidl.InvalidActionException;
import sgaidl.JobControlAction;

/**
 * O Executor é o responsável por iniciar a execução de jobs em um ambiente
 * específico e por monitorar o estado do job.
 *
 * Através de um {@link JobObserver observer} o executor notifica o
 * {@link JobObserver#onCommandCompleted término} e a
 * {@link JobObserver#onCommandLost perda} do comando.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobExecutor {

  /**
   * Executa um job no ambiente de execução. Um job representa um comando de
   * execução, que é um programa ou script junto com os seus argumento, no
   * ambiente de execução.
   *
   * @param jobCommand comando do job que deve ser executado.
   * @param extraParams parametros extras usados pelo ambiente de execução para
   *        executar o job
   * @param observer observador do job
   *
   * @return os dados do job
   */
  public JobData executeJob(String jobCommand, Map<String, String> extraParams,
    JobObserver observer);

  /**
   * Recupera um job no ambiente de execução.
   *
   * Quando o servidor é reiniciado é necessário obtér o estado dos jobs que
   * estavam em execução antes da parada no ambiente de execução.
   *
   * @param data dados do job
   * @param observer observador do job
   *
   * @return true se o job foi recuperado e o observador cadastrado e false se
   *         não foi possível recuperar o job no ambiente de execução
   */
  public boolean retrieveJob(JobData data, JobObserver observer);

  /**
   * Exerce uma ação sobre um job ou sobre um processo filho do job.
   *
   * @param data os dados do job
   * @param child o processo filho do job
   * @param action a ação a ser exercida
   *
   * @throws InvalidActionException se a ação é inválida
   * @throws ActionNotSupportedException se a áção não é suportada pelo ambiente
   *         de execução
   */
  public void controlJob(JobData data, String child, JobControlAction action)
    throws InvalidActionException, ActionNotSupportedException;

  /**
   * Obtém as informações sobre a execução do job no ambiente de execução.
   *
   * @param data os dados do job
   *
   * @return as informações sobre o job
   */
  public JobInfo getJobInfo(JobData data);
}
