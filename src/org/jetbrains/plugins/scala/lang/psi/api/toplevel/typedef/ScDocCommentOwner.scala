package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

/**
 * @author ilyas
 */

import org.jetbrains.annotations.Nullable
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

  def isDeprecated = false

}