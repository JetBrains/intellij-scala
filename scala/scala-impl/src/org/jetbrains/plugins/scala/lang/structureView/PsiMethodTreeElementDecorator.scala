package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.psi.PsiMethod

/**
  * @author Pavel Fatin
  */
private class PsiMethodTreeElementDecorator(method: PsiMethod, inherited: Boolean) extends PsiMethodTreeElement(method, inherited) {
  override def getPresentableText: String =
    PsiMethodTreeElementDecorator.asScalaType(super.getPresentableText)
}

private object PsiMethodTreeElementDecorator {
  private val PrimitiveType = "\\b(?:boolean|byte|char|short|int|long|float|double)\\b".r

  private val Transformations = Seq(
    "\\bvoid\\b".r -> "Unit",
    "(\\w+)\\[\\]".r -> "Array[$1]",
    "\\.\\.\\.".r -> "*",
    "\\<".r -> "[",
    "\\>".r -> "]",
    "\\?".r -> "_",
    "\\bextends\\b".r -> "<:",
    "\\bsuper\\b".r -> ">:")

  private def asScalaType(javaType: String) =
    Transformations.foldLeft(PrimitiveType.replaceAllIn(javaType, _.matched.capitalize)) {
      case (acc, (regex, replacement)) => regex.replaceAllIn(acc, replacement)
    }
}