/**
 * $Id$
 */
package csbase.sga;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Hashtable;
import java.util.Map;

import csbase.sga.executor.JobData;

/**
 * Camada de persist�ncia de jobs.
 *
 * @author Tecgraf/PUC-Rio
 */
class CommandPersistence {
  /** Int�ncia �nica */
  private static CommandPersistence instance;
  /** Caminho para o arquivo de persist�ncia */
  private String persistenceFile;
  /**
   * Mapa com os identificadores de comandos e os dados dos job na camada de
   * execu��o de jobs.
   */
  private Map<String, JobData> commands;

  /**
   * Construtor
   *
   * @param persistenceFile caminho para o arquivo de persist�ncia
   */
  private CommandPersistence(String persistenceFile) {
    File file = new File(persistenceFile);
    try {
      file.createNewFile();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    this.persistenceFile = persistenceFile;
    this.commands = new Hashtable<>();

    read();
  }

  /**
   * Obt�m a inst�ncia do objeto de persist�ncia.
   *
   * @param persistenceFile caminho para o arquivo de persist�ncia
   *
   * @return a inst�ncia
   */
  protected static CommandPersistence getInstance(String persistenceFile) {
    if (instance == null) {
      instance = new CommandPersistence(persistenceFile);
    }
    return instance;
  }

  /**
   * Adiciona um comando e grava no arquivo
   *
   * @param commandId identificador do comando
   * @param data dado da camanda de execu��o ao associado comando
   */
  protected void addCommand(String commandId, JobData data) {
    commands.put(commandId, data);
    //TODO Pensar em um forma melhor de gravar quando adicionar somente um comando
    write();
  }

  /**
   * Adiciona um conjunto de comandos e grava no arquivo
   *
   * @param commands um mapa com os identificadores de comandos os dados
   *        associado a eles
   */
  protected void addCommands(Map<String, JobData> commands) {
    this.commands = commands;
    write();
  }

  /**
   * Obt�m os comando armazenados
   *
   * @return um mapa com os identificadores de comandos os dados associado a
   *         eles
   */
  protected Map<String, JobData> getCommands() {
    return this.commands;
  }

  /**
   * Grava os comandos e seus dados no arquivo
   */
  private void write() {
    try (FileOutputStream fos = new FileOutputStream(persistenceFile);
      ObjectOutputStream oos =
        new ObjectOutputStream(new BufferedOutputStream(fos))) {
      oos.writeObject(commands);
    }
    catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Le os comando e seus dados do arquivo
   */
  private void read() {
    try (FileInputStream fis = new FileInputStream(persistenceFile);
      ObjectInputStream ois = new ObjectInputStream(fis);) {

      //TODO Verificar se esse cast est� ok
      commands = (Map<String, JobData>) ois.readObject();
    }
    catch (EOFException e) {

    }
    catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
