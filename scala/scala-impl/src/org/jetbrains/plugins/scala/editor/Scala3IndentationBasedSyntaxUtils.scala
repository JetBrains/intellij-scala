package org.jetbrains.plugins.scala.editor

import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScIf, ScWhile}

import scala.annotation.tailrec

// TODO rework this
// TODO test this
// see https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html
object Scala3IndentationBasedSyntaxUtils {
  def indentedRegionCanStart(leaf: PsiElement): Boolean = leaf.elementType match {
    case ScalaTokenTypes.tASSIGN |
         ScalaTokenTypes.tFUNTYPE |
         ScalaTokenType.ImplicitFunctionArrow |
         ScalaTokenTypes.tCHOOSE |
         ScalaTokenTypes.kYIELD |
         ScalaTokenTypes.kDO |
         ScalaTokenType.ThenKeyword |
         ScalaTokenTypes.kELSE |
         ScalaTokenTypes.kTRY |
         ScalaTokenTypes.kFINALLY |
         ScalaTokenTypes.kCATCH |
         ScalaTokenTypes.kMATCH |
         ScalaTokenTypes.kRETURN |
         ScalaTokenTypes.kTHROW =>
      true
    case ScalaTokenTypes.tRPARENTHESIS => leaf.parent match {
      case Some(innerIf: ScIf) => innerIf.condition.isDefined && innerIf.thenExpression.isEmpty
      case Some(innerWhile: ScWhile) => innerWhile.condition.isDefined && innerWhile.expression.isEmpty
      case Some(innerFor: ScFor) => innerFor.enumerators.isDefined && innerFor.yieldOrDoKeyword.isEmpty && innerFor.body.isEmpty
      case _ => false
    }
    case ScalaTokenTypes.tRBRACE => leaf.parent match {
      case Some(innerFor: ScFor) => innerFor.enumerators.isDefined && innerFor.yieldOrDoKeyword.isEmpty && innerFor.body.isEmpty
      case _ => false
    }
    case _ =>
      false
  }

  def outdentedRegionCanStart(leaf: PsiElement): Boolean = leaf.elementType match {
    case ScalaTokenType.ThenKeyword |
         ScalaTokenTypes.kELSE |
         ScalaTokenTypes.kDO |
         ScalaTokenTypes.kYIELD |
         ScalaTokenTypes.kCATCH |
         ScalaTokenTypes.kFINALLY |
         ScalaTokenTypes.kMATCH => false
    case _ => true
  }

  def continuesCompoundStatement(leaf: PsiElement): Boolean = leaf.elementType match {
    case ScalaTokenTypes.kMATCH => false
    case _ => !outdentedRegionCanStart(leaf)
  }

  def indentWhitespace(element: PsiElement, endOffset: Int, ignoreComments: Boolean, ignoreElementsOnLine: Boolean): String = {
    var indent = ""

    @tailrec
    def inner(el: PsiElement, first: Boolean = true): String = el match {
      case null => indent
      case ws: PsiWhiteSpace =>
        val wsTextToEndOffset = ws.getText.substring(0, Math.min(ws.getTextLength, endOffset - ws.startOffset))
        val wsLineBreak = wsTextToEndOffset.lastIndexOf('\n')
        if (wsLineBreak >= 0)
          wsTextToEndOffset.substring(wsLineBreak + 1) + indent
        else {
          indent = wsTextToEndOffset + indent
          inner(PsiTreeUtil.prevLeaf(el), first = false)
        }
      case _: PsiComment if ignoreComments || first =>
        inner(PsiTreeUtil.prevLeaf(el), first = false)
      case _ if el.getTextLength == 0 => // empty annotations, modifiers, parse errors, etc...
        inner(PsiTreeUtil.prevLeaf(el), first = false)
      case _ if ignoreElementsOnLine || first =>
        indent = ""
        inner(PsiTreeUtil.prevLeaf(el), first = false)
      case _ => ""
    }

    inner(element)
  }

  @inline
  def isNotIndentedAtFirstColumn(element: PsiElement): Boolean = {
    val indent = indentWhitespace(element, element.endOffset, ignoreComments = true, ignoreElementsOnLine = false)
    indent != null && indent.isEmpty
  }
}
