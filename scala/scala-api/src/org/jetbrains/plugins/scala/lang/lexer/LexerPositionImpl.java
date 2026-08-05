package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.LexerPosition;

// Copy of the corresponding class in IDEA
class LexerPositionImpl implements LexerPosition {
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
