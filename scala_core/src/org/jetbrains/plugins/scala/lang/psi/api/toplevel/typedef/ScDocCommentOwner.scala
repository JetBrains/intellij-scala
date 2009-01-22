package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

/**
 * @author ilyas
 */

import annotations.Nullable
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiWhiteSpace, PsiDocCommentOwner}
import scaladoc.psi.api.ScDocComment

trait ScDocCommentOwner extends PsiDocCommentOwner {
  def docComment: Option[ScDocComment] = {
    val prev = getPrevSibling
    val cur = if (prev.isInstanceOf[PsiWhiteSpace]) prev else this
    cur.getPrevSibling match {
      case dc: ScDocComment => Some(dc)
      case x => None
    }
  }

  @Nullable
  def getDocComment: PsiDocComment = {
    docComment match {
      case Some(x) => x
      case None => null
    }
  }

  // todo implement me!
  def isDeprecated = false

}