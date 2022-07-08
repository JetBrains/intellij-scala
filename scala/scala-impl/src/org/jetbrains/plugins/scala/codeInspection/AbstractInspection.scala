package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.{Nls, Nullable}

@deprecated(AbstractInspection.DeprecationText)
abstract class AbstractInspection protected(@Nullable @Nls customDisplayName: String = null) extends LocalInspectionTool {

  override final def getDisplayName: String =
    if (customDisplayName == null) AbstractInspection.byClassName(this)
    else customDisplayName

  protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any]

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = actionFor(holder, isOnTheFly) match {
      case action if action.isDefinedAt(element) => action(element)
      case _ =>
    }
  }

}

object AbstractInspection {

  final val DeprecationText = "use org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection instead"

  private[this] val CapitalLetterPattern = "(?<!=.)\\p{Lu}".r
  private[this] val InspectionSuffix = "Inspection"

  //noinspection ScalaDeprecation
  private def byClassName(inspection: AbstractInspection): String = {
    val id = inspection.getClass.getSimpleName.stripSuffix(InspectionSuffix)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }
}