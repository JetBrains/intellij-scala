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
    PsiTreeUtil.getPrevSiblingOfType(this, classOf[ScDocComment]) match {
      case null => None
      case x => Some(x)
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