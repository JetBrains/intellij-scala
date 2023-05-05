package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement

/*
 * Surrounds expression with for: for { <Cursor> } yield Expression
 */
class ScalaWithForYieldSurrounder extends ScalaWithForSurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "for (a <- as) yield {" + super.getTemplateAsString(elements) + "}"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "for / yield"
}
