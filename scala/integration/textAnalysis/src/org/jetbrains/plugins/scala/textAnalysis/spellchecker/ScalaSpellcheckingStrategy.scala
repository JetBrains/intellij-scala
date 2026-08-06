package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement}
import com.intellij.spellchecker.inspections.{PlainTextSplitter, Splitter}
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.EMPTY_TOKENIZER as emptyTokenizer
import com.intellij.spellchecker.tokenizer.{SpellcheckingStrategy, TokenConsumer, Tokenizer, TokenizerBase}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

final class ScalaSpellcheckingStrategy extends SpellcheckingStrategy {
  private val myStringLiteralExpressionTokenizer: ScLiteralExpressionTokenizer = new ScLiteralExpressionTokenizer
  private val myDocCommentTokenizer: ScalaDocCommentTokenizer = new ScalaDocCommentTokenizer
  private val codeTokenizer: Tokenizer[PsiElement] = new TokenizerBase[PsiElement](PlainTextSplitter.getInstance()) {
    override def consumeToken(element: PsiElement, consumer: TokenConsumer, splitter: Splitter): Unit = {
      consumer.consumeToken(element, true, splitter)
    }
  }

  override def getTokenizer(element: PsiElement): Tokenizer[? <: PsiElement] =
    if useTextLevelSpellchecking() && element.is[PsiComment, ScLiteral] then emptyTokenizer
    else element match {
      case _: ScStringLiteral => myStringLiteralExpressionTokenizer
      case _: ScDocComment => myDocCommentTokenizer
      case _: PsiComment => super.getTokenizer(element)
      case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        leaf.getParent match {
          case _: ScReference => emptyTokenizer
          case param: ScParameter =>
            param.owner match {
              case owner: ScModifierListOwner => getTokenizer(owner)
              case _ => codeTokenizer
            }
          case owner: ScModifierListOwner => getTokenizer(owner)
          case pattern: ScBindingPattern =>
            pattern.nameContext match {
              case valOrVar: ScValueOrVariable => getTokenizer(valOrVar)
              case _ => codeTokenizer
            }
          case _ => codeTokenizer
        }
      case _ => emptyTokenizer
    }

  override def useTextLevelSpellchecking(): Boolean = Registry.is("spellchecker.grazie.enabled", false)

  private def getTokenizer(owner: ScModifierListOwner): Tokenizer[? <: PsiElement] =
    if owner.hasModifierPropertyScala("override") then emptyTokenizer else codeTokenizer

  override def isMyContext(element: PsiElement): Boolean = {
    val file = element.getContainingFile
    val project = if (file != null) file.getProject else element.getProject // Avoid tree walk-up

    element.isVisible(project, file)
  }
}
