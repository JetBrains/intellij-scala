//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractInlineMethodCompletionCommandProvider
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScTypeAliasDefinition}

/**
 * Technically, it is not only about "Inline Method", but rather a more general [[com.intellij.refactoring.actions.InlineAction]] action.
 *
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineActionHandler]]
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.inline.method.ScalaInlineMethodHandler]]
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias.ScalaInlineTypeAliasHandler]]
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.inline.variable.ScalaInlineVariableHandler]]
 */
final class ScalaInlineCompletionCommandProvider extends AbstractInlineMethodCompletionCommandProvider {
  override def findOffsetToCall(offset: Int, psiFile: PsiFile): Integer = {
    val element = getNonWhitespaceCommandContext(psiFile, offset)
    if (element == null || !element.isWritable) null
    else {
      val elementType = element.elementType
      elementType match {
        case ScalaTokenTypes.tIDENTIFIER =>
          findOffsetForIdentifier(element)
        case ScalaTokenTypes.tRBRACE =>
          findOffsetForClosingBrace(element)
        case ScalaTokenTypes.tRPARENTHESIS =>
          findOffsetForClosingParen(element)
        case ScalaTokenTypes.tRSQBRACKET =>
          findOffsetForClosingBracket(element)
        case _ => null
      }
    }
  }

  @Nullable
  private def findOffsetForIdentifier(element: PsiElement): Integer = {
    def isApplicable(@Nullable e: PsiElement): Boolean = e match {
      case _: ScFunctionDefinition => true
      case _: ScBindingPattern => true
      case _: ScTypeAliasDefinition => true
      case _ => false
    }

    val applicable = element.getParent match {
      case ref: ScReference =>
        val resolved = ref.resolve()
        isApplicable(resolved)
      case parent => isApplicable(parent)
    }
    if (applicable) element.endOffset else null
  }

  @Nullable
  private def findOffsetForClosingBrace(element: PsiElement): Integer = element.getParent match {
    case block: ScBlockExpr =>
      block.getParent match {
        case fn: ScFunctionDefinition =>
          endOffset(fn.nameId)
        case args: ScArgumentExprList =>
          args.callReference.map(endOffset).orNull
        case _ => null
      }
    case _ => null
  }

  @Nullable
  private def findOffsetForClosingParen(element: PsiElement): Integer = element.getParent match {
    case args: ScArgumentExprList =>
      args.callReference.map(endOffset).orNull
    case _ => null
  }

  @Nullable
  private def findOffsetForClosingBracket(element: PsiElement): Integer = element.getParent match {
    case (_: ScTypeArgs) & Parent(genCall: ScGenericCall) =>
      endOffset(genCall.referencedExpr)
    case _ => null
  }

  @Nullable
  private def endOffset(@Nullable element: PsiElement): Integer =
    if (element == null) null
    else element.endOffset
}
