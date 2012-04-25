package org.jetbrains.plugins.scala
package lang.psi.api.toplevel.typedef

import lang.scaladoc.psi.api.ScDocComment
import extensions._

/**
 * @author ilyas
 */

import org.jetbrains.annotations.Nullable
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.PsiDocCommentOwner

trait ScDocCommentOwner extends PsiDocCommentOwner {
  def docComment: Option[ScDocComment] = getFirstChild.asOptionOf[ScDocComment]

  @Nullable
  def getDocComment: PsiDocComment = docComment.orNull

  def isDeprecated = false
}