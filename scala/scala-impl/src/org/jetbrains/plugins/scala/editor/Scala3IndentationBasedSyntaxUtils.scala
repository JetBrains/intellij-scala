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

  def indentWhitespace(element: PsiElement, ignoreComments: Boolean = true, skipElementsOnLine: Boolean = false): String = {
    var indent = ""

    @tailrec
    def inner(el: PsiElement): String = el match {
      case null => indent
      case ws: PsiWhiteSpace =>
        val text = ws.getText
        val linebreak = text.lastIndexOf('\n')
        if (linebreak >= 0)
          text.substring(linebreak + 1) + indent
        else {
          indent = text + indent
          inner(PsiTreeUtil.prevLeaf(el))
        }
      case _: PsiComment if ignoreComments =>
        inner(PsiTreeUtil.prevLeaf(el))
      case _ if el.getTextLength == 0 => // empty annotations, modifiers, parse errors, etc...
        inner(PsiTreeUtil.prevLeaf(el))
      case _ if skipElementsOnLine =>
        indent = ""
        inner(PsiTreeUtil.prevLeaf(el))
      case _ => ""
    }

    inner(PsiTreeUtil.prevLeaf(element))
  }

  @inline def lineIndentWhitespace(element: PsiElement): String =
    indentWhitespace(element, skipElementsOnLine = true)

  @inline def isIndented(element: PsiElement): Boolean = indentWhitespace(element).isEmpty
}
