package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement

/*
 * Surrounds expression with { } and if: if { <Cursor> } { Expression }
 */
class ScalaWithIfElseSurrounder extends ScalaWithIfSurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (a) { " + super.getTemplateAsString(elements) + "} else {  }"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "if / else"
}
