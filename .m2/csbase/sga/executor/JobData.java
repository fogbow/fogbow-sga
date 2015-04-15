/**
 * $Id$
 */
package csbase.sga.executor;

import java.io.Serializable;

/**
 * Os dados que representam o job no ambiente de execução. Objetos que
 * implementação essa interface são armazenados na camada de persistência e são
 * usados para fazer a recuperação de jobs.
 *
 * Objetos que implementem essa interface devem conter os dados suficientes para
 * que o {@link JobExecutor excutor} possa recuperar o job correspondente.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobData extends Serializable {

}
