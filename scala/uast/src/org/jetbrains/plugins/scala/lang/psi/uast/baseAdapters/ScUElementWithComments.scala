package org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters

import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter
import org.jetbrains.uast.java.internal.JavaUElementWithComments
import org.jetbrains.uast.{UComment, UElement, UExpression}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

/**
  * Default implementation of the [[UElement]]#getComments
  * same as [[JavaUElementWithComments]] implementation.
  */
trait ScUElementWithComments extends UElement {

  override def getComments: java.util.List[UComment] =
    getSourcePsi match {
      case null => java.util.Collections.emptyList()
      case element =>
        val childrenComments = element.getChildren.toSeq
          .filter(_.isInstanceOf[PsiComment])
          .map(_.asInstanceOf[PsiComment])

        val nearestComments = this match {
          case _: UExpression =>
            nearestCommentSibling(element)(_.getNextSibling) ++
              nearestCommentSibling(element)(_.getPrevSibling)
          case _ => Seq.empty
        }

        (childrenComments ++ nearestComments)
          .map(Scala2UastConverter.createUComment(_, parent = this))
          .asJava
    }

  @tailrec
  private def nearestCommentSibling(
                                     element: PsiElement
                                   )(sibling: PsiElement => PsiElement): Option[PsiComment] =
    sibling(element) match {
      case comment: PsiComment => Some(comment)
      case whiteSpace: PsiWhiteSpace if !whiteSpace.textContains('\n') =>
        nearestCommentSibling(whiteSpace)(sibling)
      case _ => None
    }
}
