package org.jetbrains.plugins.scala.lang.lexer.util;

import com.intellij.lexer.Lexer;

import java.util.HashMap;
import java.util.Map;

/**
 * User: Dmitry Naydanov
 * Date: 9/2/13
 */
public class PersistentStateManager implements MyStateManagingLayeredLexer.StateManager {
  private final Map<Lexer, Integer> states = new HashMap<Lexer, Integer>();
  private final int threshold;

  public PersistentStateManager() {
    threshold = Integer.MAX_VALUE;
  }
  
  public PersistentStateManager(int thresholdState) {
    this.threshold = thresholdState;
  }

  @Override
  public int getState(Lexer lexer, CharSequence buf, int start, int end) {
    Integer state = states.get(lexer);
    return state == null? 0 : state;
  }

  @Override
  public void layerStarted(Lexer lexer, CharSequence buf, int start, int end) { }

  @Override
  public void layerFinished(Lexer lexer, CharSequence buf, int start, int end) {
    final int state = lexer.getState();
    states.put(lexer, state < threshold? state : 0);
  }
}
