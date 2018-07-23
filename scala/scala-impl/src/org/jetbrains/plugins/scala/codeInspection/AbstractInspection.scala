package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
  * Pavel Fatin
  */
abstract class AbstractInspection protected(override final val getDisplayName: String = AbstractInspection.formatName(getClass.getSimpleName))
  extends LocalInspectionTool {

  protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any]

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new PsiElementVisitor {

      override def visitElement(element: PsiElement): Unit = actionFor(holder) match {
        case action if action.isDefinedAt(element) => action(element)
        case _ =>
      }
    }
}

object AbstractInspection {

  private[this] val CapitalLetterPattern = "(?<!=.)\\p{Lu}".r
  private[this] val InspectionSuffix = "Inspection"

  private def formatName(simpleName: String): String = {
    val id = simpleName.stripSuffix(InspectionSuffix)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }
}