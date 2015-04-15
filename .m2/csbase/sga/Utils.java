/**
 * $Id$
 */
package csbase.sga;

import java.util.HashMap;
import java.util.Map;

import sgaidl.Pair;

/**
 * Utilitários.
 *
 * @author Tecgraf/PUC-Rio
 */
public class Utils {

  /**
   * Adiciona as entradas de um dicionário em um mapa.
   *
   * @param dictionary o odicionário
   * @param map o mapa
   */
  protected static void convertDicToMap(Pair[] dictionary,
    Map<String, String> map) {
    for (Pair pair : dictionary) {
      map.put(pair.key, pair.value);
    }
  }

  /**
   * Converte um dicionário para mapa.
   *
   * @param dictionary o dicionário
   *
   * @return o mapa
   */
  protected static Map<String, String> convertDicToMap(Pair[] dictionary) {
    Map<String, String> map = new HashMap<String, String>();
    for (Pair pair : dictionary) {
      map.put(pair.key, pair.value);
    }

    return map;
  }
}
