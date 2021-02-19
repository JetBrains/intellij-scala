package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * @author Nikolay.Tropin
 */

trait ScCommentOwnerBase extends PsiElement { this: ScCommentOwner =>

  def simpleComment: Option[PsiComment] = {
    val element: PsiElement = this
    element.children.collectFirst {
      case c: PsiComment if !c.isInstanceOf[ScDocComment] => c
    }
  }

  private def scDocComment: Option[ScDocComment] = this match {
    case dco: ScDocCommentOwner => dco.docComment
    case _ => None
  }

  def allComments: Seq[PsiComment] = scDocComment.toSeq ++ simpleComment

  def hasComments: Boolean = allComments.nonEmpty
}