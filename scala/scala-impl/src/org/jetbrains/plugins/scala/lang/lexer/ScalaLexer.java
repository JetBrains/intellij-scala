/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class ScalaLexer extends Lexer {

  private static final String XML_BEGIN_PATTERN = "<\\w";
  private static final int MASK = 0x3F;
  private static final int XML_SHIFT = 6;

  private static final java.util.regex.Pattern LINE_BREAK_PATTERN = java.util.regex.Pattern.compile("\\s*\n\\s*");

  private final ScalaPlainLexer myScalaPlainLexer;
  private final ScalaXmlLexer myXmlLexer;

  private Lexer myCurrentLexer;

  private TIntStack myBraceStack = new TIntStack();
  private Stack<Stack<MyOpenXmlTag>> myLayeredTagStack = new Stack<>();

  private int myBufferEnd;
  private CharSequence myBuffer;
  private int myXmlState;
  private int myTokenStart;
  private int myTokenEnd;
  private boolean inCdata = false;
  private IElementType myTokenType;
  private int xmlSteps = -1;

  /* We need to store it as in some cases (e.g. when we have uninterrupted xml elements sequence like '<a></a>')
   * when the last xml element was located in 'locateToken()' there is no way to determine from xml state/xml tags stack
   * that the lexer was inside xml. That means the lexer isn't able to patch state => the state can be == 0 =>
   * any incremental stuff (highlighting etc) can start lexical analysis from this position with 0 state => we get wrong token sequence
   */
  private IElementType previousToken = null;

  public ScalaLexer(boolean isScala3,
                    @Nullable Project project) {
    myScalaPlainLexer = new ScalaPlainLexer(
        isScala3,
        project != null && ScalaProjectSettings.getInstance(project).isTreatDocCommentAsBlockComment()
    );
    myXmlLexer = new ScalaXmlLexer();
    myCurrentLexer = myScalaPlainLexer;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myCurrentLexer = myScalaPlainLexer;
    myCurrentLexer.start(buffer, startOffset, endOffset, initialState & MASK);
    myBraceStack.clear();
    myLayeredTagStack.clear();
    myXmlState = (initialState >> XML_SHIFT) & MASK;
    inCdata = false;
    xmlSteps = -1;
    myBuffer = buffer;
    myBufferEnd = endOffset;
    myTokenType = null;
  }

  @Override
  public int getState() {
    locateToken();
    int state = 0;
    if (myLayeredTagStack.size() > 0) state = 239;
    if (myXmlState != 0 || isXmlTokenType(previousToken)) state = 239;
    int scalaState = myScalaPlainLexer.getState();
    if (scalaState != 0) state = 239;
    // work-around for the strange advance()-related assumption / behavior in locateToken()
    if (myTokenStart == 0) return 0;
    return state;
  }

  @Override
  @Nullable
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  private void locateToken() {
    previousToken = myTokenType;

    if (myTokenType == null) {
      doLocateToken();
    }
  }

  private void doLocateToken() {
    assert myTokenType == null;

    final IElementType type = myCurrentLexer.getTokenType();
    final int start = myCurrentLexer.getTokenStart();
    final int end = myCurrentLexer.getTokenEnd();
    final CharSequence tokenText = myCurrentLexer.getBufferSequence().subSequence(start, end);

    boolean xmlLexerFinishedProcessing = myCurrentLexer == myXmlLexer && xmlSteps == 0;
    if (xmlLexerFinishedProcessing) {
      myCurrentLexer = myScalaPlainLexer;
      myCurrentLexer.start(getBufferSequence(), start, myXmlLexer.getBufferEnd(), 0);
    }

    boolean isLineBreakInsideXml = (isWhiteSpaceInsideXml(type)) && isLineBreakText(tokenText);
    if (type == null) return;

    boolean isXmlToken = type instanceof IXmlLeafElementType ||
        ScalaXmlLexer.ScalaXmlTokenType$.MODULE$.unapply(type) ||
        type == ScalaTokenTypesEx.SCALA_XML_CONTENT_START;

    boolean isInsideXmlInjection = myBraceStack.size() > 0;

    final boolean handled;
    if (isXmlToken || isInsideXmlInjection || isLineBreakInsideXml) {
      LocateXmlTokenResult res = doLocateTokenInsideXml(start, type, tokenText);
      switch (res) {
        case STARTED_SCALA_PLAIN_LEXER:
          handled = true;
          break;
        case ADVANCE:
        default:
          handled = false;
          break;
      }
    }
    else handled = false;

    if (!handled) {
      if (myTokenType == null) {
        myTokenType = isLineBreakInsideXml ? ScalaTokenTypes.tWHITE_SPACE_IN_LINE : type;
        locateTextRange();
      }

      //we have to advance current lexer only if we didn't start scala plain lexer on this iteration
      //because of wrong behaviour of the latter ScalaPlainLexer
      myCurrentLexer.advance();
    }

    //System.out.printf("token: (%d->%d) %d - %d  %s | %s%n", xmlSteps, xmlStepsBefore, myTokenStart, myTokenEnd, type0, myTokenType);
  }

  private enum LocateXmlTokenResult {
    STARTED_SCALA_PLAIN_LEXER, ADVANCE
  }

  private LocateXmlTokenResult doLocateTokenInsideXml(
      final int start,
      @Nullable final IElementType type,
      @NotNull final CharSequence tokenText
  ) {
    --xmlSteps;

    if (type == ScalaTokenTypesEx.SCALA_XML_CONTENT_START) {
      final XmlTagValidator xmlTagValidator = new XmlTagValidator(myCurrentLexer);
      if (!xmlTagValidator.validate()) {
        xmlSteps = xmlTagValidator.step;
      }

      myCurrentLexer = myXmlLexer;
      myXmlState = 0;
      myCurrentLexer.start(getBufferSequence(), start, myBufferEnd, 0);
      myLayeredTagStack.push(new Stack<>());
      myLayeredTagStack.peek().push(new MyOpenXmlTag());
      myTokenType = myCurrentLexer.getTokenType();
      locateTextRange();
    }
    else if ((/*type == XML_ATTRIBUTE_VALUE_TOKEN || */
        type == ScalaXmlTokenTypes.XML_DATA_CHARACTERS()) && //todo: Dafuq???
        // in xmlLexer injection start `{` is represented by XML_DATA_CHARACTERS
        // (<ul>{ {<li></li>} }</ul>).toString == <ul><li></li></ul>
        // (<ul>{{<li></li>}}</ul>).toString == <ul>{<li></li>}</ul>
        startsWith(tokenText, "{") &&
        !startsWith(tokenText, "{{") &&
        !inCdata
    ) {
      myXmlState = myCurrentLexer.getState();
      myCurrentLexer = myScalaPlainLexer;
      myCurrentLexer.start(getBufferSequence(), start, myBufferEnd, 0);
      locateTextRange();
      myBraceStack.push(1);
      myTokenType = ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START;
    }
    else if (type == ScalaTokenTypes.tRBRACE) {
      int currentLayer = myBraceStack.pop();
      if (currentLayer == 1) {
        locateTextRange();
        myCurrentLexer = myXmlLexer;
        myXmlLexer.start(getBufferSequence(), start + 1, myBufferEnd, myXmlState);
        myTokenType = ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END;
      } else {
        myBraceStack.push(--currentLayer);
      }
    }
    else if (type == ScalaTokenTypes.tLBRACE) {
      int currentLayer = myBraceStack.pop();
      myBraceStack.push(++currentLayer);
    }
    else if ((ScalaXmlTokenTypes.XML_START_TAG_START() == type ||
        ScalaXmlTokenTypes.XML_COMMENT_START() == type ||
        ScalaXmlTokenTypes.XML_CDATA_START() == type ||
        ScalaXmlTokenTypes.XML_PI_START() == type) &&
        !myLayeredTagStack.isEmpty()
    ) {
      if (type == ScalaXmlTokenTypes.XML_CDATA_START()) {
        inCdata = true;
      }
      myLayeredTagStack.peek().push(new MyOpenXmlTag());
    }
    else if (ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END() == type &&
        !myLayeredTagStack.isEmpty() &&
        !myLayeredTagStack.peek().isEmpty() &&
        myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED) {

      myLayeredTagStack.peek().pop();
      if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
        myLayeredTagStack.pop();
        locateTextRange();
        myTokenType = ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END();
        startScalaPlainLexer(start + 2);
        return LocateXmlTokenResult.STARTED_SCALA_PLAIN_LEXER;
      }
    }
    else if (ScalaXmlTokenTypes.XML_TAG_END() == type &&
        !myLayeredTagStack.isEmpty() &&
        !myLayeredTagStack.peek().isEmpty()
    ) {
      MyOpenXmlTag tag = myLayeredTagStack.peek().peek();
      if (tag.state == TAG_STATE.UNDEFINED) {
        tag.state = TAG_STATE.NONEMPTY;
      }
      else if (tag.state == TAG_STATE.NONEMPTY) {
        myLayeredTagStack.peek().pop();
      }
      if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
        myLayeredTagStack.pop();
        locateTextRange();
        myTokenType = ScalaXmlTokenTypes.XML_TAG_END();
        startScalaPlainLexer(start + 1);
        return LocateXmlTokenResult.STARTED_SCALA_PLAIN_LEXER;
      }
    }
    else if (ScalaXmlTokenTypes.XML_PI_END() == type &&
        !myLayeredTagStack.isEmpty() &&
        !myLayeredTagStack.peek().isEmpty() &&
        myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED
    ) {

      myLayeredTagStack.peek().pop();
      if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
        myLayeredTagStack.pop();
        locateTextRange();
        myTokenType = ScalaXmlTokenTypes.XML_PI_END();
        startScalaPlainLexer(start + 2);
        return LocateXmlTokenResult.STARTED_SCALA_PLAIN_LEXER;
      }
    }
    else if (ScalaXmlTokenTypes.XML_COMMENT_END() == type &&
        !myLayeredTagStack.isEmpty() &&
        !myLayeredTagStack.peek().isEmpty() &&
        myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED
    ) {

      myLayeredTagStack.peek().pop();
      if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
        myLayeredTagStack.pop();
        locateTextRange();
        myTokenType = ScalaXmlTokenTypes.XML_COMMENT_END();
        startScalaPlainLexer(start + 3);
        return LocateXmlTokenResult.STARTED_SCALA_PLAIN_LEXER;
      }
    }
    else if (ScalaXmlTokenTypes.XML_CDATA_END() == type &&
        !myLayeredTagStack.isEmpty() &&
        !myLayeredTagStack.peek().isEmpty() &&
        myLayeredTagStack.peek().peek().state == TAG_STATE.UNDEFINED
    ) {
      inCdata = false;
      myLayeredTagStack.peek().pop();
      if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
        myLayeredTagStack.pop();
        locateTextRange();
        myTokenType = ScalaXmlTokenTypes.XML_CDATA_END();
        startScalaPlainLexer(start + 3);
        return LocateXmlTokenResult.STARTED_SCALA_PLAIN_LEXER;
      }
    }
    else if (type == ScalaXmlTokenTypes.XML_DATA_CHARACTERS() &&
        CharArrayUtil.indexOf(tokenText, "{", 0) != -1 && !inCdata
    ) {
      int scalaToken = CharArrayUtil.indexOf(tokenText, "{", 0);
      while (scalaToken != -1 && scalaToken + 1 < tokenText.length() && tokenText.charAt(scalaToken + 1) == '{')
        scalaToken = CharArrayUtil.indexOf(tokenText, "{", scalaToken + 2);
      if (scalaToken != -1) {
        myTokenType = ScalaXmlTokenTypes.XML_DATA_CHARACTERS();
        myTokenStart = myCurrentLexer.getTokenStart();
        myTokenEnd = myTokenStart + scalaToken;
        myCurrentLexer.start(getBufferSequence(), myTokenEnd, myBufferEnd, myCurrentLexer.getState());
      }
    }

    return LocateXmlTokenResult.ADVANCE;
  }

  private static boolean isWhiteSpaceInsideXml(IElementType type) {
    return type == XmlTokenType.XML_REAL_WHITE_SPACE ||
        // NOTE!!!  it's not instance of IXmlLeafElementType, it refers to com.intellij.psi.TokenType.WHITE_SPACE
        type == XmlTokenType.XML_WHITE_SPACE ||
        type == XmlTokenType.TAG_WHITE_SPACE;
  }

  private static boolean isLineBreakText(CharSequence tokenText) {
    return LINE_BREAK_PATTERN.matcher(tokenText).matches();
  }

  private static boolean startsWith(CharSequence chars, String prefix) {
    int i = 0;
    int charsLength = chars.length();
    int prefixLength = prefix.length();

    if (prefixLength > charsLength)
      return false;

    while (i < prefixLength) {
      if (chars.charAt(i) != prefix.charAt(i))
        return false;
      i++;
    }
    return true;
  }

  private void startScalaPlainLexer(int start) {
    myCurrentLexer = myScalaPlainLexer;
    myCurrentLexer.start(getBufferSequence(), start, myBufferEnd);
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

  @Override
  public int getTokenStart() {
    locateToken();
    if (myTokenType == null) return myTokenEnd;
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  @Override
  public void advance() {
    myTokenType = null;
  }

  @Override
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

  @Override
  public void restore(@NotNull LexerPosition position) {
    MyPosition pos = (MyPosition) position;
    myBraceStack = pos.state.braceStack;
    myCurrentLexer = pos.state.currentLexer;
    myTokenStart = pos.getOffset();
    myTokenEnd = pos.end;
    myLayeredTagStack = pos.state.tagStack;
    myCurrentLexer.start(
        myCurrentLexer.getBufferSequence(),
        myTokenStart,
        myBufferEnd,
        myCurrentLexer instanceof ScalaXmlLexer ? pos.state.xmlState : 0
    );
  }

  @Override
  @NotNull
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  private boolean isXmlTokenType(IElementType tpe) {
    return tpe != null && ScalaXmlTokenTypes.XML_ELEMENTS().contains(tpe);
  }

  private static class MyState {

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

    @Override
    public int getOffset() {
      return start;
    }

    @Override
    public int getState() {
      return state.currentLexer.getState();
    }
  }

  private enum TAG_STATE {
    UNDEFINED, EMPTY, NONEMPTY
  }

  private static class MyOpenXmlTag {
    public TAG_STATE state = TAG_STATE.UNDEFINED;
  }

  private static class XmlTagValidator {
    final private static List<IElementType> allStopTokens =
        Arrays.asList(ScalaXmlTokenTypes.XML_TAG_END(), ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END(), ScalaXmlTokenTypes.XML_PI_END(),
            ScalaXmlTokenTypes.XML_COMMENT_END(), null, ScalaXmlTokenTypes.XML_START_TAG_START());
    final private static List<IElementType> validStopTokens = Arrays.asList(ScalaXmlTokenTypes.XML_TAG_END(),
        ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END(), ScalaXmlTokenTypes.XML_PI_END(), ScalaXmlTokenTypes.XML_COMMENT_END());

    final private Lexer lexer;
    final private LinkedList<IElementType> xmlTokens;
    final private ScalaXmlLexer valLexer;
    private int step = 0;

    private XmlTagValidator(Lexer lexer) {
      this.lexer = lexer;
      xmlTokens = new LinkedList<>();
      valLexer = new ScalaXmlLexer();
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
