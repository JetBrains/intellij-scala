package org.jetbrains.plugins.scala
package conversion
package ast

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{JavaTokenType, PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 12/4/15
  */
object CommentsCollector {

  def collectCommentsAtStart(element: PsiElement)
                            (implicit usedComments:
                            mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    val result = new ArrayBuffer[PsiElement]()
    var child = element.getFirstChild
    while (child != null && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child)) {
      if (!usedComments.contains(child) && isComment(child)) result += child
      child = child.getNextSibling
    }
    usedComments ++= result
    result
  }

  def collectCommentsAtTheEnd(element: PsiElement)
                             (implicit usedComments:
                             mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    val result = new ArrayBuffer[PsiElement]()
    var child = element.getLastChild
    if (child != null && !isCommentOrSpace(child)) child = child.getPrevSibling

    while ((child != null) && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child)) {
      if (!usedComments.contains(child) && isComment(child)) result += child
      child = child.getPrevSibling
    }
    usedComments ++= result
    result.reverse
  }

  //collect all comments before, while there is no other elements or next comment is used
  def collectCommentsAndSpacesBefore(element: PsiElement)
                                    (implicit usedComments:
                                    mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {

    var prev = element.getPrevSibling
    val resultComments = new ArrayBuffer[PsiElement]()
    while ((prev != null) && !usedComments.contains(prev)) {
      if (!usedComments.contains(prev) && isComment(prev)) resultComments += prev
      prev = prev.getPrevSibling
    }
    //    usedComments ++= resultComments
    resultComments
  }

  def collectCommentsAndSpacesAfter(element: PsiElement, resultComments: ArrayBuffer[PsiElement])
                                   (implicit usedComments:
                                   mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    val next = element.getNextSibling
    if (next != null) {
      if (isCommentOrSpace(next)) {
        if (next.isInstanceOf[PsiWhiteSpace] && hasLineBreaks(next))
          return resultComments
        if (!usedComments.contains(next)) {
          if (isComment(next)) {
            resultComments += next
            usedComments += next
          }
          collectCommentsAndSpacesAfter(next, resultComments)
        }
      } else if (isEmptyElement(next)) collectCommentsAndSpacesAfter(next, resultComments)
    }

    resultComments
  }

  def getAllBeforeComments(element: PsiElement)
                          (implicit usedComments:
                          mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[LiteralExpression] = {
    def collectedCommentsUsedInParent(comments: collection.Seq[PsiElement]): Boolean = {
      comments.size match {
        case 0 => false
        case _ => Option(comments.head.getParent) match {
          case Some(value: PsiElement) =>
            comments.contains(value.getFirstChild)
          case _ => false
        }
      }
    }

    val innerComments = collectCommentsAndSpacesBefore(element).reverse
    val result = collectCommentsAtStart(element)
    if (!collectedCommentsUsedInParent(innerComments)) {
      result ++= innerComments
    }

    usedComments ++= result
    result.map(convertComment)
  }

  def getAllInsideComments(element: PsiElement)
                          (implicit usedComments:
                          mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    val result = new ArrayBuffer[PsiElement]()
    element.depthFirst().foreach {
      case c: PsiComment =>
        if (!usedComments.contains(c)) result += c
      case _ =>
    }

    usedComments ++= result
    result
  }

  def getAllAfterComments(element: PsiElement)
                         (implicit usedComments:
                         mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[LiteralExpression] = {
    val buffer = new ArrayBuffer[PsiElement]()
    val result = collectCommentsAndSpacesAfter(element, buffer)
    result.map(c => LiteralExpression(c.getText))
  }

  def allCommentsForElement(element: PsiElement)
                           (implicit usedComments:
                           mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): IntermediateNode.Comments = {
    val before = getAllBeforeComments(element)
    val after = getAllAfterComments(element)
    val latest = collectCommentsAtTheEnd(element)

    IntermediateNode.Comments(before, after, latest.map(convertComment))
  }

  def isCommentOrSpace(element: PsiElement): Boolean =
    isComment(element) || element.isInstanceOf[PsiWhiteSpace]

  def isComment(element: PsiElement): Boolean = element.isInstanceOf[PsiComment]

  def isEmptyElement(element: PsiElement): Boolean =
    (element.getFirstChild == null) && (element.getTextLength == 0)

  def hasLineBreaks(whiteSpace: PsiElement): Boolean = StringUtil.containsLineBreak(whiteSpace.getText)

  def convertComment(c: PsiElement): LiteralExpression = {
    c match {
      case c: PsiComment if c.getTokenType == JavaTokenType.END_OF_LINE_COMMENT =>
        val text = c.getText
        if (text.last != '\n') LiteralExpression(c.getText + '\n') else LiteralExpression(c.getText)
      case _ => LiteralExpression(c.getText)
    }
  }
}

