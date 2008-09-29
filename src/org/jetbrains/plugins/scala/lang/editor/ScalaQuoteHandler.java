/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.scala.lang.editor;

import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class ScalaQuoteHandler implements JavaLikeQuoteHandler {

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == ScalaTokenTypes.tSTRING ||
        tokenType == ScalaTokenTypes.tCHAR) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }
    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == ScalaTokenTypes.tWRONG_STRING) {
      int start = iterator.getStart();
      return offset == start;
    }
    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return true;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();
    return tokenType == ScalaTokenTypes.tSTRING ||
        tokenType == ScalaTokenTypes.tCHAR;
  }

  public TokenSet getConcatenatableStringTokenTypes() {
    return TokenSet.create(ScalaTokenTypes.tSTRING);
  }

  public String getStringConcatenationOperatorRepresentation() {
    return "+";
  }

  public TokenSet getStringTokenTypes() {
    return TokenSet.create(ScalaTokenTypes.tSTRING);
  }

  public boolean isAppropriateElementTypeForLiteral(@NotNull IElementType tokenType) {
    return tokenType == ScalaTokenTypes.tSEMICOLON
        || tokenType == ScalaTokenTypes.tCOMMA
        || tokenType == ScalaTokenTypes.tRPARENTHESIS
        || tokenType == ScalaTokenTypes.tRSQBRACKET
        || tokenType == ScalaTokenTypes.tRBRACE
        || tokenType == ScalaTokenTypes.tSTRING
        || tokenType == ScalaTokenTypes.tCHAR
        || ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(tokenType)
        || ScalaTokenTypes.WHITES_SPACES_TOKEN_SET.contains(tokenType);
  }

  public boolean needParenthesesAroundConcatenation(PsiElement element) {
    return element.getParent() instanceof ScLiteral && element.getParent().getParent() instanceof ScReferenceExpression;
  }
}