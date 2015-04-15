package csbase.sga;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.omg.CORBA.IntHolder;

import sgaidl.COMMAND_CPU_TIME_SEC;
import sgaidl.COMMAND_USER_TIME_SEC;
import sgaidl.COMMAND_WALL_TIME_SEC;
import sgaidl.CompletedCommandInfo;
import sgaidl.InvalidCommandException;
import sgaidl.InvalidParameterException;
import sgaidl.InvalidPathException;
import sgaidl.InvalidSGAException;
import sgaidl.MissingParameterException;
import sgaidl.NoPermissionException;
import sgaidl.Pair;
import sgaidl.PathNotFoundException;
import sgaidl.RetrievedInfo;
import sgaidl.SGAAlreadyRegisteredException;
import sgaidl.SGACommand;
import sgaidl.SGAControlAction;
import sgaidl.SGANotRegisteredException;
import sgaidl.SGAPath;
import sgaidl.SGAProperties;
import sgaidl.SystemException;
import csbase.server.plugin.service.IServiceManager;
import csbase.server.plugin.service.sgaservice.ISGADaemon;
import csbase.server.plugin.service.sgaservice.ISGAService;
import csbase.server.plugin.service.sgaservice.SGADaemonException;
import csbase.sga.executor.DefaultJobExecutor;
import csbase.sga.executor.JobData;
import csbase.sga.executor.JobExecutor;
import csbase.sga.executor.JobInfo;
import csbase.sga.executor.JobObserver;
import csbase.sga.monitor.EnvironmentMonitor;

/**
 * Exemplo de um SGA.
 *
 * @author Tecgraf/PUC-Rio
 */
public class SGALocal implements ISGADaemon {

  /** As propriedades do SGADaemonPlugin. */
  private Properties pluginProperties;

  /**
   * Nome do serviço SGA.
   */
  public static final String SGA_SERVICE_NAME = "SGAService";

  /** A interface SGAService com o qual o SGA se comunica. */
  private ISGAService sgaService;
  /** Executor de comandos */
  private JobExecutor executor;
  /** Monitor do ambiente de execução */
  private EnvironmentMonitor monitor;

  /** Mapa com os identificadores de commando e suas referências */
  private Map<String, SGALocalCommand> commands;
  /** Mapa com os identificadores de job e referências de comandos */
  private Map<JobData, SGALocalCommand> jobs;

  /** Pool de threads para disparar os comando recebidos */
  ExecutorService pool = Executors.newCachedThreadPool();

  /** Logger usado pelo SGA */
  static Logger logger = Logger.getLogger(SGALocal.class.getName());

  /** Agendamento da renovação do registro do SGA no SGAService. */
  private TimerExecutor renewCallback;

  /** Agendamento da atualização das propriedades do SGA. */
  private TimerExecutor updatePropertiesCallback;

  /** Camada de persistência de comandos */
  private CommandPersistence persistence;

  /** Nome do SGA */
  private String sgaName;

  /** Chaves padrões de configuração do SGA */
  private String[] defaltConfigKeys = { sgaidl.SGA_NODE_NUM_PROCESSORS.value,
      sgaidl.SGA_NODE_MEMORY_RAM_INFO_MB.value,
      sgaidl.SGA_NODE_MEMORY_SWAP_INFO_MB.value,
      sgaidl.JOB_CONTROL_ACTIONS.value };

  /** Chaves padrões de informações do SGA */
  private String[] defaltInfoKeys = { sgaidl.SGA_NODE_LOAD_AVG_1MIN_PERC.value,
      sgaidl.SGA_NODE_LOAD_AVG_5MIN_PERC.value,
      sgaidl.SGA_NODE_LOAD_AVG_15MIN_PERC.value,
      sgaidl.SGA_NODE_MEMORY_RAM_FREE_PERC.value,
      sgaidl.SGA_NODE_MEMORY_SWAP_FREE_PERC.value,
      sgaidl.SGA_NODE_NUMBER_OF_JOBS.value };

  /** Chaves padrões de informações de processo */
  private String[] defaltProcessKeys = { sgaidl.COMMAND_PID.value,
      sgaidl.COMMAND_STRING.value, sgaidl.COMMAND_EXEC_HOST.value,
      sgaidl.COMMAND_STATE.value, sgaidl.COMMAND_MEMORY_RAM_SIZE_MB.value,
      sgaidl.COMMAND_MEMORY_SWAP_SIZE_MB.value, sgaidl.COMMAND_CPU_PERC.value,
      //    sgaidl.COMMAND_ "csbase_command_time_sec",
      sgaidl.COMMAND_WALL_TIME_SEC.value, sgaidl.COMMAND_USER_TIME_SEC.value,
      sgaidl.COMMAND_SYSTEM_TIME_SEC.value };

