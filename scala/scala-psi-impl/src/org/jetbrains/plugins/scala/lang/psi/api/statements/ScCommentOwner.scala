package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

trait ScCommentOwner {
  self: ScalaPsiElement =>

  def allComments: Seq[PsiComment] = scDocComment.toSeq ++ simpleComments

  private def simpleComments: Seq[PsiComment] = self.children.collect {
    case comment: PsiComment if !comment.is[ScDocComment] => comment
  }.toSeq

  private def scDocComment: Option[ScDocComment] = self match {
    case dco: ScDocCommentOwner => dco.docComment
    case _ => None
  }

}
