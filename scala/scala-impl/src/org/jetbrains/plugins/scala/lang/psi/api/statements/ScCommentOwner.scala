package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * @author Nikolay.Tropin
 */

trait ScCommentOwner {

  self: ScalaPsiElement =>

  // TODO simplify and move
  def allComments: List[PsiComment] = {
    val maybeSimpleComment = self.children.collectFirst {
      case comment: PsiComment if !comment.isInstanceOf[ScDocComment] => comment
    }.toList

    self match {
      case ScDocCommentOwner(docComment) => docComment :: maybeSimpleComment
      case _ => maybeSimpleComment
    }
  }
}