  /**
   * Executa uma tarefa periódica, de acordo com uma configuração de intervalo
   * de tempo.
   */
  private class TimerExecutor {
    /** Thread que faz a execução periódica de uma tarefa. */
    final private Timer timer;
    /** Tempo (em segundos) para execução da tarefa. */
    final private int seconds;
    /** Tarefa agendada. */
    final private TimerTask task;

    /**
     * Construtor.
     *
     * @param seconds tempo (em segundos) para renovação do registro.
     * @param task tarefa cuja execução está agendada.
     */
    protected TimerExecutor(int seconds, TimerTask task) {
      this.seconds = seconds;
      this.task = task;
      this.timer = new Timer();
    }

    /**
     * Inicia a thread de agendamento de execução da tarefa.
     */
    protected void start() {
      timer.schedule(task, seconds, seconds);
    }

    /**
     * Interrompe a thread de agendamento de execução da tarefa.
     */
    protected void stop() {
      timer.cancel();
    }
  }

  /**
   * Tarefa agendada que executa a chamada ao SGAService para avisar que o SGA
   * se mantém registrado.
   */
  private class SGARegisterRenewTask extends TimerTask {
    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
      logger.finest("Renova o SGA " + sgaName);
      try {
        sgaService.isRegistered(SGALocal.this, sgaName);
      }
      catch (InvalidSGAException e) {
        logger.log(Level.SEVERE, "Erro ao renovar o registro do SGA: {0}: {1}",
          new Object[] { e, e.message });
      }
      catch (NoPermissionException e) {
        logger.log(Level.SEVERE, "Erro ao renovar o registro do SGA: {0}: {1}",
          new Object[] { e, e.message });
      }
    }
  }

  /**
   * Tarefa agendada que executa a chamada ao SGAService para atualizar as
   * propriedades do SGA e seus nós de execução.
   */
  private class SGAUpdatePropertiesTask extends TimerTask {
    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
      logger.finest("Atualiza os dados do SGA  " + sgaName);
      SGAProperties sgaProps;
      //TODO monitor.update()
      sgaProps = loadSGAProperties();
      if (sgaProps.nodesProperties.length <= 0) {
        logger.log(Level.WARNING, "Não existem nós de SGAs");
      }
      else {
        try {
          if (sgaService.isRegistered(SGALocal.this, sgaName)) {
            sgaService.updateSGAInfo(SGALocal.this, sgaName, sgaProps);
          }
          else {
            registerSGA(sgaProps);
          }
        }
        catch (InvalidParameterException e) {
          logger.log(Level.SEVERE,
            "Erro ao atualizar as informações do SGA: {0}: {1}", new Object[] {
                e, e.message });
        }
        catch (NoPermissionException e) {
          logger.log(Level.SEVERE,
            "Erro ao atualizar as informações do SGA: {0}: {1}", new Object[] {
                e, e.message });
        }
        catch (SGANotRegisteredException e) {
          logger.log(Level.SEVERE,
            "Erro ao atualizar as informações do SGA: {0}: {1}", new Object[] {
                e, e.message });
        }
        catch (InvalidSGAException e) {
          logger.log(Level.SEVERE,
            "Erro ao atualizar as informações do SGA: {0}: {1}", new Object[] {
                e, e.message });
        }
      }
    }
  }

  /**
   * Construtor obrigatório.
   *
   * @param serviceManager gerente dos serviços
   */

  public SGALocal(IServiceManager serviceManager) {
    this.sgaService =
      ISGAService.class.cast(serviceManager.getService(SGA_SERVICE_NAME));
    commands = new Hashtable<>();
    jobs = new Hashtable<>();

    //    setMonitor();
    setExecutor(new DefaultJobExecutor());
  }

  /**
   * Obtém o executor de jobs.
   *
   * @return o executor
   */
  protected JobExecutor getExecutor() {
    return executor;
  }

  /**
   * Define o executor de jobs.
   *
   * @param executor o executor
   */
  protected void setExecutor(JobExecutor executor) {
    this.executor = executor;
  }

  /**
   * Obtém o monitor do ambiente de execução.
   *
   * @return o monitor
   */
  protected EnvironmentMonitor getMonitor() {
    return monitor;
  }

  /**
   * Difine o monitor do ambiente de execução.
   *
   * @param monitor o monitor
   */
  protected void setMonitor(EnvironmentMonitor monitor) {
    this.monitor = monitor;
  }

  /**
   * Carrega as propriedades do SGA.
   *
   * @return retorna as propriedades do SGA.
   */
  private synchronized SGAProperties loadSGAProperties() {
    SGAProperties sgaProperties = new SGAProperties();
    sgaProperties.properties = new Pair[pluginProperties.size()];

    int i = 0;
    for (Object key : pluginProperties.keySet()) {
      sgaProperties.properties[i] = new Pair();
      sgaProperties.properties[i].key = (String) key;
      sgaProperties.properties[i].value =
        pluginProperties.getProperty((String) key);
      i++;
    }

    //TODO Buscar as infos dos nós pelo monitor
    sgaProperties.nodesProperties = new Pair[1][];
    sgaProperties.nodesProperties[0] = sgaProperties.properties;

    // Propriedades dos nós

    // Coloca no log as propriedades dos SGAs
    logger.info("Propriedades do SGA: ");
    for (Pair p : sgaProperties.properties) {
      logger.info("  " + p.key + "=" + p.value);
    }
    logger.info("  " + "Propriedades dos nós do SGA ("
      + sgaProperties.nodesProperties.length + ")");

    int j = 0;
    for (Pair[] nodes : sgaProperties.nodesProperties) {
      logger.info("  " + "Nó " + (j++));
      for (Pair p : nodes) {
        logger.info("     " + p.key + "=" + p.value);
      }
    }
    return sgaProperties;
  }

  /**
   * Valida se as propriedades do plugin estão definidas.
   *
   * @throws SGADaemonException se as propriedades do plugin não estiverem
   *         definidas
   */
  private void validatePluginProperties() throws SGADaemonException {
    if (this.pluginProperties == null) {
      throw new SGADaemonException(
        "As propriedades do SGADaemon não foram atribuídas.");
    }
    if (pluginProperties.getProperty(sgaidl.SGA_NAME.value) == null) {
      throw new SGADaemonException("A propriedade " + sgaidl.SGA_NAME.value
        + " com o nome do SGA não foi definida.");
    }

    if (pluginProperties.getProperty("csbase_machine_time_seconds") == null) {
      throw new SGADaemonException(
        "A propriedade csbase_machine_time_seconds não foi definida.");
    }
    if (pluginProperties.getProperty("csbase_log_path") == null) {
      throw new SGADaemonException(
        "A propriedade csbase_log_path não foi definida.");
    }
    File logPath = new File(pluginProperties.getProperty("csbase_log_path"));
    if (!logPath.exists() || !logPath.isDirectory()) {
      throw new SGADaemonException(
        "O diretório defindo na propriedade csbase_log_path não existe ou não é diretório.");
    }

    if (pluginProperties.getProperty("csbase_persistence_path") == null) {
      throw new SGADaemonException(
        "A propriedade csbase_persistence_path não foi definida.");
    }

    try {
      Integer.parseInt(pluginProperties
        .getProperty("csbase_machine_time_seconds"));
    }
    catch (NumberFormatException e) {
      throw new SGADaemonException(
        "A propriedade csbase_machine_time_seconds possui um valor não numérico.",
        e);
    }
  }

  /**
   * Registra o SGA no servidor.
   *
   * @param sgaProps propriedades do SGA
   */
  private synchronized void registerSGA(SGAProperties sgaProps) {
    IntHolder updateInterval = new IntHolder();
    try {
      sgaService.registerSGA(this, sgaName, sgaProps, updateInterval);
    }
    catch (InvalidParameterException e) {
      logger.log(Level.SEVERE, "Erro ao registrar o SGA: {0}: {1}",
        new Object[] { e, e.message });
      this.stop();
    }
    catch (NoPermissionException e) {
      logger.log(Level.SEVERE, "Erro ao registrar o SGA: {0}: {1}",
        new Object[] { e, e.message });
      this.stop();
    }
    catch (SGAAlreadyRegisteredException e) {
      logger.log(Level.SEVERE, "Erro ao registrar o SGA: {0}: {1}",
        new Object[] { e, e.message });
      this.stop();
    }

    Map<String, JobData> persistedCommands = persistence.getCommands();
    for (String commandId : persistedCommands.keySet()) {
      JobData jobData = persistedCommands.get(commandId);

      final ReentrantLock lock = new ReentrantLock();

      lock.lock();
      boolean commandRetrieved =
        this.executor.retrieveJob(jobData, new SynchronizedJobObserver(
          commandId, lock));
      if (commandRetrieved) {
        SGALocalCommand commandRef = new SGALocalCommand(jobData, executor);
        commands.put(commandId, commandRef);
        jobs.put(jobData, commandRef);
      }
      lock.unlock();
    }

    Map<String, JobData> jobsData = new HashMap<>();
    List<RetrievedInfo> infos = new LinkedList<RetrievedInfo>();
    for (String commandId : commands.keySet()) {
      SGALocalCommand commandRef = commands.get(commandId);
      JobData jobData = commandRef.getJobData();
      jobsData.put(commandId, jobData);
      infos.add(new RetrievedInfo(commandId, commandRef));
    }
    persistence.addCommands(jobsData);
    try {
      sgaService.commandRetrieved(sgaName, infos.toArray(new RetrievedInfo[0]));
    }
    catch (InvalidSGAException e) {
      logger.log(Level.SEVERE, "Erro ao recuperar comandos: {0}: {1}",
        new Object[] { e, e.message });
    }
    catch (NoPermissionException e) {
      logger.log(Level.SEVERE, "Erro ao recuperar comandos: {0}: {1}",
        new Object[] { e, e.message });
    }
    catch (InvalidCommandException e) {
      logger.log(Level.SEVERE, "Erro ao recuperar comandos: {0}: {1}",
        new Object[] { e, e.message });
    }

    this.renewCallback =
      new TimerExecutor(updateInterval.value * 1000, new SGARegisterRenewTask());
    renewCallback.start();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SGACommand executeCommand(final String commandString,
    final String commandId, Pair[] extraParams) throws SystemException,
    MissingParameterException {

    //TODO Verificar se todos os parâmetros estão preenchidos (MissingParameterException)

    final Map<String, String> paramMap = Utils.convertDicToMap(extraParams);

    final ReentrantLock lock = new ReentrantLock();

    Future<SGALocalCommand> future =
      pool.submit(new Callable<SGALocalCommand>() {
        @Override
        public SGALocalCommand call() throws Exception {
          SGALocalCommand commandRef;
          try {
            lock.lock();
            JobData data =
              executor.executeJob(commandString, paramMap,
                new SynchronizedJobObserver(commandId, lock));
            commandRef = new SGALocalCommand(data, executor);
            commands.put(commandId, commandRef);
            jobs.put(data, commandRef);
          }
          catch (Exception e) {
            logger.log(Level.SEVERE, e.toString());
            throw e;
          }
          finally {
            lock.unlock();
          }

          return commandRef;
        }
      });

    try {
      SGALocalCommand command = future.get();
      this.persistence.addCommand(commandId, command.getJobData());

      return command;
    }
    catch (ExecutionException | InterruptedException e) {
      logger.log(Level.SEVERE, "Erro ao executar o comando: {0}. [{1}:{2}]",
        new Object[] { commandId, e, e.getMessage() });
      throw new SystemException();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void control(SGAControlAction action) {
    logger.info("Solicitado " + action.value());
    if (action.equals(SGAControlAction.SHUTDOWN)) {
      try {
        sgaService.unregisterSGA(this, sgaName);
      }
      catch (NoPermissionException e) {
        logger.log(Level.SEVERE, "Erro ao desregistrar o SGA: {0}: {1}",
          new Object[] { e, e.message });
      }
      catch (SGANotRegisteredException e) {
        logger.log(Level.SEVERE, "Erro ao desregistrar o SGA: {0}: {1}",
          new Object[] { e, e.message });
      }
      renewCallback.stop();
      updatePropertiesCallback.stop();
    }
  }

  /**
   * {@inheritDoc}
   *
   * Se o path passado como parâmetro não for acessível no sistema de arquivos
   * do SGA, retorna null. Senão, retorna o SGAPath com as informações sobre o
   * caminho recebido.
   */
  @Override
  public SGAPath getPath(String path) throws InvalidPathException,
    PathNotFoundException {

    //    File file = new File(path);

    return new SGAPath(path, 0, true, false, path, true, true, false, true);

    //    struct SGAPath {
    //      string path;
    //      double sizeKB;
    //      boolean isDir;
    //      boolean isSymbolicLink;
    //      string linkPath;
    //      boolean readable;
    //      boolean writable;
    //      boolean executable;
    //      boolean exists;
    //    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SGAPath[] getPaths(String root) throws InvalidPathException,
    PathNotFoundException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void ping() {
    logger.info("Acesso ao SGA com sucesso.");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDefaultConfigKeys(String[] keys) {
    //TODO Implementar
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDefaultInfoKeys(String[] keys) {
    //TODO Implementar
  }

  /**
   *
   * {@inheritDoc}
   */
  @Override
  public void setProperties(Properties pluginProps) {
    this.pluginProperties = pluginProps;
  }

  /**
   *
   * {@inheritDoc}
   *
   */
  @Override
  public boolean start() throws SGADaemonException {
    if (this.sgaService == null) {
      throw new SGADaemonException("O serviço SGAService está nulo.");
    }
    // Valida as propriedades do plugin
    validatePluginProperties();

    sgaName = pluginProperties.getProperty(sgaidl.SGA_NAME.value);
    String logFile = "";
    try {
      if (pluginProperties.getProperty("csbase_log_path") != null) {
        logFile =
          pluginProperties.getProperty("csbase_log_path") + sgaName + ".log";
      }
      FileHandler fh = new FileHandler(logFile);
      logger.addHandler(fh);
    }
    catch (SecurityException | IOException e) {
      throw new SGADaemonException("Erro na criação do log " + logFile, e);
    }

    commands = new Hashtable<>();
    persistence =
      CommandPersistence.getInstance(pluginProperties
        .getProperty("csbase_persistence_path")
        + sgaName + ".dat");

    // Busca pelas propriedades dos SGAs através do OpenDreans
    SGAProperties sgaProps = loadSGAProperties();
    if (sgaProps != null && sgaProps.nodesProperties.length > 0) {
      registerSGA(sgaProps);
    }
    int updateTime =
      Integer.parseInt(pluginProperties
        .getProperty("csbase_machine_time_seconds"));
    this.updatePropertiesCallback =
      new TimerExecutor(updateTime * 1000, new SGAUpdatePropertiesTask());
    updatePropertiesCallback.start();

    //    this.updateCommandsCallback =
    //      new TimerExecutor(updateTime * 1000, new SGAUpdateCommandTask());
    //    updateCommandsCallback.start();

    logger.info("SGADaemon " + sgaName + " iniciado");

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stop() {

    if (renewCallback != null) {
      renewCallback.stop();
    }

    if (updatePropertiesCallback != null) {
      updatePropertiesCallback.stop();
    }

    logger.info("Finaliza o plugin do SGA.");
  }

  /**
   * Observador que é chamado somente após o lock de sincronização ser liberado.
   *
   * @author Tecgraf/PUC-Rio
   */
  class SynchronizedJobObserver implements JobObserver {
    /**
     * Identificador do comando
     */
    private String commandId;
    /**
     * Lock para sincronização
     */
    private Lock lock;

    /**
     * Construtor
     *
     * @param commandId identificador do comando
     * @param lock lock para sincronização
     */
    SynchronizedJobObserver(String commandId, Lock lock) {
      this.commandId = commandId;
      this.lock = lock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onJobCompleted(JobInfo jobInfo) {
      int elapsedTimeSec =
        Integer.parseInt(jobInfo.jobParam.get(COMMAND_WALL_TIME_SEC.value));
      int userTimeSec =
        Integer.parseInt(jobInfo.jobParam.get(COMMAND_USER_TIME_SEC.value));
      int cpuTimeSec =
        Integer.parseInt(jobInfo.jobParam.get(COMMAND_CPU_TIME_SEC.value));

      CompletedCommandInfo completedInfo =
        new CompletedCommandInfo(elapsedTimeSec, userTimeSec, cpuTimeSec);

      lock.lock();
      SGALocalCommand command = commands.get(commandId);
      lock.unlock();

      try {
        sgaService.commandCompleted(sgaName, command, commandId, completedInfo);
      }
      catch (InvalidSGAException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar fim do comando {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
      catch (NoPermissionException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar fim do comando {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
      catch (InvalidCommandException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar fim do comando {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onJobLost() {
      try {
        sgaService.commandLost(sgaName, commandId);
      }
      catch (InvalidSGAException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar o comando perdido: {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
      catch (NoPermissionException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar o comando perdido: {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
      catch (InvalidCommandException e) {
        logger.log(Level.SEVERE,
          "Erro ao notificar o comando perdido: {0}. [{1}:{2}]", new Object[] {
              commandId, e, e.message });
      }
    }
  }
}
