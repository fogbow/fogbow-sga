/**
 * $Id$
 */
package csbase.sga.executor;

/**
 * Implementa��o padr�o da interface JobData. Esta implementa��o encapsula o
 * identificador do job e o identificador do processo no SO.
 *
 * @author Tecgraf/PUC-Rio
 */
public class DefaultJobData implements JobData {
  /** UID */
  private static final long serialVersionUID = -9142976023522982409L;
  /** Identificador do job */
  private String jobId;
  /** Identificador do processo no SO */
  private String pid;

  /**
   * Construtor.
   *
   * @param jobId identificador do job
   */
  protected DefaultJobData(String jobId) {
    this.jobId = jobId;
  }

  /**
   * Obt�m o identificador do job.
   *
   * @return o identificador do job
   */
  protected String getJobId() {
    return this.jobId;
  }

  /**
   * Obt�m o identificador do processo.
   *
   * @return o identificador do processo
   */
  protected String getPID() {
    return this.pid;
  }

  /**
   * Retorna a representa��o textual dos dados, que � o JobId.
   */
  @Override
  public String toString() {
    return jobId;
  }
}