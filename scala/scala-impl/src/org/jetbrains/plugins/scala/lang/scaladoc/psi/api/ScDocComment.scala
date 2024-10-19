package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocComment extends PsiDocComment with ScalaPsiElement {
  def tags: Seq[ScDocTag]

  override def findTagsByName(name: String): Array[PsiDocTag]

  def findTagsByName(filter: String => Boolean): Array[PsiDocTag]

  override def getOwner: PsiDocCommentOwner

  /** same as [[getDescriptionElements]] but only returns instances of [[ScDocDescriptionPart]] */
  def descriptionParts: Seq[ScDocDescriptionPart]
}
