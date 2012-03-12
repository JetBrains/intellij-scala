package org.jetbrains.plugins.scala
package lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.{Surrounder, SurroundDescriptor}
import com.intellij.psi.{PsiElement, PsiFile}
import collection.mutable.ArrayBuffer
import lang.scaladoc.lexer.ScalaDocTokenType._
import lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import lang.surroundWith.surrounders.scaladoc._


/**
 * User: Dmitry Naydanov
 * Date: 3/2/12
 */

class ScalaDocCommentDataSurroundDescriptor extends SurroundDescriptor {
  val surrounders = Array[Surrounder](new ScalaDocWithBoldSurrounder, new ScalaDocWithUnderlinedSurrounder,
    new ScalaDocWithMonospaceSurrounder, new ScalaDocWithItalicSurrounder, new ScalaDocWithSubscriptSurrounder,
    new ScalaDocWithSuperscriptSurrounder)

  def getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array[PsiElement] = {
    if (endOffset == startOffset) return PsiElement.EMPTY_ARRAY

    val validBoundElements = Set(DOC_COMMENT_DATA, DOC_WHITESPACE)
    def checkBoundElement(element: PsiElement) = element != null && validBoundElements.contains(element.getNode.getElementType)


    val startElement = file.findElementAt(startOffset)
    if (!checkBoundElement(startElement)) {
      return PsiElement.EMPTY_ARRAY
    }

    val endElement = file.findElementAt(endOffset - 1)
    if (!checkBoundElement(endElement) || endElement.getParent != startElement.getParent) {
      return PsiElement.EMPTY_ARRAY
    }

    if (endElement == startElement) {
      return Array(startElement)
    }

    var nextElement = startElement.getNextSibling
    var hasAsterisk = false
    val elementsToSurround = ArrayBuffer(startElement)

    while (nextElement != endElement) {
      if (nextElement == null) return PsiElement.EMPTY_ARRAY

      if ((!Set(DOC_COMMENT_DATA, DOC_COMMENT_LEADING_ASTERISKS, DOC_WHITESPACE).contains(nextElement.getNode.getElementType) &&
              !nextElement.getNode.getElementType.isInstanceOf[ScaladocSyntaxElementType]) ||
              (nextElement.getNode.getElementType == DOC_WHITESPACE && nextElement.getText.indexOf("\n") != nextElement.getText.lastIndexOf("\n"))) {
        return PsiElement.EMPTY_ARRAY
      } else if (nextElement.getNode.getElementType == DOC_COMMENT_LEADING_ASTERISKS) {
        if (hasAsterisk) return PsiElement.EMPTY_ARRAY
        hasAsterisk = true
      } else if (nextElement.getNode.getElementType != DOC_WHITESPACE) {
        hasAsterisk = false
      }

      elementsToSurround += nextElement
      nextElement = nextElement.getNextSibling
    }

    (elementsToSurround + endElement).toArray
  }

  def getSurrounders: Array[Surrounder] = surrounders

  def isExclusive: Boolean = false
}
