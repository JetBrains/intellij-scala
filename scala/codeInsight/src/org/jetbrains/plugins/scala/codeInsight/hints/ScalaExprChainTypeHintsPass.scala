package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaExprChainTypeHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression

import scala.annotation.tailrec

private[codeInsight] trait ScalaExprChainTypeHintsPass {
  private val settings = ScalaCodeInsightSettings.getInstance
  import settings._

  def collectExpressionChainTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (editor.isOneLineMode || !showExpressionChainType) Seq.empty
    else (
      for {
        ExprChain(exprChain) <- root.elements
        if exprChain.length >= 3

        exprsAtLineEnd = exprChain.filter(isFollowedByLineEnd)
        if exprsAtLineEnd.length >= 2

        exprs =
          if (Expression(exprsAtLineEnd.head).hasStableType) exprsAtLineEnd.tail
          else exprsAtLineEnd

        types = exprs
          .map(e => e.`type`())
          .takeWhile {
            _.isRight
          }
          .map(_.right.get)
        if types.toSet.size >= 2

        document = editor.getDocument
        (longestExpr, maxLineWidth) = exprs.map(expr => expr -> getOffsetInLine(expr.getTextRange.getEndOffset, document)).maxBy(_._2)
        longestExprEndOffset = longestExpr.getTextRange.getEndOffset
        longestLine = " " + editor.getDocument.getCharsSequence.substring(longestExprEndOffset - maxLineWidth, longestExprEndOffset).reverse

        (expr, ty) <-
          if (showIdenticalTypeInExpressionChain) exprs.zip(types)
          else removeConsecutiveDuplicates(exprs.zip(types))

        if showObviousTypesInExpressionChain || !hasObviousType(expr, ty)

      } yield {
        val exprEndOffsetInLine = getOffsetInLine(expr.getTextRange.getEndOffset, document)
        val marginLike = longestLine.substring(0, longestLine.length - exprEndOffsetInLine)
        inlayInfoFor(expr, ty, if (alignExpressionChain) marginLike else " ", editor, TypePresentationContext(expr))
      }
    ).toSeq
  }

  private def inlayInfoFor(expr: ScExpression, ty: ScType, marginLike: String, editor: Editor, context: TypePresentationContext): Hint = {
    implicit val scheme: EditorColorsScheme = editor.getColorsScheme
    val text = Text(": ") +: textPartsOf(ty, presentationLength)
    Hint(text, expr, suffix = true, margin = Hint.leftInsetLikeString(marginLike), menu = Some("TypeHintsMenu"), relatesToPrecedingElement = true)
  }
}

private object ScalaExprChainTypeHintsPass {

  object ExprChain {
    def unapply(element: PsiElement): Option[Seq[ScExpression]] = {
      element match {
        case expr: ScExpression if isMostOuterExpression(expr) =>
          Some(collectChain(expr))
        case _ => None
      }
    }

    private def isMostOuterExpression(expr: PsiElement): Boolean = {
      expr.getParent match {
        case _: ScReferenceExpression | _: ScMethodCall | _: ScParenthesisedExpr => false
        case _ => true
      }
    }

    private def collectChain(expr: ScExpression): List[ScExpression] = {
      @tailrec
      def collectChainAcc(expr: ScExpression, acc: List[ScExpression]): List[ScExpression] = {
        val newAcc = expr :: acc
        expr match {
          case ChainCall(inner) => collectChainAcc(inner, newAcc)
          case _ => newAcc
        }
      }
      collectChainAcc(expr, Nil)
    }

    private object ChainCall {
      def unapply(element: PsiElement): Option[ScExpression] = element match {
        case ScReferenceExpression.withQualifier(inner) => Some(inner)
        case MethodInvocation(inner, _) => Some(inner)
        case ScParenthesisedExpr(inner) => Some(inner)
        case _ => None
      }
    }
  }

  @tailrec
  def isFollowedByLineEnd(elem: PsiElement): Boolean =
    elem.getNextSibling match {
      case ws: PsiWhiteSpace => ws.textContains('\n')
      case null =>
        elem.getParent match {
          case null => true
          case parent => isFollowedByLineEnd(parent)
        }
      case _ => false
    }

  def removeConsecutiveDuplicates(exprsWithTypes: Seq[(ScExpression, ScType)]): Seq[(ScExpression, ScType)] =
    exprsWithTypes.foldLeft(List.empty[(ScExpression, ScType)]) {
      case (Nil, ewt) => List(ewt)
      case (ls, ewt) if ls.head._2 == ewt._2 => ls
      case (ls, ewt) => ewt :: ls
    }

  def hasObviousType(expr: ScExpression, ty: ScType): Boolean = {
    @tailrec
    def refName(expr: ScExpression): Option[String] = expr match {
      case mi: MethodInvocation =>
        mi.getEffectiveInvokedExpr.toOption.collect { case ref: ScReferenceExpression => ref.refName}
      case ref: ScReferenceExpression => Some(ref.refName)
      case ScParenthesisedExpr(inner) => refName(inner)
      case _ => None
    }

    refName(expr).exists(isTypeObvious("", ty.presentableText, _))
  }

  def getOffsetInLine(offset: Int, document: Document): Int =
    offset - getLineOffset(offset, document)

  def getLineOffset(offset: Int, document: Document): Int =
    document.getLineStartOffset(document.getLineNumber(offset))
}