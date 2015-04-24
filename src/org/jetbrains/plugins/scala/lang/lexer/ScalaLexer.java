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
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.*;


/**
 * @author ilyas
 */
public class ScalaLexer extends Lexer {

  protected final Lexer myScalaPlainLexer;
  private final Lexer myXmlLexer = new ScalaXmlTokenTypes.PatchedXmlLexer();

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
  private boolean inCdata = false;
  protected IElementType myTokenType;
  public final String XML_BEGIN_PATTERN = "<\\w";
  private int xmlSteps = -1;

  public ScalaLexer() {
    this(false);
  }

  public ScalaLexer(boolean treatDocCommentAsBlockComment) {
    myScalaPlainLexer = new ScalaLayeredPlainLexer(treatDocCommentAsBlockComment);
    myCurrentLexer = myScalaPlainLexer;
  }

  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myCurrentLexer = myScalaPlainLexer;
    myCurrentLexer.start(buffer, startOffset, endOffset, initialState & MASK);
    myBraceStack.clear();
    myLayeredTagStack.clear();
    myXmlState = (initialState >> XML_SHIFT) & MASK;
    inCdata = false;
    xmlSteps = -1;
    myBuffer = buffer;
    myBufferStart = startOffset;
    myBufferEnd = endOffset;
    myTokenType = null;
  }

  public int getState() {
    locateToken();
    int state = 0;
    if (myLayeredTagStack.size() > 0) state = 239;
    if (myXmlState != 0) state = 239;
    int scalaState = myScalaPlainLexer.getState();
    if (scalaState != 0) state = 239;
    return state;
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
      if (myCurrentLexer == myXmlLexer && xmlSteps == 0) {
        myCurrentLexer = myScalaPlainLexer;
        myCurrentLexer.start(getBufferSequence(), start, myXmlLexer.getBufferEnd(), 0);
      }

      --xmlSteps;
      if (type == SCALA_XML_CONTENT_START) {
        final XmlTagValidator xmlTagValidator = new XmlTagValidator(myCurrentLexer);
        if (!xmlTagValidator.validate()) {
          xmlSteps = xmlTagValidator.step;
        }

        myCurrentLexer = myXmlLexer;
        myXmlState = 0;
        myCurrentLexer.start(getBufferSequence(), start, myBufferEnd, 0);
        myLayeredTagStack.push(new Stack<MyOpenXmlTag>());
        myLayeredTagStack.peek().push(new MyOpenXmlTag());
        myTokenType = myCurrentLexer.getTokenType();
        locateTextRange();
      } else if ((/*type == XML_ATTRIBUTE_VALUE_TOKEN || */type == ScalaXmlTokenTypes.XML_DATA_CHARACTERS()) && //todo: Dafuq???
          tokenText.startsWith("{") && !tokenText.startsWith("{{") && !inCdata) {
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
      } else if ((ScalaXmlTokenTypes.XML_START_TAG_START() == type || ScalaXmlTokenTypes.XML_COMMENT_START() == type
          || ScalaXmlTokenTypes.XML_CDATA_START() == type || ScalaXmlTokenTypes.XML_PI_START() == type) && !myLayeredTagStack.isEmpty()) {
        if (type == ScalaXmlTokenTypes.XML_CDATA_START()) {
          inCdata = true;
        }
        myLayeredTagStack.peek().push(new MyOpenXmlTag());
      } else if (ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END() == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 2);
          myTokenType = ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END();
          return;
        }
      } else if (ScalaXmlTokenTypes.XML_TAG_END() == type && !myLayeredTagStack.isEmpty() && !myLayeredTagStack.peek().isEmpty()) {
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
          myTokenType = ScalaXmlTokenTypes.XML_TAG_END();
          return;
        }
      } else if (ScalaXmlTokenTypes.XML_PI_END() == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 2);
          myTokenType = ScalaXmlTokenTypes.XML_PI_END();
          return;
        }
      } else if (ScalaXmlTokenTypes.XML_COMMENT_END() == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 3);
          myTokenType = ScalaXmlTokenTypes.XML_COMMENT_END();
          return;
        }
      } else if (ScalaXmlTokenTypes.XML_CDATA_END() == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {
        inCdata = false;
        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          locateTextRange();
          startScalaPlainLexer(start + 3);
          myTokenType = ScalaXmlTokenTypes.XML_CDATA_END();
          return;
        }
      } else if (type == ScalaXmlTokenTypes.XML_DATA_CHARACTERS() && tokenText.indexOf('{') != -1 && !inCdata) {
        int scalaToken = tokenText.indexOf('{');
        while (scalaToken != -1 && scalaToken + 1 < tokenText.length() && tokenText.charAt(scalaToken + 1) == '{')
          scalaToken = tokenText.indexOf('{', scalaToken + 2);
        if (scalaToken != -1) {
          myTokenType = ScalaXmlTokenTypes.XML_DATA_CHARACTERS();
          myTokenStart = myCurrentLexer.getTokenStart();
          myTokenEnd = myTokenStart + scalaToken;
          myCurrentLexer.start(getBufferSequence(), myTokenEnd, myBufferEnd, myCurrentLexer.getState());
        }
      } else if ((type == XmlTokenType.XML_REAL_WHITE_SPACE ||
          type == XmlTokenType.XML_WHITE_SPACE ||
          type == XmlTokenType.TAG_WHITE_SPACE) &&
          tokenText.matches("\\s*\n(\n|\\s)*")) {
        type = ScalaTokenTypes.tWHITE_SPACE_IN_LINE;
      } else if (type == null || !(type instanceof IXmlLeafElementType) && !ScalaXmlTokenTypes.isSubstituted(type.toString())) {
        ++xmlSteps;
      }
      if (myTokenType == null) {
        myTokenType = type;
        if (myTokenType == null) return;
        locateTextRange();
      }
      //we have to advance current lexer only if we didn't start scala plain lexer on this iteration
      //because of wrong behaviour of the latter ScalaPlainLexer
      myCurrentLexer.advance();
    }
  }

  private void startScalaPlainLexer(int start) {
    (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd);
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
    if (myTokenType == null) return myTokenEnd;
    return myTokenStart;
  }

  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  public void advance() {
    myTokenType = null;
  }

  @NotNull
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

  public void restore(@NotNull LexerPosition position) {
    MyPosition pos = (MyPosition) position;
    myBraceStack = pos.state.braceStack;
    myCurrentLexer = pos.state.currentLexer;
    myTokenStart = pos.getOffset();
    myTokenEnd = pos.end;
    myLayeredTagStack = pos.state.tagStack;
    myCurrentLexer.start(myCurrentLexer.getBufferSequence(), myTokenStart, myBufferEnd,
        myCurrentLexer instanceof ScalaXmlTokenTypes.PatchedXmlLexer ? pos.state.xmlState : 0);
  }

  @NotNull
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

  private static class XmlTagValidator {
    final private static List<IElementType> allStopTokens =
        Arrays.<IElementType>asList(ScalaXmlTokenTypes.XML_TAG_END(), ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END(), ScalaXmlTokenTypes.XML_PI_END(),
            ScalaXmlTokenTypes.XML_COMMENT_END(), null, ScalaXmlTokenTypes.XML_START_TAG_START());
    final private static List<IElementType> validStopTokens = Arrays.<IElementType>asList(ScalaXmlTokenTypes.XML_TAG_END(),
        ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END(), ScalaXmlTokenTypes.XML_PI_END(), ScalaXmlTokenTypes.XML_COMMENT_END());

    final private Lexer lexer;
    final private LinkedList<IElementType> xmlTokens;
    final private ScalaXmlTokenTypes.PatchedXmlLexer valLexer;
    private int step = 0;

    private XmlTagValidator(Lexer lexer) {
      this.lexer = lexer;
      xmlTokens = new LinkedList<IElementType>();
      valLexer = new ScalaXmlTokenTypes.PatchedXmlLexer();
    }

    private boolean validate() {
      valLexer.start(lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getBufferEnd(), 0);
      step = 0;

      boolean isCdata = valLexer.getTokenType() == ScalaXmlTokenTypes.XML_CDATA_START();

      advanceLexer();
      step = 1;

      if (!isCdata) {
        while (canProcess()) {
          if (valLexer.getTokenType() == ScalaXmlTokenTypes.XML_DATA_CHARACTERS()) {
              return xmlTokens.peekLast() == ScalaXmlTokenTypes.XML_EQ() && valLexer.getTokenText().startsWith("{") &&
                      !valLexer.getTokenText().startsWith("{{");
          } else {
            advanceLexer();
          }
        }

        return validStopTokens.contains(valLexer.getTokenType());
      }

      if (valLexer.getTokenType() == ScalaXmlTokenTypes.XML_CDATA_END()) return true;
      advanceLexer();
      return valLexer.getTokenType() == ScalaXmlTokenTypes.XML_CDATA_END();
    }

    private void advanceLexer() {
      xmlTokens.addLast(valLexer.getTokenType());
      valLexer.advance();
      ++step;
    }

    private boolean canProcess() {
      if (allStopTokens.contains(valLexer.getTokenType())) return false;

      if (valLexer.getTokenType() == ScalaXmlTokenTypes.XML_NAME()) {
        if (xmlTokens.size() == 1) return true;
        if (xmlTokens.peekLast() != XmlTokenType.XML_WHITE_SPACE) return false;
        xmlTokens.pollLast();

        if (xmlTokens.peekLast() == ScalaXmlTokenTypes.XML_NAME()) {
          return xmlTokens.size() == 2;
        }

        return xmlTokens.peekLast() == ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER();
      } else {
        return true;
      }
    }
  }
}
