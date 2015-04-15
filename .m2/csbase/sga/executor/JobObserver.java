/**
 * $Id$
 */
package csbase.sga.executor;


/**
 * Observador de jobs. O {@link JobExecutor executor} notifica o término da
 * execução de jobs através destes observadores.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobObserver {

  /**
   * Callback de notificação de término de execução de jobs.
   *
   * @param jobInfo as informações sobre a execução do job
   */
  public void onJobCompleted(JobInfo jobInfo);

  /**
   * Callback de notificação de perda de job. Jobs perdidos são aqueles que o
   * ambiente de execução não consegue mais obter o estado.
   */
  public void onJobLost();
}
