package org.jetbrains.plugins.scala.lang
package surroundWith
package descriptors

import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression._

import scala.annotation.tailrec

final class ScalaExpressionSurroundDescriptor extends SurroundDescriptor {

  import ScalaExpressionSurroundDescriptor._

  override def getSurrounders: Array[Surrounder] = Surrounders

  override def getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array[PsiElement] =
    expressionsInRange(startOffset, endOffset)(file) match {
      case Nil => PsiElement.EMPTY_ARRAY
      case expressions => expressions.toArray
    }

  override def isExclusive: Boolean = false
}

object ScalaExpressionSurroundDescriptor {

  private val Surrounders = Array[Surrounder](
    new ScalaWithIfSurrounder,
    new ScalaWithIfElseSurrounder,
    new ScalaWithWhileSurrounder,
    new ScalaWithDoWhileSurrounder,
    new ScalaWithForSurrounder,
    new ScalaWithForYieldSurrounder,
    ScalaWithTryCatchSurrounder,
    new ScalaWithTryFinallySurrounder,
    new ScalaWithTryCatchFinallySurrounder,
    new ScalaWithBracesSurrounder,
    ScalaWithMatchSurrounder,
    new ScalaWithParenthesisSurrounder,
    ScalaWithIfConditionSurrounder,
    new ScalaWithIfElseConditionSurrounder,
    new ScalaWithUnaryNotSurrounder,
    new ScalaTypeSurrounder,
  )

  import ScalaTokenTypes._

  @tailrec
  private def expressionsInRange(startOffset: Int, endOffset: Int)
                                (implicit file: PsiFile): List[PsiElement] = {
    import ScalaPsiUtil.isLineTerminator

    (file.findElementAt(startOffset), file.findElementAt(endOffset - 1)) match {
      case (ws: PsiWhiteSpace, _) => expressionsInRange(ws.getTextRange.getEndOffset, endOffset)
      case (_, ws: PsiWhiteSpace) => expressionsInRange(startOffset, ws.getTextRange.getStartOffset)
      case (null, _) | (_, null) => Nil
      case (_, semicolon) if semicolon.getNode.getElementType == tSEMICOLON => expressionsInRange(startOffset, endOffset - 1)
      case (lineSeparator, _) if isLineTerminator(lineSeparator) => expressionsInRange(lineSeparator.getTextRange.getEndOffset, endOffset)
      case (_, lineSeparator) if isLineTerminator(lineSeparator) => expressionsInRange(startOffset, lineSeparator.getTextRange.getStartOffset)
      case (comment, _) if COMMENTS_TOKEN_SET.contains(comment.getNode.getElementType) => expressionsInRange(comment.getTextRange.getEndOffset, endOffset)
      case (_, comment) if COMMENTS_TOKEN_SET.contains(comment.getNode.getElementType) => expressionsInRange(startOffset, comment.getTextRange.getStartOffset)
      case _ => findAllInRange(startOffset, endOffset)
    }
  }

  private[this] def findAllInRange(startOffset: Int, endOffset: Int)
                                  (implicit file: PsiFile): List[PsiElement] = {
    var element = file.findElementAt(startOffset)

    while (element != null &&
      isValid(element) &&
      !element.isInstanceOf[PsiWhiteSpace] &&
      element.getNode.getElementType != tSEMICOLON &&
      !COMMENTS_TOKEN_SET.contains(element.getNode.getElementType) ||
      (!isValid(element.getParent) && {
        val textRange = element.getParent.getTextRange
        textRange.getStartOffset == startOffset && textRange.getEndOffset <= endOffset
      })) {
      element = element.getParent
      if (element == null || element.getTextRange == null || element.getTextRange.getStartOffset != startOffset) return Nil
    }
    if (element == null) return Nil

    element.getTextRange.getEndOffset match {
      case `endOffset` => element :: Nil
      case offset if offset < endOffset =>
        findAllInRange(offset, endOffset) match {
          case Nil => Nil
          case list => element :: list
        }
      case _ => Nil
    }
  }

  private[this] def isValid(element: PsiElement) = element match {
    case _: ScExpression |
         _: ScValue |
         _: ScVariable |
         _: ScFunction |
         _: ScTypeAlias => false
    case _ => true
  }
}