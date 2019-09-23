/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.scala.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression;

import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.*;

/**
 * @author ilyas
 */
public class ScalaQuoteHandler implements JavaLikeQuoteHandler {

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    return (tokenType == tSTRING ||
            tokenType == tCHAR ||
            tokenType == tINTERPOLATED_STRING_END) &&
            offset - iterator.getStart() >= 0 &&
            offset == iterator.getEnd() - 1;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    return (tokenType == tWRONG_STRING ||
            tokenType == tINTERPOLATED_STRING) &&
            offset == iterator.getStart();
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return true;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();

    return tokenType == tSTRING ||
        tokenType == tCHAR ||
        tokenType == tMULTILINE_STRING ||
        tokenType == tINTERPOLATED_STRING;
  }

  @NotNull
  public TokenSet getConcatenatableStringTokenTypes() {
    return TokenSet.create(tSTRING);
  }

  public String getStringConcatenationOperatorRepresentation() {
    return "+";
  }

  public TokenSet getStringTokenTypes() {
    return TokenSet.create(tSTRING, tINTERPOLATED_STRING);
  }

  public boolean isAppropriateElementTypeForLiteral(@NotNull IElementType tokenType) {
    return tokenType == tSEMICOLON
        || tokenType == tCOMMA
        || tokenType == tRPARENTHESIS
        || tokenType == tRSQBRACKET
        || tokenType == tRBRACE
        || tokenType == tSTRING
        || tokenType == tCHAR
        || COMMENTS_TOKEN_SET.contains(tokenType)
        || WHITES_SPACES_TOKEN_SET.contains(tokenType);
  }

  public boolean needParenthesesAroundConcatenation(PsiElement element) {
    PsiElement parent = element.getParent();
    return parent instanceof ScLiteral &&
            parent.getParent() instanceof ScReferenceExpression;
  }
}