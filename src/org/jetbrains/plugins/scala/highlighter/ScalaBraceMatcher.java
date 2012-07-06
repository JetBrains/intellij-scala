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

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
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
      new BracePair(ScalaDocElementTypes.DOC_LINK_TAG, ScalaDocElementTypes.DOC_LINK_CLOSE_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_HTTP_LINK_TAG, ScalaDocElementTypes.DOC_LINK_CLOSE_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_INNER_CODE_TAG, ScalaDocElementTypes.DOC_INNER_CLOSE_CODE_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_UNDERLINE_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_BOLD_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_ITALIC_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_MONOSPACE_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_SUBSCRIPT_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaDocElementTypes.DOC_SUPERSCRIPT_TAG, ScalaDocElementTypes.DOC_COMMON_CLOSE_WIKI_TAG, true),
      new BracePair(ScalaTokenTypes.tOPENXMLTAG, ScalaTokenTypes.tCLOSEXMLTAG, true), 
      new BracePair(ScalaTokenTypes.tXML_COMMENT_START, ScalaTokenTypes.tXML_COMMENT_END, true)
  };

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
