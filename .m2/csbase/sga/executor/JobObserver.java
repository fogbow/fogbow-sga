/**
 * $Id$
 */
package csbase.sga.executor;


/**
 * Observador de jobs. O {@link JobExecutor executor} notifica o t�rmino da
 * execu��o de jobs atrav�s destes observadores.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobObserver {

  /**
   * Callback de notifica��o de t�rmino de execu��o de jobs.
   *
   * @param jobInfo as informa��es sobre a execu��o do job
   */
  public void onJobCompleted(JobInfo jobInfo);

  /**
   * Callback de notifica��o de perda de job. Jobs perdidos s�o aqueles que o
   * ambiente de execu��o n�o consegue mais obter o estado.
   */
  public void onJobLost();
}
