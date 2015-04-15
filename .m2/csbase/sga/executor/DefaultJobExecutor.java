/**
 * $Id$
 */
package csbase.sga.executor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgaidl.ActionNotSupportedException;
import sgaidl.COMMAND_CPU_TIME_SEC;
import sgaidl.COMMAND_STATE;
import sgaidl.COMMAND_USER_TIME_SEC;
import sgaidl.COMMAND_WALL_TIME_SEC;
import sgaidl.JobControlAction;
import sgaidl.ProcessState;
import csbase.sga.SGALocal;

/**
 * Executor padr�o do SGA. Neste executor os jobs s�o executados via shell (ksh)
 * na mesma m�quina onde est� o servidor CSBase.
 *
 * @author Tecgraf/PUC-Rio
 */
public class DefaultJobExecutor implements JobExecutor {
  //TODO Obter o path dos comandos ksh e time. Ou apenas verificar sua exist�ncia

  /**
   * Modelo usado para iniciar jobs: o primeiro parametro � o arquivo de tempos,
   * o segundo o arquivo indicador de t�rmino e o terceiro o comando do job.
   */
  private String commandTemplate =
    "/usr/bin/time -p 2> {0} /bin/ksh -c ''{1}; echo $PPID>{2}''";
  /** Comando do shell que ser� usado para executar o job */
  private String shellCommand = "/bin/ksh";
  /** Argumento do comando do shell */
  private String shellArgs = "-c";

  /** Mapa relacionando identificadores de jobs com seus processos */
  private Map<String, Process> processes;
  /** Mapa relacionando identificadores de jobs com suas informa��es */
  private Map<String, JobInfo> infos;
  /** Map relacionando identificadores de jobs com seus observadores */
  private Map<String, JobObserver> observers;

  //TODO Definir um diret�rio de sandbox via propriedade
  /** Diret�rio onde os arquivos de controle s�o criados */
  String sandBoxPath = "/tmp/sga_sandbox";
  /**
   * Thread para monitor o diret�rio onde os comandos escrevem os arquivos de
   * controle
   */
  private Thread eventMonitorThread;

  /** Logger */
  private Logger logger = Logger.getLogger(SGALocal.class.getName());

  //TODO Criar mecanismo para definir vari�veis de ambiente

