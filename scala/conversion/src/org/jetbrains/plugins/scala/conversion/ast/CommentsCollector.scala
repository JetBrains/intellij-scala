package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{JavaTokenType, PsiBlockStatement, PsiCodeBlock, PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

//noinspection InstanceOf
object CommentsCollector {

  private def collectCommentsAtStart(element: PsiElement)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[PsiComment] = {
    val result = new ArrayBuffer[PsiComment]()

    var child = element.getFirstChild
    while (child != null && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child)) {
      child match {
        case c: PsiComment if !usedComments.contains(c) =>
          result += c
        case _ =>
      }
      child = child.getNextSibling
    }

    usedComments ++= result
    result
  }

  private def collectCommentsAtTheEnd(element: PsiElement)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[PsiComment] = {
    val result = new ArrayBuffer[PsiComment]()
    var child = element.getLastChild
    if (child != null) {
      if (!isCommentOrSpace(child)) {
        child = child.getPrevSibling
      }
      else if (child.getParent.isInstanceOf[PsiBlockStatement] && child.getParent.getFirstChild.isInstanceOf[PsiCodeBlock]) {
        //handling special case when comment is on the same line after the block, e.g. here:
        //else {
        //   ...
        //} /*end*/
        child = child.prevSiblingNotWhitespaceComment.get
      }
    }

    while ((child != null) && (isCommentOrSpace(child) || isEmptyElement(child)) && !usedComments.contains(child)) {
      child match {
        case c: PsiComment if !usedComments.contains(c) =>
          result += c
        case _ =>
      }
      child = child.getPrevSibling
    }
    usedComments ++= result
    result.reverse
  }

  //collect all comments before, while there is no other elements or next comment is used
  private def collectCommentsAndSpacesBefore(element: PsiElement)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[PsiComment] = {
    val result = new ArrayBuffer[PsiComment]()

    var prev = element.getPrevSibling
    while ((prev != null) && (isCommentOrSpace(prev) || isEmptyElement(prev)) && !usedComments.contains(prev)) {
      prev match {
        case c: PsiComment if !usedComments.contains(c) =>
          result += c
        case _ =>
      }
      prev = prev.getPrevSibling
    }
    //note: used comments will be registered in a callee

    result
  }

  private def collectCommentsAndSpacesAfter(element: PsiElement, resultComments: ArrayBuffer[PsiComment])(
    implicit usedComments: UsedComments
  ): ArrayBuffer[PsiComment] = {
    val next = element.getNextSibling
    if (next != null) {
      if (isCommentOrSpace(next)) {
        if (next.isInstanceOf[PsiWhiteSpace] && hasLineBreaks(next))
          return resultComments
        if (!usedComments.contains(next)) {
          next match {
            case c: PsiComment =>
              resultComments += c
              usedComments += c
            case _ =>
          }
          collectCommentsAndSpacesAfter(next, resultComments)
        }
      }
      else if (isEmptyElement(next)) {
        collectCommentsAndSpacesAfter(next, resultComments)
      }
    }

    resultComments
  }

  private[conversion]
  def getAllBeforeComments(element: PsiElement, ignoreCommentsUsedInParent: Boolean = true)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[LiteralExpression] = {
    def commentsUsedInParent(comments: collection.Seq[PsiElement]): Boolean =
      comments.nonEmpty && {
        val parent = comments.head.getParent
        parent != null && comments.contains(parent.getFirstChild)
      }

    val innerComments = collectCommentsAndSpacesBefore(element)
    val result = collectCommentsAtStart(element)
    if (ignoreCommentsUsedInParent && commentsUsedInParent(innerComments)) {
      //skip comments
    } else {
      result ++= innerComments.reverseIterator
    }

    usedComments ++= result
    result.map(convertComment)
  }

  def getAllInsideComments(element: PsiElement)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[PsiComment] = {
    val result = new ArrayBuffer[PsiComment]()

    element.depthFirst().foreach {
      case c: PsiComment =>
        if (!usedComments.contains(c))
          result += c
      case _ =>
    }

    usedComments ++= result
    result
  }

  private def getAllAfterComments(element: PsiElement)(
    implicit usedComments: UsedComments
  ): ArrayBuffer[LiteralExpression] = {
    val buffer = new ArrayBuffer[PsiComment]()
    val result = collectCommentsAndSpacesAfter(element, buffer)
    usedComments ++= result
    result.map(c => LiteralExpression(c.getText))
  }

  def allCommentsForElement(element: PsiElement)(
    implicit usedComments: UsedComments
  ): IntermediateNode.Comments = {
    val before = getAllBeforeComments(element)
    val after = getAllAfterComments(element)
    val latest = collectCommentsAtTheEnd(element)

    IntermediateNode.Comments(before, after, latest.map(convertComment))
  }

  private def isCommentOrSpace(element: PsiElement): Boolean =
    isComment(element) || element.isInstanceOf[PsiWhiteSpace]

  def isComment(element: PsiElement): Boolean = element.isInstanceOf[PsiComment]

  private def isEmptyElement(element: PsiElement): Boolean =
    (element.getFirstChild == null) && (element.getTextLength == 0)

  private def hasLineBreaks(whiteSpace: PsiElement): Boolean = StringUtil.containsLineBreak(whiteSpace.getText)

  def convertComment(c: PsiComment): LiteralExpression = {
    val text0 = c.getText
    val needsNewLineBefore = c.startsFromNewLine(false) && c.prevVisibleLeaf.exists(_.getNode.getElementType == JavaTokenType.LBRACE)
    val needsNewLineAfter = c.followedByNewLine(false)
    val prefix = if (needsNewLineBefore) "\n" else ""
    val suffix = if (needsNewLineAfter) "\n" else ""
    val text = prefix + text0 + suffix
    LiteralExpression(text)
  }

  class UsedComments(set: mutable.Set[PsiComment] = new mutable.HashSet[PsiComment]()) {
    def contains(element: PsiElement): Boolean = {
      element match {
        case c: PsiComment =>
          set.contains(c)
        case _ => false
      }
    }

    def ++= (elements: IterableOnce[PsiComment]): Unit = {
      set ++= elements
    }

    def += (element: PsiComment): Unit = {
      set += element
    }
  }
}

