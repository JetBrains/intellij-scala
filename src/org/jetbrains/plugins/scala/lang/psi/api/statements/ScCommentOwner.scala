package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * @author Nikolay.Tropin
 */

trait ScCommentOwner {
  self: ScalaPsiElement =>

  def simpleComment: Option[PsiComment] = {
    self.children.collectFirst {
      case c: PsiComment if !c.isInstanceOf[ScDocComment] => c
    }
  }

  private def scDocComment: Option[ScDocComment] = self match {
    case dco: ScDocCommentOwner => dco.docComment
    case _ => None
  }

  def allComments: Seq[PsiComment] = scDocComment.toSeq ++ simpleComment

  def hasComments = allComments.nonEmpty
}
