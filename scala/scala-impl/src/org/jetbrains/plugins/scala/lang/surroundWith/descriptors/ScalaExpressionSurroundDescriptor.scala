package org.jetbrains.plugins.scala.lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
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
    if (startOffset > endOffset)
      return Nil

    import ScalaPsiUtil.isLineTerminator

    val element1 = file.findElementAt(startOffset)
    val element2 = file.findElementAt(endOffset - 1)
    val (startOffsetNew, endOffsetNew) = (element1, element2) match {
      case (ws: PsiWhiteSpace, _) =>
        (ws.getTextRange.getEndOffset, endOffset)
      case (_, ws: PsiWhiteSpace) =>
        (startOffset, ws.getTextRange.getStartOffset)
      case (null, _) | (_, null) =>
        (-1, -1)
      case (_, semicolon) if semicolon.getNode.getElementType == tSEMICOLON =>
        (startOffset, endOffset - 1)
      case (lineSeparator, _) if isLineTerminator(lineSeparator) =>
        (lineSeparator.getTextRange.getEndOffset, endOffset)
      case (_, lineSeparator) if isLineTerminator(lineSeparator) =>
        (startOffset, lineSeparator.getTextRange.getStartOffset)
      case (comment, _) if COMMENTS_TOKEN_SET.contains(comment.getNode.getElementType) =>
        (comment.getTextRange.getEndOffset, endOffset)
      case (_, comment) if COMMENTS_TOKEN_SET.contains(comment.getNode.getElementType) =>
        (startOffset, comment.getTextRange.getStartOffset)
      case _ =>
        (startOffset, endOffset)
    }

    if (startOffset == -1)
      Nil
    else {
      val rangeLength = endOffset - startOffset
      val rangeLengthNew = endOffsetNew - startOffsetNew
      val rangeIsNarrowed = rangeLengthNew < rangeLength
      if (rangeIsNarrowed && rangeLengthNew > 0) //avoid potential infinite recursion
        expressionsInRange(startOffsetNew, endOffsetNew)
      else
        findAllInRange(startOffset, endOffset)
    }
  }

  private[this] def findAllInRange(startOffset: Int, endOffset: Int)
                                  (implicit file: PsiFile): List[PsiElement] = {
    val elementAtCaret = file.findElementAt(startOffset)

    def continueProcessing(e: PsiElement): Boolean = {
      isAcceptable(e) && !isWhitespaceOrCommentOrSemicolon(e) ||
        !isAcceptable(e.getParent) && {
          val textRange = e.getParent.getTextRange
          // Q: is it expected that we have `textRange.getStartOffset == startOffset` here and not `textRange.getStartOffset <= startOffset`
          textRange.getStartOffset == startOffset && textRange.getEndOffset <= endOffset
        }
    }

    var element = elementAtCaret
    while (element != null && continueProcessing(element)) {
      element = element.getParent
      if (element == null || element.getTextRange == null || element.getTextRange.getStartOffset != startOffset)
        return Nil
    }
    if (element == null)
      return Nil

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

  private def isWhitespaceOrCommentOrSemicolon(e: PsiElement): Boolean =
    e.isWhitespaceOrComment || e.getNode.getElementType == tSEMICOLON

  private[this] def isAcceptable(element: PsiElement) = element match {
    case _: ScExpression |
         _: ScValue |
         _: ScVariable |
         _: ScFunction |
         _: ScTypeAlias => false
    case _ => true
  }
}