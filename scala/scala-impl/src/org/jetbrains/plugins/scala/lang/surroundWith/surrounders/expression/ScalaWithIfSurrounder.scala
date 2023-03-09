package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement

/*
 * Surrounds expression with if: if { <Cursor> } Expression
 */
class ScalaWithIfSurrounder extends ScalaWithIfSurrounderBase {

  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (true) {\n" + super.getTemplateAsString(elements) + "\n}"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "if"
}
