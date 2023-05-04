package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement

class ScalaWithForSurrounder extends ScalaWithForSurrounderBase {
  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "for"

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    "for (a <- as) {" + super.getTemplateAsString(elements) + "}"
  }
}
