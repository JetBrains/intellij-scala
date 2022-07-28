package org.jetbrains.plugins.scala
package lang.psi.api.toplevel.typedef

import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotations, ScAnnotationsHolder, ScModifierList}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.annotations.Nullable

trait ScDocCommentOwner extends PsiDocCommentOwner {

  @Cached(ModTracker.anyScalaPsiChange, this)
  final def docComment: Option[ScDocComment] =
    this.children.dropWhile(_.is[ScAnnotations, ScModifierList, PsiWhiteSpace])
      .headOption
      .filterByType[ScDocComment]

  @Nullable
  override def getDocComment: PsiDocComment = docComment.orNull

  override def isDeprecated: Boolean = this match {
    case holder: ScAnnotationsHolder =>
      holder.hasAnnotation("scala.deprecated") || holder.hasAnnotation("java.lang.Deprecated")
    case _ => false
  }
}