  /**
   * Construtor.
   */
  public DefaultJobExecutor() {
    processes = new Hashtable<>();
    infos = new Hashtable<>();
    observers = new Hashtable<>();

    File sandboxdir = new File(sandBoxPath);
    if (!sandboxdir.exists()) {
      sandboxdir.mkdir();
    }

    eventMonitorThread = new Thread(new DirectoryMonitor(sandBoxPath, this));
    eventMonitorThread.setName(this.getClass().getSimpleName() + "::"
      + "FileSystemMonitorThread");

    eventMonitorThread.start();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized JobData executeJob(String jobCommand,
    Map<String, String> extraParams, JobObserver observer) {
    String id = getId();

    logger.info("Executando o job com id " + id);

    try {
      String[] cmd = buildJobCommand(id, jobCommand);

      ProcessBuilder builder =
        new ProcessBuilder(cmd).directory(new File(sandBoxPath));
      //TODO Solu��o de contorno para adicionar itens no PATH
      Map<String, String> env = builder.environment();
      env.put("PATH", env.get("PATH") + ":/usr/local/bin");

      Process p = builder.start();

      JobInfo info = new JobInfo();
      //TODO somete o estado do job por enquanto
      info.jobParam.put(COMMAND_STATE.value, ProcessState.RUNNING.toString());

      infos.put(id, info);
      processes.put(id, p);
      observers.put(id, observer);
    }
    catch (IOException ex) {
    }

    //TODO Pegar o pid do processo e colocar no JobData
    return new DefaultJobData(id);
  }

  /**
   * Constr�i o comando completo que ser� usado para execu��o do job. O comando
   * retornado incorpora o comando do job a mecanismos de controle.
   *
   * @param jobId identificador do job
   * @param jobCommand o comando do job
   *
   * @return o comando do job com os mecanismos de controle
   */
  private String[] buildJobCommand(String jobId, String jobCommand) {
    String cmd =
      MessageFormat.format(commandTemplate, new Object[] {
          DirectoryMonitor.getTimeFile(jobId), jobCommand,
          DirectoryMonitor.getDoneFile(jobId) });

    return new String[] { shellCommand, shellArgs, cmd };
  }

  /**
   * Obt�m um identificador �nico.
   *
   * @return o identificador
   */
  private static String getId() {
    return UUID.randomUUID().toString();
  }

  /**
   * {@inheritDoc}
   *
   * Apenas a a��o {@link sgaidl.JobControlAction#TERMINATE TERMINATE} �
   * suportada.
   */
  @Override
  public synchronized void controlJob(JobData data, String child,
    JobControlAction action) throws ActionNotSupportedException {
    String jobId = data.toString();

    logger.info("Solicita��o de aplica��o de a��o no job com id " + jobId);

    if (!processes.containsKey(jobId)) {
      return;
    }

    if (action.value() == JobControlAction._TERMINATE) {
      Process p = processes.get(jobId);
      p.destroy();

      notifyJobFinished(jobId);
      logger.info("T�rmino do job com id " + jobId);
    }
    else {
      throw new ActionNotSupportedException();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized JobInfo getJobInfo(JobData data) {
    //TODO Obter as infos e atualizar no map
    //    COMMAND_STATE.value;
    //    COMMAND_CPU_PERC.value;
    //    COMMAND_MEMORY_RAM_SIZE_MB.value;
    //    COMMAND_MEMORY_SWAP_SIZE_MB.value;
    //    COMMAND_WALL_TIME_SEC.value;
    //    COMMAND_VIRTUAL_MEMORY_SIZE_MB.value;
    //    COMMAND_BYTES_IN_KB.value;
    //    COMMAND_BYTES_OUT_KB.value;
    //    COMMAND_DISK_BYTES_WRITE_KB.value;
    //    COMMAND_EXEC_HOST.value;

    //    JobInfo pInfo = new JobInfo();
    //    pInfo.jobParam = new HashMap<String, String>();
    //    pInfo.jobParam.put(COMMAND_STATE.value, ProcessState.RUNNING.toString());
    //    pInfo.jobParam.put(COMMAND_CPU_PERC.value, "0.15");
    //    pInfo.jobParam.put(COMMAND_MEMORY_RAM_SIZE_MB.value, "4");
    //    pInfo.jobParam.put(COMMAND_MEMORY_SWAP_SIZE_MB.value, "16");
    //    pInfo.jobParam.put(COMMAND_WALL_TIME_SEC.value, "20");
    //    pInfo.jobParam.put(COMMAND_VIRTUAL_MEMORY_SIZE_MB.value, "8");
    //    pInfo.jobParam.put(COMMAND_BYTES_IN_KB.value, "2048");
    //    pInfo.jobParam.put(COMMAND_BYTES_OUT_KB.value, "1024");
    //    pInfo.jobParam.put(COMMAND_DISK_BYTES_WRITE_KB.value, "512");
    //    pInfo.jobParam.put(COMMAND_EXEC_HOST.value, "256");
    //    info.childrenParam = new LinkedList<JobInfo>();
    //
    //    info.childrenParam.add(pInfo);
    //    return jobId.toString();

    String jobId = data.toString();
    return infos.get(jobId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized boolean retrieveJob(JobData data, JobObserver observer) {
    String jobId = data.toString();

    logger.info("Recupera��o do job com id " + jobId);

    String doneFile = DirectoryMonitor.getDoneFile(jobId);
    if (Files.exists(Paths.get(doneFile))) {
      notifyJobFinished(jobId);
    }
    else {
      /*
       * Os jobs que n�o foram encontrados s�o considerados como perdidos pelo
       * Manager, pois n�o estar�o na lista de jobs recuperados. Assim n�o �
       * necess�rio enviar notifica��o de job perdido.
       */
      //TODO Verificar se o job ainda est� em execu��o (via ps) e se sim cadastr�-lo.
      return false;
    }

    return true;
  }

  /**
   * Notifica o t�rmino de um job para o observador deste job.
   *
   * @param jobId o identificador do job
   */
  public synchronized void notifyJobFinished(String jobId) {
    logger.info("Recebida a notifica��o de t�rmino do job com id " + jobId);
    if (infos.containsKey(jobId)) {
      //TODO Atualizar as infos de t�rmino do job e limpar os recursos do job
      String timeFile = DirectoryMonitor.getTimeFile(jobId);

      Map<String, String> timeMap = getTimes(timeFile);
      JobInfo jobInfo = infos.get(jobId);
      jobInfo.jobParam.putAll(timeMap);

      JobObserver observer = observers.get(jobId);
      observer.onJobCompleted(jobInfo);
    }
  }

  /**
   * Obt�m os tempos de execu��o do job.
   *
   * @param timeFile nome do arquivo de tempos
   *
   * @return um mapa com os tempos
   */
  private Map<String, String> getTimes(String timeFile) {
    Pattern realPattern = Pattern.compile("^real.*?(\\d+)\\.(\\d+)");
    Pattern userPattern = Pattern.compile("^user.*?(\\d+)\\.(\\d+)");
    Pattern sysPattern = Pattern.compile("^sys.*?(\\d+)\\.(\\d+)");

    List<String> lines = null;
    try {
      lines = Files.readAllLines(Paths.get(timeFile), Charset.defaultCharset());
    }
    catch (IOException e) {
      logger.severe("Erro ao ler o arquivo de tempos: " + timeFile);
    }

    String wallTime = null, userTim = null, cpuTime = null;
    for (String line : lines) {
      String temp;
      temp = matchTime(line, realPattern);
      if (temp != null) {
        wallTime = temp;
      }
      temp = matchTime(line, userPattern);
      if (temp != null) {
        userTim = temp;
      }
      temp = matchTime(line, sysPattern);
      if (temp != null) {
        cpuTime = temp;
      }
    }

    return createTimeMap(wallTime, userTim, cpuTime);
  }

  /**
   * Faz o casamento de um padr�o com grupo em uma string.
   *
   * @param string a string
   * @param pattern o padr�o
   *
   * @return o texto da string que casa com o o grupo do padr�o
   */
  private static String matchTime(String string, Pattern pattern) {
    Matcher matcher = pattern.matcher(string);
    if (matcher.find()) {
      return matcher.group(1);
    }
    else {
      return null;
    }
  }

  /**
   * Cria um mapa de tempos.
   *
   * @param wall tempo de rel�gio
   * @param user tempo de usu�rio
   * @param cpu tempo de CPU
   *
   * @return o mapa de tempos
   */
  private Map<String, String> createTimeMap(String wall, String user, String cpu) {
    Map<String, String> timeMap = new HashMap<>();

    timeMap.put(COMMAND_WALL_TIME_SEC.value, wall != null ? wall : "-1");
    timeMap.put(COMMAND_USER_TIME_SEC.value, user != null ? user : "-1");
    timeMap.put(COMMAND_CPU_TIME_SEC.value, cpu != null ? cpu : "-1");

    return timeMap;
  }
}
