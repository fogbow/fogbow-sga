/**
 * $Id$
 */
package csbase.sga.executor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Monitor de diretório que monitora a criação de arquivos com os sufixos .time
 * .done. O primeiro á o arquivo de tempo de um job e o segundo o arquivo que
 * indica que o job finalizou.
 *
 * @author Tecgraf/PUC-Rio
 */
public class DirectoryMonitor implements Runnable {
  /** Sufixo dos arquivos de tempo */
  static String TIME_SUFIX = ".time";
  /** Sufixo dos arquivos indicadores de termino de job */
  static String DONE_SUFIX = ".done";
  /** Executor de jobs */
  private DefaultJobExecutor executor;
  /** Diretório monitorado */
  private static Path dir;
  /** Referencia ao WatchService */
  private WatchService watcher;
  /** Chave de monitoramento do diretório */
  private WatchKey watchKey;

  /**
   * Construtor.
   *
   * @param path o diretório monitorado
   * @param executor executor de jobs
   */
  protected DirectoryMonitor(String path, DefaultJobExecutor executor) {
    try {
      this.executor = executor;
      DirectoryMonitor.dir = Paths.get(path);
      this.watcher = FileSystems.getDefault().newWatchService();
      this.watchKey =
        DirectoryMonitor.dir.register(this.watcher,
          StandardWatchEventKinds.ENTRY_CREATE);
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Obtém o arquivo de tempos de um job.
   *
   * @param jobId o identificador do job
   *
   * @return o arquivo de tempos
   */
  protected static String getTimeFile(String jobId) {
    return dir.toString() + File.separator + jobId
      + DirectoryMonitor.TIME_SUFIX;
  }

  /**
   * Obtém o arquivo indicador de finalização de um job.
   *
   * @param jobId o identificador do job
   *
   * @return o arquivo indicador de finalização
   */
  protected static String getDoneFile(String jobId) {
    return dir.toString() + File.separator + jobId
      + DirectoryMonitor.DONE_SUFIX;
  }

  /**
   * Limpa os recusrsos usados pelo job.
   *
   * @param jobId o identificador do job
   */
  protected static void cleanUp(String jobId) {
    //TODO Limpar os recursos usados
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run() {
    while (true) {
      try {
        WatchKey key = this.watcher.take();

        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind<?> kind = event.kind();
          if (kind == StandardWatchEventKinds.OVERFLOW) {
            continue;
          }

          if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path path = ev.context();
            String[] filename = path.toFile().getName().split("\\.");

            if (path.toFile().getName().endsWith(DONE_SUFIX)) {
              this.executor.notifyJobFinished(filename[0]);
            }
          }
        }

        boolean valid = key.reset();
        if (!valid) {
          break;
        }
      }
      catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
}
