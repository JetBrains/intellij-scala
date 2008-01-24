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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaFlexLexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 14:52:13
 */
public class ScalaSyntaxHighlighter extends SyntaxHighlighterBase {

  // Comments
  static final TokenSet tCOMMENTS = TokenSet.create(
    ScalaTokenTypes.tCOMMENT,
    ScalaTokenTypes.tBLOCK_COMMENT,
    // New
    ScalaTokenTypes.tCOMMENT_BEGIN,
    ScalaTokenTypes.tCOMMENT_END,
    ScalaTokenTypes.tDOC_COMMENT_BEGIN,
    ScalaTokenTypes.tDOC_COMMENT_END,
    ScalaTokenTypes.tCOMMENT_CONTENT
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
    ScalaTokenTypes.tRBRACE,
    ScalaTokenTypes.tLPARENTHESIS,
    ScalaTokenTypes.tRPARENTHESIS,
    ScalaTokenTypes.tLSQBRACKET,
    ScalaTokenTypes.tRSQBRACKET
  );

  // Strings
  static final TokenSet tSTRINGS = TokenSet.create(
    ScalaTokenTypes.tSTRING,
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
    ScalaTokenTypes.tASSIGN,
    ScalaTokenTypes.tDIV,
    ScalaTokenTypes.tMINUS,
    ScalaTokenTypes.tPLUS,
    ScalaTokenTypes.tSTAR
  );

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

  static {
    fillMap(ATTRIBUTES, tCOMMENTS, DefaultHighlighter.LINE_COMMENT);
    fillMap(ATTRIBUTES, kRESWORDS, DefaultHighlighter.KEYWORD);
    fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER);
    fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACKETS);

    fillMap(ATTRIBUTES, tOPS, DefaultHighlighter.OPERATION_SIGN);
    fillMap(ATTRIBUTES, tXML_TAGS, DefaultHighlighter.OPERATION_SIGN);

//        ATTRIBUTES.put(ScalaTokenTypes.tBAD_CHARACTER, DefaultHighliter.BAD_CHARACTER);

  }


  @NotNull
  public Lexer getHighlightingLexer() {
    return new ScalaFlexLexer();
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType iElementType) {
    return pack(ATTRIBUTES.get(iElementType));
  }
}
