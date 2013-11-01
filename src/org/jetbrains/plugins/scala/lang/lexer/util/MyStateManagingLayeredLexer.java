package org.jetbrains.plugins.scala.lang.lexer.util;

import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;
import java.util.Map;

/**
 * Carefully copypasted from {@link com.intellij.lexer.LayeredLexer} to enable smart state managing of 
 * registered lexers
 * 
 * @see StateManager
 */
public class MyStateManagingLayeredLexer extends DelegateLexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.LayeredLexer");
  private static final StateManager DEFAULT_STATE_MANAGER = new StateManager() {
    @Override
    public int getState(Lexer lexer, CharSequence buf, int start, int end) {
      return 0;
    }

    @Override
    public void layerStarted(Lexer lexer, CharSequence buf, int start, int end) { }

    @Override
    public void layerFinished(Lexer lexer, CharSequence buf, int start, int end) { }
  };
  private static final int IN_LAYER_STATE = 1024; // TODO: Other value?
  private static final int IN_LAYER_LEXER_FINISHED_STATE = 2048;

  private int myState;

  private final Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<IElementType, Lexer>();
  private Lexer myCurrentLayerLexer;
  // In some cases IDEA-57933 layered lexer is not able to parse all the token, that triggered this lexer,
  // for this purposes we store left part of token in the following fields
  private IElementType myCurrentBaseTokenType;
  private int myLayerLeftPart = -1;
  private int myBaseTokenEnd = -1;

  private final HashSet<Lexer> mySelfStoppingLexers = new HashSet<Lexer>(1);
  private final HashMap<Lexer, IElementType[]> myStopTokens = new HashMap<Lexer,IElementType[]>(1);
  private final StateManager myStateManager;


  public MyStateManagingLayeredLexer(Lexer baseLexer) {
    this(baseLexer, DEFAULT_STATE_MANAGER);
  }
  
  public MyStateManagingLayeredLexer(Lexer baseLexer, StateManager stateManager) {
    super(baseLexer);
    myStateManager = stateManager;
  }

  public void registerSelfStoppingLayer(Lexer lexer, IElementType[] startTokens, IElementType[] stopTokens) {
    registerLayer(lexer, startTokens);
    mySelfStoppingLexers.add(lexer);
    myStopTokens.put(lexer, stopTokens);
  }

  public void registerLayer(Lexer lexer, IElementType... startTokens) {
    for (IElementType startToken : startTokens) {
      LOG.assertTrue(!myStartTokenToLayerLexer.containsKey(startToken));
      myStartTokenToLayerLexer.put(startToken, lexer);
    }
  }

  private void activateLayerIfNecessary() {
    final IElementType baseTokenType = super.getTokenType();
    myCurrentLayerLexer = myStartTokenToLayerLexer.get(baseTokenType);
    if (myCurrentLayerLexer != null) {
      myCurrentBaseTokenType = baseTokenType;
      final int end = super.getTokenEnd();
      final CharSequence bufferSequence = super.getBufferSequence();
      final int start = super.getTokenStart();

      myBaseTokenEnd = end;
      myStateManager.layerStarted(myCurrentLayerLexer, bufferSequence, start, end);
      myCurrentLayerLexer.start(bufferSequence, start, end, myStateManager.getState(myCurrentLayerLexer, bufferSequence, start, end));
      
      if (mySelfStoppingLexers.contains(myCurrentLayerLexer)) super.advance();
    }
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    LOG.assertTrue(initialState != IN_LAYER_STATE, "Restoring to layer is not supported.");
    myState = initialState;
    myCurrentLayerLexer = null;

    super.start(buffer, startOffset, endOffset, initialState);
    activateLayerIfNecessary();
  }

  public int getState() {
    return myState;
  }

  public IElementType getTokenType() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myCurrentBaseTokenType;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenType() : super.getTokenType();
  }

  public int getTokenStart() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myLayerLeftPart;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenStart() : super.getTokenStart();
  }

  public int getTokenEnd() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myBaseTokenEnd;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenEnd() : super.getTokenEnd();
  }

  public void advance() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE){
      myState = super.getState();
      return;
    }

    if (isLayerActive()) {
      final Lexer activeLayerLexer = myCurrentLayerLexer;
      IElementType layerTokenType = activeLayerLexer.getTokenType();
      if (!isStopToken(myCurrentLayerLexer, layerTokenType)) {
        myCurrentLayerLexer.advance();
        layerTokenType = myCurrentLayerLexer.getTokenType();
      } else {
        layerTokenType = null;
      }
      if (layerTokenType == null) {
        int tokenEnd = myCurrentLayerLexer.getTokenEnd();
        if (!mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
          myCurrentLayerLexer = null;
          super.advance();
          activateLayerIfNecessary();
        } else {
          myCurrentLayerLexer = null;

          // In case when we have non-covered gap we should return left part as next token
          if (tokenEnd != myBaseTokenEnd) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("We've got not covered gap from layered lexer: " + activeLayerLexer +
                  "\n on token: " + getBufferSequence().subSequence(myLayerLeftPart, myBaseTokenEnd));
            }
            myState = IN_LAYER_LEXER_FINISHED_STATE;
            myLayerLeftPart = tokenEnd;
            return;
          }
        }
      }
      
      if (myCurrentLayerLexer == null) {
        myStateManager.layerFinished(activeLayerLexer, activeLayerLexer.getBufferSequence(), 
            activeLayerLexer.getTokenStart(), activeLayerLexer.getTokenEnd());
      }
    } else {
      super.advance();
      activateLayerIfNecessary();
    }
    myState = isLayerActive() ? IN_LAYER_STATE : super.getState();
  }

  public LexerPosition getCurrentPosition() {
    return new LexerPositionImpl(getTokenStart(), getState());
  }

  public void restore(LexerPosition position) {
    start(getBufferSequence(), position.getOffset(), getBufferEnd(), position.getState());
  }

  private boolean isStopToken(Lexer lexer, IElementType tokenType) {
    final IElementType[] stopTokens = myStopTokens.get(lexer);
    if (stopTokens == null) return false;
    for (IElementType stopToken : stopTokens) {
      if (stopToken == tokenType) return true;
    }
    return false;
  }

  private boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }

  /**
   * Carefully copypasted from {@link com.intellij.lexer.LexerPositionImpl}  
   * because of access restriction 
   */
  public static class LexerPositionImpl implements LexerPosition {
    private final int myOffset;
    private final int myState;

    public LexerPositionImpl(final int offset, final int state) {
      myOffset = offset;
      myState = state;
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public int getState() {
      return myState;
    }
  }             
  
  public interface StateManager {
    int getState(Lexer lexer, CharSequence buf, int start, int end);
    void layerStarted(Lexer lexer, CharSequence buf, int start, int end);
    void layerFinished(Lexer lexer, CharSequence buf, int start, int end);
  }
}
