package org.jetbrains.plugins.scala.editor

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScIf, ScWhile}

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

  def isNotIndentedAtFirstColumn(element: PsiElement): Boolean =
    indentWhitespace(element).exists(_.isEmpty)

  private def indentWhitespace(element: PsiElement): Option[String] =
    calcIndentationString(element, element.startOffset)

  /**
   * @return Some indentation string if element starts from a new line<br>
   *         None if element doesn't start form a new line
   */
  def calcIndentationString(
    element: PsiElement,
    endOffset: Int,
  ): Option[String] = {
    val whiteSpaceOrPrevious = element match {
      case ws: PsiWhiteSpace => ws
      case el => el.getPrevNonEmptyLeaf
    }
    whiteSpaceOrPrevious match {
      case ws: PsiWhiteSpace =>
        calcWhitespaceIndent(ws, endOffset)
      case _ =>
        None
    }
  }

  private def calcWhitespaceIndent(ws: PsiWhiteSpace, endOffset: Int) = {
    val wsText = ws.getText
    val wsTextToEndOffset = wsText.substring(0, (endOffset - ws.startOffset).min(ws.getTextLength))
    val wsLineBreak = wsTextToEndOffset.lastIndexOf('\n')
    if (wsLineBreak >= 0)
      Some(wsTextToEndOffset.substring(wsLineBreak + 1))
    else
      None
  }
}
