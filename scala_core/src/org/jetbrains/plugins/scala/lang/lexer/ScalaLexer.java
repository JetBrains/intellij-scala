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
import com.intellij.lexer.XmlLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.xml.XmlTokenType.*;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.Nullable;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.SCALA_CORE_MASK;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.SCALA_CORE_SHIFT;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.*;
import org.jetbrains.plugins.scala.lang.lexer.core._ScalaCoreLexer;


/**
 * @author ilyas
 */
public class ScalaLexer implements Lexer {

  protected final Lexer myScalaPlainLexer = new ScalaPlainLexer();
  private final Lexer myXmlLexer = new XmlLexer();

  protected Lexer myCurrentLexer;

  protected static final int MASK = 0x3F;
  protected static final int XML_SHIFT = 6;
  protected TIntStack myBraceStack = new TIntStack();
  protected Stack<Stack<MyOpenXmlTag>> myLayeredTagStack = new Stack<Stack<MyOpenXmlTag>>();


  protected int myBufferEnd;
  protected int myBufferStart;
  protected CharSequence myBuffer;
  protected int myXmlState;
  private int myTokenStart;
  private int myTokenEnd;
  protected IElementType myTokenType;
  public final String XML_BEGIN_PATTERN = "<\\w";
  public final int SCALA_NEW_LINE_ALLOWED_STATE = (_ScalaCoreLexer.NEW_LINE_ALLOWED & (SCALA_CORE_MASK >> SCALA_CORE_SHIFT)) << SCALA_CORE_SHIFT;
  public final int SCALA_NEW_LINE_DEPRECATED_STATE = (_ScalaCoreLexer.NEW_LINE_DEPRECATED & (SCALA_CORE_MASK >> SCALA_CORE_SHIFT)) << SCALA_CORE_SHIFT;

  public ScalaLexer() {
    myCurrentLexer = myScalaPlainLexer;
  }

