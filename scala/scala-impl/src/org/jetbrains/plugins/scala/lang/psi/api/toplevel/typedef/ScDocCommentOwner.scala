package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.{PsiDocCommentOwner, PsiWhiteSpace}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotations, ScAnnotationsHolder, ScModifierList}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

trait ScDocCommentOwner extends PsiDocCommentOwner {

  final def docComment: Option[ScDocComment] = _docComment()

  private val _docComment = cached("docComment", ModTracker.anyScalaPsiChange, () => {
    this.children.dropWhile(_.is[ScAnnotations, ScModifierList, PsiWhiteSpace])
      .nextOption()
      .filterByType[ScDocComment]
  })

  @Nullable
  override def getDocComment: PsiDocComment = docComment.orNull

  override def isDeprecated: Boolean = this match {
    case holder: ScAnnotationsHolder =>
      holder.hasAnnotation("scala.deprecated") ||
        holder.hasAnnotation("java.lang.Deprecated") ||
        holder.hasAnnotation("kotlin.Deprecated")
    case _ => false
  }
}