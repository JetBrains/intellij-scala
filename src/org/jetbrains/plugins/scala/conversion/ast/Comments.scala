package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{JavaTokenType, PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 12/4/15
  */
object Comments {
  def apply: Comments = {
    new Comments(new ArrayBuffer[LiteralExpression](),
      new ArrayBuffer[LiteralExpression](), new ArrayBuffer[LiteralExpression]())
  }

  val topElements = new mutable.HashSet[PsiElement]()
}

case class Comments(beforeComments: ArrayBuffer[LiteralExpression],
                    afterComments: ArrayBuffer[LiteralExpression],
                    latestCommtets: ArrayBuffer[LiteralExpression])


object CommentsCollector {
  def getAllInsideComments(element: PsiElement)
                          (implicit usedComments:
                          mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    val result = new ArrayBuffer[PsiElement]()
    element.depthFirst.foreach {
      case c: PsiComment => if (!usedComments.contains(c)) result += c
      case _ =>
    }

    usedComments ++= result
    result
  }

  def allCommentsForElement(element: PsiElement)
                           (implicit usedComments:
                           mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Comments = {
    val before = getAllBeforeComments(element)
    val after = getAllAfterComments(element)
    val latest = collectCommentsAtTheEnd(element)

    Comments(new ArrayBuffer[LiteralExpression]() ++ before,
      new ArrayBuffer[LiteralExpression]() ++ after, new ArrayBuffer[LiteralExpression]() ++ latest.map(convertComment))
  }

  def collectCommentsAtTheEnd(element: PsiElement)
                             (implicit usedComments:
                             mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Array[PsiElement] = {
    val child = element.getLastChild
    if (child == null) return Array[PsiElement]()
    val iterator = if (!isCommentOrSpace(child)) child.prevSiblings else child.prevElements

    val result = iterator.takeWhile(child =>
      (child != null) && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child))
      .filter(child => isComment(child)).toArray

    usedComments ++= result
    result.reverse
  }

  def getAllBeforeComments(element: PsiElement)
                          (implicit usedComments:
                          mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()) = {
    def collectedCommentsUsedInParent(comments: Seq[PsiElement]): Boolean = {
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
    val startComments = collectCommentsAtStart(element)
    val result = if (!collectedCommentsUsedInParent(innerComments)) {
      startComments ++ innerComments
    } else startComments

    usedComments ++= result
    result.map(convertComment)
  }

  //collect all comments before, while there is no other elements or next comment is used
  private def collectCommentsAndSpacesBefore(element: PsiElement)
                                            (implicit usedComments:
                                            mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Array[PsiElement] = {

    if (Comments.topElements.contains(element)) return Array[PsiElement]()
    element.prevSiblings.takeWhile(prev => (prev != null) && !usedComments.contains(prev))
      .filter(prev => isComment(prev)).toArray
  }

  private def collectCommentsAtStart(element: PsiElement)
                                    (implicit usedComments:
                                    mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Array[PsiElement] = {
    element.children.takeWhile(child =>
      child != null && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child))
      .filter(child => isComment(child)).toArray
  }

  private def collectCommentsAndSpacesAfter(element: PsiElement, resultComments: ArrayBuffer[PsiElement])
                                           (implicit usedComments:
                                           mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): ArrayBuffer[PsiElement] = {
    if (Comments.topElements.contains(element)) return new ArrayBuffer[PsiElement]()
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

  def getAllAfterComments(element: PsiElement)
                         (implicit usedComments:
                         mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Array[LiteralExpression] = {
    val buffer = new ArrayBuffer[PsiElement]()
    val result = collectCommentsAndSpacesAfter(element, buffer).toArray
    result.map(c => LiteralExpression(c.getText))
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