  @Deprecated
  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  @Deprecated
  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, 0);
  }

  @Deprecated
  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(new CharArrayCharSequence(buffer), startOffset, endOffset, initialState);
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myCurrentLexer = myScalaPlainLexer;
    myCurrentLexer.start(buffer, startOffset, endOffset, initialState & MASK);
    myBraceStack.clear();
    myLayeredTagStack.clear();
    myXmlState = (initialState >> XML_SHIFT) & MASK;
    myBuffer = buffer;
    myBufferStart = startOffset;
    myBufferEnd = endOffset;
    myTokenType = null;
  }

  public int getState() {
    locateToken();
    return myTokenStart == 0 ? 0 : 239;
  }

  @Nullable
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  private void locateToken() {
    if (myTokenType == null) {
      IElementType type = myCurrentLexer.getTokenType();
      int start = myCurrentLexer.getTokenStart();
      String tokenText = myCurrentLexer.getBufferSequence().subSequence(start, myCurrentLexer.getTokenEnd()).toString();

      if (type == SCALA_XML_CONTENT_START) {
        myCurrentLexer = myXmlLexer;
        myCurrentLexer.start(getBufferSequence(), start, myBufferEnd, myXmlState);
        myLayeredTagStack.push(new Stack<MyOpenXmlTag>());
        myLayeredTagStack.peek().push(new MyOpenXmlTag());
        myTokenType = myCurrentLexer.getTokenType();
        locateTextRange();
      } else if ((type == XML_ATTRIBUTE_VALUE_TOKEN || type == XML_DATA_CHARACTERS) &&
          tokenText.startsWith("{") && !tokenText.startsWith("{{")) {
        myXmlState = myCurrentLexer.getState();
        (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd, 0);
        locateTextRange();
        myBraceStack.push(1);
        myTokenType = SCALA_IN_XML_INJECTION_START;
      } else if (type == ScalaTokenTypes.tRBRACE && myBraceStack.size() > 0) {
        int currentLayer = myBraceStack.pop();
        if (currentLayer == 1) {
          locateTextRange();
          (myCurrentLexer = myXmlLexer).start(getBufferSequence(), start + 1, myBufferEnd, myXmlState);
          myTokenType = SCALA_IN_XML_INJECTION_END;
        } else {
          myBraceStack.push(--currentLayer);
        }
      } else if (type == ScalaTokenTypes.tLBRACE && myBraceStack.size() > 0) {
        int currentLayer = myBraceStack.pop();
        myBraceStack.push(++currentLayer);
      } else if ((XML_START_TAG_START == type || XML_COMMENT_START == type
          || XML_CDATA_START == type || XML_PI_START == type) && !myLayeredTagStack.isEmpty()) {
        myLayeredTagStack.peek().push(new MyOpenXmlTag());
      } else if (XML_EMPTY_ELEMENT_END == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 2);
          myTokenType = XML_EMPTY_ELEMENT_END;
        }
      } else if (XML_TAG_END == type && !myLayeredTagStack.isEmpty() && !myLayeredTagStack.peek().isEmpty()) {
        MyOpenXmlTag tag = myLayeredTagStack.peek().peek();
        if (tag.state == TAG_STATE.UNDEFINED) {
          tag.state = TAG_STATE.NONEMPTY;
        } else if (tag.state == TAG_STATE.NONEMPTY) {
          myLayeredTagStack.peek().pop();
        }
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 1);
          myTokenType = XML_TAG_END;
        }
      } else if (XML_PI_END == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 2);
          myTokenType = XML_PI_END;
        }
      } else if (XML_COMMENT_END == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 3);
          myTokenType = XML_COMMENT_END;
        }
      } else if (XML_CDATA_END == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 3);
          myTokenType = XML_CDATA_END;
        }
      } else if (type == XML_DATA_CHARACTERS && tokenText.indexOf('{') != -1) {
        int scalaToken = tokenText.indexOf('{');
        while (scalaToken != -1 && scalaToken + 1 < tokenText.length() && tokenText.charAt(scalaToken + 1) == '{')
          scalaToken = tokenText.indexOf('{', scalaToken + 2);
        if (scalaToken != -1) {
          myTokenType = XML_DATA_CHARACTERS;
          myTokenStart = myCurrentLexer.getTokenStart();
          myTokenEnd = myTokenStart + scalaToken;
          myCurrentLexer.start(getBufferSequence(), myTokenEnd, myBufferEnd, myCurrentLexer.getState());
        }
      } else if ((type == XML_REAL_WHITE_SPACE ||
          type == XML_WHITE_SPACE ||
          type == TAG_WHITE_SPACE) &&
          tokenText.matches("\\s*\n(\n|\\s)*")) {
        type = ScalaTokenTypes.tWHITE_SPACE_IN_LINE;
      }
      if (myTokenType == null) {
        myTokenType = type;
        if (myTokenType == null) return;
        locateTextRange();
      }
      myCurrentLexer.advance();
    }
  }

  private void startScalaPlainLexer(int start) {
    (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd,
        ((ScalaPlainLexer) myScalaPlainLexer).newLineAllowed() ? SCALA_NEW_LINE_ALLOWED_STATE : SCALA_NEW_LINE_DEPRECATED_STATE);
  }

  private void locateTextRange() {
    myTokenStart = myCurrentLexer.getTokenStart();
    myTokenEnd = myCurrentLexer.getTokenEnd();
  }

  private boolean checkNotNextXmlBegin(Lexer lexer) {
    String text = lexer.getBufferSequence().toString();
    int beginIndex = lexer.getTokenEnd();
    if (beginIndex < text.length()) {
      text = text.substring(beginIndex).trim();
      if (text.length() > 2) {
        text = text.substring(0, 2);
      }
      return !text.matches(XML_BEGIN_PATTERN);
    }
    return true;
  }

  public int getTokenStart() {
    locateToken();
    return myTokenStart;
  }

  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  public void advance() {
    myTokenType = null;
  }

  public LexerPosition getCurrentPosition() {
    return new MyPosition(
        myTokenStart,
        myTokenEnd,
        new MyState(
            myXmlState,
            0,
            myBraceStack,
            myCurrentLexer,
            myLayeredTagStack));
  }

  public void restore(LexerPosition position) {
    MyPosition pos = (MyPosition) position;
    myBraceStack = pos.state.braceStack;
    myCurrentLexer = pos.state.currentLexer;
    myTokenStart = pos.getOffset();
    myTokenEnd = pos.end;
    myLayeredTagStack = pos.state.tagStack;
    myCurrentLexer.start(myCurrentLexer.getBufferSequence(), myTokenStart, myBufferEnd,
        myCurrentLexer instanceof XmlLexer ? pos.state.xmlState : 0);
  }

  @Deprecated
  public char[] getBuffer() {
    return myCurrentLexer.getBuffer();
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }


  private static class MyState implements LexerState {

    public TIntStack braceStack;
    public Stack<Stack<MyOpenXmlTag>> tagStack;
    public Lexer currentLexer;
    public int xmlState;
    public int scalaState;


    public MyState(final int xmlState, final int scalaState, TIntStack braceStack, Lexer lexer, Stack<Stack<MyOpenXmlTag>> tagStack) {
      this.braceStack = braceStack;
      this.tagStack = tagStack;
      this.currentLexer = lexer;
      this.xmlState = xmlState;
      this.scalaState = scalaState;
    }

    public short intern() {
      return 0;
    }
  }

  private static class MyPosition implements LexerPosition {
    public int start;
    public int end;
    public MyState state;

    public MyPosition(final int start, final int end, final MyState state) {
      this.start = start;
      this.end = end;
      this.state = state;
    }

    public int getOffset() {
      return start;
    }

    public int getState() {
      return state.currentLexer.getState();
    }
  }

  protected static enum TAG_STATE {
    UNDEFINED, EMPTY, NONEMPTY
  }

  private static class MyOpenXmlTag {
    public TAG_STATE state = TAG_STATE.UNDEFINED;
  }


}
