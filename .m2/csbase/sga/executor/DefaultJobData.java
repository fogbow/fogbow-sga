/**
 * $Id$
 */
package csbase.sga.executor;

/**
 * Implementação padrão da interface JobData. Esta implementação encapsula o
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
   * Obtém o identificador do job.
   *
   * @return o identificador do job
   */
  protected String getJobId() {
    return this.jobId;
  }

  /**
   * Obtém o identificador do processo.
   *
   * @return o identificador do processo
   */
  protected String getPID() {
    return this.pid;
  }

  /**
   * Retorna a representação textual dos dados, que é o JobId.
   */
  @Override
  public String toString() {
    return jobId;
  }
}