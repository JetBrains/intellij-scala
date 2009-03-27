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

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import static com.intellij.psi.xml.XmlTokenType.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.SCALA_XML_CONTENT;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public class ScalaSyntaxHighlighter extends SyntaxHighlighterBase {

  // Comments
  static final TokenSet tLINE_COMMENTS = TokenSet.create(
          ScalaTokenTypes.tLINE_COMMENT
  );

  static final TokenSet tBLOCK_COMMENTS = TokenSet.create(
          ScalaTokenTypes.tBLOCK_COMMENT, ScalaTokenTypes.tSH_COMMENT
  );

  static final TokenSet tDOC_COMMENTS = TokenSet.create(
          ScalaDocElementTypes.SCALA_DOC_COMMENT
  );

  // XML tags
  static final TokenSet tXML_TAGS = TokenSet.create(
          ScalaTokenTypes.tOPENXMLTAG
  );

  // Variables
  static final TokenSet tVARIABLES = TokenSet.create(
          ScalaTokenTypes.tIDENTIFIER
  );

  // Numbers
  static final TokenSet tNUMBERS = TokenSet.create(
          ScalaTokenTypes.tINTEGER,
          ScalaTokenTypes.tFLOAT
  );

  // Braces
  static final TokenSet tBRACES = TokenSet.create(
          ScalaTokenTypes.tLBRACE,
          ScalaTokenTypes.tRBRACE
  );

  // Brackets
  static final TokenSet tBRACKETS = TokenSet.create(
          ScalaTokenTypes.tLSQBRACKET,
          ScalaTokenTypes.tRSQBRACKET
  );

  // Parentheses
  static final TokenSet tPARENTESES = TokenSet.create(
          ScalaTokenTypes.tLPARENTHESIS,
          ScalaTokenTypes.tRPARENTHESIS
  );


  // Strings
  static final TokenSet tSTRINGS = TokenSet.create(
          ScalaTokenTypes.tSTRING,
          ScalaTokenTypes.tMULTILINE_STRING,
          ScalaTokenTypes.tWRONG_STRING,
          ScalaTokenTypes.tCHAR,
          ScalaTokenTypes.tSYMBOL
  );

  // Keywords
  public static final TokenSet kRESWORDS = TokenSet.create(
          ScalaTokenTypes.kABSTRACT,
          ScalaTokenTypes.kCASE,
          ScalaTokenTypes.kCATCH,
          ScalaTokenTypes.kCLASS,
          ScalaTokenTypes.kDEF,
          ScalaTokenTypes.kDO,
          ScalaTokenTypes.kELSE,
          ScalaTokenTypes.kEXTENDS,
          ScalaTokenTypes.kFALSE,
          ScalaTokenTypes.kFINAL,
          ScalaTokenTypes.kFINALLY,
          ScalaTokenTypes.kFOR,
          ScalaTokenTypes.kFOR_SOME,
          ScalaTokenTypes.kIF,
          ScalaTokenTypes.kIMPLICIT,
          ScalaTokenTypes.kIMPORT,
          ScalaTokenTypes.kLAZY,
          ScalaTokenTypes.kMATCH,
          ScalaTokenTypes.kNEW,
          ScalaTokenTypes.kNULL,
          ScalaTokenTypes.kOBJECT,
          ScalaTokenTypes.kOVERRIDE,
          ScalaTokenTypes.kPACKAGE,
          ScalaTokenTypes.kPRIVATE,
          ScalaTokenTypes.kPROTECTED,
          ScalaTokenTypes.kREQUIRES,
          ScalaTokenTypes.kRETURN,
          ScalaTokenTypes.kSEALED,
          ScalaTokenTypes.kSUPER,
          ScalaTokenTypes.kTHIS,
          ScalaTokenTypes.kTHROW,
          ScalaTokenTypes.kTRAIT,
          ScalaTokenTypes.kTRY,
          ScalaTokenTypes.kTRUE,
          ScalaTokenTypes.kTYPE,
          ScalaTokenTypes.kVAL,
          ScalaTokenTypes.kVAR,
          ScalaTokenTypes.kWHILE,
          ScalaTokenTypes.kWITH,
          ScalaTokenTypes.kYIELD
  );

  static final TokenSet tOPS = TokenSet.create(
          ScalaTokenTypes.tASSIGN
  );

  static final TokenSet tSEMICOLON = TokenSet.create(
          ScalaTokenTypes.tSEMICOLON
  );

  static final TokenSet tDOT = TokenSet.create(
          ScalaTokenTypes.tDOT
  );

  static final TokenSet tCOMMA = TokenSet.create(
          ScalaTokenTypes.tCOMMA
  );

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

  static {
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tLINE_COMMENTS, DefaultHighlighter.LINE_COMMENT);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tBLOCK_COMMENTS, DefaultHighlighter.BLOCK_COMMENT);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tDOC_COMMENTS, DefaultHighlighter.DOC_COMMENT);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, kRESWORDS, DefaultHighlighter.KEYWORD);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tBRACKETS, DefaultHighlighter.BRACKETS);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tPARENTESES, DefaultHighlighter.PARENTHESES);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tSEMICOLON, DefaultHighlighter.SEMICOLON);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tDOT, DefaultHighlighter.DOT);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tCOMMA, DefaultHighlighter.COMMA);

    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tOPS, DefaultHighlighter.ASSIGN);
    SyntaxHighlighterBase.fillMap(ATTRIBUTES, tXML_TAGS, DefaultHighlighter.ASSIGN);

  }


  @NotNull
  public Lexer getHighlightingLexer() {
    return new ScalaLexer() {
      public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myCurrentLexer = myScalaPlainLexer;
        myCurrentLexer.start(buffer, startOffset, endOffset, initialState);
        myBraceStack.clear();
        myLayeredTagStack.clear();
        myXmlState = 0;
        myBuffer = buffer;
        myBufferEnd = buffer.length();
        myTokenType = null;
      }

      public IElementType getTokenType() {
        IElementType type = super.getTokenType();
        if (type instanceof IXmlLeafElementType ||
                XML_WHITE_SPACE == type ||
                type == XML_REAL_WHITE_SPACE ||
                type == TAG_WHITE_SPACE) {
          return SCALA_XML_CONTENT;
        }
        return type;
      }
    };
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType iElementType) {
    return pack(ATTRIBUTES.get(iElementType));
  }
}
