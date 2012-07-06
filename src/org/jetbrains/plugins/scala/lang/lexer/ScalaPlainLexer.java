/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.LexerState;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayCharSequence;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.SCALA_PLAIN_CONTENT;
import org.jetbrains.plugins.scala.lang.lexer.core.ScalaCoreLexer;
import org.jetbrains.plugins.scala.lang.lexer.core.ScalaSplittingLexer;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author ilyas
 */
public class ScalaPlainLexer extends Lexer {     //todo delete if we don't need it 

  public static final int SCALA_CORE_MASK = 0x38;
  public static final int SPLIT_MASK = 0x7;

  public static final int SCALA_CORE_SHIFT = 3;
  public static final int SCALA_SHIFT = 6;


  private ScalaCoreLexer myScalaLexer = new ScalaCoreLexer();
  private Lexer mySplittingLexer;

  private int myBufferEnd;
  private int myTokenStart;
  private int myTokenEnd;

  private IElementType myTokenType;
  private Queue<Token> myTokenQueue;

  private int myLastScalaState;

  public ScalaPlainLexer(boolean treatDocCommentAsBlockComment) {
    mySplittingLexer = new ScalaSplittingLexer(treatDocCommentAsBlockComment);
  }

  public ScalaPlainLexer() {
    this(false);
  }

  private static class Token {
    public Token(final int tokenEnd, final int tokenStart, final IElementType tokenType) {
      this.tokenEnd = tokenEnd;
      this.tokenStart = tokenStart;
      this.tokenType = tokenType;
    }

    public int tokenStart;
    public int tokenEnd;
    public IElementType tokenType;
    public boolean newLineAllowed;
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    mySplittingLexer.start(buffer, startOffset, endOffset, initialState & SPLIT_MASK);
    myLastScalaState = (initialState & SCALA_CORE_MASK) >> SCALA_CORE_SHIFT;
    myTokenQueue = new LinkedList<Token>();
    myTokenStart = myTokenEnd = startOffset;
    myTokenType = null;
    myBufferEnd = endOffset;
  }

  public CharSequence getBufferSequence() {
    return mySplittingLexer.getBufferSequence();
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public int getTokenStart() {
    locateToken();
    return myTokenStart;
  }

  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  public int getState() {
    final int splitState = mySplittingLexer.getState();
    final int scalaState = myScalaLexer.getState();
    return splitState | (scalaState << SCALA_CORE_SHIFT);
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

  public void advance() {
    myTokenType = null;
    if (myTokenQueue.poll() == null) {
      mySplittingLexer.advance();
    }
  }

  private static class MyState implements LexerState {

    public Queue<Token> queue;
    public int splitState;
    public int splitStart;
    public int scalaState;

    public MyState(final Queue<Token> queue, final int splitState, final int splitStart,
                   final int scalaState) {
      this.queue = queue;
      this.splitState = splitState;
      this.splitStart = splitStart;
      this.scalaState = scalaState;
    }

    public short intern() {
      return 0;
    }
  }

  private static class MyPosition implements LexerPosition {
    public int start;
    public MyState state;

    public MyPosition(final int start, final MyState state) {
      this.start = start;
      this.state = state;
    }

    public int getOffset() {
      return start;
    }

    public int getState() {
      return state.scalaState;
    }
  }

  public LexerPosition getCurrentPosition() {
    return new MyPosition(myTokenStart,
        new MyState(
            new LinkedList<Token>(myTokenQueue),
            mySplittingLexer.getState(),
            mySplittingLexer.getTokenStart(),
            myScalaLexer.getState()
        )
    );
  }

  public void restore(LexerPosition position) {
    MyPosition pos = (MyPosition) position;

    myTokenType = null;
    myTokenStart = myTokenEnd = pos.getOffset();

    mySplittingLexer.start(mySplittingLexer.getBufferSequence(), pos.state.splitStart, myBufferEnd, pos.state.splitState);
    myLastScalaState = pos.state.scalaState;
    myTokenQueue = pos.state.queue;
  }

  private void locateToken() {
    if (myTokenType != null) return;
    myTokenStart = myTokenEnd;
    final Token queuedToken = myTokenQueue.peek();
    if (queuedToken != null) {
      myTokenType = queuedToken.tokenType;
      myTokenEnd = queuedToken.tokenEnd;
      return;
    }

    final IElementType tokenType = mySplittingLexer.getTokenType();
    if (tokenType == SCALA_PLAIN_CONTENT) {
      fedQueueFromLexer(myScalaLexer);
    } else {
      myTokenType = tokenType;
      myTokenEnd = mySplittingLexer.getTokenEnd();
    }
  }

  private void fedQueueFromLexer(Lexer lexer) {
    lexer.start(mySplittingLexer.getBufferSequence(), mySplittingLexer.getTokenStart(), mySplittingLexer.getTokenEnd(),
        lexer instanceof ScalaCoreLexer ? myLastScalaState : 0);
    mySplittingLexer.advance();
    do {
      IElementType type = lexer.getTokenType();
      if (type == null) break;
      Token token = new Token(lexer.getTokenEnd(), lexer.getTokenStart(), type);
      myTokenQueue.offer(token);
      lexer.advance();
      if (lexer instanceof ScalaCoreLexer) myLastScalaState = lexer.getState();
    } while (true);
    locateToken();
  }


}
