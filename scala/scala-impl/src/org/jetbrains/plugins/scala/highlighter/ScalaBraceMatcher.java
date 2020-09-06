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

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

/**
 * @author ilyas
 *         Date: 29.09.2006
 *         Time: 20:26:52
 */
public class ScalaBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
      new BracePair(ScalaTokenTypes.tLPARENTHESIS, ScalaTokenTypes.tRPARENTHESIS, false),
      new BracePair(ScalaTokenTypes.tLSQBRACKET, ScalaTokenTypes.tRSQBRACKET, false),
      new BracePair(ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE, true),
      new BracePair(ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, true),
      new BracePair(ScalaDocTokenType.DOC_LINK_TAG, ScalaDocTokenType.DOC_LINK_CLOSE_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_HTTP_LINK_TAG, ScalaDocTokenType.DOC_LINK_CLOSE_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_INNER_CODE_TAG, ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_UNDERLINE_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_BOLD_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_ITALIC_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_MONOSPACE_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_SUBSCRIPT_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaDocTokenType.DOC_SUPERSCRIPT_TAG, ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG, false),
      new BracePair(ScalaTokenTypes.tOPENXMLTAG, ScalaTokenTypes.tCLOSEXMLTAG, true), 
      new BracePair(ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER(), ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER(), false),
      new BracePair(ScalaTokenTypes.tXML_COMMENT_START, ScalaTokenTypes.tXML_COMMENT_END, false)
  };

  @NotNull
  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType type, @Nullable IElementType tokenType) {
    return tokenType == null
        || ScalaTokenTypes.tWHITE_SPACE_IN_LINE == tokenType
        || tokenType == ScalaTokenTypes.tSEMICOLON
        || tokenType == ScalaTokenTypes.tLINE_COMMENT
        || tokenType == ScalaDocElementTypes.SCALA_DOC_COMMENT
        || tokenType == ScalaTokenTypes.tBLOCK_COMMENT
        || tokenType == ScalaTokenTypes.tCOMMA
        || tokenType == ScalaTokenTypes.tRPARENTHESIS
        || tokenType == ScalaTokenTypes.tRSQBRACKET
        || tokenType == ScalaTokenTypes.tRBRACE;
  }

  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
