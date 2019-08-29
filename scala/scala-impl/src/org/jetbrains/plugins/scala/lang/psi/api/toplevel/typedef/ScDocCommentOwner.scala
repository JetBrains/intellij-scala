package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.CommonClassNames.JAVA_LANG_DEPRECATED
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.DeprecatedFqn

/**
 * @author ilyas
 */
import com.intellij.psi.PsiDocCommentOwner
import org.jetbrains.annotations.Nullable

trait ScDocCommentOwner extends PsiDocCommentOwner {

  @Nullable
  override final def getDocComment: ScDocComment = getFirstChild match {
    case comment: ScDocComment => comment
    case _ => null
  }

  override def isDeprecated: Boolean = this match {
    case holder: ScAnnotationsHolder =>
      holder.hasAnnotation(DeprecatedFqn) || holder.hasAnnotation(JAVA_LANG_DEPRECATED)
    case _ => false
  }
}

object ScDocCommentOwner {

  def unapply(owner: ScDocCommentOwner): Option[ScDocComment] =
    Option(owner.getDocComment)
}