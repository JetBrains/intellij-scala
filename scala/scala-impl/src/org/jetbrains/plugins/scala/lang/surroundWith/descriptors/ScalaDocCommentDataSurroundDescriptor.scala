package org.jetbrains.plugins.scala
package lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc._

import scala.collection.mutable.ArrayBuffer


/**
 * User: Dmitry Naydanov
 * Date: 3/2/12
 */

class ScalaDocCommentDataSurroundDescriptor extends SurroundDescriptor {
  val surrounders: Array[Surrounder] = Array[Surrounder](new ScalaDocWithBoldSurrounder, new ScalaDocWithUnderlinedSurrounder,
    new ScalaDocWithMonospaceSurrounder, new ScalaDocWithItalicSurrounder, new ScalaDocWithSubscriptSurrounder,
    new ScalaDocWithSuperscriptSurrounder)

  override def getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array[PsiElement] = {
    if (endOffset == startOffset) return PsiElement.EMPTY_ARRAY

    val validBoundElements = Set(DOC_COMMENT_DATA, DOC_WHITESPACE)


    def checkBoundElement(element: PsiElement): Boolean = validBoundElements.contains(element.getNode.getElementType)

    def checkSyntaxBoundElement(element: PsiElement, isStart: Boolean): Boolean =
      element.getNode.getElementType.isInstanceOf[ScalaDocSyntaxElementType] &&
              (isStart && startOffset == element.getTextOffset || !isStart && endOffset == element.getTextRange.getEndOffset)


    val startElement = file.findElementAt(startOffset)
    val endElement = file.findElementAt(endOffset - 1)

    if (startElement == null || endElement == null) return PsiElement.EMPTY_ARRAY

    val isFirstElementMarked = if (checkBoundElement(startElement)) { //cannot extract function because of return
      false
    } else {
      if (checkSyntaxBoundElement(startElement, true)) true else return PsiElement.EMPTY_ARRAY
    }

    val isLastElementMarked = if (checkBoundElement(endElement)) {
      false
    } else {
      if (checkSyntaxBoundElement(endElement, false)) true else return PsiElement.EMPTY_ARRAY
    }

    if (startElement.getParent != endElement.getParent) {
      (isFirstElementMarked, isLastElementMarked) match {
        case (true, true) if (startElement.getParent.getParent == endElement.getParent.getParent) =>
        case (true, false) if (startElement.getParent.getParent == endElement.getParent) =>
        case (false, true) if (startElement.getParent == endElement.getParent.getParent) =>
        case _ => return PsiElement.EMPTY_ARRAY
      }
    } else if (isFirstElementMarked && isLastElementMarked) { // in case <selection>__blah blah__</selection>
      return Array(startElement.getParent)
    }

    if (endElement == startElement) {
      return Array(startElement)
    }

    var (nextElement, elementsToSurround) = if (isFirstElementMarked) {
      if (startElement.getParent.getTextRange.getEndOffset <= endOffset)
        (startElement.getParent.getNextSibling, ArrayBuffer(startElement.getParent))
      else
        return PsiElement.EMPTY_ARRAY
    } else {
      (startElement.getNextSibling, ArrayBuffer(startElement))
    }
    val lastBoundElement = if (isLastElementMarked) {
      if (endElement.getTextOffset >= startOffset) (endElement.getParent) else return PsiElement.EMPTY_ARRAY
    } else {
      endElement
    }

    var hasAsterisk = false

    do {
      if (nextElement == null) return PsiElement.EMPTY_ARRAY

      if ((!Set(DOC_COMMENT_DATA, DOC_COMMENT_LEADING_ASTERISKS, DOC_WHITESPACE).contains(nextElement.getNode.getElementType) &&
              !nextElement.getNode.getElementType.isInstanceOf[ScalaDocSyntaxElementType]) ||
              (nextElement.getNode.getElementType == DOC_WHITESPACE && nextElement.getText.indexOf("\n") != nextElement.getText.lastIndexOf("\n"))) {
        return PsiElement.EMPTY_ARRAY
      } else if (nextElement.getNode.getElementType == DOC_COMMENT_LEADING_ASTERISKS) {
        if (hasAsterisk) return PsiElement.EMPTY_ARRAY
        hasAsterisk = true
      } else if (nextElement.getNode.getElementType != DOC_WHITESPACE) {
        hasAsterisk = false
      }

      elementsToSurround += nextElement
    } while (nextElement != lastBoundElement && (nextElement = nextElement.getNextSibling, true)._2);

    elementsToSurround.toArray
  }

  override def getSurrounders: Array[Surrounder] = surrounders

  override def isExclusive: Boolean = false
}
