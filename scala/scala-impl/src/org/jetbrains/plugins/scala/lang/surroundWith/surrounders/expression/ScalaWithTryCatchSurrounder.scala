package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement

object ScalaWithTryCatchSurrounder extends ScalaWithTryCatchSurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "try {\n" +
      super.getTemplateAsString(elements) +
      s"\n} catch {\n case _ ${arrow(elements)} \n}"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "try / catch"
}
