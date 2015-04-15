/**
 * $Id$
 */
package csbase.sga.executor;

import java.io.Serializable;

/**
 * Os dados que representam o job no ambiente de execu��o. Objetos que
 * implementa��o essa interface s�o armazenados na camada de persist�ncia e s�o
 * usados para fazer a recupera��o de jobs.
 *
 * Objetos que implementem essa interface devem conter os dados suficientes para
 * que o {@link JobExecutor excutor} possa recuperar o job correspondente.
 *
 * @author Tecgraf/PUC-Rio
 */
public interface JobData extends Serializable {

}
