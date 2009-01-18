package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

/** 
* @author ilyas
*/

import annotations.Nullable
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.{PsiWhiteSpace, PsiDocCommentOwner}
import scaladoc.psi.api.ScDocComment

trait ScDocCommentOwner extends PsiDocCommentOwner {

  def docComment: Option[ScDocComment] = {
    var prev = getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(prev))) prev = prev.getPrevSibling
    prev match {
      case x: ScDocComment => Some(x)
      case _ => None
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