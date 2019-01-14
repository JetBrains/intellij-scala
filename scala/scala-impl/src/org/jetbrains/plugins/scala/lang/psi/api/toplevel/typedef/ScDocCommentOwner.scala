package org.jetbrains.plugins.scala
package lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * @author ilyas
 */

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.annotations.Nullable

trait ScDocCommentOwner extends PsiDocCommentOwner {
  def docComment: Option[ScDocComment] = getFirstChild.asOptionOf[ScDocComment]

  @Nullable
  def getDocComment: PsiDocComment = docComment.orNull

  def isDeprecated = this match {
    case holder: ScAnnotationsHolder =>
      holder.hasAnnotation("scala.deprecated") || holder.hasAnnotation("java.lang.Deprecated")
    case _ => false
  }
}